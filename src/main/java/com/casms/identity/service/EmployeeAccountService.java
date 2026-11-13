package com.casms.identity.service;

import com.casms.audit.AuditLog;
import com.casms.audit.AuditRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.identity.entity.User;
import com.casms.identity.enums.Role;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.repository.UserRepository;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.service.CruiseStaffService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmployeeAccountService {
    private static final List<Role> HR_ROLES = List.of(
            Role.ONBOARD_STAFF, Role.SHIP_MANAGER, Role.ACTIVITY_SERVICE_MANAGER,
            Role.FINANCE_OFFICER, Role.BOOKING_AGENT);
    private static final String PROFILE_SELECT = """
            SELECT e.employee_id, e.user_id, e.employee_code, e.full_name, u.email,
                   e.phone, e.gender, e.date_of_birth, e.address, e.department, e.position, e.status,
                   ARRAY(SELECT ur.role::text FROM "UserRole" ur WHERE ur.user_id = e.user_id ORDER BY ur.role) AS roles
            FROM "Employee" e JOIN "User" u USING (user_id)
            """;

    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final EmployeeActivationToken tokens;
    private final AuditRepository audit;
    private final ObjectMapper mapper;
    private final CruiseStaffService cruiseStaff;

    public record CreateInput(
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9_.-]{0,49}") String employeeCode,
            @NotBlank @Size(max = 180) String fullName,
            @NotBlank @Email @Size(max = 100) String email,
            @Size(max = 30) String phone,
            @Size(max = 50) String gender,
            @Past LocalDate dateOfBirth,
            @Size(max = 350) String address,
            @Size(max = 150) String department,
            @Size(max = 150) String position,
            @NotNull Role role) {}

    public record EmployeeView(
            UUID employeeId, UUID userId, String employeeCode, String fullName, String email,
            String phone, String gender, LocalDate dateOfBirth, String address, String department,
            String position, String status, List<String> roles) {}

    public record UpdateInput(
            @NotBlank @Size(max = 180) String fullName,
            @NotBlank @Email @Size(max = 100) String email,
            @Size(max = 30) String phone,
            @Size(max = 50) String gender,
            @Past LocalDate dateOfBirth,
            @Size(max = 350) String address,
            @Size(max = 150) String department,
            @Size(max = 150) String position) {}

    private static final RowMapper<EmployeeView> PROFILE = (rs, row) -> new EmployeeView(
            rs.getObject("employee_id", UUID.class), rs.getObject("user_id", UUID.class),
            rs.getString("employee_code"), rs.getString("full_name"), rs.getString("email"),
            rs.getString("phone"), rs.getString("gender"), rs.getObject("date_of_birth", LocalDate.class),
            rs.getString("address"), rs.getString("department"), rs.getString("position"),
            rs.getString("status"), List.of((String[]) rs.getArray("roles").getArray()));

    public List<Role> assignableRoles(CasmsPrincipal actor) {
        requireAuthority(actor, "USER_CREATE");
        return actor.hasRole(Role.ADMIN) ? List.of(Role.values()) : HR_ROLES;
    }

    @Transactional
    public EmployeeView create(CreateInput input, CasmsPrincipal actor) {
        if (!assignableRoles(actor).contains(input.role())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "EMPLOYEE_ROLE_FORBIDDEN",
                    "Không có quyền cấp role đã chọn.");
        }
        String code = input.employeeCode().toUpperCase(Locale.ROOT);
        String email = input.email().trim().toLowerCase(Locale.ROOT);
        if (users.findByNormalizedEmail(email).isPresent() || users.existsByUsername(email)) {
            throw new BusinessException(HttpStatus.CONFLICT, "EMPLOYEE_EMAIL_EXISTS",
                    "Email đã được sử dụng bởi tài khoản khác.");
        }
        User account = new User();
        account.setUsername(email);
        account.setEmail(email);
        account.setStatus(UserStatus.DISABLED);
        account.setPasswordHash(encoder.encode(UUID.randomUUID().toString()));
        account.setRoles(EnumSet.of(input.role()));
        users.saveAndFlush(account);

        UUID id = jdbc.queryForObject("""
                INSERT INTO "Employee"
                    (user_id, employee_code, full_name, phone, gender, date_of_birth, address,
                        department, position, status, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING_ACTIVATION', ?) RETURNING employee_id
                """, UUID.class, account.getUserId(), code, input.fullName().trim(), input.phone(),
                input.gender(), input.dateOfBirth(), input.address(), input.department(),
                input.position(), actor.getUserId());
        jdbc.update("INSERT INTO \"EmployeeActivationOutbox\" (employee_id) VALUES (?)", id);
        audit(actor.getUserId(), id, "EMPLOYEE_CREATED",
                Map.of("employeeCode", code, "role", input.role().name(), "status", "PENDING_ACTIVATION"));
        return profile(id, actor);
    }

    @Transactional(readOnly = true)
    public List<EmployeeView> list(int page, int size, CasmsPrincipal actor) {
        requireAuthority(actor, "USER_READ");
        return jdbc.query(PROFILE_SELECT + """
                WHERE (? OR NOT EXISTS (SELECT 1 FROM "UserRole" ur WHERE ur.user_id = e.user_id AND ur.role = 'ADMIN'))
                ORDER BY e.employee_code LIMIT ? OFFSET ?
                """, PROFILE, actor.hasRole(Role.ADMIN), size, (long) page * size);
    }

    @Transactional(readOnly = true)
    public EmployeeView profile(UUID id, CasmsPrincipal actor) {
        requireAuthority(actor, "USER_READ");
        return jdbc.query(PROFILE_SELECT + """
                WHERE e.employee_id = ?
                    AND (? OR NOT EXISTS (SELECT 1 FROM "UserRole" ur WHERE ur.user_id = e.user_id AND ur.role = 'ADMIN'))
                """, PROFILE, id, actor.hasRole(Role.ADMIN)).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Employee", id));
    }

    @Transactional
    public EmployeeView update(UUID id, UpdateInput input, CasmsPrincipal actor) {
        requireAuthority(actor, "USER_UPDATE");
        var before = lockedProfile(id, actor);
        String email = input.email().trim().toLowerCase(Locale.ROOT);
        if (!email.equals(before.email())) {
            jdbc.update("UPDATE \"User\" SET email = ?, username = ?, updated_at = now() WHERE user_id = ?",
                    email, email, before.userId());
            if ("PENDING_ACTIVATION".equals(before.status())) {
                jdbc.update("""
                        UPDATE "EmployeeActivationOutbox" SET activation_id = gen_random_uuid(),
                            expires_at = now() + interval '7 days', next_attempt_at = now(),
                            attempts = 0, sent_at = NULL WHERE employee_id = ? AND consumed_at IS NULL
                        """, id);
            }
        }
        jdbc.update("""
                UPDATE "Employee" SET full_name = ?, phone = ?, gender = ?, date_of_birth = ?,
                    address = ?, department = ?, position = ?, updated_at = now() WHERE employee_id = ?
                """, input.fullName().trim(), input.phone(), input.gender(), input.dateOfBirth(), input.address(),
                input.department(), input.position(), id);
        var after = profile(id, actor);
        audit(actor.getUserId(), id, "EMPLOYEE_UPDATED", Map.of("before", before, "after", after));
        return after;
    }

    @Transactional
    public EmployeeView inactivate(UUID id, String reason, CasmsPrincipal actor) {
        requireAuthority(actor, "USER_STATUS_CHANGE");
        var employee = profile(id, actor);
        var lockedCruises = jdbc.queryForList("""
                SELECT c.cruise_id FROM "CruiseStaff" cs JOIN "Cruise" c USING (cruise_id)
                WHERE cs.user_id = ? AND cs.status = 'ACTIVE'
                    AND c.status NOT IN ('COMPLETED', 'CANCELLED')
                ORDER BY c.cruise_id FOR UPDATE OF c
                """, UUID.class, employee.userId());
        var before = lockedProfile(id, actor);
        if ("INACTIVE".equals(before.status())) return before;
        if (actor.getUserId().equals(before.userId())) {
            throw new BusinessException(HttpStatus.CONFLICT, "EMPLOYEE_SELF_INACTIVATION",
                    "Không thể vô hiệu hóa tài khoản đang thực hiện thao tác.");
        }
        var assignments = jdbc.query("""
                SELECT c.cruise_id, c.status::text AS cruise_status
                FROM "CruiseStaff" cs JOIN "Cruise" c USING (cruise_id)
                WHERE cs.user_id = ? AND cs.status = 'ACTIVE'
                    AND c.status NOT IN ('COMPLETED', 'CANCELLED')
                ORDER BY c.cruise_id
                """, (rs, row) -> Map.entry(rs.getObject("cruise_id", UUID.class),
                rs.getString("cruise_status")), before.userId());
        if (assignments.stream().anyMatch(assignment -> !lockedCruises.contains(assignment.getKey()))) {
            throw new BusinessException(HttpStatus.CONFLICT, "EMPLOYEE_ASSIGNMENT_CHANGED",
                    "Phân công của nhân viên vừa thay đổi. Vui lòng tải lại và thử lại.");
        }
        if (assignments.stream().anyMatch(assignment -> "IN_PROGRESS".equals(assignment.getValue()))) {
            throw new BusinessException(HttpStatus.CONFLICT, "EMPLOYEE_ON_ACTIVE_CRUISE",
                    "Nhân viên đang phục vụ chuyến đang hoạt động.");
        }
        for (var assignment : assignments) {
            cruiseStaff.removeForInactivation(assignment.getKey(), before.userId(), reason, actor);
        }
        jdbc.update("UPDATE \"User\" SET status = 'DISABLED', updated_at = now() WHERE user_id = ?", before.userId());
        jdbc.update("UPDATE \"Employee\" SET status = 'INACTIVE', updated_at = now() WHERE employee_id = ?", id);
        audit(actor.getUserId(), id, "EMPLOYEE_INACTIVATED",
                Map.of("before", before.status(), "after", "INACTIVE", "reason", reason.trim(),
                        "cancelledCruiseAssignments", assignments.stream().map(Map.Entry::getKey).toList()));
        return profile(id, actor);
    }

    private EmployeeView lockedProfile(UUID id, CasmsPrincipal actor) {
        return jdbc.query(PROFILE_SELECT + """
                WHERE e.employee_id = ?
                    AND (? OR NOT EXISTS (SELECT 1 FROM "UserRole" ur WHERE ur.user_id = e.user_id AND ur.role = 'ADMIN'))
                FOR NO KEY UPDATE OF e, u
                """, PROFILE, id, actor.hasRole(Role.ADMIN)).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Employee", id));
    }

    @Transactional
    public void activate(String token, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PASSWORD_TOO_LONG", "Mật khẩu tối đa 72 byte UTF-8.");
        }
        UUID activationId = tokens.verify(token);
        var pending = jdbc.queryForList("""
                SELECT e.employee_id, e.user_id
                FROM "EmployeeActivationOutbox" o JOIN "Employee" e USING (employee_id)
                JOIN "User" u USING (user_id)
                WHERE o.activation_id = ? AND o.expires_at > now() AND o.consumed_at IS NULL
                    AND e.status = 'PENDING_ACTIVATION' AND u.status = 'DISABLED'
                FOR NO KEY UPDATE OF o, e, u
                """, activationId);
        if (pending.isEmpty()) throw EmployeeActivationToken.invalid();
        UUID id = (UUID) pending.getFirst().get("employee_id");
        UUID userId = (UUID) pending.getFirst().get("user_id");
        jdbc.update("""
                UPDATE "User" SET password_hash = ?, status = 'ACTIVE', updated_at = now() WHERE user_id = ?
                """, encoder.encode(password), userId);
        jdbc.update("UPDATE \"Employee\" SET status = 'ACTIVE', updated_at = now() WHERE employee_id = ?", id);
        jdbc.update("UPDATE \"EmployeeActivationOutbox\" SET consumed_at = now() WHERE activation_id = ?", activationId);
        audit(userId, id, "EMPLOYEE_ACTIVATED", Map.of("before", "PENDING_ACTIVATION", "after", "ACTIVE"));
    }

    @Transactional
    public void retryActivationEmail(UUID id, CasmsPrincipal actor) {
        requireAuthority(actor, "USER_UPDATE");
        profile(id, actor);
        int updated = jdbc.update("""
                UPDATE "EmployeeActivationOutbox" o SET attempts = 0, next_attempt_at = now(),
                    last_retry_by = ?, last_retry_at = now(),
                    activation_id = CASE WHEN o.expires_at <= now() THEN gen_random_uuid() ELSE o.activation_id END,
                    expires_at = CASE WHEN o.expires_at <= now() THEN now() + interval '7 days' ELSE o.expires_at END
                FROM "Employee" e WHERE e.employee_id = o.employee_id AND e.employee_id = ?
                    AND e.status = 'PENDING_ACTIVATION' AND o.consumed_at IS NULL AND o.sent_at IS NULL
                    AND (o.attempts = 5 OR o.expires_at <= now())
                """, actor.getUserId(), id);
        if (updated > 0) audit(actor.getUserId(), id, "EMPLOYEE_ACTIVATION_EMAIL_RETRY", Map.of());
    }

    private void requireAuthority(CasmsPrincipal actor, String permission) {
        if (actor == null || !actor.isEnabled() || !actor.isAccountNonLocked()
                || !actor.hasAnyRole(Role.ADMIN, Role.HR_MANAGER) || !actor.hasAuthority(permission)) {
            throw new AccessDeniedException("Employee administration permission required");
        }
    }

    private void audit(UUID actorId, UUID id, String action, Map<String, Object> detail) {
        var event = new AuditLog();
        event.setActorUserId(actorId);
        event.setEntityType("Employee");
        event.setEntityId(id);
        event.setAction(action);
        event.setResult("SUCCESS");
        try {
            event.setDetail(mapper.writeValueAsString(detail));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Could not serialize employee audit", failure);
        }
        audit.save(event);
    }
}
