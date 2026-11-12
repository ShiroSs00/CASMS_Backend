package com.casms.identity.service;

import static org.junit.jupiter.api.Assertions.*;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.identity.entity.User;
import com.casms.identity.enums.Role;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.repository.RolePermissionRepository;
import com.casms.identity.repository.UserRepository;
import com.casms.security.principal.CasmsPrincipal;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@EnabledIfEnvironmentVariable(named = "CASMS_TEST_POSTGRES_URL", matches = ".+")
@SpringBootTest(properties = {
    "spring.datasource.url=${CASMS_TEST_POSTGRES_URL}",
    "spring.datasource.username=postgres",
    "spring.datasource.password=",
    "casms.booking-email.enabled=false",
    "casms.staff-assignment-email.enabled=false",
    "casms.employee-activation-email.enabled=false",
    "casms.employee-activation-email.token-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
class EmployeeImportPostgresIntegrationTest {
    @Autowired private UserRepository users;
    @Autowired private RolePermissionRepository roles;
    @Autowired private PasswordEncoder encoder;
    @Autowired private EmployeeImportService imports;
    @Autowired private EmployeeAccountService accounts;
    @Autowired private EmployeeActivationToken tokens;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void concurrentConfirmCreatesOneAccountAndActivationAndReportsInvalidRows() throws Exception {
        CasmsPrincipal admin = actor(Role.ADMIN);
        String suffix = UUID.randomUUID().toString();
        String employeeCode = "EMP-" + suffix;
        String email = suffix + "@example.org";
        var preview = imports.upload(workbook(employeeCode, email), admin);
        assertEquals(List.of("PENDING", "INVALID"),
                preview.rows().stream().map(EmployeeImportService.RowResult::result).toList());
        assertTrue(users.findByNormalizedEmail(email).isEmpty());

        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return imports.confirm(preview.batchId(), admin);
            });
            var second = pool.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return imports.confirm(preview.batchId(), admin);
            });
            start.countDown();
            assertEquals(first.get(25, TimeUnit.SECONDS), second.get(25, TimeUnit.SECONDS));
        }
        var result = imports.read(preview.batchId(), admin);
        assertEquals("CONFIRMED", result.status());
        assertEquals(List.of("CREATED", "INVALID"),
                result.rows().stream().map(EmployeeImportService.RowResult::result).toList());
        UUID employee = result.rows().getFirst().employeeId();
        assertEquals(1L, jdbc.queryForObject("""
                SELECT count(*) FROM "Employee" e JOIN "User" u USING (user_id)
                JOIN "EmployeeActivationOutbox" o USING (employee_id)
                WHERE e.employee_id = ? AND u.email = ? AND u.status = 'DISABLED'
                """, Long.class, employee, email));
        UUID activation = jdbc.queryForObject("""
                SELECT activation_id FROM "EmployeeActivationOutbox" WHERE employee_id = ?
                """, UUID.class, employee);
        String linkToken = tokens.create(activation);
        accounts.activate(linkToken, "A-new-password-2026");
        assertEquals(UserStatus.ACTIVE, users.findByNormalizedEmail(email).orElseThrow().getStatus());
        assertEquals("EMPLOYEE_ACTIVATION_INVALID", assertThrows(BusinessException.class,
                () -> accounts.activate(linkToken, "Another-password-2026")).getCode());
    }

    @Test
    void anotherHrCannotReadDownloadOrConfirmTheOwnersBatch() throws Exception {
        CasmsPrincipal owner = actor(Role.HR_MANAGER);
        CasmsPrincipal otherHr = actor(Role.HR_MANAGER);
        CasmsPrincipal admin = actor(Role.ADMIN);
        String suffix = UUID.randomUUID().toString();
        String email = suffix + "@example.org";
        var preview = imports.upload(workbook("EMP-" + suffix, email), owner);
        UUID batchId = preview.batchId();

        assertThrows(ResourceNotFoundException.class, () -> imports.read(batchId, otherHr));
        assertThrows(ResourceNotFoundException.class, () -> imports.resultFile(batchId, otherHr));
        assertThrows(ResourceNotFoundException.class, () -> imports.confirm(batchId, otherHr));
        assertEquals(preview, imports.read(batchId, owner));
        assertTrue(users.findByNormalizedEmail(email).isEmpty());

        assertEquals(preview, imports.read(batchId, admin));
        var confirmed = imports.confirm(batchId, admin);
        assertEquals("CONFIRMED", confirmed.status());
        assertEquals("CREATED", confirmed.rows().getFirst().result());
        assertEquals(UserStatus.DISABLED, users.findByNormalizedEmail(email).orElseThrow().getStatus());
    }

    private CasmsPrincipal actor(Role role) {
        User user = new User();
        user.setUsername("hr-test-" + UUID.randomUUID());
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash(encoder.encode(UUID.randomUUID().toString()));
        user.setRoles(EnumSet.of(role));
        return new CasmsPrincipal(users.saveAndFlush(user), roles);
    }

    private byte[] workbook(String employeeCode, String email) throws Exception {
        byte[] template = EmployeeWorkbook.template(List.of(Role.ONBOARD_STAFF));
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(template));
                var output = new ByteArrayOutputStream()) {
            var sheet = book.getSheet("Employees");
            var good = sheet.createRow(1);
            good.createCell(0).setCellValue(employeeCode);
            good.createCell(1).setCellValue("Nhân viên thử nghiệm");
            good.createCell(2).setCellValue(email);
            good.createCell(9).setCellValue("ONBOARD_STAFF");
            var invalid = sheet.createRow(2);
            invalid.createCell(0).setCellValue("Invalid");
            invalid.createCell(1).setCellValue("Dòng lỗi");
            invalid.createCell(2).setCellValue("invalid-email");
            invalid.createCell(9).setCellValue("ONBOARD_STAFF");
            book.write(output);
            return output.toByteArray();
        }
    }
}
