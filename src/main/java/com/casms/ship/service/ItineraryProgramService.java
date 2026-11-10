package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.common.service.BusinessCodeGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class ItineraryProgramService {
    private final JdbcTemplate jdbc;
    private final CruiseMainflowService plans;
    private final ItineraryProgramPolicy policy;
    private final BusinessCodeGenerator codes;

    public record ShoreSelection(UUID sourceExcursionId, UUID cruiseStopId, UUID destinationId) {}
    public record AssignmentResult(long revision, List<UUID> activityIds, List<UUID> excursionIds) {}
    public record ActivitySchedule(Instant startsAt, Instant endsAt, UUID areaId, String location,
                                   Integer capacity, BigDecimal price) {}
    public record ExcursionSchedule(Instant startsAt, Instant returnsAt, String meetingPoint,
                                    String location, Integer capacity, BigDecimal price) {}

    @Transactional(readOnly = true)
    public Map<String, Object> read(UUID itineraryId) {
        var itinerary = itinerary(itineraryId);
        UUID cruise = (UUID) itinerary.get("cruise_id");
        return Map.of("cruiseId", cruise,
                "revision", jdbc.queryForObject("SELECT revision FROM \"Cruise\" WHERE cruise_id=?", Long.class, cruise),
                "activities", statusAsText(jdbc.queryForList("SELECT * FROM \"OnboardActivity\" WHERE cruise_id=? AND NOT catalog_entry ORDER BY created_at,onboard_activity_id", cruise)),
                "excursions", statusAsText(jdbc.queryForList("SELECT e.* FROM \"ShoreExcursion\" e JOIN \"CruiseStop\" s USING(cruise_stop_id) WHERE s.itinerary_id=? ORDER BY e.created_at,e.shore_excursion_id", itineraryId)),
                "issues", policy.issues(cruise));
    }

    private List<Map<String,Object>> statusAsText(List<Map<String,Object>> rows) {
        rows.forEach(row -> row.computeIfPresent("status", (key, value) -> value.toString()));
        return rows;
    }

    private Map<String, Object> itinerary(UUID id) {
        var rows = jdbc.queryForList("SELECT * FROM \"Itinerary\" WHERE itinerary_id=?", id);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Itinerary", id);
        return rows.getFirst();
    }

    private UUID start(UUID itineraryId, Long revision) {
        UUID cruise = (UUID) itinerary(itineraryId).get("cruise_id");
        plans.assertEditable(cruise); // shared cruise lock serializes edits against operations submission
        var i = jdbc.queryForMap("SELECT * FROM \"Itinerary\" WHERE itinerary_id=? FOR UPDATE", itineraryId);
        if (!"DRAFT".equals(Objects.toString(i.get("status"))))
            throw new BusinessException(HttpStatus.CONFLICT, "ITINERARY_NOT_EDITABLE", "Return itinerary to draft before editing programs");
        Long current = jdbc.queryForObject("SELECT revision FROM \"Cruise\" WHERE cruise_id=?", Long.class, cruise);
        if (revision == null || !revision.equals(current))
            throw new BusinessException(HttpStatus.CONFLICT, "STALE_REVISION", "Reload the cruise revision before editing");
        return cruise;
    }

    private long finish(UUID cruise) {
        jdbc.update("UPDATE \"Cruise\" SET revision=revision+1 WHERE cruise_id=?", cruise);
        return jdbc.queryForObject("SELECT revision FROM \"Cruise\" WHERE cruise_id=?", Long.class, cruise);
    }

    /** Additive copy of selected definitions; no fake dates or automatic catalog-wide assignment. */
    public AssignmentResult assign(UUID itineraryId, Long expected, List<UUID> onboard, List<ShoreSelection> shores) {
        UUID cruise = start(itineraryId, expected);
        List<UUID> activities = new ArrayList<>(), excursions = new ArrayList<>();
        for (UUID sourceId : onboard) {
            var sources = jdbc.queryForList("SELECT * FROM \"OnboardActivity\" WHERE onboard_activity_id=? AND catalog_entry AND status='PLANNED' FOR SHARE", sourceId);
            if (sources.isEmpty()) throw new BusinessException("ACTIVITY_SOURCE_REQUIRED", "Select an active activity definition");
            UUID id = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO "OnboardActivity" (onboard_activity_id,activity_code,activity_name,description,
                      category,capacity,price,currency_code,status,cruise_id,catalog_entry,source_activity_id,created_at,updated_at)
                    SELECT ?,?,activity_name,description,category,capacity,price,currency_code,'PLANNED',?,false,onboard_activity_id,now(),now()
                    FROM "OnboardActivity" WHERE onboard_activity_id=?
                    """, id, codes.next("AC"), cruise, sourceId);
            activities.add(id);
        }
        for (ShoreSelection selection : shores) {
            var stops = jdbc.queryForList("SELECT * FROM \"CruiseStop\" WHERE cruise_stop_id=? AND itinerary_id=? FOR UPDATE", selection.cruiseStopId(), itineraryId);
            if (stops.isEmpty()) throw new BusinessException("PORT_CALL_SCOPE_MISMATCH", "Port call must belong to this itinerary");
            policy.requireDestination(selection.cruiseStopId(), selection.destinationId());
            var sources = jdbc.queryForList("SELECT * FROM \"ShoreExcursion\" WHERE shore_excursion_id=? AND cruise_stop_id IS NULL AND status='PLANNED' FOR SHARE", selection.sourceExcursionId());
            if (sources.isEmpty()) throw new BusinessException("EXCURSION_SOURCE_REQUIRED", "Select an excursion definition");
            var source = sources.getFirst();
            if (source.get("port_id") != null && !source.get("port_id").equals(stops.getFirst().get("port_id"))
                    || source.get("destination_id") != null && !source.get("destination_id").equals(selection.destinationId()))
                throw new BusinessException("EXCURSION_SOURCE_DESTINATION_MISMATCH", "Source destination/port does not match the selected call");
            UUID id = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO "ShoreExcursion" (shore_excursion_id,cruise_stop_id,port_id,destination_id,
                      source_excursion_id,excursion_code,excursion_name,supplier_id,meeting_point,minimum_participants,
                      maximum_capacity,price,currency_code,status,is_mandatory,created_at,updated_at)
                    SELECT ?,?,?,?,shore_excursion_id,?,excursion_name,supplier_id,meeting_point,minimum_participants,
                      maximum_capacity,price,currency_code,'PLANNED',false,now(),now()
                    FROM "ShoreExcursion" WHERE shore_excursion_id=?
                    """, id, selection.cruiseStopId(), stops.getFirst().get("port_id"), selection.destinationId(),
                    codes.next("EX"), selection.sourceExcursionId());
            excursions.add(id);
        }
        return new AssignmentResult(finish(cruise), activities, excursions);
    }

    public long scheduleActivity(UUID itineraryId, UUID activityId, Long expected, ActivitySchedule input) {
        UUID cruiseId = start(itineraryId, expected);
        var rows = jdbc.queryForList("SELECT * FROM \"OnboardActivity\" WHERE onboard_activity_id=? AND cruise_id=? AND NOT catalog_entry FOR UPDATE", activityId, cruiseId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Activity in itinerary", activityId);
        requirePlanned(rows.getFirst());
        var cruise = jdbc.queryForMap("SELECT * FROM \"Cruise\" WHERE cruise_id=?", cruiseId);
        ItineraryProgramPolicy.requireWindow(input.startsAt(), input.endsAt(),
                ItineraryProgramPolicy.instant(cruise.get("departure_datetime")),
                ItineraryProgramPolicy.instant(cruise.get("return_datetime")), "ACTIVITY_OUTSIDE_CRUISE");
        policy.requireActivityArea(input.areaId(), (UUID) cruise.get("ship_id"), true);
        ItineraryProgramPolicy.requireDetails(input.capacity(), input.price(), "USD", input.location());
        requireAllocatedCapacity("onboard_activity_id", activityId, input.capacity());
        jdbc.update("""
                UPDATE "OnboardActivity" SET start_time=?,end_time=?,area_object_id=?,location=?,capacity=?,
                  price=?,currency_code='USD',deck_id=(SELECT deck_id FROM "ShipModelObject" WHERE ship_model_object_id=?),
                  cabin_id=NULL,updated_at=now() WHERE onboard_activity_id=?
                """, timestamp(input.startsAt()), timestamp(input.endsAt()), input.areaId(), input.location().trim(),
                input.capacity(), input.price(), input.areaId(), activityId);
        return finish(cruiseId);
    }

    public long scheduleExcursion(UUID itineraryId, UUID excursionId, Long expected, ExcursionSchedule input) {
        UUID cruiseId = start(itineraryId, expected);
        var rows = jdbc.queryForList("""
                SELECT e.* FROM "ShoreExcursion" e JOIN "CruiseStop" s USING(cruise_stop_id)
                WHERE e.shore_excursion_id=? AND s.itinerary_id=? FOR UPDATE OF e,s
                """, excursionId, itineraryId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Excursion in itinerary", excursionId);
        var excursion = rows.getFirst(); requirePlanned(excursion);
        var stop = jdbc.queryForMap("SELECT * FROM \"CruiseStop\" WHERE cruise_stop_id=?", excursion.get("cruise_stop_id"));
        var cruise = jdbc.queryForMap("SELECT * FROM \"Cruise\" WHERE cruise_id=?", cruiseId);
        policy.requireDestination((UUID) stop.get("cruise_stop_id"), (UUID) excursion.get("destination_id"));
        policy.validateExcursionTimes(stop, input.startsAt(), input.returnsAt(), cruise);
        ItineraryProgramPolicy.requireDetails(input.capacity(), input.price(), "USD", input.meetingPoint());
        if (input.location() == null || input.location().isBlank()) throw new BusinessException("PROGRAM_DETAILS_REQUIRED", "Excursion location is required");
        if (excursion.get("minimum_participants") instanceof Number min && min.intValue() > input.capacity())
            throw new BusinessException("INVALID_CAPACITY", "Capacity cannot be below minimum participants");
        requireAllocatedCapacity("shore_excursion_id", excursionId, input.capacity());
        jdbc.update("""
                UPDATE "ShoreExcursion" SET start_time=?,planned_return_time=?,meeting_point=?,location_text=?,
                  maximum_capacity=?,price=?,currency_code='USD',updated_at=now() WHERE shore_excursion_id=?
                """, timestamp(input.startsAt()), timestamp(input.returnsAt()), input.meetingPoint().trim(),
                input.location().trim(), input.capacity(), input.price(), excursionId);
        return finish(cruiseId);
    }

    public long remove(UUID itineraryId, String kind, UUID id, Long expected) {
        UUID cruise = start(itineraryId, expected);
        boolean activity = switch (kind) {
            case "activities" -> true;
            case "excursions" -> false;
            default -> throw new IllegalArgumentException("Unknown program kind");
        };
        var rows = activity
                ? jdbc.queryForList("SELECT * FROM \"OnboardActivity\" WHERE onboard_activity_id=? AND cruise_id=? AND NOT catalog_entry FOR UPDATE", id, cruise)
                : jdbc.queryForList("SELECT e.* FROM \"ShoreExcursion\" e JOIN \"CruiseStop\" s USING(cruise_stop_id) WHERE e.shore_excursion_id=? AND s.itinerary_id=? FOR UPDATE OF e", id, itineraryId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Program in itinerary", id);
        requirePlanned(rows.getFirst());
        String column = activity ? "onboard_activity_id" : "shore_excursion_id";
        Integer reserved = jdbc.queryForObject("SELECT COALESCE(sum(quantity),0)::integer FROM \"Registration\" WHERE "
                + column + "=? AND status IN ('PENDING','CONFIRMED','COMPLETED')", Integer.class, id);
        if (reserved != null && reserved > 0) throw new BusinessException("PROGRAM_HAS_REGISTRATIONS", "Use the registration cancellation workflow first");
        if (activity) jdbc.update("UPDATE \"OnboardActivity\" SET status='CANCELLED',updated_at=now() WHERE onboard_activity_id=?", id);
        else jdbc.update("UPDATE \"ShoreExcursion\" SET status='CANCELLED',cancellation_reason='Removed from draft itinerary',updated_at=now() WHERE shore_excursion_id=?", id);
        return finish(cruise);
    }

    private void requirePlanned(Map<String, Object> row) {
        if (!"PLANNED".equals(Objects.toString(row.get("status"))))
            throw new BusinessException(HttpStatus.CONFLICT, "PROGRAM_NOT_EDITABLE", "Only planned occurrences can be configured");
    }

    private void requireAllocatedCapacity(String column, UUID id, int capacity) {
        // Column is selected from two internal constants; values always use bound parameters.
        Integer reserved = jdbc.queryForObject("SELECT COALESCE(sum(quantity),0)::integer FROM \"Registration\" WHERE "
                + column + "=? AND status IN ('PENDING','CONFIRMED','COMPLETED')", Integer.class, id);
        if (reserved != null && capacity < reserved) throw new BusinessException("CAPACITY_BELOW_ALLOCATED", "Capacity cannot be below existing reservations");
    }

    private java.sql.Timestamp timestamp(Instant value) { return java.sql.Timestamp.from(value); }
}
