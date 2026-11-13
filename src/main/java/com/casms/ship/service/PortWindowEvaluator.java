package com.casms.ship.service;

import com.casms.audit.AuditService;
import com.casms.common.exception.BusinessException;
import com.casms.ship.dto.PortWindowContract.*;
import com.casms.ship.entity.Port;
import com.casms.ship.enums.PortStatus;
import com.casms.ship.repository.CruiseStopRepository;
import com.casms.ship.repository.ItineraryRepository;
import com.casms.ship.repository.PortRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.*;
import java.util.*;

/**
 * One current-data evaluator for preview, create, OPS review and Ready. No nautical calculations.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PortWindowEvaluator {
    private final PortWindowService directory;
    private final PortRepository ports;
    private final CruiseStopRepository stops;
    private final ItineraryRepository itineraries;
    private final AuditService audit;

    public List<Check> cruise(UUID id) {
        var itinerary = itineraries.findByCruiseCruiseId(id).orElse(null);
        if (itinerary == null) return List.of();
        var calls = stops.findByItineraryItineraryIdOrderByStopOrderAsc(itinerary.getItineraryId());
        var cruise = itinerary.getCruise();
        if (!TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            var lockIds = new TreeSet<UUID>();
            calls.forEach(call -> lockIds.add(call.getPort().getPortId()));
            if (cruise.getDeparturePort() != null)
                lockIds.add(cruise.getDeparturePort().getPortId());
            lockIds.forEach(directory::lock);
        }
        List<Check> result = new ArrayList<>();
        if (cruise.getDeparturePort() != null
                && calls.stream()
                        .noneMatch(
                                c ->
                                        c.getPort()
                                                .getPortId()
                                                .equals(cruise.getDeparturePort().getPortId())))
            result.addAll(
                    departureMovement(
                            cruise.getDeparturePort().getPortId(),
                            itinerary.getDeparturePortFacts() == null
                                    ? null
                                    : itinerary.getDeparturePortFacts().timeZoneId(),
                            cruise.getDepartureDatetime()));
        for (var c : calls)
            result.addAll(
                    evaluate(
                            c.getPort().getPortId(),
                            c.getPortFacts() == null ? null : c.getPortFacts().timeZoneId(),
                            c.getPlannedArrival(),
                            c.getPlannedDeparture()));
        return result;
    }

    public void requireCruise(UUID id) {
        var checks = cruise(id);
        requirePass(checks);
        audit.recordPortWindowEvaluation(id, checks);
    }

    private Check result(
            UUID id,
            String type,
            LocalDateTime local,
            String zone,
            String code,
            String message,
            Window w,
            Closure closure) {
        return new Check(
                id,
                type,
                "MATCHED".equals(code) || "WINDOW_NOT_REQUIRED".equals(code) ? "PASS" : "BLOCK",
                code,
                message,
                local,
                zone,
                w == null ? null : w.windowId(),
                w == null ? null : w.revision(),
                w == null
                        ? closure == null ? null : closure.sourceReference()
                        : w.sourceReference(),
                closure == null ? null : closure.exceptionId(),
                closure == null ? null : closure.revision(),
                Instant.now(),
                List.of());
    }

    public List<Check> evaluate(
            UUID portId, String capturedZone, Instant arrival, Instant departure) {
        // All directory writers acquire this same port lock. Preview is informational and does not
        // lock.
        Port p =
                TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                        ? ports.findById(portId).orElseThrow()
                        : directory.lock(portId);
        if (capturedZone == null)
            return List.of(
                    result(
                            portId,
                            "ARRIVAL",
                            null,
                            null,
                            "PORT_SNAPSHOT_UNAVAILABLE",
                            "Chưa có múi giờ cảng đã lưu; cần rà soát lịch sử trước khi xác nhận.",
                            null,
                            null),
                    result(
                            portId,
                            "DEPARTURE",
                            null,
                            null,
                            "PORT_SNAPSHOT_UNAVAILABLE",
                            "Chưa có múi giờ cảng đã lưu; cần rà soát lịch sử trước khi xác nhận.",
                            null,
                            null));
        ZoneId zone;
        try {
            zone = ZoneId.of(capturedZone);
        } catch (java.time.DateTimeException ex) {
            return List.of(
                    result(
                            portId,
                            "ARRIVAL",
                            null,
                            capturedZone,
                            "INVALID_PORT_TIMEZONE",
                            "Múi giờ cảng không hợp lệ.",
                            null,
                            null),
                    result(
                            portId,
                            "DEPARTURE",
                            null,
                            capturedZone,
                            "INVALID_PORT_TIMEZONE",
                            "Múi giờ cảng không hợp lệ.",
                            null,
                            null));
        }
        var a = arrival == null ? null : arrival.atZone(zone).toLocalDateTime();
        var d = departure == null ? null : departure.atZone(zone).toLocalDateTime();
        String fail =
                !capturedZone.equals(p.getTimeZoneId())
                        ? "PORT_TIMEZONE_CHANGED"
                        : p.getStatus() != PortStatus.ACTIVE
                                ? "PORT_NOT_ACTIVE"
                                : a == null || d == null || !departure.isAfter(arrival)
                                        ? "INVALID_PORT_CALL_TIME"
                                        : null;
        if (fail != null)
            return List.of(
                    result(
                            portId,
                            "ARRIVAL",
                            a,
                            capturedZone,
                            fail,
                            "Cảng, múi giờ hoặc mốc ghé đã thay đổi/không hợp lệ; cần rà soát trước"
                                + " khi tiếp tục.",
                            null,
                            null),
                    result(
                            portId,
                            "DEPARTURE",
                            d,
                            capturedZone,
                            fail,
                            "Cảng, múi giờ hoặc mốc ghé đã thay đổi/không hợp lệ; cần rà soát trước"
                                + " khi tiếp tục.",
                            null,
                            null));
        return List.of(
                result(
                        portId,
                        "ARRIVAL",
                        a,
                        capturedZone,
                        "WINDOW_NOT_REQUIRED",
                        "Cảng không áp dụng khung giờ cho mốc này.",
                        null,
                        null),
                result(
                        portId,
                        "DEPARTURE",
                        d,
                        capturedZone,
                        "WINDOW_NOT_REQUIRED",
                        "Cảng không áp dụng khung giờ cho mốc này.",
                        null,
                        null));
    }

    public List<Check> departureMovement(UUID portId, String capturedZone, Instant at) {
        Port p =
                TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                        ? ports.findById(portId).orElseThrow()
                        : directory.lock(portId);
        if (capturedZone == null || !Objects.equals(capturedZone, p.getTimeZoneId()))
            return List.of(
                    result(
                            portId,
                            "DEPARTURE",
                            null,
                            capturedZone,
                            "PORT_TIMEZONE_CHANGED",
                            "Chưa có cơ sở múi giờ khởi hành đã được đối chiếu.",
                            null,
                            null));
        ZoneId z;
        try {
            z = ZoneId.of(capturedZone);
        } catch (DateTimeException ex) {
            return List.of(
                    result(
                            portId,
                            "DEPARTURE",
                            null,
                            capturedZone,
                            "INVALID_PORT_TIMEZONE",
                            "Múi giờ khởi hành không hợp lệ.",
                            null,
                            null));
        }
        var local = at.atZone(z).toLocalDateTime();
        if (p.getStatus() != PortStatus.ACTIVE)
            return List.of(
                    result(
                            portId,
                            "DEPARTURE",
                            local,
                            capturedZone,
                            "PORT_NOT_ACTIVE",
                            "Cảng khởi hành không hoạt động.",
                            null,
                            null));
        return List.of(
                result(
                        portId,
                        "DEPARTURE",
                        local,
                        capturedZone,
                        "WINDOW_NOT_REQUIRED",
                        "Cảng không áp dụng khung giờ cho mốc này.",
                        null,
                        null));
    }

    public void requirePass(List<Check> checks) {
        if (checks != null && checks.stream().anyMatch(c -> !"PASS".equals(c.status())))
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "PORT_CALL_WINDOW_VALIDATION_REQUIRED",
                    "Khung giờ cảng chưa đáp ứng cho các mốc đến/rời thực tế.",
                    Map.of("portWindowChecks", checks));
    }
}
