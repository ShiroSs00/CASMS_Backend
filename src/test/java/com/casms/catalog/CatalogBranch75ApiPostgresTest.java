package com.casms.catalog;

import com.casms.identity.entity.User;
import com.casms.identity.enums.Role;
import com.casms.identity.enums.UserStatus;
import com.casms.security.principal.CasmsPrincipal;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfEnvironmentVariable(named = "CASMS_CATALOG_TEST_PG_PORT", matches = "[0-9]{1,5}")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:${CASMS_CATALOG_TEST_PG_PORT}/casms_catalog_api",
        "spring.datasource.username=postgres", "spring.datasource.password=",
        "casms.booking-email.enabled=false"
})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
class CatalogBranch75ApiPostgresTest {
    private static final String LABELS = "/api/catalog/labels/SERVICE";
    private static final String BENEFITS = "/api/catalog/package-benefits/VN-PKG-001";
    private static final String VERIFY = "/api/catalog/tour-candidates/VN-SHORE-001/verify";
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;

    @BeforeAll
    void seedDisposableDatabase() throws Exception {
        CatalogBranch75PostgresFixtures.runSeeds("jdbc:postgresql://127.0.0.1:"
                + System.getenv("CASMS_CATALOG_TEST_PG_PORT") + "/casms_catalog_api");
    }

    @Test
    void localeFallbackValidationPaginationAndPermissions() throws Exception {
        var staff = actor(Role.CRUISE_MANAGER, UUID.randomUUID(), "SERVICE_READ");
        Integer offset = jdbc.queryForObject("""
                SELECT count(*)::int FROM "Service" WHERE service_code < 'VN-SVC-001'
                """, Integer.class);
        var vi = mvc.perform(get(LABELS).contextPath("/api").param("locale", "vi")
                        .param("page", offset.toString()).param("size", "1").with(staff))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value("VN-SVC-001"))
                .andExpect(jsonPath("$.data[0].resolvedLocale").value("vi"));
        vi.andExpect(jsonPath("$.data[0].status").value("INACTIVE"));
        mvc.perform(get(LABELS).contextPath("/api").param("locale", "en-US")
                        .param("page", offset.toString()).param("size", "1").with(staff))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].resolvedLocale").value("vi"));
        jdbc.update("""
                INSERT INTO "CatalogTranslation" (entity_kind, entity_code, locale, display_name)
                VALUES ('SERVICE', 'VN-SVC-001', 'en', 'Breakfast buffet')
                """);
        mvc.perform(get(LABELS).contextPath("/api").param("locale", "en-US")
                        .param("page", offset.toString()).param("size", "1").with(staff))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].resolvedLocale").value("en"))
                .andExpect(jsonPath("$.data[0].displayName").value("Breakfast buffet"));
        mvc.perform(get(LABELS).contextPath("/api").param("locale", "en_US").with(staff))
                .andExpect(status().isBadRequest());
        mvc.perform(get(LABELS).contextPath("/api").param("size", "101").with(staff))
                .andExpect(status().isBadRequest());
        mvc.perform(get(LABELS).contextPath("/api")
                        .with(actor(Role.PASSENGER, UUID.randomUUID(), "SERVICE_READ")))
                .andExpect(status().isForbidden());
    }

    @Test
    void packageExposesProposedServiceQuotas() throws Exception {
        mvc.perform(get(BENEFITS).contextPath("/api")
                        .with(actor(Role.CRUISE_MANAGER, UUID.randomUUID(), "SERVICE_READ")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.benefits[0].resourceType").value("SERVICE"))
                .andExpect(jsonPath("$.data.benefits[0].quantityLimit").value(1))
                .andExpect(jsonPath("$.data.benefits[0].unitCode").value("SERVICE_UNIT"));
    }

    @Test
    void onlyAuthorizedCompleteReviewCreatesVerifiedStagingRecord() throws Exception {
        UUID user = UUID.randomUUID(), port = UUID.randomUUID(), supplier = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO "User"(user_id,username,password_hash,status,created_at,updated_at)
                VALUES (?,?,'test-account-with-no-login','ACTIVE',now(),now())
                """, user, "catalog-test-" + user);
        jdbc.update("""
                INSERT INTO "Port"(port_id,port_name,country_code,time_zone_id,latitude,longitude,
                    status,created_at,updated_at)
                VALUES (?,'Catalog test port','VN','Asia/Ho_Chi_Minh',20,107,'INACTIVE',now(),now())
                """, port);
        jdbc.update("""
                INSERT INTO "Supplier"(supplier_id,supplier_name,contact_name,status,verification_status,
                    created_at,updated_at)
                VALUES (?,'Catalog test supplier','Synthetic test contact','ACTIVE','PENDING',now(),now())
                """, supplier);
        var ops = actor(Role.OPERATIONS_MANAGER, user, "PORT_VALIDATE", "SUPPLIER_VERIFY");
        String valid = """
                {"portId":"%s","supplierId":"%s","portEvidenceUrl":"https://example.invalid/port",
                "supplierEvidenceUrl":"https://example.invalid/supplier","note":"Reviewed test evidence"}
                """.formatted(port, supplier);
        int liveBefore = jdbc.queryForObject("SELECT count(*)::int FROM \"ShoreExcursion\"", Integer.class);
        mvc.perform(post(VERIFY).contextPath("/api").with(csrf())
                        .with(actor(Role.OPERATIONS_MANAGER, user, "PORT_VALIDATE"))
                        .contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isForbidden());
        mvc.perform(post(VERIFY).contextPath("/api").with(csrf()).with(ops)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(VERIFY).contextPath("/api").with(csrf()).with(ops)
                        .contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isConflict());
        jdbc.update("UPDATE \"Port\" SET status='ACTIVE' WHERE port_id=?", port);
        mvc.perform(post(VERIFY).contextPath("/api").with(csrf()).with(ops)
                        .contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isConflict());
        jdbc.update("UPDATE \"Supplier\" SET verification_status='VERIFIED' WHERE supplier_id=?", supplier);
        mvc.perform(post(VERIFY).contextPath("/api").with(csrf()).with(ops)
                        .contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("VERIFIED"));
        assertEquals(user, jdbc.queryForObject("""
                SELECT reviewed_by FROM "OperationalVerifiedTour" WHERE tour_code='VN-SHORE-001'
                """, UUID.class));
        assertEquals(liveBefore, jdbc.queryForObject("SELECT count(*)::int FROM \"ShoreExcursion\"", Integer.class));
        jdbc.update("UPDATE \"Port\" SET status='INACTIVE' WHERE port_id=?", port);
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*)::int FROM "OperationalVerifiedTour" WHERE tour_code='VN-SHORE-001'
                """, Integer.class));
    }

    private RequestPostProcessor actor(Role role, UUID id, String... permissions) {
        var user = new User();
        user.setUserId(id);
        user.setUsername("catalog-test");
        user.setStatus(UserStatus.ACTIVE);
        user.addRole(role);
        var authorities = AuthorityUtils.createAuthorityList(permissions);
        authorities.addAll(AuthorityUtils.createAuthorityList("ROLE_" + role.name()));
        return authentication(new UsernamePasswordAuthenticationToken(
                new CasmsPrincipal(user, null), null, authorities));
    }
}
