package com.casms.ship.service;

import com.casms.checkin.enums.GuidanceSessionStatus;
import com.casms.checkin.repository.GuidanceSessionRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.notification.entity.Notification;
import com.casms.notification.repository.NotificationRepository;
import com.casms.servicepackage.repository.ServiceScheduleRepository;
import com.casms.ship.dto.PortWindowContract.Check;
import com.casms.ship.dto.request.CruiseDelayRequest;
import com.casms.ship.dto.response.CruiseSchedulePreviewResponse;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.CruiseAuditEvent;
import com.casms.ship.entity.CruiseStop;
import com.casms.ship.entity.Itinerary;
import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.enums.OperationalIssueStatus;
import com.casms.ship.repository.*;
import com.casms.ship.util.PortTimeZoneHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CruiseDelayService {
    private static final String EVENT_TYPE = "CRUISE_PRE_DEPARTURE_DELAY";

    private final CruiseRepository cruises;
    private final ShipRepository ships;
    private final ItineraryRepository itineraries;
    private final CruiseStopRepository stops;
    private final CruiseStaffRepository staff;
    private final OperationalIssueRepository issues;
    private final GuidanceSessionRepository guidance;
    private final ServiceScheduleRepository serviceSchedules;
    private final CruiseAuditEventRepository events;
    private final CruiseAuditService audit;
    private final NotificationRepository notifications;
    private final CruiseService cruiseService;
    private final ScheduleMaterializationService schedules;
    private final PortWindowEvaluator portWindows;
    private final EntityManager entityManager;
    private final ObjectMapper json;

    public record Preview(
            long revision, Instant evaluatedAt, long secondsUntilDeparture,
            List<String> blockers, List<Check> portWindowChecks,
            JsonNode before, CruiseSchedulePreviewResponse after) {}

    public record Event(
            UUID eventId, UUID requestId, UUID cruiseId, UUID createdBy, Instant createdAt,
            int delayMinutes, String category, String reason, String status, long revision,
            JsonNode before, JsonNode after) {}

    public record History(
            Instant originalDeparture, Instant expectedDeparture, Instant actualDeparture,
            long totalDelayMinutes, List<Event> events) {}

    @Transactional(readOnly = true)
    public History history(UUID cruiseId) {
        Cruise cruise = cruises.findById(cruiseId).orElseThrow(() -> missing(cruiseId));
        List<Event> history = events.findByCruise_CruiseIdAndEventTypeOrderByTimestampDesc(
                        cruiseId, EVENT_TYPE).stream().map(this::event).toList();
        Instant original = history.isEmpty() ? cruise.getDepartureDatetime()
                : Instant.parse(history.getLast().before().required("departureDatetime").asText());
        return new History(original, cruise.getDepartureDatetime(), cruise.getActualDepartureDatetime(),
                history.stream().mapToLong(Event::delayMinutes).sum(), history);
    }

    @Transactional(readOnly = true)
    public Preview preview(UUID cruiseId, CruiseDelayRequest request) {
        Cruise cruise = cruises.findById(cruiseId).orElseThrow(() -> missing(cruiseId));
        requireRevision(cruise, request);
        Itinerary itinerary = itineraries.findByCruiseCruiseId(cruiseId).orElse(null);
        return evaluate(cruise, itinerary, calls(itinerary), request);
    }

    @Transactional
    public Event confirm(UUID cruiseId, CruiseDelayRequest request, UUID actorId) {
        Cruise cruise = cruises.findByIdForUpdate(cruiseId).orElseThrow(() -> missing(cruiseId));
        String identity = identity(cruiseId, request.requestId());
        var existing = events.findByCruise_CruiseIdAndEventTypeAndEventIdentity(cruiseId, EVENT_TYPE, identity);
        if (existing.isPresent()) {
            Event result = event(existing.get());
            if (!Objects.equals(result.createdBy(), actorId)
                    || result.delayMinutes() != request.delayMinutes()
                    || !result.category().equals(request.category())
                    || !result.reason().equals(request.reason().trim())) {
                throw new BusinessException(HttpStatus.CONFLICT, "DELAY_REQUEST_REUSED",
                        "Request ID đã được dùng cho một yêu cầu khác.");
            }
            return result;
        }
        requireRevision(cruise, request);
        if (cruise.getShip() != null) {
            var ship = ships.findByIdForUpdate(cruise.getShip().getShipId()).orElseThrow();
            entityManager.refresh(ship);
        }
        Itinerary itinerary = itineraries.findByCruiseCruiseId(cruiseId).orElse(null);
        List<CruiseStop> calls = calls(itinerary);
        calls.forEach(call -> entityManager.refresh(call, LockModeType.PESSIMISTIC_WRITE));
        Preview preview = evaluate(cruise, itinerary, calls, request);
        if (!preview.blockers().isEmpty()) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "CRUISE_DELAY_BLOCKED",
                    "Chuyến khởi hành không đủ điều kiện áp dụng delay.",
                    Map.of("blockers", preview.blockers(), "portWindowChecks", preview.portWindowChecks()));
        }
        Instant previousDeparture = cruise.getDepartureDatetime();
        if (!Instant.now().isBefore(previousDeparture)) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "DEPARTURE_TIME_REACHED",
                    "Đã tới thời điểm khởi hành dự kiến.");
        }
        cruise.setDepartureDatetime(preview.after().getDepartureDatetime());
        cruise.setReturnDatetime(preview.after().getReturnDatetime());
        cruiseService.shiftScheduleAndPrograms(cruise, previousDeparture, cruise.getDepartureDatetime());
        var checksByStop = preview.after().getDays().stream().flatMap(day -> day.getStops().stream())
                .collect(java.util.stream.Collectors.toMap(
                        CruiseSchedulePreviewResponse.ProjectedStopDto::getCruiseStopId,
                        CruiseSchedulePreviewResponse.ProjectedStopDto::getPortWindowChecks));
        Duration shift = Duration.ofMinutes(request.delayMinutes());
        for (CruiseStop call : calls) {
            call.setAllAboardTime(shift(call.getAllAboardTime(), shift));
            call.setPortWindowEvidence(checksByStop.getOrDefault(call.getCruiseStopId(), List.of()));
        }
        cruises.flush();
        CruiseAuditEvent saved = audit.recordEvent(cruise, EVENT_TYPE, identity, actorId,
                "OPERATIONS_MANAGER", "CRUISE_START", "WHOLE_CRUISE", preview.before().toString(),
                json.valueToTree(preview.after()).toString(), request.reason().trim(),
                json.valueToTree(Map.of("delayMinutes", request.delayMinutes(), "category", request.category())).toString());
        if (saved == null) {
            throw new BusinessException(HttpStatus.CONFLICT, "DELAY_REQUEST_REUSED",
                    "Request ID đã được sử dụng.");
        }
        notifyAssignedStaff(cruise, actorId);
        return event(saved);
    }

    private void notifyAssignedStaff(Cruise cruise, UUID actorId) {
        var notified = new java.util.HashSet<UUID>();
        notified.add(actorId);
        for (var member : staff.findByCruise_CruiseId(cruise.getCruiseId())) {
            if (!"ACTIVE".equals(member.getStatus()) || !notified.add(member.getUser().getUserId())) continue;
            Notification message = new Notification();
            message.setRecipientUser(member.getUser());
            message.setNotificationType(EVENT_TYPE);
            message.setTitle("Chuyến khởi hành đã dời giờ");
            message.setContent(cruise.getCruiseCode() + " · Giờ khởi hành mới: "
                    + cruise.getDepartureDatetime().atZone(PortTimeZoneHelper.SHIP_TIME_ZONE));
            notifications.save(message);
        }
    }

    private Preview evaluate(Cruise cruise, Itinerary itinerary, List<CruiseStop> calls, CruiseDelayRequest request) {
        UUID id = cruise.getCruiseId();
        Instant now = Instant.now();
        Instant departure = cruise.getDepartureDatetime();
        Instant arrival = cruise.getReturnDatetime();
        List<String> blockers = new ArrayList<>();
        if (cruise.getStatus() != CruiseStatus.PUBLISHED) blockers.add("CRUISE_NOT_PUBLISHED");
        if (cruise.getActualDepartureDatetime() != null
                || calls.stream().anyMatch(call -> call.getActualDeparture() != null)) {
            blockers.add("ACTUAL_DEPARTURE_RECORDED");
        }
        if (departure == null || !now.isBefore(departure)) blockers.add("DEPARTURE_TIME_REACHED");
        if (cruise.getShip() == null) blockers.add("SHIP_NOT_ASSIGNED");
        if (cruise.getDeparturePort() == null) blockers.add("DEPARTURE_PORT_REQUIRED");
        if (itinerary == null || arrival == null || departure == null || !arrival.isAfter(departure)) {
            blockers.add("OPERATIONAL_SCHEDULE_REQUIRED");
        }
        if (blockers.isEmpty()) {
            var gate = cruiseService.validateDepartureReadiness(id);
            if (!gate.isReady()) {
                blockers.add("CRUISE_NOT_DEPARTURE_READY");
                blockers.addAll(gate.getBlockers());
            }
        }
        if (issues.countByCruise_CruiseIdAndStatus(id, OperationalIssueStatus.OPEN) > 0
                || issues.countByCruise_CruiseIdAndStatus(id, OperationalIssueStatus.IN_PROGRESS) > 0) {
            blockers.add("UNRESOLVED_OPERATIONAL_ISSUES");
        }
        if (serviceSchedules.countByCruise_CruiseId(id) > 0) blockers.add("FIXED_SERVICE_SCHEDULE");
        if (guidance.findByCruise_CruiseId(id).stream().anyMatch(session ->
                session.getStatus() == GuidanceSessionStatus.Scheduled
                        || session.getStatus() == GuidanceSessionStatus.Published)) {
            blockers.add("FIXED_GUIDANCE_SESSION");
        }
        var projected = schedules.getCruiseSchedule(id);
        JsonNode before = json.valueToTree(projected);
        List<Check> checks = new ArrayList<>();
        if (itinerary != null && departure != null && arrival != null && arrival.isAfter(departure)) {
            Duration delta = Duration.ofMinutes(request.delayMinutes());
            Instant newDeparture = departure.plus(delta);
            Instant newReturn = arrival.plus(delta);
            if (cruise.getShip() != null && cruises.existsOverlappingCruisesExcluding(
                    cruise.getShip().getShipId(), id, newDeparture, newReturn)) blockers.add("SHIP_SCHEDULE_CONFLICT");
            if (staff.findByCruise_CruiseId(id).stream().filter(member -> "ACTIVE".equals(member.getStatus()))
                    .anyMatch(member -> !staff.findConflictingAssignments(
                            member.getUser().getUserId(), newDeparture, newReturn, id).isEmpty())) {
                blockers.add("STAFF_SCHEDULE_CONFLICT");
            }
            project(projected, itinerary, delta, checks, blockers);
        }
        return new Preview(cruise.getRevision(), now,
                departure == null ? 0 : Math.max(0, Duration.between(now, departure).toSeconds()),
                List.copyOf(blockers), List.copyOf(checks), before, projected);
    }

    private void project(CruiseSchedulePreviewResponse schedule, Itinerary itinerary,
            Duration delta, List<Check> checks, List<String> blockers) {
        var zone = PortTimeZoneHelper.SHIP_TIME_ZONE;
        Instant departure = schedule.getDepartureDatetime();
        Instant newDeparture = departure.plus(delta);
        Instant newReturn = schedule.getReturnDatetime().plus(delta);
        LocalDate firstDay = newDeparture.atZone(zone).toLocalDate();
        long dayShift = java.time.temporal.ChronoUnit.DAYS.between(departure.atZone(zone).toLocalDate(), firstDay);
        if (!schedule.getReturnDatetime().atZone(zone).toLocalDate().plusDays(dayShift)
                .equals(newReturn.atZone(zone).toLocalDate())) blockers.add("SCHEDULE_DAY_BOUNDARY_CHANGED");
        schedule.setDepartureDatetime(newDeparture);
        schedule.setReturnDatetime(newReturn);
        boolean departureCovered = false;
        for (var day : schedule.getDays()) {
            day.setCalendarDate(day.getCalendarDate().plusDays(dayShift));
            for (var stop : day.getStops()) {
                if (PortTimeZoneHelper.isValidTimeZoneId(stop.getPortTimeZoneId())) {
                    var portZone = PortTimeZoneHelper.resolveZone(stop.getPortTimeZoneId());
                    checkDayBoundary(stop.getPlannedArrival(), delta, dayShift, portZone, blockers);
                    checkDayBoundary(stop.getPlannedDeparture(), delta, dayShift, portZone, blockers);
                }
                stop.setPlannedArrival(shift(stop.getPlannedArrival(), delta));
                stop.setPlannedDeparture(shift(stop.getPlannedDeparture(), delta));
                stop.setAllAboardTime(shift(stop.getAllAboardTime(), delta));
                var result = portWindows.evaluate(stop.getPortId(), stop.getPortTimeZoneId(),
                        stop.getPlannedArrival(), stop.getPlannedDeparture());
                stop.setPortWindowChecks(result);
                checks.addAll(result);
                departureCovered |= Objects.equals(stop.getPortId(), schedule.getDeparturePortId())
                        && Objects.equals(stop.getPlannedDeparture(), newDeparture);
            }
            for (var entry : day.getEntries()) {
                var entryZone = PortTimeZoneHelper.isValidTimeZoneId(entry.getTimeZoneId())
                        ? PortTimeZoneHelper.resolveZone(entry.getTimeZoneId()) : zone;
                checkDayBoundary(entry.getPlannedStartTime(), delta, dayShift, entryZone, blockers);
                checkDayBoundary(entry.getPlannedEndTime(), delta, dayShift, entryZone, blockers);
                entry.setPlannedStartTime(shift(entry.getPlannedStartTime(), delta));
                entry.setPlannedEndTime(shift(entry.getPlannedEndTime(), delta));
            }
        }
        if (!departureCovered && schedule.getDeparturePortId() != null) {
            checks.addAll(portWindows.departureMovement(schedule.getDeparturePortId(),
                    itinerary.getDeparturePortFacts() == null ? null : itinerary.getDeparturePortFacts().timeZoneId(),
                    newDeparture));
        }
        boolean passed = !checks.isEmpty() && checks.stream().allMatch(check -> "PASS".equals(check.status()));
        schedule.setPortCallWindowStatus(passed ? "PASS" : "BLOCK");
        schedule.setPortWindowChecks(List.copyOf(checks));
        if (!passed) blockers.add("PORT_WINDOW_BLOCKED");
    }

    private static void checkDayBoundary(Instant time, Duration delta, long dayShift,
            java.time.ZoneId zone, List<String> blockers) {
        if (time != null && !time.atZone(zone).toLocalDate().plusDays(dayShift)
                .equals(time.plus(delta).atZone(zone).toLocalDate())
                && !blockers.contains("SCHEDULE_DAY_BOUNDARY_CHANGED")) {
            blockers.add("SCHEDULE_DAY_BOUNDARY_CHANGED");
        }
    }

    private List<CruiseStop> calls(Itinerary itinerary) {
        return itinerary == null ? List.of()
                : stops.findByItineraryItineraryIdOrderByStopOrderAsc(itinerary.getItineraryId());
    }

    private Event event(CruiseAuditEvent row) {
        try {
            JsonNode evidence = json.readTree(row.getEvidence());
            return new Event(row.getEventId(), UUID.fromString(row.getEventIdentity().substring(
                    row.getEventIdentity().lastIndexOf(':') + 1)), row.getCruise().getCruiseId(),
                    row.getActorId(), row.getTimestamp(), evidence.required("delayMinutes").asInt(),
                    evidence.required("category").asText(), row.getReason(), "CONFIRMED", row.getRevision(),
                    json.readTree(row.getBeforeDigest()), json.readTree(row.getAfterDigest()));
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalidAudit) {
            throw new IllegalStateException("Invalid stored cruise delay event", invalidAudit);
        }
    }

    private static void requireRevision(Cruise cruise, CruiseDelayRequest request) {
        if (!Objects.equals(cruise.getRevision(), request.expectedRevision())) {
            throw new BusinessException(HttpStatus.CONFLICT, "STALE_CRUISE_REVISION",
                    "Lịch trình đã thay đổi. Hãy tải lại và xem trước delay.");
        }
    }

    private static String identity(UUID cruiseId, UUID requestId) {
        return "DELAY:" + cruiseId + ":" + requestId;
    }

    private static Instant shift(Instant time, Duration delta) {
        return time == null ? null : time.plus(delta);
    }

    private static ResourceNotFoundException missing(UUID cruiseId) {
        return new ResourceNotFoundException("Cruise", cruiseId);
    }
}
