package com.casms.identity.service;

import com.casms.audit.AuditLog;
import com.casms.audit.AuditRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.identity.enums.Role;
import com.casms.identity.repository.UserRepository;
import com.casms.identity.service.EmployeeAccountService.CreateInput;
import com.casms.security.principal.CasmsPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeImportService {
    private final EmployeeAccountService accounts;
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactions;
    private final Validator validator;
    private final ObjectMapper mapper;
    private final AuditRepository audit;

    public record RowResult(int rowNumber, String employeeCode, String result, String error, UUID employeeId) {}
    public record Batch(UUID batchId, String status, List<RowResult> rows) {}

    public byte[] template(CasmsPrincipal actor) {
        return EmployeeWorkbook.template(accounts.assignableRoles(actor));
    }

    @Transactional
    public Batch upload(byte[] workbook, CasmsPrincipal actor) {
        List<Role> roles = accounts.assignableRoles(actor);
        var parsed = EmployeeWorkbook.read(workbook);
        UUID batch = jdbc.queryForObject("""
                INSERT INTO "EmployeeImportBatch" (created_by) VALUES (?) RETURNING batch_id
                """, UUID.class, actor.getUserId());
        Set<String> codes = new HashSet<>();
        Set<String> emails = new HashSet<>();
        for (var row : parsed) {
            CreateInput input = row.input();
            String error = row.error();
            if (error == null) error = validate(input, roles, codes, emails);
            jdbc.update("""
                    INSERT INTO "EmployeeImportRow" (batch_id, row_number, payload, validation_error, result)
                    VALUES (?, ?, ?::jsonb, ?, ?)
                    """, batch, row.number(), input == null ? null : serialize(input), error,
                    error == null ? "PENDING" : "INVALID");
        }
        audit(actor.getUserId(), batch, "EMPLOYEE_IMPORT_VALIDATED");
        return read(batch, actor);
    }

    @Transactional(readOnly = true)
    public Batch read(UUID batch, CasmsPrincipal actor) {
        boolean confirmed = requireBatch(batch, actor);
        var rows = jdbc.query("""
                SELECT row_number, payload->>'employeeCode' AS code, result,
                       COALESCE(result_code, validation_error) AS error, employee_id
                FROM "EmployeeImportRow" WHERE batch_id = ? ORDER BY row_number
                """, (rs, row) -> new RowResult(rs.getInt("row_number"), rs.getString("code"),
                rs.getString("result"), rs.getString("error"), rs.getObject("employee_id", UUID.class)), batch);
        return new Batch(batch, confirmed ? "CONFIRMED" : "PREVIEW", rows);
    }

    public Batch confirm(UUID batch, CasmsPrincipal actor) {
        if (requireBatch(batch, actor)) return read(batch, actor);
        var pending = jdbc.queryForList("""
                SELECT row_number FROM "EmployeeImportRow" WHERE batch_id = ? AND result = 'PENDING' ORDER BY row_number
                """, Integer.class, batch);
        var rowTransaction = new TransactionTemplate(transactions);
        rowTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        for (int number : pending) {
            try {
                rowTransaction.executeWithoutResult(status -> confirmRow(batch, number, actor));
            } catch (BusinessException failure) {
                rowTransaction.executeWithoutResult(status -> failRow(batch, number, failure.getCode()));
            } catch (DataIntegrityViolationException duplicate) {
                rowTransaction.executeWithoutResult(status -> failRow(batch, number, "EMPLOYEE_DATA_CONFLICT"));
            }
        }
        rowTransaction.executeWithoutResult(status -> {
            int updated = jdbc.update("""
                    UPDATE "EmployeeImportBatch" b SET confirmed_at = now(), confirmed_by = ?
                    WHERE b.batch_id = ? AND b.confirmed_at IS NULL AND NOT EXISTS (
                        SELECT 1 FROM "EmployeeImportRow" r WHERE r.batch_id = b.batch_id AND r.result = 'PENDING')
                    """, actor.getUserId(), batch);
            if (updated == 1) audit(actor.getUserId(), batch, "EMPLOYEE_IMPORT_CONFIRMED");
        });
        return read(batch, actor);
    }

    public byte[] resultFile(UUID batch, CasmsPrincipal actor) {
        return EmployeeWorkbook.results(read(batch, actor).rows());
    }

    private void confirmRow(UUID batch, int number, CasmsPrincipal actor) {
        var rows = jdbc.queryForList("""
                SELECT payload::text FROM "EmployeeImportRow"
                WHERE batch_id = ? AND row_number = ? AND result = 'PENDING' FOR UPDATE
                """, String.class, batch, number);
        if (rows.isEmpty()) return;
        var input = deserialize(rows.getFirst());
        String error = validate(input, accounts.assignableRoles(actor), new HashSet<>(), new HashSet<>());
        if (error != null) {
            failRow(batch, number, error);
            return;
        }
        UUID employee = accounts.create(input, actor).employeeId();
        jdbc.update("""
                UPDATE "EmployeeImportRow" SET result = 'CREATED', employee_id = ?, processed_at = now()
                WHERE batch_id = ? AND row_number = ?
                """, employee, batch, number);
    }

    private void failRow(UUID batch, int number, String error) {
        jdbc.update("""
                UPDATE "EmployeeImportRow" SET result = 'FAILED', result_code = ?, processed_at = now()
                WHERE batch_id = ? AND row_number = ? AND result = 'PENDING'
                """, error, batch, number);
    }

    private String validate(CreateInput input, List<Role> roles, Set<String> codes, Set<String> emails) {
        var violations = validator.validate(input);
        if (!violations.isEmpty()) {
            return "INVALID_FIELDS:" + violations.stream().map(v -> v.getPropertyPath().toString())
                    .distinct().sorted().collect(Collectors.joining(","));
        }
        if (!roles.contains(input.role())) return "EMPLOYEE_ROLE_FORBIDDEN";
        String code = input.employeeCode().toUpperCase(Locale.ROOT);
        String email = input.email().trim().toLowerCase(Locale.ROOT);
        if (!codes.add(code)) return "DUPLICATE_EMPLOYEE_CODE_IN_BATCH";
        if (!emails.add(email)) return "DUPLICATE_EMAIL_IN_BATCH";
        if (users.findByNormalizedEmail(email).isPresent() || users.existsByUsername(email)) return "EMPLOYEE_EMAIL_EXISTS";
        if (Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM "Employee" WHERE employee_code = ?)
                """, Boolean.class, code))) return "EMPLOYEE_CODE_EXISTS";
        return null;
    }

    private boolean requireBatch(UUID batch, CasmsPrincipal actor) {
        accounts.assignableRoles(actor);
        return jdbc.queryForList("""
                SELECT confirmed_at IS NOT NULL FROM "EmployeeImportBatch"
                WHERE batch_id = ? AND (created_by = ? OR ?)
                """, Boolean.class, batch, actor.getUserId(), actor.hasRole(Role.ADMIN))
                .stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Employee import", batch));
    }

    private String serialize(CreateInput input) {
        try {
            return mapper.writeValueAsString(input);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Could not save employee import row", failure);
        }
    }

    private CreateInput deserialize(String json) {
        try {
            return mapper.readValue(json, CreateInput.class);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Could not read employee import row", failure);
        }
    }

    private void audit(UUID actor, UUID batch, String action) {
        var event = new AuditLog();
        event.setActorUserId(actor);
        event.setEntityType("EmployeeImportBatch");
        event.setEntityId(batch);
        event.setAction(action);
        event.setResult("SUCCESS");
        audit.save(event);
    }
}
