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
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CruiseWorkHandoffService {
    private final JdbcTemplate jdbc;
    private final ItineraryProgramPolicy programPolicy;

    public enum Group { ASM, SHIP }
    public enum Action { ACCEPT, COMPLETE, RETURN }
    public record Task(UUID cruiseId, String cruiseName, String cruiseCode, Group group, String status,
                       String request, String response, String assignee, String operatingBudgetVnd, long revision,
                       boolean canAccept, boolean canRespond,
                       UUID shipId, String shipName, Integer shipCapacity,
                       Instant departureAt, Instant returnAt, Integer expectedPassengerCount,
                       String sourceTripCode, String sourceTripName, Integer sourceVersionNumber,
                       String staffingStatus, boolean shipAssigned) {}
    public record Panel(boolean canDispatch, boolean shipAssigned, List<Task> tasks, UUID staffingRequestId,
                        boolean canReopenAsm) {}
    public record ProgramContext(String planStatus, boolean unifiedPackageReview, Windows windows,
                                 boolean mayConfigure, List<ItineraryProgramPolicy.Issue> completionIssues) {}
    public record Windows(long expectedRevision) {}

    private static final String SELECT_TASKS = """
            SELECT c.cruise_id,c.cruise_name,c.cruise_code,w.work_group,w.status,w.request_text,w.response_text,
                   u.username,c.operating_budget_vnd::text,w.revision,w.assignee_id,w.requested_by,
                   s.ship_id,s.ship_name,s.total_capacity,
                   c.departure_datetime,c.return_datetime,c.expected_passenger_count,
                   t.trip_code,t.name,tv.version_number,
                   (SELECT req.status FROM "StaffingRequest" req WHERE req.cruise_id=c.cruise_id
                       AND req.asm_program_workflow AND req.status NOT IN ('CANCELLED','RETURNED')),
                   EXISTS (SELECT 1 FROM "CruiseStaff" crew WHERE crew.cruise_id=c.cruise_id
                       AND crew.status='ACTIVE' AND crew.role_in_cruise='SHIP_MANAGER')
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
        UUID staffingRequestId = jdbc.queryForList("""
                SELECT request_id FROM "StaffingRequest" WHERE cruise_id=?
                    AND asm_program_workflow AND status NOT IN ('CANCELLED','RETURNED')
                """, UUID.class, cruiseId).stream().findFirst().orElse(null);
        return new Panel(canChange(cruiseId), hasShipReviewer(cruiseId),
                select(SELECT_TASKS + " WHERE c.cruise_id=? ORDER BY w.work_group", actor, false, cruiseId),
                staffingRequestId, staffingRequestId == null && canChange(cruiseId)
                    && "COMPLETED".equals(currentRevision(cruiseId, Group.ASM).status()));
    }

    @Transactional(readOnly = true)
    public List<Task> inbox(CasmsPrincipal actor) {
        if (!asm(actor) && !ship(actor)) throw denied();
        return select(SELECT_TASKS + """
                WHERE c.status NOT IN ('CANCELLED','COMPLETED') AND (
                  (? AND w.work_group='ASM' AND (w.assignee_id IS NULL OR w.assignee_id=?))
                  OR (? AND w.work_group='SHIP' AND NOT EXISTS (
                     SELECT 1 FROM "StaffingRequest" req WHERE req.cruise_id=c.cruise_id
                     AND req.asm_program_workflow AND req.status NOT IN ('CANCELLED','RETURNED'))
                     AND EXISTS (
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
        requireStandaloneShipHandoff(cruiseId, group);
        if (group == Group.SHIP && !hasShipReviewer(cruiseId))
            throw conflict("SHIP_MANAGER_REQUIRED", "Phân công Quản lý tàu cho tour trước khi gửi.");
        sendTask(cruiseId, group, request, actor, false);
        return forCoordinator(cruiseId, actor);
    }

    @Transactional
    public boolean ensureAsmForStaffing(UUID cruiseId, CasmsPrincipal actor) {
        lock(cruiseId);
        requireCoordinator(cruiseId, actor);
        String status = currentRevision(cruiseId, Group.ASM).status();
        boolean completed = "COMPLETED".equals(status);
        if (completed && asmIssues(cruiseId).isEmpty()) return true;
        requireEditable(cruiseId);
        if (status == null || "RETURNED".equals(status) || completed) {
            sendTask(cruiseId, Group.ASM, "Hoàn thiện chương trình dịch vụ và hoạt động của chuyến.", actor, completed);
        }
        return false;
    }

    @Transactional
    public void returnAsmFromStaffing(UUID cruiseId, CasmsPrincipal actor, String reason) {
        lock(cruiseId);
        if (!assignedShip(cruiseId, actor)) throw denied();
        requireEditable(cruiseId);
        sendTask(cruiseId, Group.ASM, reason, actor, true);
    }

    /** Recovery for standalone/legacy handoffs; V2 must return through its staffing workflow. */
    @Transactional
    public Panel reopenAsm(UUID cruiseId, long expectedRevision, String reason, CasmsPrincipal actor) {
        lock(cruiseId);
        requireCoordinator(cruiseId, actor);
        requireEditable(cruiseId);
        var panel = forCoordinator(cruiseId, actor);
        if (panel.staffingRequestId() != null)
            throw conflict("HANDOFF_MANAGED_BY_STAFFING", "Trả chương trình qua yêu cầu bố trí để các bộ phận cùng nhận đúng phiên bản.");
        var previous = currentRevision(cruiseId, Group.ASM);
        if (!"COMPLETED".equals(previous.status()) || previous.revision() != expectedRevision)
            throw conflict("HANDOFF_CHANGED", "Yêu cầu đã thay đổi. Tải lại trước khi trả bổ sung.");
        if (reason == null || reason.isBlank())
            throw conflict("HANDOFF_RESPONSE_REQUIRED", "Nhập nội dung cần Quản lý dịch vụ bổ sung.");
        sendTask(cruiseId, Group.ASM, reason.strip(), actor, true);
        return forCoordinator(cruiseId, actor);
    }

    private void sendTask(UUID cruiseId, Group group, String request, CasmsPrincipal actor, boolean reopen) {
        var previous = currentRevision(cruiseId, group);
        int changed = jdbc.update("""
                INSERT INTO "CruiseWorkHandoff" AS w (cruise_id,work_group,requested_by,request_text,status)
                VALUES (?,?,?,?,'PENDING')
                ON CONFLICT (cruise_id,work_group) DO UPDATE SET requested_by=excluded.requested_by,
                    request_text=excluded.request_text,response_text=NULL,assignee_id=NULL,status='PENDING',
                    revision=w.revision+1,updated_at=now()
                WHERE w.status='RETURNED' OR (? AND w.status='COMPLETED')
                """, cruiseId, group.name(), actor.getUserId(), request, reopen);
        if (changed != 1) throw conflict("HANDOFF_ALREADY_SENT", "Yêu cầu đã được gửi. Xem tiến độ trên tour.");
        recordEvent(cruiseId, group, actor, reopen ? "REOPEN" : "SEND", previous);
    }

    private void requireStandaloneShipHandoff(UUID cruiseId, Group group) {
        if (group == Group.SHIP && Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM "StaffingRequest" WHERE cruise_id=?
                    AND asm_program_workflow AND status NOT IN ('CANCELLED','RETURNED'))
                """, Boolean.class, cruiseId))) {
            throw conflict("HANDOFF_MANAGED_BY_STAFFING", "Xử lý kiểm tra tàu trong yêu cầu bố trí.");
        }
    }

    @Transactional
    public void completeShipForStaffing(UUID cruiseId, CasmsPrincipal actor) {
        var previous = currentRevision(cruiseId, Group.SHIP);
        int changed = jdbc.update("""
                UPDATE "CruiseWorkHandoff" SET status='COMPLETED', assignee_id=?,
                    response_text='Đã xác nhận kiểm tra tàu trên yêu cầu bố trí.',
                    revision=revision+1, updated_at=now()
                WHERE cruise_id=? AND work_group='SHIP' AND status IN ('PENDING','RETURNED')
                """, actor.getUserId(), cruiseId);
        if (changed == 1) recordEvent(cruiseId, Group.SHIP, actor, "COMPLETE", previous);
    }

    @Transactional
    public Task respond(UUID cruiseId, Group group, Action action, long revision, String text, CasmsPrincipal actor) {
        if (group == Group.ASM ? !asm(actor) : !ship(actor)) throw denied();
        if (action == Action.ACCEPT && group != Group.ASM) throw denied();
        lock(cruiseId);
        requireStandaloneShipHandoff(cruiseId, group);
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
        if (group == Group.ASM && action == Action.COMPLETE) {
            var issues = asmIssues(cruiseId);
            if (!issues.isEmpty()) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "ITINERARY_PROGRAMS_INCOMPLETE",
                        "Hoàn thiện chương trình trước khi chuyển yêu cầu cho quản lý tàu.", Map.of("issues", issues));
            }
            jdbc.update("""
                    WITH promoted AS (
                        UPDATE "StaffingRequest" SET status='PENDING_SHIP_REVIEW',
                            revision=revision+1, updated_at=now()
                        WHERE cruise_id=? AND asm_program_workflow AND status='PENDING_ASM'
                        RETURNING request_id, revision
                    )
                    INSERT INTO "StaffingDecision" (request_id,revision,action,actor_id)
                    SELECT request_id,revision,'ASM_COMPLETED',? FROM promoted
                    """, cruiseId, actor.getUserId());
            jdbc.update("""
                    INSERT INTO "Notification" (notification_id,recipient_user_id,notification_type,title,content,created_at)
                    SELECT gen_random_uuid(), cs.user_id, 'STAFFING_WORKFLOW', 'Chương trình chuyến đã hoàn tất',
                        'Kiểm tra tàu và chuẩn bị nhu cầu nhân sự. Mã yêu cầu: ' || req.request_id, now()
                    FROM "StaffingRequest" req JOIN "CruiseStaff" cs USING(cruise_id)
                    WHERE req.cruise_id=? AND req.workflow_version='V2' AND req.status='PENDING_SHIP_REVIEW'
                        AND cs.status='ACTIVE' AND cs.role_in_cruise='SHIP_MANAGER'
                    """, cruiseId);
        }
        return select(SELECT_TASKS + " WHERE c.cruise_id=? AND w.work_group=?", actor, true, cruiseId, group.name()).getFirst();
    }

    public boolean hasPendingStaffingProgram(UUID cruiseId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM "StaffingRequest" WHERE cruise_id=?
                    AND asm_program_workflow AND status='PENDING_ASM')
                """, Boolean.class, cruiseId));
    }

    private List<ItineraryProgramPolicy.Issue> asmIssues(UUID cruiseId) {
        var issues = new ArrayList<>(programPolicy.issues(cruiseId));
        issues.addAll(jdbc.query("""
                SELECT DISTINCT s.service_id,s.service_name
                FROM "DepartureFarePackage" o
                JOIN "FarePackageBenefit" b ON b.template_version_id=o.template_version_id
                JOIN "Service" s ON s.service_id=b.service_id
                WHERE o.cruise_id=? AND o.status<>'RETIRED'
                  AND b.inclusion IN ('INCLUDED','LIMITED_INCLUDED') AND s.requires_schedule
                  AND NOT EXISTS (SELECT 1 FROM "ServiceSchedule" w
                    LEFT JOIN "CruiseStop" stop USING(cruise_stop_id)
                    LEFT JOIN "Itinerary" i USING(itinerary_id)
                    WHERE w.service_id=s.service_id AND (w.cruise_id=o.cruise_id OR i.cruise_id=o.cruise_id)
                      AND w.status='OPEN' AND w.ends_at>now()
                      AND w.capacity>coalesce((SELECT sum(r.quantity) FROM "ServiceRequest" r
                        WHERE r.service_schedule_id=w.service_schedule_id
                          AND r.status IN ('REQUESTED','ACCEPTED','IN_PROGRESS')),0))
                """, (rs, row) -> new ItineraryProgramPolicy.Issue("INCLUDED_BENEFIT_UNAVAILABLE", "SERVICE_WINDOW",
                    rs.getObject(1, UUID.class), "Bổ sung khung giờ phục vụ còn chỗ cho quyền lợi trong gói: " + rs.getString(2)), cruiseId));
        issues.addAll(jdbc.query("""
                SELECT w.service_schedule_id
                FROM "ServiceSchedule" w
                JOIN "Cruise" c USING (cruise_id)
                JOIN "Service" s USING (service_id)
                LEFT JOIN "CruiseStop" stop USING (cruise_stop_id)
                LEFT JOIN "Itinerary" itinerary ON itinerary.itinerary_id=stop.itinerary_id
                LEFT JOIN "Supplier" supplier ON supplier.supplier_id=w.supplier_id
                WHERE w.cruise_id=? AND w.status<>'CANCELLED' AND (
                    s.status<>'ACTIVE' OR NOT s.requires_schedule OR trim(w.location)=''
                    OR w.starts_at<c.departure_datetime OR w.ends_at>c.return_datetime
                    OR w.cruise_stop_id IS NOT NULL AND (
                        itinerary.cruise_id IS DISTINCT FROM c.cruise_id OR stop.status IN ('OMITTED','SUBSTITUTED')
                        OR stop.planned_arrival IS NULL OR stop.planned_departure IS NULL
                        OR w.starts_at<stop.planned_arrival OR w.ends_at>stop.planned_departure
                    )
                    OR w.supplier_id IS NOT NULL AND (
                        supplier.status IS DISTINCT FROM 'ACTIVE'
                        OR supplier.verification_status IS DISTINCT FROM 'VERIFIED'
                    )
                )
                ORDER BY w.service_schedule_id
                """, (rs, row) -> new ItineraryProgramPolicy.Issue("SERVING_WINDOW_INVALID", "SERVICE_WINDOW",
                rs.getObject(1, UUID.class), "Kiểm tra dịch vụ, nhà cung cấp, địa điểm và thời gian phục vụ."), cruiseId));
        return issues;
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
                        editable, asmIssues(id)), id);
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

    public boolean canChange(UUID id) {
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
        jdbc.queryForList("SELECT cruise_id FROM \"Cruise\" WHERE cruise_id=? FOR NO KEY UPDATE", UUID.class, id);
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
                    rs.getString(19), rs.getString(20), (Integer) rs.getObject(21),
                    rs.getString(22), rs.getBoolean(23));
        }, args);
    }

    private AccessDeniedException denied() {
        return new AccessDeniedException("Bạn không được giao xử lý yêu cầu này.");
    }

    private BusinessException conflict(String code, String message) {
        return new BusinessException(HttpStatus.CONFLICT, code, message);
    }
}
