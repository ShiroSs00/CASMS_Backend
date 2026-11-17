package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.identity.enums.Role;
import com.casms.security.principal.CasmsPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CruiseWorkHandoffService {
    private final JdbcTemplate jdbc;

    public enum Group { ASM, SHIP }
    public enum Action { ACCEPT, COMPLETE, RETURN }
    public record Task(UUID cruiseId, String cruiseName, String cruiseCode, Group group, String status,
                       String request, String response, String assignee, String operatingBudgetVnd, long revision,
                       boolean canAccept, boolean canRespond,
                       UUID shipId, String shipName, Integer shipCapacity,
                       Instant departureAt, Instant returnAt, Integer expectedPassengerCount,
                       String sourceTripCode, String sourceTripName, Integer sourceVersionNumber) {}
    public record Panel(boolean canDispatch, boolean shipAssigned, List<Task> tasks) {}
    public record ProgramContext(String planStatus, boolean unifiedPackageReview, Windows windows,
                                 boolean mayConfigure) {}
    public record Windows(long expectedRevision) {}

    private static final String SELECT_TASKS = """
            SELECT c.cruise_id,c.cruise_name,c.cruise_code,w.work_group,w.status,w.request_text,w.response_text,
                   u.username,c.operating_budget_vnd::text,w.revision,w.assignee_id,w.requested_by,
                   s.ship_id,s.ship_name,s.total_capacity,
                   c.departure_datetime,c.return_datetime,c.expected_passenger_count,
                   t.trip_code,t.name,tv.version_number
            FROM "CruiseWorkHandoff" w
            JOIN "Cruise" c USING(cruise_id)
            LEFT JOIN "User" u ON u.user_id=w.assignee_id
            LEFT JOIN "Ship" s ON s.ship_id=c.ship_id
            LEFT JOIN "Trip" t ON t.trip_id=c.source_trip_id
            LEFT JOIN "TripScheduleVersion" tv ON tv.version_id=c.source_version_id
            """;

    private record HandoffRevision(String status, long revision) {}

    private HandoffRevision currentRevision(UUID cruiseId, Group group) {
        return jdbc.query("""
                SELECT status,revision FROM "CruiseWorkHandoff" WHERE cruise_id=? AND work_group=?
                """, (rs, index) -> new HandoffRevision(rs.getString(1), rs.getLong(2)), cruiseId, group.name())
                .stream().findFirst().orElse(new HandoffRevision(null, 0));
    }

    private void recordEvent(UUID cruiseId, Group group, CasmsPrincipal actor, String action,
            HandoffRevision previous) {
        jdbc.update("""
                INSERT INTO "CruisePreparationEvent"
                    (event_id,cruise_id,section_code,revision,action,actor_id,snapshot,previous_status,previous_revision)
                SELECT ?,cruise_id,?,revision,?,?,to_jsonb(w),?,?
                FROM "CruiseWorkHandoff" w WHERE cruise_id=? AND work_group=?
                """, UUID.randomUUID(), "HANDOFF_" + group.name(), "HANDOFF_" + action,
                actor.getUserId(), previous.status(), previous.revision(), cruiseId, group.name());
    }

    @Transactional(readOnly = true)
    public Panel forCoordinator(UUID cruiseId, CasmsPrincipal actor) {
        requireCoordinator(cruiseId, actor);
        return new Panel(canChange(cruiseId), hasShipReviewer(cruiseId),
                select(SELECT_TASKS + " WHERE c.cruise_id=? ORDER BY w.work_group", actor, false, cruiseId));
    }

    @Transactional(readOnly = true)
    public List<Task> inbox(CasmsPrincipal actor) {
        if (!asm(actor) && !ship(actor)) throw denied();
        return select(SELECT_TASKS + """
                WHERE c.status NOT IN ('CANCELLED','COMPLETED') AND (
                  (? AND w.work_group='ASM' AND (w.assignee_id IS NULL OR w.assignee_id=?))
                  OR (? AND w.work_group='SHIP' AND EXISTS (
                     SELECT 1 FROM "CruiseStaff" crew WHERE crew.cruise_id=c.cruise_id
                     AND crew.user_id=? AND crew.role_in_cruise='SHIP_MANAGER' AND crew.status='ACTIVE')))
                ORDER BY w.updated_at DESC,c.cruise_id,w.work_group
                """, actor, true, asm(actor), actor.getUserId(), ship(actor), actor.getUserId());
    }

    @Transactional
    public Panel send(UUID cruiseId, Group group, String request, CasmsPrincipal actor) {
        lock(cruiseId);
        requireCoordinator(cruiseId, actor);
        requireEditable(cruiseId);
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT operating_budget_vnd IS NOT NULL FROM "Cruise" WHERE cruise_id=?
                """, Boolean.class, cruiseId))) {
            throw conflict("PREPARATION_BUDGET_REQUIRED", "Lưu ngân sách vận hành trước khi giao việc.");
        }
        if (group == Group.SHIP && !hasShipReviewer(cruiseId))
            throw conflict("SHIP_MANAGER_REQUIRED", "Phân công Quản lý tàu cho tour trước khi gửi.");
        var previous = currentRevision(cruiseId, group);
        int changed = jdbc.update("""
                INSERT INTO "CruiseWorkHandoff" AS w (cruise_id,work_group,requested_by,request_text,status)
                VALUES (?,?,?,?,'PENDING')
                ON CONFLICT (cruise_id,work_group) DO UPDATE SET requested_by=excluded.requested_by,
                    request_text=excluded.request_text,response_text=NULL,assignee_id=NULL,status='PENDING',
                    revision=w.revision+1,updated_at=now()
                WHERE w.status='RETURNED'
                """, cruiseId, group.name(), actor.getUserId(), request);
        if (changed != 1) throw conflict("HANDOFF_ALREADY_SENT", "Yêu cầu đã được gửi. Xem tiến độ trên tour.");
        recordEvent(cruiseId, group, actor, "SEND", previous);
        return forCoordinator(cruiseId, actor);
    }

    @Transactional
    public Task respond(UUID cruiseId, Group group, Action action, long revision, String text, CasmsPrincipal actor) {
        if (group == Group.ASM ? !asm(actor) : !ship(actor)) throw denied();
        if (action == Action.ACCEPT && group != Group.ASM) throw denied();
        lock(cruiseId);
        if (group == Group.SHIP && !assignedShip(cruiseId, actor)) throw denied();
        requireEditable(cruiseId);
        if ((action == Action.RETURN || group == Group.ASM && action == Action.COMPLETE)
                && (text == null || text.isBlank())) {
            throw conflict("HANDOFF_RESPONSE_REQUIRED", "Nhập nội dung để CRM biết việc đã làm hoặc cần bổ sung.");
        }
        String status = switch (action) {
            case ACCEPT -> "IN_PROGRESS";
            case COMPLETE -> "COMPLETED";
            case RETURN -> "RETURNED";
        };
        boolean accepting = action == Action.ACCEPT;
        String expectedStatus = group == Group.SHIP || accepting ? "PENDING" : "IN_PROGRESS";
        String assignment = group == Group.SHIP || accepting ? "assignee_id IS NULL" : "assignee_id=request_actor.actor";
        var previous = currentRevision(cruiseId, group);
        int changed = jdbc.update("""
                UPDATE "CruiseWorkHandoff" w SET status=?,assignee_id=request_actor.actor,response_text=?,
                    revision=w.revision+1,updated_at=now()
                FROM (SELECT CAST(? AS uuid) AS actor) request_actor
                WHERE w.cruise_id=? AND w.work_group=? AND w.status=? AND w.revision=?
                  AND w.requested_by<>request_actor.actor AND %s
                """.formatted(assignment), status, text, actor.getUserId(), cruiseId, group.name(), expectedStatus, revision);
        if (changed != 1) throw conflict("HANDOFF_CHANGED", "Yêu cầu đã thay đổi hoặc do chính bạn gửi. Tải lại để kiểm tra.");
        recordEvent(cruiseId, group, actor, action.name(), previous);
        return select(SELECT_TASKS + " WHERE c.cruise_id=? AND w.work_group=?", actor, true, cruiseId, group.name()).getFirst();
    }

    public boolean mayReadPrograms(UUID id, CasmsPrincipal actor) {
        return coordinator(id, actor) || asm(actor) && asmAssigned(id, actor, false);
    }

    public boolean mayConfigurePrograms(UUID id, CasmsPrincipal actor) {
        return asm(actor) && asmAssigned(id, actor, true);
    }

    public ProgramContext programContext(UUID id, CasmsPrincipal actor) {
        boolean editable = mayConfigurePrograms(id, actor);
        return jdbc.queryForObject("""
                SELECT coalesce(p.status,'DRAFT'), c.revision,
                      c.source_version_id IS NOT NULL OR EXISTS (
                        SELECT 1 FROM "DepartureFarePackage" f WHERE f.cruise_id=c.cruise_id)
                FROM "Cruise" c LEFT JOIN "CruisePlanReview" p USING(cruise_id) WHERE c.cruise_id=?
                """, (rs, i) -> new ProgramContext(rs.getString(1), rs.getBoolean(3), new Windows(rs.getLong(2)),
                        editable), id);
    }

    public boolean asmAssigned(UUID id, CasmsPrincipal actor, boolean editing) {
        return actor != null && Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM "CruiseWorkHandoff" WHERE cruise_id=? AND work_group='ASM'
                    AND assignee_id=? AND (NOT ? OR status='IN_PROGRESS'))
                """, Boolean.class, id, actor.getUserId(), editing));
    }

    public boolean coordinator(UUID id, CasmsPrincipal actor) {
        return actor != null && actor.hasRole(Role.CRUISE_MANAGER) && actor.hasAuthority("CRUISE_PREPARATION_SUBMIT")
                && Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM "Cruise" c LEFT JOIN "Trip" t ON c.source_trip_id=t.trip_id
                    WHERE c.cruise_id=? AND coalesce(c.preparation_coordinator_id,t.created_by_user_id)=?)
                """, Boolean.class, id, actor.getUserId()));
    }

    private boolean asm(CasmsPrincipal actor) {
        return actor != null && actor.hasRole(Role.ACTIVITY_SERVICE_MANAGER) && actor.hasAuthority("TOUR_PACKAGE_MANAGE");
    }

    private boolean ship(CasmsPrincipal actor) {
        return actor != null && actor.hasRole(Role.SHIP_MANAGER) && actor.hasAuthority("CRUISE_PREPARATION_READ");
    }

    private boolean assignedShip(UUID id, CasmsPrincipal actor) {
        return ship(actor) && Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM "CruiseStaff" WHERE cruise_id=? AND user_id=?
                    AND status='ACTIVE' AND role_in_cruise='SHIP_MANAGER')
                """, Boolean.class, id, actor.getUserId()));
    }

    private boolean hasShipReviewer(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM "CruiseStaff" WHERE cruise_id=?
                    AND status='ACTIVE' AND role_in_cruise='SHIP_MANAGER')
                """, Boolean.class, id));
    }

    private void requireCoordinator(UUID id, CasmsPrincipal actor) {
        if (!coordinator(id, actor)) throw denied();
    }

    private boolean canChange(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM "Cruise" c LEFT JOIN "CruisePlanReview" p USING(cruise_id)
                    WHERE c.cruise_id=? AND c.status='DRAFT'
                    AND coalesce(p.status,'DRAFT') IN ('DRAFT','WITHDRAWN','REVISION_REQUESTED'))
                """, Boolean.class, id));
    }

    private void requireEditable(UUID id) {
        if (!canChange(id)) throw conflict("HANDOFF_TOUR_LOCKED", "Tour đã được gửi xét duyệt hoặc không còn chỉnh sửa được.");
    }

    private void lock(UUID id) {
        jdbc.queryForList("SELECT cruise_id FROM \"Cruise\" WHERE cruise_id=? FOR UPDATE", UUID.class, id);
    }

    private List<Task> select(String sql, CasmsPrincipal actor, boolean responder, Object... args) {
        return jdbc.query(sql, (rs, i) -> {
            var group = Group.valueOf(rs.getString(4));
            var status = rs.getString(5);
            var assignee = rs.getObject(11, UUID.class);
            boolean allowed = responder && !actor.getUserId().equals(rs.getObject(12, UUID.class));
            var depTs = rs.getTimestamp(16);
            var retTs = rs.getTimestamp(17);
            return new Task(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), group, status,
                    rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getLong(10),
                    allowed && group == Group.ASM && "PENDING".equals(status),
                    allowed && (group == Group.SHIP && "PENDING".equals(status)
                        || group == Group.ASM && actor.getUserId().equals(assignee) && "IN_PROGRESS".equals(status)),
                    rs.getObject(13, UUID.class), rs.getString(14), (Integer) rs.getObject(15),
                    depTs == null ? null : depTs.toInstant(),
                    retTs == null ? null : retTs.toInstant(),
                    (Integer) rs.getObject(18),
                    rs.getString(19), rs.getString(20), (Integer) rs.getObject(21));
        }, args);
    }

    private AccessDeniedException denied() {
        return new AccessDeniedException("Bạn không được giao xử lý yêu cầu này.");
    }

    private BusinessException conflict(String code, String message) {
        return new BusinessException(HttpStatus.CONFLICT, code, message);
    }
}
