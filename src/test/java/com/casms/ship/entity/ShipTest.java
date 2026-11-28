package com.casms.ship.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShipTest {
    @Test
    void typedShipUsesVietnamCreationDateForCommissionDate() {
        Ship ship = new Ship();
        ship.setShipType(new ShipType());
        ship.setCommissionDate(LocalDate.of(2000, 1, 1));

        ship.onCreate();

        assertEquals(LocalDate.ofInstant(ship.getCreatedAt(), ZoneId.of("Asia/Ho_Chi_Minh")),
                ship.getCommissionDate());
    }
}
