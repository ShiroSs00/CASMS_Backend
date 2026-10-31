package com.casms.ship.enums;

public enum ShipStatus {
    DRAFT,
    READY,
    IN_SERVICE,
    MAINTENANCE,
    SUSPENDED,
    RETIRED;

    public static boolean isOperational(ShipStatus status) {
        return status == READY || status == IN_SERVICE;
    }
}
