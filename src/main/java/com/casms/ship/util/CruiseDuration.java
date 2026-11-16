package com.casms.ship.util;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public record CruiseDuration(long seconds, int days, int nights) {
    public static CruiseDuration from(Instant departure, Instant arrival, ZoneId departureZone) {
        var departureDate = departure.atZone(departureZone).toLocalDate();
        var arrivalDate = arrival.atZone(departureZone).toLocalDate();
        int days = Math.toIntExact(ChronoUnit.DAYS.between(departureDate, arrivalDate.plusDays(1)));
        int nights = Math.toIntExact(ChronoUnit.DAYS.between(departureDate, arrivalDate));
        return new CruiseDuration(Duration.between(departure, arrival).toSeconds(), days, nights);
    }
}
