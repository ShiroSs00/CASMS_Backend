package com.casms.ship.controller;

import com.casms.identity.entity.User;
import com.casms.identity.enums.Role;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.repository.UserRepository;
import com.casms.security.principal.CasmsPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfEnvironmentVariable(
        named = "CASMS_SALE_TERMS_POSTGRES_URL",
        matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/casms_sale_terms_test(?:\\?.*)?")
@SpringBootTest(properties = {
        "spring.datasource.url=${CASMS_SALE_TERMS_POSTGRES_URL}",
        "spring.datasource.username=${CASMS_SALE_TERMS_POSTGRES_USER}",
        "spring.datasource.password=${CASMS_SALE_TERMS_POSTGRES_PASSWORD}",
        "spring.flyway.url=${CASMS_SALE_TERMS_POSTGRES_URL}",
        "spring.flyway.user=${CASMS_SALE_TERMS_POSTGRES_USER}",
        "spring.flyway.password=${CASMS_SALE_TERMS_POSTGRES_PASSWORD}",
        "spring.flyway.clean-disabled=true",
        "casms.booking-email.enabled=false",
        "casms.staff-assignment-email.enabled=false",
        "casms.employee-activation-email.enabled=false",
        "casms.payment.sepay.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class SaleTermsInboxPostgresIntegrationTest {

    private static final String INBOX = "/cruise-plans/sale-terms-requests";
    private static final String REQUEST = "/cruise-plans/{cruiseId}/sale-terms-request";
    private static final String TERMS = "/cruise-plans/{cruiseId}/commercial-terms/sale";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;

    private RequestPostProcessor crm;
    private RequestPostProcessor ops;
    private RequestPostProcessor opsWithoutPricingPermission;
    private UUID departurePort;
    private UUID returnPort;
    private UUID sourceTrip;
    private UUID sourceVersion;

    @BeforeEach
    void setUp() {
        crm = actor(Role.CRUISE_MANAGER, "CRUISE_UPDATE_DRAFT", "CRUISE_READ");
        ops = actor(Role.OPERATIONS_MANAGER, "CRUISE_PRICING_APPROVE", "CRUISE_READ");
        opsWithoutPricingPermission = actor(Role.OPERATIONS_MANAGER, "CRUISE_READ");
        departurePort = port("Inbox departure");
        returnPort = port("Inbox return");
        sourceTrip = jdbc.queryForObject("""
                INSERT INTO "Trip" (trip_code, name, duration_days, duration_nights, trip_type)
                VALUES (?, 'Inbox trip', 2, 1, 'ONE_WAY') RETURNING trip_id
                """, UUID.class, code());
        sourceVersion = jdbc.queryForObject("""
                INSERT INTO "TripScheduleVersion" (trip_id, version_number, status)
                VALUES (?, 1, 'PUBLISHED') RETURNING version_id
                """, UUID.class, sourceTrip);
    }

    @Test
    void returnsEmptyInbox() throws Exception {
        mvc.perform(get(INBOX).with(ops))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void listsOnlyPendingCruiseWithItsPorts() throws Exception {
        UUID pending = cruise("Pending setup");
        cruise("Unrequested draft");
        requestSetup(pending);
        String cruiseCode = jdbc.queryForObject(
                "SELECT cruise_code FROM \"Cruise\" WHERE cruise_id=?", String.class, pending);

        mvc.perform(get(INBOX).with(ops))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].cruiseId").value(pending.toString()))
                .andExpect(jsonPath("$.data[0].cruiseCode").value(cruiseCode))
                .andExpect(jsonPath("$.data[0].cruiseName").value("Pending setup"))
                .andExpect(jsonPath("$.data[0].departurePort").value("Inbox departure"))
                .andExpect(jsonPath("$.data[0].returnPort").value("Inbox return"))
                .andExpect(jsonPath("$.data[0].ports.length()").value(0));
    }

    @Test
    void deniesOpsWithoutPricingPermission() throws Exception {
        UUID cruise = cruise("Pending setup");
        requestSetup(cruise);

        mvc.perform(get(INBOX).with(opsWithoutPricingPermission))
                .andExpect(status().isForbidden());
        mvc.perform(get(TERMS, cruise).with(opsWithoutPricingPermission))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsOpsToReadTermsOnlyForRequestedDraft() throws Exception {
        UUID requested = cruise("Pending setup");
        UUID unrelated = cruise("Unrequested draft");
        requestSetup(requested);

        mvc.perform(get(TERMS, requested).with(ops))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("MISSING"));
        mvc.perform(get(TERMS, unrelated).with(ops))
                .andExpect(status().isForbidden());
    }

    @Test
    void savingTermsCompletesRequestAndRemovesItFromInbox() throws Exception {
        UUID cruise = cruise("Pending setup");
        requestSetup(cruise);

        mvc.perform(put(TERMS, cruise).with(ops).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedRevision": 0,
                                  "terms": {
                                    "tax": {"applicability": "NOT_APPLICABLE", "reason": "Test tax"},
                                    "portFee": {"applicability": "NOT_APPLICABLE", "reason": "Test port fee"}
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.revision").value(1));

        assertTrue(Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT completed_at IS NOT NULL FROM "CruiseSaleTermsRequest" WHERE cruise_id=?
                """, Boolean.class, cruise)));
        mvc.perform(get(INBOX).with(ops))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get(REQUEST, cruise).with(crm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));
    }

    private void requestSetup(UUID cruise) throws Exception {
        mvc.perform(post(REQUEST, cruise).with(crm).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }

    private UUID cruise(String name) {
        return jdbc.queryForObject("""
                INSERT INTO "Cruise" (
                    cruise_id, cruise_code, cruise_name, cruise_type, departure_datetime, return_datetime,
                    departure_port_id, return_port_id, status, source_trip_id, source_version_id, created_at, updated_at
                ) VALUES (gen_random_uuid(), ?, ?, 'MULTI_DAYS', now() + interval '5 days', now() + interval '7 days',
                          ?, ?, 'DRAFT', ?, ?, now(), now())
                RETURNING cruise_id
                """, UUID.class, code(), name, departurePort, returnPort, sourceTrip, sourceVersion);
    }

    private UUID port(String name) {
        return jdbc.queryForObject("""
                INSERT INTO "Port" (
                    port_id, port_code, port_name, country_code, time_zone_id, status, created_at, updated_at
                ) VALUES (gen_random_uuid(), ?, ?, 'VN', 'Asia/Ho_Chi_Minh', 'ACTIVE', now(), now())
                RETURNING port_id
                """, UUID.class, code(), name);
    }

    private RequestPostProcessor actor(Role role, String... permissions) {
        User user = new User();
        user.setUsername(code());
        user.setPasswordHash("unused-fixture-password-hash");
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(role));
        CasmsPrincipal principal = new CasmsPrincipal(users.saveAndFlush(user), null);
        var authorities = Stream.concat(Stream.of("ROLE_" + role.name()), Arrays.stream(permissions))
                .map(SimpleGrantedAuthority::new)
                .toList();
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
    }

    private String code() {
        return "INBOX-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
