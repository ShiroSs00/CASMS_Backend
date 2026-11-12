package com.casms.identity.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.casms.common.exception.BusinessException;
import com.casms.identity.dto.LoginRequest;
import com.casms.identity.dto.LoginResponse;
import com.casms.identity.dto.UserMeResponse;
import com.casms.identity.entity.RolePermission;
import com.casms.identity.entity.User;
import com.casms.identity.enums.Permission;
import com.casms.identity.enums.Role;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.repository.PassengerRepository;
import com.casms.identity.repository.RolePermissionRepository;
import com.casms.identity.repository.UserRepository;
import com.casms.security.auth.AuthenticationService;
import com.casms.security.jwt.JwtService;
import com.casms.security.principal.CasmsPrincipal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;

    @Mock private RolePermissionRepository rolePermissionRepository;

    @Mock private PassengerRepository passengerRepository;

    @Mock private PasswordEncoder passwordEncoder;

    @Mock private JwtService jwtService;

    @Mock private AuthenticationManager authenticationManager;

    @InjectMocks private AuthService authService;

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
    void getEffectivePermissions_adminUser_returnsConfiguredRolePermissions() {
        sampleUser.addRole(Role.ADMIN);
        RolePermission mapping = new RolePermission();
        mapping.setRole(Role.ADMIN);
        mapping.setPermissions(
                Set.of(
                        Permission.USER_READ,
                        Permission.SHIP_MODEL_READ,
                        Permission.BLE_ANCHOR_READ));
        when(rolePermissionRepository.findByRole(Role.ADMIN)).thenReturn(Optional.of(mapping));

        Set<String> permissions = authService.getEffectivePermissions(sampleUser);

        assertNotNull(permissions);
        assertEquals(Set.of("USER_READ", "SHIP_MODEL_READ", "BLE_ANCHOR_READ"), permissions);
        assertFalse(permissions.contains(Permission.SUPPLIER_CREATE.name()));
        assertFalse(permissions.contains(Permission.ITINERARY_CREATE.name()));
    }

    @Test
    void getEffectivePermissions_regularRole_returnsMappedPermissions() {
        sampleUser.addRole(Role.CRUISE_MANAGER);

        RolePermission mapping = new RolePermission();
        mapping.setRole(Role.CRUISE_MANAGER);
        mapping.setPermissions(
                Set.of(Permission.CRUISE_READ, Permission.SHIP_READ, Permission.ITINERARY_CREATE));

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
    void getCurrentUser_rejectsPrincipalIfAccountWasDeletedAfterAuthentication() {
        var principal = new CasmsPrincipal(sampleUser, rolePermissionRepository);

        var failure = assertThrows(BusinessException.class, () -> authService.getCurrentUser(principal));

        assertEquals(HttpStatus.UNAUTHORIZED, failure.getStatus());
        assertEquals("AUTH_REQUIRED", failure.getCode());
        verifyNoInteractions(passengerRepository);
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
        assertNotNull(sampleUser.getLastLoginAt());
        verify(userRepository).save(sampleUser);
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

        BusinessException ex =
                assertThrows(BusinessException.class, () -> authService.login(loginReq));
        assertInvalidCredentials(ex);
        assertNull(sampleUser.getLastLoginAt());
        verifyNoInteractions(userRepository, jwtService);
    }

    private enum RejectedLogin {
        UNKNOWN_USER,
        WRONG_PASSWORD,
        LOCKED,
        DISABLED
    }

    @ParameterizedTest
    @EnumSource(RejectedLogin.class)
    void login_providerRejectionsHaveSameResponse(RejectedLogin rejection) {
        var encoder = new BCryptPasswordEncoder(4);
        var service = serviceWithProvider(encoder);
        sampleUser.setPasswordHash(encoder.encode("correct-password"));
        if (rejection == RejectedLogin.LOCKED) {
            sampleUser.setStatus(UserStatus.LOCKED);
        } else if (rejection == RejectedLogin.DISABLED) {
            sampleUser.setStatus(UserStatus.DISABLED);
        }
        if (rejection != RejectedLogin.UNKNOWN_USER) {
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(sampleUser));
        }
        var request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword(rejection == RejectedLogin.WRONG_PASSWORD ? "wrong-password" : "correct-password");

        assertInvalidCredentials(assertThrows(BusinessException.class, () -> service.login(request)));
        assertNull(sampleUser.getLastLoginAt());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(jwtService);
    }

    @Test
    void login_providerDatabaseFailureRemainsAnInternalError() {
        var service = serviceWithProvider(new BCryptPasswordEncoder(4));
        when(userRepository.findByUsername("testuser"))
                .thenThrow(new DataAccessResourceFailureException("Database unavailable"));
        var request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("correct-password");

        assertThrows(InternalAuthenticationServiceException.class, () -> service.login(request));
        verify(userRepository, never()).save(any());
        verifyNoInteractions(jwtService);
    }

    @Test
    void login_databaseFailureWhenProviderLoadsAuthoritiesRemainsAnInternalError() {
        var encoder = new BCryptPasswordEncoder(4);
        var service = serviceWithProvider(encoder);
        sampleUser.addRole(Role.ADMIN);
        sampleUser.setPasswordHash(encoder.encode("correct-password"));
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(sampleUser));
        var databaseFailure = new DataAccessResourceFailureException("Database unavailable");
        when(rolePermissionRepository.findByRole(Role.ADMIN)).thenThrow(databaseFailure);
        var request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("correct-password");

        var failure = assertThrows(AuthenticationServiceException.class, () -> service.login(request));
        assertSame(databaseFailure, failure.getCause());
        assertNull(sampleUser.getLastLoginAt());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(jwtService);
    }

    @Test
    void refreshToken_invalidTokenSkipsAccountLookup() {
        assertInvalidToken(assertThrows(BusinessException.class, () -> authService.refreshToken("invalid")));
        verifyNoInteractions(userRepository);
    }

    @Test
    void refreshToken_deletedAccountDoesNotReceiveToken() {
        when(jwtService.findValidUserId("current")).thenReturn(Optional.of(userId));

        assertInvalidToken(assertThrows(BusinessException.class, () -> authService.refreshToken("current")));
        verify(jwtService, never()).generateToken(any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "ACTIVE")
    void refreshToken_inactiveAccountDoesNotReceiveToken(UserStatus status) {
        sampleUser.setStatus(status);
        when(jwtService.findValidUserId("current")).thenReturn(Optional.of(userId));
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));

        assertInvalidToken(assertThrows(BusinessException.class, () -> authService.refreshToken("current")));
        verify(jwtService, never()).generateToken(any(), any());
    }

    @Test
    void refreshToken_activeAccountReceivesTokenForCurrentUsername() {
        sampleUser.setUsername("renamed");
        when(jwtService.findValidUserId("current")).thenReturn(Optional.of(userId));
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(jwtService.generateToken("renamed", userId)).thenReturn("replacement");

        var result = authService.refreshToken("current");

        assertEquals("replacement", result.getAccessToken());
        assertEquals("renamed", result.getUsername());
        verify(jwtService).findValidUserId("current");
        verify(userRepository, never()).save(any());
    }

    private AuthService serviceWithProvider(PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(new AuthenticationService(userRepository, rolePermissionRepository));
        provider.setPasswordEncoder(encoder);
        return new AuthService(
                userRepository, rolePermissionRepository, passengerRepository, encoder, jwtService,
                provider::authenticate);
    }

    private static void assertInvalidCredentials(BusinessException exception) {
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        assertEquals("INVALID_CREDENTIALS", exception.getCode());
        assertEquals("Invalid username or password", exception.getMessage());
    }

    private static void assertInvalidToken(BusinessException exception) {
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        assertEquals("INVALID_TOKEN", exception.getCode());
        assertEquals("Invalid or expired token", exception.getMessage());
    }
}
