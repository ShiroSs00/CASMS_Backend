package com.casms.notification.service;

import com.casms.common.exception.ResourceNotFoundException;
import com.casms.identity.entity.User;
import com.casms.notification.entity.Notification;
import com.casms.notification.repository.NotificationRepository;
import com.casms.security.principal.CasmsPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    @Mock NotificationRepository repository;
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

        assertThrows(ResourceNotFoundException.class, () -> service.markRead(notificationId, principal));
        verify(repository).findByNotificationIdAndRecipientUserUserId(notificationId, ownerId);
    }
}
