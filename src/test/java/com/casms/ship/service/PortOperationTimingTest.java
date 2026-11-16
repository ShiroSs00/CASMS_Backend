package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.servicepackage.enums.ServiceOperatingPeriod;
import com.casms.servicepackage.service.ServiceOperatingHours;
import com.casms.ship.entity.CruiseStop;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PortOperationTimingTest {
    private static final Instant ARRIVAL = Instant.parse("2027-01-02T01:00:00Z");
    private static final Instant DEPARTURE = ARRIVAL.plus(9, ChronoUnit.HOURS);

    @Test
    void plannedDepartureDoesNotOpenOnboardBookings() {
        CruiseStop stop = new CruiseStop();
        stop.setPlannedArrival(ARRIVAL);
        stop.setPlannedDeparture(DEPARTURE);
        assertEquals(PortCallBookingPolicy.State.EN_ROUTE, PortCallBookingPolicy.stateAt(stop, ARRIVAL.minusSeconds(1)));
        assertEquals(PortCallBookingPolicy.State.ARRIVAL_PENDING, PortCallBookingPolicy.stateAt(stop, ARRIVAL));
        assertEquals(PortCallBookingPolicy.State.ARRIVAL_PENDING, PortCallBookingPolicy.stateAt(stop, DEPARTURE));

        stop.setActualArrival(ARRIVAL.plusSeconds(60));
        assertEquals(PortCallBookingPolicy.State.IN_PORT, PortCallBookingPolicy.stateAt(stop, ARRIVAL.plusSeconds(60)));
        assertEquals(PortCallBookingPolicy.State.DEPARTURE_PENDING, PortCallBookingPolicy.stateAt(stop, DEPARTURE));
        stop.setActualDeparture(DEPARTURE.plusSeconds(300));
        assertEquals(PortCallBookingPolicy.State.DEPARTED,
                PortCallBookingPolicy.stateAt(stop, DEPARTURE.plusSeconds(300)));
    }

    @Test
    void checksTwoHourCutoffAndSixtyMinuteActivityBoundary() {
        Instant start = DEPARTURE.minus(121, ChronoUnit.MINUTES);
        assertDoesNotThrow(() -> ActivityTimingPolicy.requireNewSlot(
                start, start.plus(61, ChronoUnit.MINUTES), DEPARTURE, start));
        assertThrows(BusinessException.class, () -> ActivityTimingPolicy.requireNewSlot(
                start, start.plus(60, ChronoUnit.MINUTES), DEPARTURE, start));
        Instant exactlyTwoHours = start.plusSeconds(60);
        assertThrows(BusinessException.class, () -> ActivityTimingPolicy.requireNewSlot(
                start, start.plus(61, ChronoUnit.MINUTES), DEPARTURE, exactlyTwoHours));
        Instant tooLate = start.plus(2, ChronoUnit.MINUTES);
        assertThrows(BusinessException.class, () -> ActivityTimingPolicy.requireNewSlot(
                tooLate, tooLate.plus(61, ChronoUnit.MINUTES), DEPARTURE, tooLate));
    }

    @Test
    void adjacentGroupsCoverMidnightWithoutAllowingAnUnconfiguredGroup() {
        var hours = new ServiceOperatingHours("06:00", "18:00", "22:00");
        var zone = ZoneId.of("Asia/Ho_Chi_Minh");
        Instant localTwentyOne = Instant.parse("2027-01-02T14:00:00Z");
        Instant localSixTomorrow = Instant.parse("2027-01-02T23:00:00Z");
        assertDoesNotThrow(() -> hours.requireOpen(
                Set.of(ServiceOperatingPeriod.EVENING, ServiceOperatingPeriod.NIGHTTIME),
                localTwentyOne, localSixTomorrow, zone));
        assertThrows(BusinessException.class, () -> hours.requireOpen(
                Set.of(ServiceOperatingPeriod.EVENING, ServiceOperatingPeriod.NIGHTTIME),
                localTwentyOne, localSixTomorrow.plusSeconds(1), zone));
        assertThrows(BusinessException.class, () -> hours.requireOpen(
                Set.of(ServiceOperatingPeriod.EVENING), localTwentyOne, localSixTomorrow, zone));
    }
}
