package com.casms.identity.service;

import com.casms.common.exception.BusinessException;
import com.casms.identity.dto.LoginRequest;
import com.casms.identity.dto.LoginResponse;
import com.casms.identity.dto.UserMeResponse;
import com.casms.identity.entity.RolePermission;
import com.casms.identity.entity.User;
import com.casms.identity.enums.Permission;
import com.casms.identity.enums.Role;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.repository.RolePermissionRepository;
import com.casms.identity.repository.UserRepository;
import com.casms.security.jwt.JwtService;
import com.casms.security.principal.CasmsPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    private User sampleUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        sampleUser = new User();
        sampleUser.setUserId(userId);
        sampleUser.setUsername("testuser");
        sampleUser.setEmail("test@casms.com");
        sampleUser.setStatus(UserStatus.ACTIVE);
    }

    @Test
    void getEffectivePermissions_adminUser_returnsAllPermissions() {
        sampleUser.addRole(Role.ADMIN);

        Set<String> permissions = authService.getEffectivePermissions(sampleUser);

        assertNotNull(permissions);
        assertEquals(Permission.values().length, permissions.size());
        assertTrue(permissions.contains(Permission.SUPPLIER_CREATE.name()));
        assertTrue(permissions.contains(Permission.ITINERARY_CREATE.name()));
    }

    @Test
    void getEffectivePermissions_regularRole_returnsMappedPermissions() {
        sampleUser.addRole(Role.CRUISE_MANAGER);

        RolePermission mapping = new RolePermission();
        mapping.setRole(Role.CRUISE_MANAGER);
        mapping.setPermissions(Set.of(Permission.CRUISE_READ, Permission.SHIP_READ, Permission.ITINERARY_CREATE));

        when(rolePermissionRepository.findByRole(Role.CRUISE_MANAGER))
                .thenReturn(Optional.of(mapping));

        Set<String> permissions = authService.getEffectivePermissions(sampleUser);

        assertNotNull(permissions);
        assertEquals(3, permissions.size());
        assertTrue(permissions.contains("CRUISE_READ"));
        assertTrue(permissions.contains("SHIP_READ"));
        assertTrue(permissions.contains("ITINERARY_CREATE"));
    }

    @Test
    void getCurrentUser_returnsUserMeResponseWithPermissions() {
        sampleUser.addRole(Role.SHIP_MANAGER);
        CasmsPrincipal principal = new CasmsPrincipal(sampleUser, rolePermissionRepository);

        RolePermission mapping = new RolePermission();
        mapping.setRole(Role.SHIP_MANAGER);
        mapping.setPermissions(Set.of(Permission.SHIP_READ, Permission.SHIP_READINESS));

        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(rolePermissionRepository.findByRole(Role.SHIP_MANAGER))
                .thenReturn(Optional.of(mapping));

        UserMeResponse response = authService.getCurrentUser(principal);

        assertNotNull(response);
        assertEquals(userId, response.getUserId());
        assertEquals("testuser", response.getUsername());
        assertEquals("test@casms.com", response.getEmail());
        assertTrue(response.getRoles().contains(Role.SHIP_MANAGER));
        assertTrue(response.getEffectivePermissions().contains("SHIP_READ"));
        assertTrue(response.getEffectivePermissions().contains("SHIP_READINESS"));
    }

    @Test
    void login_activeUser_returnsLoginResponseWithEffectivePermissions() {
        sampleUser.addRole(Role.ONBOARD_STAFF);
        CasmsPrincipal principal = new CasmsPrincipal(sampleUser, rolePermissionRepository);
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(auth);
        when(jwtService.generateToken("testuser", userId)).thenReturn("dummy_token");
        when(jwtService.getExpirationMs()).thenReturn(3600000L);

        RolePermission mapping = new RolePermission();
        mapping.setRole(Role.ONBOARD_STAFF);
        mapping.setPermissions(Set.of(Permission.TOUR_GROUP_READ, Permission.SUPPLIER_READ));

        when(rolePermissionRepository.findByRole(Role.ONBOARD_STAFF))
                .thenReturn(Optional.of(mapping));

        LoginRequest loginReq = new LoginRequest();
        loginReq.setUsername("testuser");
        loginReq.setPassword("Password@123");

        LoginResponse response = authService.login(loginReq);

        assertNotNull(response);
        assertEquals("dummy_token", response.getAccessToken());
        assertEquals(userId, response.getUserId());
        assertTrue(response.getEffectivePermissions().contains("TOUR_GROUP_READ"));
        assertTrue(response.getEffectivePermissions().contains("SUPPLIER_READ"));
    }

    @Test
    void login_disabledAccount_throwsException() {
        sampleUser.setStatus(UserStatus.DISABLED);
        CasmsPrincipal principal = new CasmsPrincipal(sampleUser, rolePermissionRepository);
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(auth);

        LoginRequest loginReq = new LoginRequest();
        loginReq.setUsername("testuser");
        loginReq.setPassword("Password@123");

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(loginReq));
        assertEquals("ACCOUNT_DISABLED", ex.getCode());
    }
}
