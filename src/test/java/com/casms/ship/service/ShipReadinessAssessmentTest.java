package com.casms.ship.service;

import com.casms.common.service.BusinessCodeGenerator;
import com.casms.ship.entity.Ship;
import com.casms.ship.enums.ShipStatus;
import com.casms.ship.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipReadinessAssessmentTest {
    @Mock ShipRepository ships;
    @Mock CruiseRepository cruises;
    @Mock DeckRepository decks;
    @Mock CabinRepository cabins;
    @Mock ShipModelRepository models;
    @Mock LocationDeviceRepository devices;
    @Mock BusinessCodeGenerator codes;
    @InjectMocks ShipService service;

    @Test
    void readinessIsStructuralAndDoesNotClaimCabinVacancy() {
        UUID id = UUID.randomUUID();
        Ship ship = ship(id, ShipStatus.READY);
        when(ships.findById(id)).thenReturn(Optional.of(ship));
        when(decks.countByShipShipId(id)).thenReturn(3L);
        when(cabins.countByShipShipId(id)).thenReturn(40L);

        var result = service.assessOperationalReadiness(id);

        assertEquals("READY", result.readiness());
        assertEquals(3, result.deckCount());
        assertEquals(40, result.cabinCount());
        org.junit.jupiter.api.Assertions.assertTrue(result.scope().contains("cabin vacancy are not assessed"));
    }

    @Test
    void maintenanceShipIsBlockedEvenWhenStructureExists() {
        UUID id = UUID.randomUUID();
        when(ships.findById(id)).thenReturn(Optional.of(ship(id, ShipStatus.MAINTENANCE)));
        when(decks.countByShipShipId(id)).thenReturn(2L);
        when(cabins.countByShipShipId(id)).thenReturn(10L);

        var result = service.assessOperationalReadiness(id);

        assertEquals("BLOCKED", result.readiness());
        assertEquals("SHIP_NOT_ACTIVE", result.issues().get(0).code());
    }

    private static Ship ship(UUID id, ShipStatus status) {
        Ship ship = new Ship();
        ship.setShipId(id);
        ship.setStatus(status);
        return ship;
    }
}
