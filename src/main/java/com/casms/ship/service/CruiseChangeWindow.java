package com.casms.ship.service;

import com.casms.ship.dto.request.CruiseChangeImpactRequest;
import com.casms.ship.entity.Cruise;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

record CruiseChangeWindow(Instant departure, Instant arrival) {
    static CruiseChangeWindow resolve(Cruise cruise, CruiseChangeImpactRequest request) {
        Instant oldDeparture = cruise.getDepartureDatetime();
        Instant oldArrival = cruise.getReturnDatetime();
        Instant departure = request.getNewDepartureDatetime() != null
                ? request.getNewDepartureDatetime()
                : oldDeparture;
        Instant arrival = request.getNewReturnDatetime();
        if (arrival == null) {
            if (request.getNewDepartureDatetime() == null) {
                arrival = oldArrival;
            } else if (oldDeparture != null && oldArrival != null) {
                arrival = departure.plus(Duration.between(oldDeparture, oldArrival));
            }
        }
        return new CruiseChangeWindow(departure, arrival);
    }

    boolean valid() {
        return departure != null && arrival != null && arrival.isAfter(departure);
    }

    boolean differsFrom(Cruise cruise) {
        return !Objects.equals(departure, cruise.getDepartureDatetime())
                || !Objects.equals(arrival, cruise.getReturnDatetime());
    }
}
