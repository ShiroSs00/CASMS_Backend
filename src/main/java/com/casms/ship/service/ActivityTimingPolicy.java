package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.Instant;

public final class ActivityTimingPolicy {
    private static final Duration MIN_DURATION = Duration.ofMinutes(60);
    private static final Duration MIN_TIME_REMAINING = Duration.ofHours(2);
    private static final Duration CHECKOUT = Duration.ofMinutes(30);
    private static final Duration PACKING = Duration.ofMinutes(30);

    private ActivityTimingPolicy() {}

    public static void requireNewSlot(Instant start, Instant end, Instant cutoff, Instant now) {
        if (start == null || end == null || cutoff == null) return;
        if (Duration.between(start, end).compareTo(MIN_DURATION) <= 0) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "ACTIVITY_DURATION_TOO_SHORT",
                    "Activity duration must exceed 60 minutes");
        }
        if (start.isBefore(now)
                || Duration.between(now, cutoff).compareTo(MIN_TIME_REMAINING) < 0
                || end.isAfter(cutoff.minus(CHECKOUT).minus(PACKING))) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "ACTIVITY_TIME_WINDOW_TOO_SHORT",
                    "Activity must finish before the checkout and packing intervals");
        }
    }
}
