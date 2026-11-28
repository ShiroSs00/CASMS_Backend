package com.casms.ship.service;

import com.casms.identity.entity.User;
import com.casms.identity.enums.Role;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.repository.UserRepository;
import com.casms.notification.entity.Notification;
import com.casms.notification.repository.NotificationRepository;
import com.casms.notification.service.NotificationSseHub;
import com.casms.ship.dto.request.ShipOperationalProfile;
import com.casms.ship.entity.Ship;
import com.casms.ship.enums.ShipStatus;
import com.casms.ship.repository.CruiseRepository;
import com.casms.ship.repository.ShipRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShipClassificationMonitorService {

    public static final String NOTIFICATION_TYPE_CLASSIFICATION_EXPIRED = "SHIP_CLASSIFICATION_EXPIRED";

    private final ShipRepository shipRepository;
    private final CruiseRepository cruiseRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationSseHub sseHub;

    /**
     * Hàng ngày kiểm tra các tàu đang Sẵn sàng hoạt động (READY) đã hết hạn đăng kiểm:
     * tự động chuyển về Bảo trì (MAINTENANCE) và gửi thông báo cho Quản lý tàu (SHIP_MANAGER).
     */
    @Scheduled(cron = "${casms.ship.classification-check-cron:0 0 1 * * *}")
    @Transactional
    public int checkAndTransitionExpiredShips() {
        LocalDate today = LocalDate.now();
        List<Ship> readyShips = shipRepository.findByStatusAndArchivedAtIsNull(ShipStatus.READY);
        int transitioned = 0;
        for (Ship ship : readyShips) {
            if (transitionToMaintenanceIfExpired(ship, today)) {
                transitioned++;
            }
        }
        if (transitioned > 0) {
            log.info("Auto-transitioned {} ship(s) with expired classification to MAINTENANCE", transitioned);
        }
        return transitioned;
    }

    @Transactional
    public boolean transitionToMaintenanceIfExpired(Ship ship, LocalDate today) {
        if (ship == null || ship.getArchivedAt() != null || ship.getStatus() != ShipStatus.READY) {
            return false;
        }
        ShipOperationalProfile profile = ship.getOperationalProfile();
        if (profile == null || profile.classificationExpiryDate() == null) {
            return false;
        }
        if (!profile.classificationExpiryDate().isBefore(today)) {
            return false;
        }
        if (cruiseRepository.findCurrentActiveCruise(ship.getShipId(), Instant.now()).isPresent()) {
            log.warn("Ship {} ({}) has expired classification ({}) but is currently on an active cruise; deferring auto-maintenance",
                    ship.getShipName(), ship.getShipCode(), profile.classificationExpiryDate());
            notifyShipManagers(ship, profile.classificationExpiryDate(), false);
            return false;
        }
        ship.setStatus(ShipStatus.MAINTENANCE);
        shipRepository.save(ship);
        notifyShipManagers(ship, profile.classificationExpiryDate(), true);
        return true;
    }

    private void notifyShipManagers(Ship ship, LocalDate expiryDate, boolean movedToMaintenance) {
        List<User> recipients = resolveRecipients(ship);
        if (recipients.isEmpty()) {
            return;
        }
        String title = movedToMaintenance
                ? String.format("Tàu %s (%s) đã hết hạn đăng kiểm — tự động chuyển Bảo trì", ship.getShipName(), ship.getShipCode())
                : String.format("Cảnh báo: Tàu %s (%s) đã hết hạn đăng kiểm khi đang chạy chuyến", ship.getShipName(), ship.getShipCode());
        String content = movedToMaintenance
                ? String.format(
                        "Chứng nhận đăng kiểm của tàu %s (%s) đã hết hạn vào ngày %s. Hệ thống đã tự động chuyển tàu sang trạng thái Bảo trì (MAINTENANCE). Vui lòng gia hạn đăng kiểm và cập nhật hồ sơ tàu trước khi đưa lại vào khai thác.",
                        ship.getShipName(), ship.getShipCode(), expiryDate)
                : String.format(
                        "Chứng nhận đăng kiểm của tàu %s (%s) đã hết hạn vào ngày %s trong khi tàu đang khai thác chuyến. Vui lòng kiểm tra và chuyển tàu sang Bảo trì ngay khi chuyến kết thúc.",
                        ship.getShipName(), ship.getShipCode(), expiryDate);

        for (User recipient : recipients) {
            Notification notification = new Notification();
            notification.setRecipientUser(recipient);
            notification.setNotificationType(NOTIFICATION_TYPE_CLASSIFICATION_EXPIRED);
            notification.setTitle(title);
            notification.setContent(content);
            notificationRepository.save(notification);
            UUID recipientId = recipient.getUserId();
            long unread = notificationRepository.countByRecipientUserUserIdAndReadAtIsNull(recipientId);
            publishUnreadAfterCommit(recipientId, unread);
        }
    }

    private List<User> resolveRecipients(Ship ship) {
        List<User> recipients = new ArrayList<>();
        if (ship.getCreatedByShipManagerId() != null) {
            userRepository.findById(ship.getCreatedByShipManagerId())
                    .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                    .ifPresent(recipients::add);
        }
        if (recipients.isEmpty()) {
            userRepository.findAll().stream()
                    .filter(u -> u.getStatus() == UserStatus.ACTIVE && u.hasRole(Role.SHIP_MANAGER))
                    .forEach(recipients::add);
        }
        return recipients;
    }

    private void publishUnreadAfterCommit(UUID userId, long unreadCount) {
        if (sseHub == null) {
            return;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            sseHub.publishUnreadCount(userId, unreadCount);
                        }
                    });
        } else {
            sseHub.publishUnreadCount(userId, unreadCount);
        }
    }
}
