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

import com.casms.identity.enums.Role;

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
        user.addRole(Role.OPERATIONS_MANAGER);

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

    @Test
    void findUsers_filtersOutPassengerForOperationalIssue() {
        User passenger = new User();
        passenger.setUserId(UUID.randomUUID());
        passenger.setUsername("passenger01");
        passenger.setStatus(UserStatus.ACTIVE);
        passenger.addRole(Role.PASSENGER);

        User opStaff = new User();
        opStaff.setUserId(UUID.randomUUID());
        opStaff.setUsername("ops01");
        opStaff.setStatus(UserStatus.ACTIVE);
        opStaff.addRole(Role.OPERATIONS_MANAGER);

        when(userRepository.findByStatus(UserStatus.ACTIVE)).thenReturn(List.of(passenger, opStaff));

        List<UserLookupResponse> result = identityService.findUsers(UserStatus.ACTIVE, "OPERATIONAL_ISSUE");

        assertEquals(1, result.size());
        assertEquals("ops01", result.get(0).getUsername());
    }

    @Test
    void findUsers_filtersOutPassengerForGeneralStaffLookup() {
        User passenger = new User();
        passenger.setUserId(UUID.randomUUID());
        passenger.setUsername("passenger01");
        passenger.setStatus(UserStatus.ACTIVE);
        passenger.addRole(Role.PASSENGER);

        User staff = new User();
        staff.setUserId(UUID.randomUUID());
        staff.setUsername("staff01");
        staff.setStatus(UserStatus.ACTIVE);
        staff.addRole(Role.ADMIN);

        when(userRepository.findByStatus(UserStatus.ACTIVE)).thenReturn(List.of(passenger, staff));

        List<UserLookupResponse> result = identityService.findUsers(UserStatus.ACTIVE, null);

        assertEquals(1, result.size());
        assertEquals("staff01", result.get(0).getUsername());
    }

    @Test
    void isStaff_includesOperationsManager() {
        User user = new User();
        user.addRole(Role.OPERATIONS_MANAGER);
        assertTrue(user.isStaff(), "Operations Manager must be staff");

        User passenger = new User();
        passenger.addRole(Role.PASSENGER);
        assertFalse(passenger.isStaff(), "Passenger must not be staff");
    }
}
