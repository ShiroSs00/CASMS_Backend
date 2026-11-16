package com.casms.trip.service;

import com.casms.common.exception.BusinessException;
import com.casms.trip.entity.Trip;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

public final class TripDepartureWindow {
    private TripDepartureWindow() {}

    public static void validate(Instant earliest, Instant latest) {
        if (earliest == null && latest == null) {
            return;
        }
        if (earliest == null || latest == null || earliest.isAfter(latest)) {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "TRIP_DEPARTURE_WINDOW_INVALID",
                    "Nhập đủ giờ khởi hành sớm nhất và muộn nhất theo đúng thứ tự.");
        }
    }

    public static void requireWithin(Trip trip, Instant departure) {
        if (includes(trip, departure)) {
            return;
        }
        Instant earliest = trip.getEarliestDeparture();
        Instant latest = trip.getLatestDeparture();
        throw new BusinessException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "DEPARTURE_OUTSIDE_TRIP_WINDOW",
                "Giờ khởi hành dự kiến nằm ngoài khung của mẫu lịch trình.",
                Map.of("earliestDeparture", earliest, "latestDeparture", latest,
                        "departureDatetime", departure));
    }

    public static boolean includes(Trip trip, Instant departure) {
        return trip == null || trip.getEarliestDeparture() == null
                || (!departure.isBefore(trip.getEarliestDeparture())
                        && !departure.isAfter(trip.getLatestDeparture()));
    }
}
