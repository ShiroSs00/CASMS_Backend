package com.casms.notification.repository;

import com.casms.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.Optional;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByRecipientUserUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Notification> findByNotificationIdAndRecipientUserUserId(UUID notificationId, UUID userId);
}
