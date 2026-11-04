package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.ship.dto.response.CruiseSchedulePreviewResponse;
import com.casms.ship.entity.Port;
import com.casms.ship.enums.PortStatus;
import com.casms.ship.repository.CruiseStopRepository;
import com.casms.ship.repository.ItineraryRepository;
import com.casms.ship.repository.PortRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;

/** Port identity, status, captured timezone and schedule facts; no port operating-window policy. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CruisePortValidationService {
    private final PortRepository ports;
    private final CruiseStopRepository stops;
    private final ItineraryRepository itineraries;
    @PersistenceContext private EntityManager entityManager;

    private Port current(UUID id) {
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            return ports.findById(id).orElseThrow(() -> new ResourceNotFoundException("Port", id));
        }
        Port port = ports.lockDirectory(id).orElseThrow(() -> new ResourceNotFoundException("Port", id));
        entityManager.refresh(port);
        return port;
    }

    private void lockPorts(TreeSet<UUID> ids) {
        if (!TransactionSynchronizationManager.isCurrentTransactionReadOnly()) ids.forEach(this::current);
    }

    private List<String> portBlockers(UUID id, String capturedZone, Instant arrival, Instant departure, boolean portCall) {
        Port port = current(id);
        if (capturedZone == null) return List.of(portCall ? "PORT_SNAPSHOT_UNAVAILABLE" : "PORT_TIMEZONE_CHANGED");
        try { ZoneId.of(capturedZone); }
        catch (DateTimeException invalidZone) { return List.of("INVALID_PORT_TIMEZONE"); }
        if (!capturedZone.equals(port.getTimeZoneId())) return List.of("PORT_TIMEZONE_CHANGED");
        if (port.getStatus() != PortStatus.ACTIVE) return List.of("PORT_NOT_ACTIVE");
        if (departure == null || portCall && (arrival == null || !departure.isAfter(arrival))) {
            return List.of("INVALID_PORT_CALL_TIME");
        }
        return List.of();
    }

    public List<String> cruiseBlockers(UUID id) {
        var itinerary = itineraries.findByCruiseCruiseId(id).orElse(null);
        // The existing itinerary validators own the missing-itinerary requirement.
        if (itinerary == null) return List.of();
        var calls = stops.findByItineraryItineraryIdOrderByStopOrderAsc(itinerary.getItineraryId());
        var cruise = itinerary.getCruise();
        var portIds = new TreeSet<UUID>();
        calls.forEach(call -> portIds.add(call.getPort().getPortId()));
        if (cruise.getDeparturePort() != null) portIds.add(cruise.getDeparturePort().getPortId());
        lockPorts(portIds);
        List<String> blockers = new ArrayList<>();
        if (cruise.getDeparturePort() != null && calls.stream().noneMatch(call ->
                call.getPort().getPortId().equals(cruise.getDeparturePort().getPortId())
                        && Objects.equals(call.getPlannedDeparture(), cruise.getDepartureDatetime()))) {
            blockers.addAll(portBlockers(cruise.getDeparturePort().getPortId(),
                    itinerary.getDeparturePortFacts() == null ? null : itinerary.getDeparturePortFacts().timeZoneId(),
                    null, cruise.getDepartureDatetime(), false));
        }
        for (var call : calls) blockers.addAll(portBlockers(call.getPort().getPortId(),
                call.getPortFacts() == null ? null : call.getPortFacts().timeZoneId(),
                call.getPlannedArrival(), call.getPlannedDeparture(), true));
        return blockers.stream().distinct().toList();
    }

    public void requireCruise(UUID id) { requireValid(cruiseBlockers(id)); }

    /** Preview and create keep validating current port facts against the captured source facts. */
    public void requireSchedule(List<CruiseSchedulePreviewResponse.ProjectedStopDto> calls,
                                UUID departurePortId, String departureZone, Instant departure) {
        var portIds = new TreeSet<UUID>();
        calls.forEach(call -> portIds.add(call.getPortId()));
        if (departurePortId != null) portIds.add(departurePortId);
        lockPorts(portIds);
        List<String> blockers = new ArrayList<>();
        for (var call : calls) blockers.addAll(portBlockers(call.getPortId(), call.getPortTimeZoneId(),
                call.getPlannedArrival(), call.getPlannedDeparture(), true));
        if (departurePortId != null && calls.stream().noneMatch(call ->
                call.getPortId().equals(departurePortId) && Objects.equals(call.getPlannedDeparture(), departure))) {
            blockers.addAll(portBlockers(departurePortId, departureZone, null, departure, false));
        }
        requireValid(blockers);
    }

    private void requireValid(List<String> blockers) {
        if (!blockers.isEmpty()) throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, blockers.getFirst(),
                "Thông tin cảng, múi giờ hoặc thời gian ghé cảng không còn hợp lệ; cần đối chiếu lịch trình.");
    }
}
