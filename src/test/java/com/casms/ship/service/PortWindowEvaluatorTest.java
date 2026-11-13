package com.casms.ship.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.casms.audit.AuditService;
import com.casms.ship.dto.PortWindowContract.*;
import com.casms.ship.entity.Port;
import com.casms.ship.enums.PortStatus;
import com.casms.ship.repository.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.*;
import java.util.*;

class PortWindowEvaluatorTest {
    private final UUID id = UUID.randomUUID();
    private final String zone = "Asia/Singapore";
    private final LocalDate day = LocalDate.of(2026, 11, 2);
    private PortWindowService directory;
    private PortWindowEvaluator evaluator;
    private Port port;

    @BeforeEach
    void setup() {
        directory = mock(PortWindowService.class);
        port = new Port();
        port.setPortId(id);
        port.setTimeZoneId(zone);
        port.setStatus(PortStatus.ACTIVE);
        when(directory.lock(id)).thenReturn(port);
        evaluator =
                new PortWindowEvaluator(
                        directory,
                        mock(PortRepository.class),
                        mock(CruiseStopRepository.class),
                        mock(ItineraryRepository.class),
                        mock(AuditService.class));
    }

    private Instant at(String time) {
        return LocalDateTime.of(day, LocalTime.parse(time)).atZone(ZoneId.of(zone)).toInstant();
    }

    @ParameterizedTest
    @CsvSource({"08:00,16:00,0", "22:00,06:00,1"})
    void validPortMovementsPassWithoutDirectoryWindows(String eta, String etd, int departureDays) {
        var checks = evaluator.evaluate(id, zone, at(eta), at(etd).plus(Duration.ofDays(departureDays)));

        assertEquals(List.of("ARRIVAL", "DEPARTURE"), checks.stream().map(Check::movementType).toList());
        assertTrue(checks.stream().allMatch(check -> "PASS".equals(check.status())
                && "WINDOW_NOT_REQUIRED".equals(check.code())));
        verify(directory, never()).read(id);
    }

    @ParameterizedTest
    @CsvSource({"08:00,08:00", "08:00,07:59"})
    void rejectsUnorderedCallTimes(String eta, String etd) {
        var checks = evaluator.evaluate(id, zone, at(eta), at(etd));
        assertEquals(2, checks.size());
        assertTrue(checks.stream()
                .allMatch(check -> "BLOCK".equals(check.status())
                        && "INVALID_PORT_CALL_TIME".equals(check.code())));
    }

    @Test
    void inactivePortStillBlocksDeparture() {
        port.setStatus(PortStatus.INACTIVE);
        assertEquals("PORT_NOT_ACTIVE", evaluator.departureMovement(id, zone, at("16:00")).getFirst().code());
    }

    @Test
    void timezoneChangeRequiresReview() {
        port.setTimeZoneId("Asia/Tokyo");
        assertEquals(
                "PORT_TIMEZONE_CHANGED",
                evaluator.evaluate(id, zone, at("08:00"), at("16:00")).getFirst().code());
    }

    @Test
    void legacySnapshotCannotGrantPass() {
        assertEquals(
                "PORT_SNAPSHOT_UNAVAILABLE",
                evaluator.evaluate(id, null, at("08:00"), at("16:00")).getFirst().code());
    }
}
