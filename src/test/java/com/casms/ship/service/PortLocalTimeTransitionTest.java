package com.casms.ship.service;

import static org.junit.jupiter.api.Assertions.*;

import com.casms.common.exception.BusinessException;
import com.casms.ship.util.PortTimeZoneHelper;

import org.junit.jupiter.api.Test;

import java.time.*;

class PortLocalTimeTransitionTest {
    @Test
    void nonexistentPortLocalTimeIsRejectedRatherThanShifted() {
        var error =
                assertThrows(
                        BusinessException.class,
                        () ->
                                PortTimeZoneHelper.strictLocalInstant(
                                        LocalDateTime.of(2027, 3, 14, 2, 30),
                                        ZoneId.of("America/New_York")));
        assertEquals("PORT_LOCAL_TIME_UNRESOLVED", error.getCode());
    }

    @Test
    void ambiguousPortLocalTimeRequiresAnExplicitResolutionRatherThanGuessing() {
        assertThrows(
                BusinessException.class,
                () ->
                        PortTimeZoneHelper.strictLocalInstant(
                                LocalDateTime.of(2027, 11, 7, 1, 30),
                                ZoneId.of("America/New_York")));
    }

    @Test
    void portAndShipZonesPreserveDifferentInstantsAndOvernightDates() {
        var local = LocalDateTime.of(2027, 1, 5, 8, 0);
        assertEquals(
                Instant.parse("2027-01-04T23:00:00Z"),
                PortTimeZoneHelper.strictLocalInstant(local, ZoneId.of("Asia/Tokyo")));
        assertEquals(
                Instant.parse("2027-01-05T01:00:00Z"),
                PortTimeZoneHelper.strictLocalInstant(local, PortTimeZoneHelper.SHIP_TIME_ZONE));
    }
}
