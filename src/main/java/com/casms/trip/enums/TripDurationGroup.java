package com.casms.trip.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TripDurationGroup {
    SHORT(1, 2, 1),
    LONG(3, 5, 3);

    private final int minDays;
    private final int maxDays;
    private final int defaultDays;

    public boolean isValidDays(int days) {
        return days >= minDays && days <= maxDays;
    }
}
