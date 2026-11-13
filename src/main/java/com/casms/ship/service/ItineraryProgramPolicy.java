package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Shared operations-submission rules. Drafts and departmental preparation remain independent. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItineraryProgramPolicy {
    private final JdbcTemplate jdbc;
    public record Issue(String code, String kind, UUID id, String message) {}

    /** Same lock order as staged edits: cruise, then itinerary, before reading transition state. */
    @Transactional
    public void lockForTransition(UUID itineraryId) {
        var rows = jdbc.queryForList("SELECT cruise_id FROM \"Itinerary\" WHERE itinerary_id=?", itineraryId);
        if (rows.isEmpty()) return; // transition service retains its normal not-found response
        jdbc.queryForList("SELECT cruise_id FROM \"Cruise\" WHERE cruise_id=? FOR UPDATE", rows.getFirst().get("cruise_id"));
        jdbc.queryForList("SELECT itinerary_id FROM \"Itinerary\" WHERE itinerary_id=? FOR UPDATE", itineraryId);
    }

    public static Instant instant(Object value) {
        if (value instanceof java.sql.Timestamp t) return t.toInstant();
        if (value instanceof java.time.OffsetDateTime t) return t.toInstant();
        return value instanceof Instant t ? t : null;
    }

    public static void requireWindow(Instant start, Instant end, Instant earliest, Instant latest, String code) {
        if (start == null || end == null || earliest == null || latest == null
                || !end.isAfter(start) || start.isBefore(earliest) || end.isAfter(latest))
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, code,
                    "Provide ordered times fully inside the cruise/port-call window");
    }

    public UUID requireActivityArea(UUID area, UUID ship, boolean lock) {
        return activityAreaDeck(area, ship, lock, false);
    }

    @Transactional
    public UUID prepareActivityArea(UUID area, UUID ship) {
        return activityAreaDeck(area, ship, true, true);
    }

    private UUID activityAreaDeck(UUID area, UUID ship, boolean lock, boolean allowUnclassified) {
        var rows = jdbc.queryForList("""
                SELECT o.area_usage, o.object_type, o.cabin_id, d.deck_id, d.ship_id,
                       EXISTS(SELECT 1 FROM "AreaServiceAssignment" s
                              WHERE s.area_object_id=o.ship_model_object_id AND s.enabled) AS service_assigned
                FROM "ShipModelObject" o JOIN "ShipModel" m USING(ship_model_id)
                JOIN "Deck" d ON d.deck_id=o.deck_id AND d.ship_id=m.ship_id
                WHERE o.ship_model_object_id=? AND m.ship_id=? AND m.status IN ('DRAFT','READY')
                """ + (lock ? " FOR UPDATE OF o" : ""), area, ship);
        boolean unclassified = rows.size() == 1 && "OTHER".equals(rows.getFirst().get("area_usage"));
        if (rows.size() != 1 || !"FACILITY".equals(rows.getFirst().get("object_type"))
                || (!"ACTIVITY_SPACE".equals(rows.getFirst().get("area_usage")) && !(allowUnclassified && unclassified))
                || rows.getFirst().get("cabin_id") != null
                || Boolean.TRUE.equals(rows.getFirst().get("service_assigned")))
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "ACTIVITY_AREA_REQUIRED",
                    "Chọn khu vực hoạt động hợp lệ trên tàu đã phân công.");
        if (unclassified) {
            jdbc.update("UPDATE \"ShipModelObject\" SET area_usage='ACTIVITY_SPACE' WHERE ship_model_object_id=?", area);
        }
        return (UUID) rows.getFirst().get("deck_id");
    }

    public void requireDestination(UUID stop, UUID destination) {
        if (destination == null || !Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM "CruiseStop" s JOIN "Destination" d ON d.destination_id=?
                 WHERE s.cruise_stop_id=? AND s.status NOT IN ('OMITTED','SUBSTITUTED')
                 AND d.status='ACTIVE' AND (s.destination_id=d.destination_id OR EXISTS(
                   SELECT 1 FROM "CruiseDestinationVisit" v
                    WHERE v.cruise_stop_id=s.cruise_stop_id AND v.destination_id=d.destination_id)))
                """, Boolean.class, destination, stop)))
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "EXCURSION_DESTINATION_NOT_PLANNED",
                    "The destination must be explicitly planned for this exact port call");
    }

    /** A selected tour destination must be planned for this call and reachable via its port. */
    public void requireServedDestination(UUID stop, UUID destination) {
        requireDestination(stop, destination);
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(
                  SELECT 1 FROM "CruiseStop" s
                  JOIN "PortDestinationAccess" a ON a.port_id=s.port_id AND a.destination_id=?
                  JOIN "Destination" d ON d.destination_id=a.destination_id AND d.status='ACTIVE'
                  WHERE s.cruise_stop_id=?)
                """, Boolean.class, destination, stop)))
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "EXCURSION_DESTINATION_NOT_SERVED",
                    "The selected destination is not served from this port call");
    }

    public List<Issue> issues(UUID cruiseId) {
        var issues = new ArrayList<Issue>();
        var cruises = jdbc.queryForList("SELECT * FROM \"Cruise\" WHERE cruise_id=?", cruiseId);
        if (cruises.isEmpty()) return List.of(new Issue("CRUISE_REQUIRED", "CRUISE", cruiseId, "Cruise not found"));
        var cruise = cruises.getFirst();
        try { requireWindow(instant(cruise.get("departure_datetime")), instant(cruise.get("return_datetime")),
                instant(cruise.get("departure_datetime")), instant(cruise.get("return_datetime")), "CRUISE_TIMES_REQUIRED"); }
        catch (BusinessException ex) { issues.add(issue(ex, "CRUISE", cruiseId)); }
        for (var a : jdbc.queryForList("SELECT * FROM \"OnboardActivity\" WHERE cruise_id=? AND status<>'CANCELLED'", cruiseId)) {
            UUID id = (UUID) a.get("onboard_activity_id");
            try {
                requireWindow(instant(a.get("start_time")), instant(a.get("end_time")),
                        instant(cruise.get("departure_datetime")), instant(cruise.get("return_datetime")), "ACTIVITY_OUTSIDE_CRUISE");
                requireActivityArea((UUID) a.get("area_object_id"), (UUID) cruise.get("ship_id"), false);
                requireDetails(a.get("capacity"), a.get("price"), a.get("currency_code"), a.get("location"));
            } catch (BusinessException ex) { issues.add(issue(ex, "ACTIVITY", id)); }
        }
        Instant previousDeparture = null;
        for (var stop : jdbc.queryForList("""
                SELECT s.* FROM "CruiseStop" s JOIN "Itinerary" i USING(itinerary_id)
                WHERE i.cruise_id=? AND s.status NOT IN ('OMITTED','SUBSTITUTED') ORDER BY s.stop_order
                """, cruiseId)) {
            UUID stopId = (UUID) stop.get("cruise_stop_id");
            Instant arrival = instant(stop.get("planned_arrival")), departure = instant(stop.get("planned_departure"));
            if (arrival == null || departure == null || !departure.isAfter(arrival)
                    || previousDeparture != null && arrival.isBefore(previousDeparture))
                issues.add(new Issue("PORT_CALL_TIMES_INVALID", "PORT_CALL", stopId, "Complete ordered arrival/departure times without overlapping port calls"));
            Instant allAboard = instant(stop.get("all_aboard_time"));
            if (allAboard != null && (arrival == null || departure == null || allAboard.isBefore(arrival) || allAboard.isAfter(departure)))
                issues.add(new Issue("PORT_CALL_TIMES_INVALID", "PORT_CALL", stopId, "All-aboard time must fit the port call"));
            if ("NORMAL".equals(Objects.toString(stop.get("call_type")))) {
                try { requireWindow(arrival, departure, instant(cruise.get("departure_datetime")),
                        instant(cruise.get("return_datetime")), "PORT_CALL_OUTSIDE_CRUISE"); }
                catch (BusinessException ex) { issues.add(issue(ex, "PORT_CALL", stopId)); }
            }
            previousDeparture = departure;
            for (var e : jdbc.queryForList("SELECT * FROM \"ShoreExcursion\" WHERE cruise_stop_id=? AND status<>'CANCELLED'", stopId)) {
                UUID id = (UUID) e.get("shore_excursion_id");
                try {
                    validateExcursionTimes(stop, instant(e.get("start_time")), instant(e.get("planned_return_time")), cruise);
                    requireServedDestination(stopId, (UUID) e.get("destination_id"));
                    requireDetails(e.get("maximum_capacity"), e.get("price"), e.get("currency_code"), e.get("meeting_point"));
                    if (e.get("location_text") == null || e.get("location_text").toString().isBlank())
                        throw new BusinessException("PROGRAM_DETAILS_REQUIRED", "Excursion location is required");
                    if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"Supplier\" WHERE supplier_id=? AND status='ACTIVE' AND verification_status='VERIFIED')", Boolean.class, e.get("supplier_id"))))
                        throw new BusinessException("EXCURSION_SUPPLIER_INVALID", "Excursion supplier must remain active and verified");
                } catch (BusinessException ex) { issues.add(issue(ex, "EXCURSION", id)); }
            }
        }
        return List.copyOf(issues);
    }

    public void validateExcursionTimes(Map<String, Object> stop, Instant start, Instant end, Map<String, Object> cruise) {
        Instant departure = instant(stop.get("planned_departure"));
        Instant deadline = instant(stop.get("all_aboard_time"));
        if (deadline == null || departure != null && deadline.isAfter(departure)) deadline = departure;
        Instant arrival = instant(stop.get("planned_arrival"));
        if (arrival != null && stop.get("tender_transfer_minutes") instanceof Number minutes)
            arrival = arrival.plusSeconds(Math.max(0, minutes.longValue()) * 60);
        requireWindow(start, end, arrival, deadline, "EXCURSION_OUTSIDE_PORT_CALL");
        if (!end.isBefore(deadline)) throw new BusinessException("EXCURSION_OUTSIDE_PORT_CALL", "Return before all-aboard/departure");
        requireWindow(start, end, instant(cruise.get("departure_datetime")), instant(cruise.get("return_datetime")), "EXCURSION_OUTSIDE_CRUISE");
    }

    public static void requireDetails(Object capacity, Object price, Object currency, Object location) {
        if (!(capacity instanceof Number n) || n.intValue() < 1 || !(price instanceof BigDecimal p)
                || p.signum() < 0 || !"USD".equals(Objects.toString(currency, "").trim())
                || location == null || location.toString().isBlank())
            throw new BusinessException("PROGRAM_DETAILS_REQUIRED", "Positive capacity, explicit price/currency and location are required");
    }

    private Issue issue(BusinessException ex, String kind, UUID id) {
        return new Issue(ex.getCode(), kind, id, ex.getMessage());
    }

    public void requireComplete(UUID cruiseId) {
        var issues = issues(cruiseId);
        if (!issues.isEmpty()) throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                "ITINERARY_PROGRAMS_INCOMPLETE", "Complete selected programs before sending to operations",
                Map.of("issues", issues));
    }
}
