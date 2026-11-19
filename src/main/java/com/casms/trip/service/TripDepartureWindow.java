package com.casms.trip.service;

import com.casms.common.exception.BusinessException;
import com.casms.trip.entity.Trip;
import com.casms.ship.util.PortTimeZoneHelper;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

public final class TripDepartureWindow {
    private TripDepartureWindow() {}

    public static void validate(LocalTime earliest, LocalTime latest) {
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
        LocalTime earliest = trip.getEarliestDeparture();
        LocalTime latest = trip.getLatestDeparture();
        throw new BusinessException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "DEPARTURE_OUTSIDE_TRIP_WINDOW",
                "Giờ khởi hành dự kiến nằm ngoài khung của mẫu lịch trình.",
                Map.of("earliestDeparture", earliest.toString(), "latestDeparture", latest.toString(),
                        "departureDatetime", departure));
    }

    public static LocalTime shipMinute(Instant departure) {
        return departure.atZone(PortTimeZoneHelper.SHIP_TIME_ZONE).toLocalTime()
                .truncatedTo(ChronoUnit.MINUTES);
    }

    public static boolean includes(Trip trip, Instant departure) {
        if (trip == null || trip.getEarliestDeparture() == null) {
            return true;
        }
        LocalTime minute = shipMinute(departure);
        return !minute.isBefore(trip.getEarliestDeparture()) && !minute.isAfter(trip.getLatestDeparture());
    }
}
