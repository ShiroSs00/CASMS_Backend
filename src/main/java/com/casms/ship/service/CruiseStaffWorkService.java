package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.servicepackage.service.ShipServiceAvailability;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CruiseStaffWorkService {
    private final JdbcTemplate jdbc;
    private final ShipServiceAvailability shipServices;

    public enum ActivityKind {
        ONBOARD("onboard_activity_id", "\"OnboardActivity\""),
        SHORE("shore_excursion_id", "\"ShoreExcursion\"");

        private final String column;
        private final String table;

        ActivityKind(String column, String table) {
            this.column = column;
            this.table = table;
        }
    }

    public record Qualification(ActivityKind kind, UUID activityId) {}

    public record WorkWindow(
            UUID windowId, UUID resourceId, UUID definitionId, String kind, String name,
            Instant startsAt, Instant endsAt, List<UUID> userIds) {}

    public record ServiceDuty(UUID userId, UUID serviceId, String serviceName) {}

    public record WorkPlan(List<WorkWindow> windows, List<ServiceDuty> serviceAssignments) {}

    @Transactional(readOnly = true)
    public WorkPlan workPlan(UUID cruiseId) {
        if (!exists("SELECT EXISTS (SELECT 1 FROM \"Cruise\" WHERE cruise_id = ?)", cruiseId)) {
            throw new ResourceNotFoundException("Cruise", cruiseId);
        }
        var windows = jdbc.query("""
                SELECT w.*, ARRAY(
                    SELECT DISTINCT d.user_id FROM "CruiseStaffDutyWindow" d
                    WHERE d.cruise_id = w.cruise_id AND d.kind = w.kind AND d.window_id = w.window_id
                    ORDER BY d.user_id
                ) AS staff_ids
                FROM "CruiseStaffWorkWindow" w WHERE cruise_id = ?
                ORDER BY starts_at, ends_at, kind, window_id
                """, (rs, row) -> new WorkWindow(
                rs.getObject("window_id", UUID.class),
                rs.getObject("resource_id", UUID.class),
                rs.getObject("definition_id", UUID.class),
                rs.getString("kind"), rs.getString("name"),
                rs.getObject("starts_at", OffsetDateTime.class).toInstant(),
                rs.getObject("ends_at", OffsetDateTime.class).toInstant(),
                List.of((UUID[]) rs.getArray("staff_ids").getArray())), cruiseId);
        var services = jdbc.query("""
                SELECT cs.user_id, d.service_id, s.service_name
                FROM "CruiseStaffDuty" d JOIN "CruiseStaff" cs USING (cruise_staff_id)
                JOIN "Service" s USING (service_id)
                JOIN "User" u ON u.user_id = cs.user_id
                WHERE cs.cruise_id = ? AND cs.status = 'ACTIVE' AND u.status = 'ACTIVE'
                    AND d.ended_at IS NULL
                ORDER BY s.service_name, cs.user_id
                """, (rs, row) -> new ServiceDuty(rs.getObject("user_id", UUID.class),
                rs.getObject("service_id", UUID.class), rs.getString("service_name")), cruiseId);
        return new WorkPlan(windows, services);
    }

    @Transactional(readOnly = true)
    public List<Qualification> qualifications(UUID userId) {
        return jdbc.query("""
                SELECT onboard_activity_id, shore_excursion_id FROM "StaffActivityQualification" q
                WHERE user_id = ? AND ended_at IS NULL
                    AND NOT EXISTS (SELECT 1 FROM "UserRole" ur WHERE ur.user_id = q.user_id AND ur.role = 'ADMIN')
                ORDER BY assigned_at, qualification_id
                """, (rs, row) -> {
                    UUID onboard = rs.getObject("onboard_activity_id", UUID.class);
                    return onboard != null ? new Qualification(ActivityKind.ONBOARD, onboard)
                            : new Qualification(ActivityKind.SHORE,
                                    rs.getObject("shore_excursion_id", UUID.class));
                }, userId);
    }

    @Transactional
    public void addQualification(UUID userId, ActivityKind kind, UUID definitionId, UUID actorId) {
        lockEmployee(userId);
        if (!exists("SELECT EXISTS (SELECT 1 FROM " + kind.table + " WHERE "
                + kind.column + " = ? AND status <> 'CANCELLED')", definitionId)) {
            throw new ResourceNotFoundException("Activity definition", definitionId);
        }
        jdbc.update("""
                INSERT INTO "StaffActivityQualification" (user_id, %s, assigned_by)
                VALUES (?, ?, ?) ON CONFLICT DO NOTHING
                """.formatted(kind.column), userId, definitionId, actorId);
    }

    @Transactional
    public void removeQualification(UUID userId, ActivityKind kind, UUID definitionId, UUID actorId) {
        lockUser(userId);
        if (exists("""
                SELECT EXISTS (SELECT 1 FROM "CruiseStaffDutyWindow" d
                    WHERE user_id = ? AND kind = ? AND definition_id = ? AND ends_at > now())
                """, userId, kind.name(), definitionId)) {
            throw conflict("STAFF_QUALIFICATION_IN_USE", "Nhân viên đang có ca hoạt động dùng năng lực này.");
        }
        jdbc.update("""
                UPDATE "StaffActivityQualification" SET ended_at = now(), ended_by = ?
                WHERE user_id = ? AND %s = ? AND ended_at IS NULL
                """.formatted(kind.column), actorId, userId, definitionId);
    }

    @Transactional
    public void assignService(UUID cruiseId, UUID userId, UUID serviceId, UUID actorId) {
        UUID staffId = activeStaff(cruiseId, userId, true);
        shipServices.requireServiceForRequest(cruiseId, serviceId);
        if (hasDuty(staffId, "service_id", serviceId)) {
            return;
        }
        requireNoOverlap(cruiseId, userId, "SERVICE", serviceId, true);
        endService(staffId, actorId);
        insertDuty(staffId, "service_id", serviceId, actorId);
    }

    @Transactional
    public void removeService(UUID cruiseId, UUID userId, UUID actorId) {
        endService(activeStaff(cruiseId, userId, false), actorId);
    }

    @Transactional
    public void assignActivity(
            UUID cruiseId, UUID userId, ActivityKind kind, UUID occurrenceId, UUID actorId) {
        UUID staffId = activeStaff(cruiseId, userId, true);
        var definitions = jdbc.queryForList("""
                SELECT definition_id FROM "CruiseStaffWorkWindow"
                WHERE cruise_id = ? AND kind = ? AND resource_id = ?
                """, UUID.class, cruiseId, kind.name(), occurrenceId);
        if (definitions.isEmpty()) {
            throw new ResourceNotFoundException("Scheduled activity on cruise", occurrenceId);
        }
        if (hasDuty(staffId, kind.column, occurrenceId)) {
            return;
        }
        if (!exists("""
                SELECT EXISTS (SELECT 1 FROM "StaffActivityQualification"
                    WHERE user_id = ? AND %s = ? AND ended_at IS NULL)
                """.formatted(kind.column), userId, definitions.getFirst())) {
            throw conflict("STAFF_QUALIFICATION_REQUIRED", "Nhân viên chưa được đăng ký năng lực cho hoạt động này.");
        }
        requireNoOverlap(cruiseId, userId, kind.name(), occurrenceId, false);
        insertDuty(staffId, kind.column, occurrenceId, actorId);
    }

    @Transactional
    public void removeActivity(
            UUID cruiseId, UUID userId, ActivityKind kind, UUID occurrenceId, UUID actorId) {
        UUID staffId = activeStaff(cruiseId, userId, false);
        jdbc.update("""
                UPDATE "CruiseStaffDuty" SET ended_at = now(), ended_by = ?
                WHERE cruise_staff_id = ? AND %s = ? AND ended_at IS NULL
                """.formatted(kind.column), actorId, staffId, occurrenceId);
    }

    private UUID activeStaff(UUID cruiseId, UUID userId, boolean assigning) {
        var cruises = jdbc.queryForList("""
                SELECT status::text FROM "Cruise" WHERE cruise_id = ? FOR UPDATE
                """, String.class, cruiseId);
        if (cruises.isEmpty()) {
            throw new ResourceNotFoundException("Cruise", cruiseId);
        }
        if (List.of("COMPLETED", "CANCELLED").contains(cruises.getFirst())) {
            throw conflict("CRUISE_STAFF_WORK_CLOSED", "Chuyến đã kết thúc hoặc đã bị hủy.");
        }
        if (assigning) {
            lockEmployee(userId);
        } else {
            lockUser(userId);
        }
        var staff = jdbc.queryForList("""
                SELECT cruise_staff_id FROM "CruiseStaff"
                WHERE cruise_id = ? AND user_id = ? AND status = 'ACTIVE'
                    AND role_in_cruise = 'ONBOARD_STAFF'
                FOR UPDATE
                """, UUID.class, cruiseId, userId);
        if (staff.isEmpty()) {
            throw conflict("ONBOARD_STAFF_ASSIGNMENT_REQUIRED", "Cần phân công nhân viên vào chuyến trước.");
        }
        return staff.getFirst();
    }

    private void lockEmployee(UUID userId) {
        if (!"ACTIVE".equals(lockUser(userId)) || !exists("""
                SELECT EXISTS (SELECT 1 FROM "UserRole" WHERE user_id = ? AND role = 'ONBOARD_STAFF')
                """, userId)) {
            throw conflict("ACTIVE_ONBOARD_STAFF_REQUIRED", "Cần tài khoản Nhân viên đang hoạt động.");
        }
    }

    private String lockUser(UUID userId) {
        var users = jdbc.queryForList("""
                SELECT status::text FROM "User" u WHERE user_id = ?
                    AND NOT EXISTS (SELECT 1 FROM "UserRole" ur WHERE ur.user_id = u.user_id AND ur.role = 'ADMIN')
                FOR UPDATE
                """, String.class, userId);
        if (users.isEmpty()) {
            throw new ResourceNotFoundException("User", userId);
        }
        return users.getFirst();
    }

    private void requireNoOverlap(
            UUID cruiseId, UUID userId, String kind, UUID resourceId, boolean replaceService) {
        if (exists("""
                SELECT EXISTS (SELECT 1 FROM "CruiseStaffWorkWindow" target
                    JOIN "CruiseStaffDutyWindow" existing ON existing.user_id = ?
                        AND target.starts_at < existing.ends_at AND existing.starts_at < target.ends_at
                    WHERE target.cruise_id = ? AND target.kind = ? AND target.resource_id = ?
                        AND NOT (? AND existing.kind = 'SERVICE' AND existing.cruise_id = target.cruise_id))
                """, userId, cruiseId, kind, resourceId, replaceService)) {
            throw conflict("STAFF_WORK_TIME_CONFLICT", "Thời gian bị trùng với ca đã phân công cho nhân viên.");
        }
    }

    private boolean hasDuty(UUID staffId, String column, UUID resourceId) {
        return exists("""
                SELECT EXISTS (SELECT 1 FROM "CruiseStaffDuty"
                    WHERE cruise_staff_id = ? AND %s = ? AND ended_at IS NULL)
                """.formatted(column), staffId, resourceId);
    }

    private void insertDuty(UUID staffId, String column, UUID resourceId, UUID actorId) {
        jdbc.update("""
                INSERT INTO "CruiseStaffDuty" (cruise_staff_id, %s, assigned_by)
                VALUES (?, ?, ?)
                """.formatted(column), staffId, resourceId, actorId);
    }

    private void endService(UUID staffId, UUID actorId) {
        jdbc.update("""
                UPDATE "CruiseStaffDuty" SET ended_at = now(), ended_by = ?
                WHERE cruise_staff_id = ? AND service_id IS NOT NULL AND ended_at IS NULL
                """, actorId, staffId);
    }

    private boolean exists(String sql, Object... parameters) {
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, parameters));
    }

    private BusinessException conflict(String code, String message) {
        return new BusinessException(HttpStatus.CONFLICT, code, message);
    }
}
