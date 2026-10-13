package com.casms.identity.service;

import com.casms.identity.dto.UserLookupResponse;
import com.casms.identity.entity.User;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private IdentityService identityService;

    @Test
    void findUsers_returnsSafeFieldsAndCapabilityLabel() {
        User user = new User();
        user.setUserId(UUID.randomUUID());
        user.setUsername("operator01");
        user.setPasswordHash("secret_hash_should_not_leak");
        user.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByStatus(UserStatus.ACTIVE)).thenReturn(List.of(user));

        List<UserLookupResponse> result = identityService.findUsers(UserStatus.ACTIVE, "OPERATIONAL_ISSUE");

        assertEquals(1, result.size());
        UserLookupResponse dto = result.get(0);
        assertEquals(user.getUserId(), dto.getId());
        assertEquals("operator01", dto.getUsername());
        assertEquals("operator01", dto.getDisplayName());
        assertEquals(UserStatus.ACTIVE, dto.getStatus());
        assertEquals("Operational Issue Handler", dto.getCapabilityLabel());
    }
}
