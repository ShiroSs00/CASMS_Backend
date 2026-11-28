package com.casms.ship;

import com.casms.ship.dto.request.ShipOperationalProfile;
import com.casms.ship.entity.Ship;

import java.time.LocalDate;

public final class CruiseShipFixtures {
    private CruiseShipFixtures() {}

    public static void setClassificationExpiry(Ship ship) {
        ship.setOperationalProfile(new ShipOperationalProfile(
                null, null, null, null, null, null, null, null, null, null,
                LocalDate.of(2100, 1, 1), null, null, null));
    }
}
