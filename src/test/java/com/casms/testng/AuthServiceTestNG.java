package com.casms.testng;

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
import com.casms.identity.service.AuthService;
import com.casms.security.auth.AuthenticationService;
import com.casms.security.jwt.JwtService;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.testng.listener.CasmsTestListener;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testng.Assert;
import org.testng.annotations.*;
import org.testng.asserts.SoftAssert;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Listeners(CasmsTestListener.class)
public class AuthServiceTestNG {

    private AutoCloseable mocks;

    @Mock private UserRepository userRepository;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private PassengerRepository passengerRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;

    @InjectMocks private AuthService authService;

    private UUID userId;
    private User sampleUser;

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        authService = null;
        mocks = MockitoAnnotations.openMocks(this);
        userId = UUID.randomUUID();
        sampleUser = new User();
        sampleUser.setUserId(userId);
        sampleUser.setUsername("casms_staff");
        sampleUser.setEmail("staff@casms.com");
        sampleUser.setStatus(UserStatus.ACTIVE);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @DataProvider(name = "rolePermissionsData")
    public Object[][] rolePermissionsData() {
        return new Object[][] {
            {Role.ADMIN, Set.of(Permission.USER_READ, Permission.SHIP_MODEL_READ, Permission.BLE_ANCHOR_READ)},
            {Role.CRUISE_MANAGER, Set.of(Permission.CRUISE_READ, Permission.SHIP_READ, Permission.ITINERARY_CREATE)},
            {Role.SHIP_MANAGER, Set.of(Permission.SHIP_READ, Permission.SHIP_READINESS)},
            {Role.OPERATIONS_MANAGER, Set.of(Permission.PORT_CREATE, Permission.DESTINATION_CREATE, Permission.REGULATION_CREATE)},
            {Role.ACTIVITY_SERVICE_MANAGER, Set.of(Permission.ACTIVITY_CREATE, Permission.SERVICE_CREATE, Permission.SUPPLIER_CREATE)},
            {Role.BOOKING_AGENT, Set.of(Permission.BOOKING_CREATE, Permission.BOOKING_READ, Permission.CHECKIN_CREATE)},
            {Role.FINANCE_OFFICER, Set.of(Permission.PAYMENT_READ, Permission.PAYMENT_REFUND, Permission.PAYMENT_REPORT)},
        };
    }

    @Test(dataProvider = "rolePermissionsData", groups = {"smoke", "regression", "security"})
    public void testEffectivePermissionsByRole(Role role, Set<Permission> configuredPermissions) {
        sampleUser.addRole(role);
        RolePermission mapping = new RolePermission();
        mapping.setRole(role);
        mapping.setPermissions(configuredPermissions);
        when(rolePermissionRepository.findByRole(role)).thenReturn(Optional.of(mapping));

        Set<String> effective = authService.getEffectivePermissions(sampleUser);

        Assert.assertNotNull(effective);
        Assert.assertEquals(effective.size(), configuredPermissions.size());
        for (Permission p : configuredPermissions) {
            Assert.assertTrue(effective.contains(p.name()), "Missing permission: " + p.name());
        }
    }

    @Test(groups = {"smoke", "regression"})
    public void testLoginActiveUserReturnsTokenAndPermissions() {
        sampleUser.addRole(Role.ONBOARD_STAFF);
        CasmsPrincipal principal = new CasmsPrincipal(sampleUser, rolePermissionRepository);
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(auth);
        when(jwtService.generateToken("casms_staff", userId)).thenReturn("jwt-token-001");
        when(jwtService.getExpirationMs()).thenReturn(3600000L);

        RolePermission mapping = new RolePermission();
        mapping.setRole(Role.ONBOARD_STAFF);
        mapping.setPermissions(Set.of(Permission.TOUR_GROUP_READ, Permission.ACTIVITY_READ));
        when(rolePermissionRepository.findByRole(Role.ONBOARD_STAFF)).thenReturn(Optional.of(mapping));

        LoginRequest req = new LoginRequest();
        req.setUsername("casms_staff");
        req.setPassword("Password@123");

        LoginResponse response = authService.login(req);

        SoftAssert soft = new SoftAssert();
        soft.assertNotNull(response, "LoginResponse must not be null");
        soft.assertEquals(response.getAccessToken(), "jwt-token-001");
        soft.assertEquals(response.getUserId(), userId);
        soft.assertTrue(response.getEffectivePermissions().contains("TOUR_GROUP_READ"));
        soft.assertTrue(response.getEffectivePermissions().contains("ACTIVITY_READ"));
        soft.assertNotNull(sampleUser.getLastLoginAt(), "lastLoginAt must be updated");
        soft.assertAll();
        verify(userRepository).save(sampleUser);
    }

    @Test(groups = {"smoke", "regression"})
    public void testGetCurrentUserReturnsProfileWithRoles() {
        sampleUser.addRole(Role.SHIP_MANAGER);
        CasmsPrincipal principal = new CasmsPrincipal(sampleUser, rolePermissionRepository);
        RolePermission mapping = new RolePermission();
        mapping.setRole(Role.SHIP_MANAGER);
        mapping.setPermissions(Set.of(Permission.SHIP_READ, Permission.SHIP_READINESS));

        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(rolePermissionRepository.findByRole(Role.SHIP_MANAGER)).thenReturn(Optional.of(mapping));

        UserMeResponse me = authService.getCurrentUser(principal);

        SoftAssert soft = new SoftAssert();
        soft.assertEquals(me.getUserId(), userId);
        soft.assertEquals(me.getUsername(), "casms_staff");
        soft.assertEquals(me.getEmail(), "staff@casms.com");
        soft.assertTrue(me.getRoles().contains(Role.SHIP_MANAGER));
        soft.assertTrue(me.getEffectivePermissions().contains("SHIP_READ"));
        soft.assertAll();
    }

    @DataProvider(name = "rejectedLoginData")
    public Object[][] rejectedLoginData() {
        return new Object[][] {
            {"UNKNOWN_USER", UserStatus.ACTIVE, "correct-password"},
            {"WRONG_PASSWORD", UserStatus.ACTIVE, "wrong-password"},
            {"LOCKED", UserStatus.LOCKED, "correct-password"},
            {"DISABLED", UserStatus.DISABLED, "correct-password"},
        };
    }

    @Test(dataProvider = "rejectedLoginData", groups = {"regression", "security"})
    public void testProviderRejectionsReturnUniformInvalidCredentials(
            String scenario, UserStatus status, String submittedPassword) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(
                new AuthenticationService(userRepository, rolePermissionRepository));
        provider.setPasswordEncoder(encoder);
        AuthService service =
                new AuthService(
                        userRepository,
                        rolePermissionRepository,
                        passengerRepository,
                        encoder,
                        jwtService,
                        provider::authenticate);

        sampleUser.setPasswordHash(encoder.encode("correct-password"));
        sampleUser.setStatus(status);
        if (!"UNKNOWN_USER".equals(scenario)) {
            when(userRepository.findByUsername("casms_staff")).thenReturn(Optional.of(sampleUser));
        }

        LoginRequest req = new LoginRequest();
        req.setUsername("casms_staff");
        req.setPassword(submittedPassword);

        BusinessException ex =
                Assert.expectThrows(BusinessException.class, () -> service.login(req));
        Assert.assertEquals(ex.getStatus(), HttpStatus.UNAUTHORIZED);
        Assert.assertEquals(ex.getCode(), "INVALID_CREDENTIALS");
        verify(userRepository, never()).save(any());
        verifyNoInteractions(jwtService);
    }

    @Test(groups = {"regression", "security"}, expectedExceptions = BusinessException.class)
    public void testRefreshTokenInvalidThrowsBusinessException() {
        authService.refreshToken("invalid-token");
    }

    @DataProvider(name = "inactiveUserStatuses")
    public Object[][] inactiveUserStatuses() {
        return new Object[][] {
            {UserStatus.DISABLED},
            {UserStatus.LOCKED},
        };
    }

    @Test(dataProvider = "inactiveUserStatuses", groups = {"regression", "security"})
    public void testRefreshTokenRejectsInactiveAccount(UserStatus inactiveStatus) {
        sampleUser.setStatus(inactiveStatus);
        when(jwtService.findValidUserId("valid-token")).thenReturn(Optional.of(userId));
        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));

        BusinessException ex =
                Assert.expectThrows(
                        BusinessException.class, () -> authService.refreshToken("valid-token"));
        Assert.assertEquals(ex.getCode(), "INVALID_TOKEN");
        verify(jwtService, never()).generateToken(any(), any());
    }
}
