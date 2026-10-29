package com.casms.ship.enums;

/** Schedule position is independent of whether the ship is at sea or in port. */
public enum ItineraryDayRole {
    DEPARTURE, REGULAR, ARRIVAL, DEPARTURE_AND_ARRIVAL;

    public static ItineraryDayRole forDay(int number, int total) {
        if (number < 1 || total < 1 || number > total) throw new IllegalArgumentException("Invalid schedule day position");
        if (total == 1) return DEPARTURE_AND_ARRIVAL;
        return number == 1 ? DEPARTURE : number == total ? ARRIVAL : REGULAR;
    }

    public boolean isEnding() { return this == ARRIVAL || this == DEPARTURE_AND_ARRIVAL; }
}
