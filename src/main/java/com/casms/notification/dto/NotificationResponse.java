package com.casms.notification.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID notificationId,
        UUID bookingPassengerId,
        String notificationType,
        String title,
        String content,
        Instant createdAt,
        Instant readAt) {
}
