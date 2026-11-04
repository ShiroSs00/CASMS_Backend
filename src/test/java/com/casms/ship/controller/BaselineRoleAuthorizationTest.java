package com.casms.ship.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.casms.common.exception.GlobalExceptionHandler;
import com.casms.identity.entity.RolePermission;
import com.casms.identity.entity.User;
import com.casms.identity.enums.Role;
import com.casms.identity.repository.RolePermissionRepository;
import com.casms.identity.service.RolePermissionInitializer;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.dto.response.CruiseResponse;
import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.service.CruiseService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Real method-security interceptor and MVC, without starting a database or running initializers on
 * it.
 */
class BaselineRoleAuthorizationTest {
    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        CruiseService cruiseService() {
            return mock(CruiseService.class);
        }

        @Bean
        CruiseController cruiseController(CruiseService service) {
            return new CruiseController(service, null, null, null, null, null, null, null, null);
        }
    }

    private AnnotationConfigApplicationContext context;
    private MockMvc mvc;
    private CruiseService service;
    private RolePermissionRepository permissions;

    @BeforeEach
    void setUp() {
        Map<Role, RolePermission> rows = new EnumMap<>(Role.class);
        permissions = mock(RolePermissionRepository.class);
        when(permissions.findByRole(any()))
                .thenAnswer(i -> Optional.ofNullable(rows.get(i.getArgument(0))));
        when(permissions.save(any()))
                .thenAnswer(
                        i -> {
                            RolePermission row = i.getArgument(0);
                            rows.put(row.getRole(), row);
                            return row;
                        });
        new RolePermissionInitializer(permissions).initializeRolePermissions();
        context = new AnnotationConfigApplicationContext(Config.class);
        service = context.getBean(CruiseService.class);
        mvc =
                MockMvcBuilders.standaloneSetup(context.getBean(CruiseController.class))
                        .setCustomArgumentResolvers(
                                new org.springframework.data.web
                                        .PageableHandlerMethodArgumentResolver())
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @AfterEach
    void close() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    private void authenticate(Role role) {
        User user = new User();
        user.setUserId(UUID.randomUUID());
        user.setRoles(Set.of(role));
        CasmsPrincipal principal = new CasmsPrincipal(user, permissions);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities()));
    }

    @Test
    void crmCannotCancelEitherRouteAliasAndServiceIsNeverCalled() throws Exception {
        authenticate(Role.CRUISE_MANAGER);
        for (String base : new String[] {"/cruise", "/cruises"}) {
            mvc.perform(
                            post(base + "/{id}/cancel", UUID.randomUUID())
                                    .contentType("application/json")
                                    .content("{\"reason\":\"Weather\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void opsCanCancelAndReturnsServiceResponse() throws Exception {
        authenticate(Role.OPERATIONS_MANAGER);
        UUID cruiseId = UUID.randomUUID();
        CruiseResponse response = new CruiseResponse();
        response.setCruiseId(cruiseId);
        response.setStatus(CruiseStatus.CANCELLED);
        when(service.cancel(cruiseId, "Weather")).thenReturn(response);
        mvc.perform(
                        post("/cruise/{id}/cancel", cruiseId)
                                .contentType("application/json")
                                .content("{\"reason\":\"Weather\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cruiseId").value(cruiseId.toString()))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        verify(service).cancel(cruiseId, "Weather");
    }

    @Test
    void opsCannotAssignShip() throws Exception {
        authenticate(Role.OPERATIONS_MANAGER);
        mvc.perform(
                        post("/cruise/{id}/assign-ship", UUID.randomUUID())
                                .param("shipId", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void crmCannotAllocateStaff() throws Exception {
        authenticate(Role.CRUISE_MANAGER);
        mvc.perform(
                        post("/cruise/{id}/staff", UUID.randomUUID())
                                .contentType("application/json")
                                .content(
                                        "{\"userId\":\""
                                                + UUID.randomUUID()
                                                + "\",\"roleInCruise\":\"CAPTAIN\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void bookingCannotUseStatusFilterToReadDraftsOnEitherAlias() throws Exception {
        authenticate(Role.BOOKING_AGENT);
        when(service.listOpenForSale(any(), any(), any(), any(), any(), any()))
                .thenReturn(
                        org.springframework.data.domain.Page.empty(
                                org.springframework.data.domain.PageRequest.of(0, 20)));
        for (String base : new String[] {"/cruise", "/cruises"}) {
            mvc.perform(
                            get(base)
                                    .param("status", "DRAFT")
                                    .principal(
                                            SecurityContextHolder.getContext().getAuthentication()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(0));
        }
        verify(service, times(2)).listOpenForSale(any(), any(), any(), any(), any(), any());
        verify(service, never()).list(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void bookingCannotReadPublishedButClosedCruiseByDirectUrl() throws Exception {
        authenticate(Role.BOOKING_AGENT);
        UUID id = UUID.randomUUID();
        CruiseResponse cruise = new CruiseResponse();
        cruise.setCruiseId(id);
        cruise.setStatus(CruiseStatus.PUBLISHED);
        when(service.detail(id)).thenReturn(cruise);
        when(service.getPublicSellability(id))
                .thenReturn(
                        com.casms.ship.dto.response.CruiseSellabilityResponse.builder()
                                .saleState("STOPPED")
                                .build());
        mvc.perform(
                        get("/cruises/{id}", id)
                                .principal(SecurityContextHolder.getContext().getAuthentication()))
                .andExpect(status().isForbidden());
    }

    @Test
    void crmCanStillReadDraftsWhilePublicCatalogUsesSaleFilter() throws Exception {
        authenticate(Role.CRUISE_MANAGER);
        when(service.list(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(
                        org.springframework.data.domain.Page.empty(
                                org.springframework.data.domain.PageRequest.of(0, 20)));
        when(service.listOpenForSale(any(), any(), any(), any(), any(), any()))
                .thenReturn(
                        org.springframework.data.domain.Page.empty(
                                org.springframework.data.domain.PageRequest.of(0, 20)));
        var auth = SecurityContextHolder.getContext().getAuthentication();
        mvc.perform(get("/cruise").param("status", "DRAFT").principal(auth))
                .andExpect(status().isOk());
        verify(service).list(eq(CruiseStatus.DRAFT), any(), any(), any(), any(), any(), any());
        mvc.perform(get("/cruise").param("saleState", "OPEN_FOR_SALE").principal(auth))
                .andExpect(status().isOk());
        verify(service).listOpenForSale(any(), any(), any(), any(), any(), any());
    }
}
