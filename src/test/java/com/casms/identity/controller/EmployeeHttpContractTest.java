package com.casms.identity.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.casms.common.exception.GlobalExceptionHandler;
import com.casms.config.SecurityConfig;
import com.casms.identity.entity.RolePermission;
import com.casms.identity.entity.User;
import com.casms.identity.enums.Permission;
import com.casms.identity.enums.Role;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.repository.RolePermissionRepository;
import com.casms.identity.service.EmployeeAccountService;
import com.casms.identity.service.EmployeeImportService;
import com.casms.identity.service.EmployeeWorkbook;
import com.casms.security.jwt.JwtAuthenticationFilter;
import com.casms.security.jwt.JwtService;
import com.casms.security.principal.CasmsPrincipal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

class EmployeeHttpContractTest {
    private static final String BEARER = "Bearer test-token";
    private static final String UPDATE_INPUT = """
            {"fullName":"Employee","email":"employee@example.org"}
            """;

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class,
            EmployeeAccountController.class, EmployeeImportController.class, EmployeeActivationController.class})
    static class Config {
        @Bean
        EmployeeAccountService accounts() {
            return mock(EmployeeAccountService.class);
        }

        @Bean
        EmployeeImportService imports() {
            return mock(EmployeeImportService.class);
        }

        @Bean
        JwtService jwtService() {
            return mock(JwtService.class);
        }

        @Bean
        UserDetailsService userDetailsService() {
            return mock(UserDetailsService.class);
        }

        @Bean
        RolePermissionRepository permissions() {
            return mock(RolePermissionRepository.class);
        }
    }

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private EmployeeAccountService accounts;
    private EmployeeImportService imports;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, "jwt.expiration-ms=60000");
        context.register(Config.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        accounts = context.getBean(EmployeeAccountService.class);
        imports = context.getBean(EmployeeImportService.class);
    }

    @AfterEach
    void closeContext() {
        context.close();
    }

    @Test
    void anonymousCannotReadEmployees() throws Exception {
        mvc.perform(get("/employees")).andExpect(status().isForbidden());
        verifyNoInteractions(accounts);
    }

    @Test
    void anonymousCannotUploadEvenWithCsrfToken() throws Exception {
        mvc.perform(multipart("/employees/imports")
                        .file(new MockMultipartFile("file", "employees.xlsx", EmployeeWorkbook.XLSX, new byte[] {1}))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(imports);
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"DISABLED", "LOCKED"})
    void inactiveHrCannotUploadWithAnOtherwiseValidBearerToken(UserStatus status) throws Exception {
        authenticate(Role.HR_MANAGER, Set.of(Permission.USER_CREATE), status);
        mvc.perform(multipart("/employees/imports")
                        .file(new MockMultipartFile("file", "employees.xlsx", EmployeeWorkbook.XLSX, new byte[] {1}))
                        .header(HttpHeaders.AUTHORIZATION, BEARER)
                        .with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(imports);
    }

    @Test
    void employeeCreatePermissionDoesNotGrantTheHrRole() throws Exception {
        authenticate(Role.BOOKING_AGENT, Set.of(Permission.USER_CREATE));
        mvc.perform(get("/employees/assignable-roles").header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isForbidden());
        verifyNoInteractions(accounts);
    }

    @Test
    void hrWithoutCreatePermissionCannotDownloadTemplate() throws Exception {
        authenticate(Role.HR_MANAGER, Set.of(Permission.USER_READ));
        mvc.perform(get("/employees/imports/template").header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isForbidden());
        verifyNoInteractions(imports);
    }

    @Test
    void hrWithoutUpdatePermissionCannotEditEmployee() throws Exception {
        authenticate(Role.HR_MANAGER, Set.of(Permission.USER_READ));
        mvc.perform(put("/employees/{employeeId}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_INPUT))
                .andExpect(status().isForbidden());
        verifyNoInteractions(accounts);
    }

    @Test
    void hrWithUpdatePermissionCanSubmitEmployeeChanges() throws Exception {
        CasmsPrincipal hr = authenticate(Role.HR_MANAGER, Set.of(Permission.USER_READ, Permission.USER_UPDATE));
        UUID employeeId = UUID.randomUUID();
        mvc.perform(put("/employees/{employeeId}", employeeId)
                        .header(HttpHeaders.AUTHORIZATION, BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_INPUT))
                .andExpect(status().isOk());
        verify(accounts).update(employeeId, new EmployeeAccountService.UpdateInput(
                "Employee", "employee@example.org", null, null, null, null, null, null), hr);
    }

    @Test
    void hrWithCreatePermissionCanReadAssignableRolesAndUploadWorkbook() throws Exception {
        CasmsPrincipal hr = authenticate(Role.HR_MANAGER, Set.of(Permission.USER_CREATE));
        when(accounts.assignableRoles(hr)).thenReturn(List.of(Role.BOOKING_AGENT));
        mvc.perform(get("/employees/assignable-roles").header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("BOOKING_AGENT"));

        byte[] workbook = {1, 2, 3};
        mvc.perform(multipart("/employees/imports")
                        .file(new MockMultipartFile("file", "employees.xlsx", EmployeeWorkbook.XLSX, workbook))
                        .header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isCreated());
        verify(imports).upload(workbook, hr);
    }

    @Test
    void adminWithCreatePermissionReceivesTemplateAttachment() throws Exception {
        CasmsPrincipal admin = authenticate(Role.ADMIN, Set.of(Permission.USER_CREATE));
        byte[] workbook = {1, 2, 3};
        when(imports.template(admin)).thenReturn(workbook);
        mvc.perform(get("/employees/imports/template").header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isOk())
                .andExpect(content().contentType(EmployeeWorkbook.XLSX))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"CASMS_Employee_Import_Template.xlsx\""))
                .andExpect(content().bytes(workbook));
    }

    @Test
    void importWithoutFileReturnsWorkbookValidationErrorBeforeService() throws Exception {
        authenticate(Role.HR_MANAGER, Set.of(Permission.USER_CREATE));
        mvc.perform(multipart("/employees/imports").header(HttpHeaders.AUTHORIZATION, BEARER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_XLSX_INVALID"));
        verifyNoInteractions(imports);
    }

    @Test
    void publicActivationBindsTokenAndPassword() throws Exception {
        mvc.perform(post("/auth/employee-account/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"activation-token","password":"test-password"}
                                """))
                .andExpect(status().isOk());
        verify(accounts).activate("activation-token", "test-password");
    }

    @Test
    void publicActivationRejectsShortPasswordBeforeService() throws Exception {
        mvc.perform(post("/auth/employee-account/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"activation-token","password":"short"}
                                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(accounts);
    }

    private CasmsPrincipal authenticate(Role role, Set<Permission> permissions) {
        return authenticate(role, permissions, UserStatus.ACTIVE);
    }

    private CasmsPrincipal authenticate(Role role, Set<Permission> permissions, UserStatus status) {
        var repository = context.getBean(RolePermissionRepository.class);
        when(repository.findByRole(role)).thenReturn(Optional.of(new RolePermission(role, permissions)));
        User user = new User();
        user.setUserId(UUID.randomUUID());
        user.setUsername("employee");
        user.setStatus(status);
        user.setRoles(Set.of(role));
        var principal = new CasmsPrincipal(user, repository);
        var jwt = context.getBean(JwtService.class);
        when(jwt.isValid("test-token")).thenReturn(true);
        when(jwt.extractUsername("test-token")).thenReturn(user.getUsername());
        when(context.getBean(UserDetailsService.class).loadUserByUsername(user.getUsername())).thenReturn(principal);
        return principal;
    }
}
