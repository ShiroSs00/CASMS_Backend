package com.casms.ship.service;

import com.casms.identity.entity.User;
import com.casms.identity.repository.UserRepository;
import com.casms.notification.service.NotificationService;
import com.casms.ship.dto.request.ShipOperationalProfile;
import com.casms.ship.entity.Ship;
import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.enums.ShipStatus;
import com.casms.ship.repository.CruiseRepository;
import com.casms.ship.repository.ShipRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShipClassificationExpiryServiceTest {
    @Mock ShipRepository ships;
    @Mock CruiseRepository cruises;
    @Mock UserRepository users;
    @Mock NotificationService notifications;
    @Mock JdbcTemplate jdbc;
    @InjectMocks ShipClassificationExpiryService service;

    private final LocalDate today = LocalDate.of(2030, 1, 2);
    private final LocalDate expiry = today.minusDays(1);
    private final UUID shipId = UUID.randomUUID();
    private final UUID managerId = UUID.randomUUID();
    private final Ship ship = new Ship();

    @BeforeEach
    void setUp() {
        ship.setShipId(shipId);
        ship.setShipName("Ocean Voyager");
        ship.setCreatedByShipManagerId(managerId);
        ship.setStatus(ShipStatus.READY);
        setExpiry(expiry);
        when(ships.findByIdForUpdate(shipId)).thenReturn(Optional.of(ship));
    }

    @Test
    void expiredShipWithoutVoyageEntersMaintenanceAndAlertsOnce() {
        var manager = stubRecipient();
        when(jdbc.update(contains("ON CONFLICT"), eq(shipId), eq(expiry))).thenReturn(1);

        service.process(shipId, today);
        service.process(shipId, today);

        assertEquals(ShipStatus.MAINTENANCE, ship.getStatus());
        verify(notifications).notifyUser(eq(manager), eq("SHIP_CLASSIFICATION_EXPIRED"),
                anyString(), contains("đã được đưa vào bảo trì"), isNull());
    }

    @Test
    void underwayShipAlertsBeforeTransitionAndDoesNotNotifyTwice() {
        var manager = stubRecipient();
        ship.setStatus(ShipStatus.IN_SERVICE);
        when(cruises.existsByShip_ShipIdAndStatus(shipId, CruiseStatus.IN_PROGRESS))
                .thenReturn(true, false);
        when(jdbc.update(contains("ON CONFLICT"), eq(shipId), eq(expiry))).thenReturn(1, 0);

        service.process(shipId, today);
        assertEquals(ShipStatus.IN_SERVICE, ship.getStatus());
        service.process(shipId, today);
        assertEquals(ShipStatus.MAINTENANCE, ship.getStatus());
        verify(notifications).notifyUser(eq(manager), eq("SHIP_CLASSIFICATION_EXPIRED"),
                anyString(), contains("sau khi không còn chuyến đang chạy"), isNull());
    }

    @Test
    void expiryTodayAndMissingExpiryDoNotChangeStatus() {
        setExpiry(today);
        service.process(shipId, today);
        setExpiry(null);
        service.process(shipId, today);
        assertEquals(ShipStatus.READY, ship.getStatus());
        verifyNoInteractions(cruises, users, notifications, jdbc);
    }

    @Test
    void archivedAndNonOperationalShipsRemainInTheirCurrentStatus() {
        ship.setArchivedAt(Instant.now());
        service.process(shipId, today);
        assertEquals(ShipStatus.READY, ship.getStatus());
        ship.setArchivedAt(null);
        for (ShipStatus current : List.of(
                ShipStatus.DRAFT, ShipStatus.SUSPENDED, ShipStatus.RETIRED, ShipStatus.MAINTENANCE)) {
            ship.setStatus(current);
            service.process(shipId, today);
            assertEquals(current, ship.getStatus());
        }
        verifyNoInteractions(cruises, users, notifications, jdbc);
    }

    private void setExpiry(LocalDate date) {
        ship.setOperationalProfile(new ShipOperationalProfile(
                null, null, null, null, null, null, null, null, null, null, date, null, null, null));
    }

    private User stubRecipient() {
        User manager = new User();
        manager.setUserId(managerId);
        when(jdbc.queryForList(contains("FROM \"User\""), eq(UUID.class),
                eq(managerId), eq(shipId), eq(managerId))).thenReturn(List.of(managerId));
        when(users.findById(managerId)).thenReturn(Optional.of(manager));
        return manager;
    }
}
