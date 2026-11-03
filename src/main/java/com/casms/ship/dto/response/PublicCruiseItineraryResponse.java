package com.casms.ship.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Customer projection of the materialized Departure schedule. */
public record PublicCruiseItineraryResponse(
        UUID cruiseId, Instant departureDatetime, Instant returnDatetime,
        Integer durationDays, Integer durationNights, List<Day> days) {
    public record Day(Integer dayNumber, LocalDate date, String shipTimeZoneId,
                      List<PortCall> portCalls) {}
    public record PortCall(Integer sequence, String portName, String countryCode,
                           String portTimeZoneId, Instant arrival, Instant departure,
                           List<Visit> destinationVisits) {}
    public record Visit(Integer sequence, String destinationName) {}
}
