package com.casms.ship.service;

import com.casms.checkin.repository.GuidanceSessionRepository;
import com.casms.common.exception.BusinessException;
import com.casms.config.JacksonConfig;
import com.casms.notification.repository.NotificationRepository;
import com.casms.servicepackage.repository.ServiceScheduleRepository;
import com.casms.ship.dto.PortWindowContract.Check;
import com.casms.ship.dto.request.CruiseDelayRequest;
import com.casms.ship.dto.response.CruiseReadinessResponse;
import com.casms.ship.dto.response.CruiseSchedulePreviewResponse;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.CruiseAuditEvent;
import com.casms.ship.entity.Itinerary;
import com.casms.ship.entity.Port;
import com.casms.ship.entity.Ship;
import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.repository.*;
import com.casms.ship.util.PortTimeZoneHelper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CruiseDelayServiceTest {
    @Mock CruiseRepository cruises;
    @Mock ShipRepository ships;
    @Mock ItineraryRepository itineraries;
    @Mock CruiseStopRepository stops;
    @Mock CruiseStaffRepository staff;
    @Mock OperationalIssueRepository issues;
    @Mock GuidanceSessionRepository guidance;
    @Mock ServiceScheduleRepository serviceSchedules;
    @Mock CruiseAuditEventRepository events;
    @Mock CruiseAuditService audit;
    @Mock NotificationRepository notifications;
    @Mock CruiseService cruiseService;
    @Mock ScheduleMaterializationService schedules;
    @Mock PortWindowEvaluator windows;
    @Mock EntityManager entityManager;

    private final UUID cruiseId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final Instant departure = Instant.parse("2099-11-20T01:00:00Z");
    private final CruiseDelayRequest request = new CruiseDelayRequest(
            90, "Weather warning", "WEATHER_CONDITION", UUID.randomUUID(), 1L);
    private CruiseDelayService service;
    private Cruise cruise;

    @BeforeEach
    void setup() {
        service = new CruiseDelayService(cruises, ships, itineraries, stops, staff, issues, guidance,
                serviceSchedules, events, audit, notifications, cruiseService, schedules, windows,
                entityManager, new JacksonConfig().objectMapper());
        cruise = new Cruise();
        cruise.setCruiseId(cruiseId);
        cruise.setStatus(CruiseStatus.PUBLISHED);
        cruise.setDepartureDatetime(departure);
        cruise.setReturnDatetime(departure.plus(Duration.ofHours(8)));
    }

    @Test
    void templateDepartureOffsetUsesLocalTimeWithoutStoringADate() {
        assertEquals(Duration.ofHours(1), PortTimeZoneHelper.departureOffset(
                LocalTime.of(8, 0), departure.plus(Duration.ofHours(1))));
    }

    @Test
    void previewShiftsTheScheduleAndPreservesDurationWithoutWriting() {
        Ship ship = new Ship();
        ship.setShipId(UUID.randomUUID());
        cruise.setShip(ship);
        Port port = new Port();
        port.setPortId(UUID.randomUUID());
        cruise.setDeparturePort(port);
        Itinerary itinerary = new Itinerary();
        itinerary.setItineraryId(UUID.randomUUID());
        var gate = new CruiseReadinessResponse();
        gate.setReady(true);
        var entry = CruiseSchedulePreviewResponse.ProjectedEntryDto.builder()
                .plannedStartTime(departure).plannedEndTime(departure.plus(Duration.ofHours(2))).build();
        var schedule = CruiseSchedulePreviewResponse.builder().departureDatetime(departure)
                .returnDatetime(cruise.getReturnDatetime()).departurePortId(port.getPortId())
                .days(List.of(CruiseSchedulePreviewResponse.ProjectedDayDto.builder()
                        .calendarDate(departure.atZone(PortTimeZoneHelper.SHIP_TIME_ZONE).toLocalDate())
                        .stops(List.of()).entries(List.of(entry)).build())).build();
        var pass = mock(Check.class);
        when(pass.status()).thenReturn("PASS");
        when(cruises.findById(cruiseId)).thenReturn(Optional.of(cruise));
        when(itineraries.findByCruiseCruiseId(cruiseId)).thenReturn(Optional.of(itinerary));
        when(cruiseService.validateDepartureReadiness(cruiseId)).thenReturn(gate);
        when(schedules.getCruiseSchedule(cruiseId)).thenReturn(schedule);
        when(windows.departureMovement(eq(port.getPortId()), isNull(), any())).thenReturn(List.of(pass));

        var result = service.preview(cruiseId, request);

        assertTrue(result.blockers().isEmpty());
        assertEquals(departure.toString(), result.before().get("departureDatetime").asText());
        assertEquals(departure.plus(Duration.ofMinutes(90)), result.after().getDepartureDatetime());
        assertEquals(cruise.getReturnDatetime().plus(Duration.ofMinutes(90)), result.after().getReturnDatetime());
        assertEquals(Duration.ofHours(2), Duration.between(entry.getPlannedStartTime(), entry.getPlannedEndTime()));
        assertEquals(departure, cruise.getDepartureDatetime());
        verifyNoInteractions(audit, notifications);
    }

    @Test
    void retryReplaysEvenAfterDepartureAndRejectsChangedPayload() {
        cruise.setStatus(CruiseStatus.IN_PROGRESS);
        String identity = "DELAY:" + cruiseId + ":" + request.requestId();
        var existing = CruiseAuditEvent.builder().cruise(cruise).eventId(UUID.randomUUID())
                .eventIdentity(identity).actorId(actorId).revision(2L).reason(request.reason())
                .beforeDigest("{}").afterDigest("{}")
                .evidence("{\"delayMinutes\":90,\"category\":\"WEATHER_CONDITION\"}").build();
        when(cruises.findByIdForUpdate(cruiseId)).thenReturn(Optional.of(cruise));
        when(events.findByCruise_CruiseIdAndEventTypeAndEventIdentity(
                cruiseId, "CRUISE_PRE_DEPARTURE_DELAY", identity)).thenReturn(Optional.of(existing));
        assertEquals(existing.getEventId(), service.confirm(cruiseId, request, actorId).eventId());
        var conflict = new CruiseDelayRequest(30, request.reason(), request.category(), request.requestId(), 1L);
        assertEquals("DELAY_REQUEST_REUSED",
                assertThrows(BusinessException.class, () -> service.confirm(cruiseId, conflict, actorId)).getCode());
        verifyNoInteractions(audit, notifications, schedules);
    }

    @Test
    void previewReportsMissingShipAndDepartureEvidence() {
        cruise.setActualDepartureDatetime(departure);
        when(cruises.findById(cruiseId)).thenReturn(Optional.of(cruise));
        when(schedules.getCruiseSchedule(cruiseId)).thenReturn(
                CruiseSchedulePreviewResponse.builder().departureDatetime(departure).build());
        var result = service.preview(cruiseId, request);
        assertTrue(result.blockers().containsAll(List.of(
                "SHIP_NOT_ASSIGNED", "ACTUAL_DEPARTURE_RECORDED", "OPERATIONAL_SCHEDULE_REQUIRED")));
        verifyNoInteractions(audit, notifications);
    }
}
