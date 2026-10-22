package com.casms.notification.service;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.notification.entity.Notification;
import com.casms.notification.dto.NotificationResponse;
import com.casms.notification.repository.NotificationRepository;
import com.casms.security.principal.CasmsPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public List<NotificationResponse> listMine(CasmsPrincipal principal) {
        UUID userId = requireUserId(principal);
        return notificationRepository.findByRecipientUserUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public NotificationResponse markRead(UUID notificationId, CasmsPrincipal principal) {
        UUID userId = requireUserId(principal);
        Notification notification = notificationRepository
                .findByNotificationIdAndRecipientUserUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
        }
        return toResponse(notificationRepository.save(notification));
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(notification.getNotificationId(), notification.getBookingPassengerId(),
                notification.getNotificationType(), notification.getTitle(), notification.getContent(),
                notification.getCreatedAt(), notification.getReadAt());
    }

    private UUID requireUserId(CasmsPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Authentication is required");
        }
        return principal.getUserId();
    }
}
