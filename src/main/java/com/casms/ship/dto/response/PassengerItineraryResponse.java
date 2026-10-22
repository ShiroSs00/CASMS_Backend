package com.casms.ship.dto.response;

import com.casms.activity.enums.RegistrationStatus;
import com.casms.booking.enums.BookingPassengerParticipationStatus;
import com.casms.booking.enums.BookingStatus;
import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.enums.ItineraryDayType;
import com.casms.ship.enums.ItineraryStatus;
import com.casms.ship.enums.CruiseStopStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** A read-only projection of a passenger's booked trips and their current published schedules. */
public record PassengerItineraryResponse(
        UUID bookingPassengerId,
        UUID bookingId,
        String bookingReference,
        BookingStatus bookingStatus,
        BookingPassengerParticipationStatus participationStatus,
        UUID cruiseId,
        String cruiseCode,
        String cruiseName,
        CruiseStatus cruiseStatus,
        UUID itineraryId,
        ItineraryStatus itineraryStatus,
        LocalDate startDate,
        LocalDate endDate,
        List<CruiseDay> cruiseSchedule,
        List<RegisteredActivity> registeredActivities
) {
    public record CruiseDay(
            Integer dayNumber,
            LocalDate date,
            ItineraryDayType dayType,
            String description,
            List<CruiseStopSchedule> stops
    ) {}

    public record CruiseStopSchedule(
            UUID cruiseStopId,
            Integer stopOrder,
            String portName,
            String destinationName,
            Instant plannedArrival,
            Instant plannedDeparture,
            Instant actualArrival,
            Instant actualDeparture,
            CruiseStopStatus status
    ) {}

    public record RegisteredActivity(
            UUID registrationId,
            String kind,
            UUID activityId,
            String name,
            Instant startTime,
            Instant endTime,
            String location,
            RegistrationStatus registrationStatus,
            String activityStatus,
            Integer quantity
    ) {}
}
