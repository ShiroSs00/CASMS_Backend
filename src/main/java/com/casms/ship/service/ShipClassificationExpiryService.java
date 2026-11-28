package com.casms.ship.service;

import com.casms.identity.repository.UserRepository;
import com.casms.notification.service.NotificationService;
import com.casms.ship.entity.Ship;
import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.enums.ShipStatus;
import com.casms.ship.repository.CruiseRepository;
import com.casms.ship.repository.ShipRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ShipClassificationExpiryService {
    static final String NOTIFICATION_TYPE = "SHIP_CLASSIFICATION_EXPIRED";

    private final ShipRepository ships;
    private final CruiseRepository cruises;
    private final UserRepository users;
    private final NotificationService notifications;
    private final JdbcTemplate jdbc;

    @Transactional
    public void process(UUID shipId, LocalDate today) {
        Ship ship = ships.findByIdForUpdate(shipId).orElse(null);
        if (ship == null || ship.getArchivedAt() != null
                || !ShipStatus.isOperational(ship.getStatus()) || !isExpired(ship, today)) {
            return;
        }

        boolean activeCruise = cruises.existsByShip_ShipIdAndStatus(shipId, CruiseStatus.IN_PROGRESS);
        applyExpiry(ship, activeCruise);
    }

    // Both callers hold the ship row lock in a write transaction and have checked expiry.
    void applyExpiry(Ship ship, boolean activeCruise) {
        LocalDate expiry = ship.getOperationalProfile().classificationExpiryDate();
        sendNotice(ship, expiry, activeCruise);
        if (!activeCruise) {
            ship.setStatus(ShipStatus.MAINTENANCE);
            log.info("Ship {} moved to MAINTENANCE after classification expiry {}", ship.getShipId(), expiry);
        }
    }

    static boolean isExpired(Ship ship, LocalDate today) {
        var profile = ship.getOperationalProfile();
        return profile != null && profile.classificationExpiryDate() != null
                && profile.classificationExpiryDate().isBefore(today);
    }

    private void sendNotice(Ship ship, LocalDate expiry, boolean activeCruise) {
        UUID shipId = ship.getShipId();
        UUID creatorId = ship.getCreatedByShipManagerId();
        var recipients = jdbc.queryForList("""
                SELECT u.user_id
                FROM "User" u
                JOIN "UserRole" r ON r.user_id = u.user_id AND r.role = 'SHIP_MANAGER'
                WHERE u.status = 'ACTIVE' AND (
                    u.user_id = ? OR EXISTS (
                        SELECT 1 FROM "CruiseStaff" staff
                        JOIN "Cruise" c ON c.cruise_id = staff.cruise_id
                        WHERE c.ship_id = ? AND staff.user_id = u.user_id
                          AND staff.status = 'ACTIVE' AND staff.role_in_cruise = 'SHIP_MANAGER'
                          AND c.status NOT IN ('CANCELLED', 'COMPLETED')))
                ORDER BY CASE WHEN u.user_id = ? THEN 0 ELSE 1 END, u.user_id
                LIMIT 1
                """, UUID.class, creatorId, shipId, creatorId);
        if (recipients.isEmpty()) {
            if (!activeCruise) {
                log.warn("No active ship manager found for expired classification on ship {}", shipId);
            }
            return;
        }
        var recipient = users.findById(recipients.getFirst()).orElse(null);
        if (recipient == null) {
            return;
        }
        int claimed = jdbc.update("""
                INSERT INTO ship_classification_expiry_notice (ship_id, expiry_date)
                VALUES (?, ?) ON CONFLICT (ship_id, expiry_date) DO NOTHING
                """, shipId, expiry);
        if (claimed == 0) {
            return;
        }
        String nextStep = activeCruise
                ? "Tàu sẽ chuyển sang bảo trì sau khi không còn chuyến đang chạy."
                : "Tàu đã được đưa vào bảo trì.";
        notifications.notifyUser(recipient, NOTIFICATION_TYPE, "Đăng kiểm tàu đã hết hạn",
                "Tàu " + ship.getShipName() + " hết hạn đăng kiểm ngày " + expiry + ". "
                        + nextStep + " Vui lòng cập nhật hồ sơ sau khi đăng kiểm lại.",
                null);
    }
}
