package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.ship.dto.response.CruiseSchedulePreviewResponse.ProjectedStopDto;
import com.casms.ship.entity.Port;
import com.casms.ship.enums.PortStatus;
import com.casms.ship.repository.CruiseStopRepository;
import com.casms.ship.repository.ItineraryRepository;
import com.casms.ship.repository.PortRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CruisePortValidationServiceTest {
    private final UUID id = UUID.randomUUID();
    private final Instant arrival = Instant.parse("2027-07-01T01:00:00Z");
    private final Instant departure = Instant.parse("2027-07-01T11:00:00Z");
    private final PortRepository ports = mock(PortRepository.class);
    private final CruisePortValidationService service = new CruisePortValidationService(ports,
            mock(CruiseStopRepository.class), mock(ItineraryRepository.class));
    private Port port;

    @BeforeEach void prepare() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        port = new Port(); port.setPortId(id); port.setTimeZoneId("Asia/Ho_Chi_Minh"); port.setStatus(PortStatus.ACTIVE);
        when(ports.findById(id)).thenReturn(Optional.of(port));
    }

    @AfterEach void resetTransactionFlag() { TransactionSynchronizationManager.setCurrentTransactionReadOnly(false); }

    private ProjectedStopDto call(String zone, Instant end) {
        return ProjectedStopDto.builder().portId(id).portTimeZoneId(zone)
                .plannedArrival(arrival).plannedDeparture(end).build();
    }

    @Test void validPortAndScheduleNeedNoOperatingWindowConfiguration() {
        assertDoesNotThrow(() -> service.requireSchedule(List.of(call(port.getTimeZoneId(), departure)), null, null, arrival));
        verify(ports).findById(id);
    }

    @Test void overnightStayNeedsOnlyValidChronology() {
        assertDoesNotThrow(() -> service.requireSchedule(List.of(call(port.getTimeZoneId(), departure.plusSeconds(86400))), null, null, arrival));
    }

    @Test void inactivePortStillBlocks() {
        port.setStatus(PortStatus.INACTIVE);
        assertEquals("PORT_NOT_ACTIVE", assertThrows(BusinessException.class, () ->
                service.requireSchedule(List.of(call(port.getTimeZoneId(), departure)), null, null, arrival)).getCode());
    }

    @Test void changedTimezoneStillBlocks() {
        assertEquals("PORT_TIMEZONE_CHANGED", assertThrows(BusinessException.class, () ->
                service.requireSchedule(List.of(call("Asia/Tokyo", departure)), null, null, arrival)).getCode());
    }

    @Test void invalidTimezoneStillBlocks() {
        assertEquals("INVALID_PORT_TIMEZONE", assertThrows(BusinessException.class, () ->
                service.requireSchedule(List.of(call("Invalid/Zone", departure)), null, null, arrival)).getCode());
    }

    @Test void missingHistoricalFactsAreNotInvented() {
        assertEquals("PORT_SNAPSHOT_UNAVAILABLE", assertThrows(BusinessException.class, () ->
                service.requireSchedule(List.of(call(null, departure)), null, null, arrival)).getCode());
    }

    @Test void invalidArrivalDepartureOrderStillBlocks() {
        assertEquals("INVALID_PORT_CALL_TIME", assertThrows(BusinessException.class, () ->
                service.requireSchedule(List.of(call(port.getTimeZoneId(), arrival)), null, null, arrival)).getCode());
    }

    @Test void standaloneDepartureStillChecksItsPort() {
        port.setStatus(PortStatus.INACTIVE);
        assertEquals("PORT_NOT_ACTIVE", assertThrows(BusinessException.class, () ->
                service.requireSchedule(List.of(), id, port.getTimeZoneId(), departure)).getCode());
    }
}
