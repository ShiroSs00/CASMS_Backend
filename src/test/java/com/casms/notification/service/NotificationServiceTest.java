package com.casms.notification.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.casms.common.exception.ResourceNotFoundException;
import com.casms.identity.entity.User;
import com.casms.notification.repository.NotificationRepository;
import com.casms.notification.repository.ImpactNotificationOutboxRepository;
import com.casms.notification.entity.ImpactNotificationOutbox;
import com.casms.notification.entity.Notification;
import com.casms.security.principal.CasmsPrincipal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    @Mock NotificationRepository repository;
    @Mock ImpactNotificationOutboxRepository outbox;
    @Mock NotificationSseHub hub;
    @InjectMocks NotificationService service;

    @Test
    void markReadOnlyLoadsNotificationForAuthenticatedOwner() {
        UUID notificationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        User owner = new User();
        owner.setUserId(ownerId);
        CasmsPrincipal principal = new CasmsPrincipal(owner, null);

        when(repository.findByNotificationIdAndRecipientUserUserId(notificationId, ownerId))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class, () -> service.markRead(notificationId, principal));
        verify(repository).findByNotificationIdAndRecipientUserUserId(notificationId, ownerId);
    }

    @Test
    void acknowledgeUpdatesOnlyTheOutboxLinkedToTheOwnedNotification() {
        UUID notificationId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        User owner = new User();
        owner.setUserId(ownerId);
        CasmsPrincipal principal = new CasmsPrincipal(owner, null);
        Notification notification = new Notification();
        notification.setNotificationId(notificationId);
        var linked = ImpactNotificationOutbox.builder().notification(notification).build();
        when(repository.findByNotificationIdAndRecipientUserUserId(notificationId, ownerId))
                .thenReturn(Optional.of(notification));
        when(outbox.findByNotification_NotificationIdAndRecipientUser_UserId(notificationId, ownerId))
                .thenReturn(Optional.of(linked));
        when(repository.countByRecipientUserUserIdAndReadAtIsNull(ownerId)).thenReturn(2L);

        service.acknowledgeNotification(notificationId, principal);

        org.junit.jupiter.api.Assertions.assertEquals("ACK", linked.getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(linked.getAcknowledgedAt());
        verify(outbox).save(linked);
        verify(hub).publishUnreadCount(ownerId, 2L);
        org.mockito.Mockito.verifyNoMoreInteractions(outbox);
    }
}
