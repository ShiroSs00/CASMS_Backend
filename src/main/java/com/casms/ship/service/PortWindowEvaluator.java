package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.ship.dto.PortWindowContract.*;
import com.casms.ship.entity.Port;
import com.casms.ship.enums.PortStatus;

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
    private final com.casms.ship.repository.PortRepository ports;
    private final com.casms.ship.repository.CruiseStopRepository stops;
    private final com.casms.ship.repository.ItineraryRepository itineraries;
    private final com.casms.audit.AuditService audit;

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
                                                        .equals(
                                                                cruise.getDeparturePort()
                                                                        .getPortId())
                                                && Objects.equals(
                                                        c.getPlannedDeparture(),
                                                        cruise.getDepartureDatetime())))
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

    private boolean date(LocalDate d, LocalDate from, LocalDate to, LocalDate source) {
        return !d.isBefore(from)
                && (to == null || !d.isAfter(to))
                && (source == null || !d.isBefore(source));
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
                "MATCHED".equals(code) ? "PASS" : "BLOCK",
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
        Directory data = directory.read(portId);
        // A verified effective closure cannot be bypassed by a recurring window, including a closed
        // date inside an overnight stay.
        var closed =
                data.exceptions().stream()
                        .filter(
                                c ->
                                        "VERIFIED".equals(c.verificationStatus())
                                                && "ACTIVE".equals(c.lifecycleStatus()))
                        .filter(
                                c ->
                                        !c.validFrom().isAfter(d.toLocalDate())
                                                && !c.validTo().isBefore(a.toLocalDate())
                                                && (c.sourceEffectiveDate() == null
                                                        || !c.sourceEffectiveDate()
                                                                .isAfter(d.toLocalDate())))
                        .toList();
        if (!closed.isEmpty()) {
            Closure c = closed.getFirst();
            String code =
                    Objects.equals(c.verifiedTimeZoneId(), capturedZone)
                            ? "CLOSED"
                            : "PORT_TIMEZONE_CHANGED";
            return List.of(
                    result(
                            portId,
                            "ARRIVAL",
                            a,
                            capturedZone,
                            code,
                            "Ngày ghé có thông báo đóng cảng đã xác minh: " + c.reason(),
                            null,
                            c),
                    result(
                            portId,
                            "DEPARTURE",
                            d,
                            capturedZone,
                            code,
                            "Ngày ghé có thông báo đóng cảng đã xác minh: " + c.reason(),
                            null,
                            c));
        }
        boolean overnight = !a.toLocalDate().equals(d.toLocalDate());
        long stay = Duration.between(arrival, departure).getSeconds();
        List<StayEvidence> permissionEvidence = new ArrayList<>();
        if (overnight) {
            // Permission to remain at Port is separate from matching arrival/departure hours.
            // Use only source-verified evidence applying to these actual movements; conflicting
            // prohibitions fail closed.
            var stayEvidence =
                    data.windows().stream()
                            .filter(
                                    w ->
                                            "VERIFIED".equals(w.verificationStatus())
                                                    && "ACTIVE".equals(w.lifecycleStatus())
                                                    && Objects.equals(
                                                            capturedZone, w.verifiedTimeZoneId()))
                            .filter(
                                    w -> {
                                        var at = "ARRIVAL".equals(w.windowType()) ? a : d;
                                        return date(
                                                        at.toLocalDate(),
                                                        w.validFrom(),
                                                        w.validTo(),
                                                        w.sourceEffectiveDate())
                                                && w.daysOfWeek().contains(at.getDayOfWeek())
                                                && !at.toLocalTime().isBefore(w.localStartTime())
                                                && at.toLocalTime().isBefore(w.localEndTime());
                                    })
                            .toList();
            stayEvidence.forEach(
                    w ->
                            permissionEvidence.add(
                                    new StayEvidence(
                                            w.windowId(),
                                            w.revision(),
                                            w.sourceReference(),
                                            w.overnightAllowed(),
                                            w.maxStayMinutes(),
                                            w.verifiedBy(),
                                            w.verifiedAt())));
            if (stayEvidence.stream().noneMatch(w -> Boolean.TRUE.equals(w.overnightAllowed()))
                    || stayEvidence.stream()
                            .anyMatch(w -> Boolean.FALSE.equals(w.overnightAllowed())))
                return List.of(
                        result(
                                portId,
                                "ARRIVAL",
                                a,
                                capturedZone,
                                "OVERNIGHT_NOT_ALLOWED",
                                "Chưa có bằng chứng đã xác minh cho phép lưu qua đêm, hoặc nguồn có"
                                    + " điều kiện không cho phép.",
                                null,
                                null),
                        result(
                                portId,
                                "DEPARTURE",
                                d,
                                capturedZone,
                                "OVERNIGHT_NOT_ALLOWED",
                                "Chưa có bằng chứng đã xác minh cho phép lưu qua đêm, hoặc nguồn có"
                                    + " điều kiện không cho phép.",
                                null,
                                null));
        }
        return List.of(
                movement(data, "ARRIVAL", a, capturedZone, false, stay)
                        .withStayEvidence(permissionEvidence),
                movement(data, "DEPARTURE", d, capturedZone, false, stay)
                        .withStayEvidence(permissionEvidence));
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
        var data = directory.read(portId);
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
        var closed =
                data.exceptions().stream()
                        .filter(
                                c ->
                                        "VERIFIED".equals(c.verificationStatus())
                                                && "ACTIVE".equals(c.lifecycleStatus())
                                                && date(
                                                        local.toLocalDate(),
                                                        c.validFrom(),
                                                        c.validTo(),
                                                        c.sourceEffectiveDate()))
                        .findFirst();
        if (closed.isPresent()) {
            var c = closed.get();
            return List.of(
                    result(
                            portId,
                            "DEPARTURE",
                            local,
                            capturedZone,
                            Objects.equals(c.verifiedTimeZoneId(), capturedZone)
                                    ? "CLOSED"
                                    : "PORT_TIMEZONE_CHANGED",
                            "Ngày khởi hành có thông báo đóng cảng đã xác minh: " + c.reason(),
                            null,
                            c));
        }
        return List.of(movement(data, "DEPARTURE", local, capturedZone, false, 0));
    }

    private Check movement(
            Directory data,
            String type,
            LocalDateTime at,
            String zone,
            boolean overnight,
            long staySeconds) {
        var configured = data.windows().stream().filter(w -> type.equals(w.windowType())).toList();
        String code = "WINDOW_NOT_CONFIGURED",
                message =
                        "Chưa cấu hình khung giờ tàu "
                                + ("ARRIVAL".equals(type) ? "đến" : "rời")
                                + ".";
        if (!configured.isEmpty()) {
            var verified =
                    configured.stream()
                            .filter(w -> "VERIFIED".equals(w.verificationStatus()))
                            .toList();
            code = "WINDOW_NOT_VERIFIED";
            message = "Khung giờ chưa được Quản lý vận hành xác minh.";
            if (!verified.isEmpty()) {
                var active =
                        verified.stream()
                                .filter(w -> "ACTIVE".equals(w.lifecycleStatus()))
                                .toList();
                code = "WINDOW_NOT_ACTIVE";
                message = "Khung giờ đã xác minh chưa được kích hoạt.";
                if (!active.isEmpty()) {
                    var effective =
                            active.stream()
                                    .filter(
                                            w ->
                                                    date(
                                                            at.toLocalDate(),
                                                            w.validFrom(),
                                                            w.validTo(),
                                                            w.sourceEffectiveDate()))
                                    .toList();
                    code = "WINDOW_EXPIRED";
                    message = "Không có khung giờ còn hiệu lực trên ngày địa phương này.";
                    if (!effective.isEmpty()) {
                        if (effective.stream()
                                .anyMatch(w -> !Objects.equals(zone, w.verifiedTimeZoneId())))
                            return result(
                                    data.portId(),
                                    type,
                                    at,
                                    zone,
                                    "PORT_TIMEZONE_CHANGED",
                                    "Múi giờ cảng đã thay đổi; cần xác minh lại nguồn giờ khai"
                                        + " thác.",
                                    null,
                                    null);
                        var matched =
                                effective.stream()
                                        .filter(
                                                w ->
                                                        w.daysOfWeek().contains(at.getDayOfWeek())
                                                                && !at.toLocalTime()
                                                                        .isBefore(
                                                                                w.localStartTime())
                                                                && at.toLocalTime()
                                                                        .isBefore(w.localEndTime()))
                                        .toList();
                        code = "OUTSIDE_WINDOW";
                        message = "Mốc dự kiến nằm ngoài các khung giờ đã xác minh trên ngày này.";
                        for (Window w : matched) {
                            if (overnight && !Boolean.TRUE.equals(w.overnightAllowed())) {
                                code = "OVERNIGHT_NOT_ALLOWED";
                                message = "Nguồn đã xác minh chưa cho phép lưu qua đêm.";
                                continue;
                            }
                            if (w.maxStayMinutes() != null
                                    && staySeconds > w.maxStayMinutes() * 60L) {
                                code = "MAX_STAY_EXCEEDED";
                                message =
                                        "Thời gian ghé vượt thời gian lưu tối đa theo nguồn đã xác"
                                            + " minh.";
                                continue;
                            }
                            return result(
                                    data.portId(),
                                    type,
                                    at,
                                    zone,
                                    "MATCHED",
                                    "Mốc dự kiến thuộc khung giờ đã xác minh; chưa phải xác nhận"
                                        + " cầu cảng.",
                                    w,
                                    null);
                        }
                        if (!matched.isEmpty())
                            return result(
                                    data.portId(),
                                    type,
                                    at,
                                    zone,
                                    code,
                                    message,
                                    matched.getFirst(),
                                    null);
                    }
                }
            }
        }
        return result(data.portId(), type, at, zone, code, message, null, null);
    }

    public void requirePass(List<Check> checks) {
        if (checks.isEmpty() || checks.stream().anyMatch(c -> !"PASS".equals(c.status())))
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "PORT_CALL_WINDOW_VALIDATION_REQUIRED",
                    "Khung giờ cảng chưa đáp ứng cho các mốc đến/rời thực tế.",
                    Map.of("portWindowChecks", checks));
    }
}
