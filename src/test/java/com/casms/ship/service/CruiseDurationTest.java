package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.ship.util.CruiseDuration;
import com.casms.trip.entity.Trip;
import com.casms.trip.service.TripDepartureWindow;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class CruiseDurationTest {
    @Test
    void equalElapsedDurationsCanHaveDifferentCalendarLabels() {
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        assertEquals(new CruiseDuration(36_000, 1, 0), CruiseDuration.from(
                Instant.parse("2026-11-10T01:00:00Z"),
                Instant.parse("2026-11-10T11:00:00Z"), zone));
        assertEquals(new CruiseDuration(36_000, 2, 1), CruiseDuration.from(
                Instant.parse("2026-11-10T13:00:00Z"),
                Instant.parse("2026-11-10T23:00:00Z"), zone));
    }

    @Test
    void calendarDaysUseDeparturePortZoneAndCountMidnightAtTheEnd() {
        Instant departure = Instant.parse("2026-11-10T13:00:00Z");
        Instant arrival = Instant.parse("2026-11-10T15:00:00Z");
        assertEquals(new CruiseDuration(7_200, 2, 1),
                CruiseDuration.from(departure, arrival, ZoneId.of("Asia/Tokyo")));
        assertEquals(new CruiseDuration(7_200, 1, 0),
                CruiseDuration.from(departure, arrival, ZoneId.of("Asia/Ho_Chi_Minh")));
    }

    @Test
    void validatesBothWindowBoundsAndIncludesEachEndpoint() {
        LocalTime earliest = LocalTime.of(8, 0);
        LocalTime latest = LocalTime.of(8, 1);
        assertDoesNotThrow(() -> TripDepartureWindow.validate(null, null));
        assertThrows(BusinessException.class, () -> TripDepartureWindow.validate(earliest, null));
        assertThrows(BusinessException.class, () -> TripDepartureWindow.validate(latest, earliest));
        Trip trip = new Trip();
        trip.setEarliestDeparture(earliest);
        trip.setLatestDeparture(latest);
        assertDoesNotThrow(() -> TripDepartureWindow.requireWithin(trip, Instant.parse("2026-10-15T01:00:00Z")));
        assertDoesNotThrow(() -> TripDepartureWindow.requireWithin(trip, Instant.parse("2026-12-15T01:01:59.999999999Z")));
        assertThrows(BusinessException.class,
                () -> TripDepartureWindow.requireWithin(trip, Instant.parse("2026-12-15T00:59:59Z")));
        assertThrows(BusinessException.class,
                () -> TripDepartureWindow.requireWithin(trip, Instant.parse("2026-10-15T01:02:00Z")));
    }
}
