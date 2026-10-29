package com.casms.ship.controller;

import com.casms.ship.dto.response.PassengerTrackingResponse;
import com.casms.ship.service.BleRuntimeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(PassengerTrackingAuthorizationTest.TestConfig.class)
class PassengerTrackingAuthorizationTest {
    @Configuration
    @EnableMethodSecurity
    static class TestConfig {
        @Bean BleRuntimeService bleRuntimeService() { return mock(BleRuntimeService.class); }
        @Bean PassengerTrackingController passengerTrackingController(BleRuntimeService service) {
            return new PassengerTrackingController(service);
        }
    }

    @Autowired PassengerTrackingController controller;
    @Autowired BleRuntimeService service;

    @BeforeEach
    void resetMock() { reset(service); }

    @AfterEach
    void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test
    void shipManagerWithTelemetryPermissionCanReadOverview() {
        UUID cruiseId = UUID.randomUUID();
        var expected = new PassengerTrackingResponse(UUID.randomUUID(), cruiseId, Instant.now(),
                "RANGE_ONLY_NO_DIRECTION", List.of());
        when(service.passengerOverview(cruiseId)).thenReturn(expected);
        authenticate("ship-manager", "ROLE_SHIP_MANAGER", "BLE_TELEMETRY_READ");

        assertSame(expected, controller.snapshot(cruiseId).getData());
        verify(service).passengerOverview(cruiseId);
    }

    @Test
    void shipManagerWithoutTelemetryPermissionIsDenied() {
        authenticate("ship-manager", "ROLE_SHIP_MANAGER");
        assertThrows(AccessDeniedException.class, () -> controller.snapshot(UUID.randomUUID()));
        verifyNoInteractions(service);
    }

    @Test
    void otherRoleWithTelemetryPermissionIsDenied() {
        authenticate("onboard-staff", "ROLE_ONBOARD_STAFF", "BLE_TELEMETRY_READ");
        assertThrows(AccessDeniedException.class, () -> controller.snapshot(UUID.randomUUID()));
        verifyNoInteractions(service);
    }

    @Test
    void unauthenticatedCallerIsDenied() {
        SecurityContextHolder.clearContext();
        assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> controller.snapshot(UUID.randomUUID()));
        verifyNoInteractions(service);
    }

    private static void authenticate(String username, String... authorities) {
        var granted = java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "n/a", granted));
    }
}
