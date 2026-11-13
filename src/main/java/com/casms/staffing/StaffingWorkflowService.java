package com.casms.staffing;

import com.casms.common.exception.BusinessException;
import com.casms.identity.enums.Role;
import com.casms.security.principal.CasmsPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The staffing demand is separate from the existing CruiseStaffDuty and POS permission grants. */
@Service
@RequiredArgsConstructor
public class StaffingWorkflowService {
    private final JdbcTemplate jdbc;

    public record Requirement(UUID serviceId, int requiredCount) {}
    public record CreateRequest(Long scheduleRevision, List<Requirement> requirements) {}
    public record ReviewRequest(Long expectedRevision, String comment) {}
    private record RequestState(UUID id, UUID cruiseId, String status, long revision, long scheduleRevision) {}

    public record Snapshot(UUID id, UUID cruiseId, String status, long revision, long scheduleRevision,
                           List<RequirementProgress> requirements) {}
    public record RequirementProgress(UUID serviceId, int required, long assigned) {}

    private boolean allowed(CasmsPrincipal actor, Role role) {
        if (actor == null || !actor.isEnabled() || !actor.isAccountNonLocked() || !actor.hasRole(role)) {
            return false;
        }
        return actor.hasAuthority(switch (role) {
            case CRUISE_MANAGER -> "CRUISE_PREPARATION_SUBMIT";
            case HR_MANAGER -> "CRUISE_STAFF_ASSIGN";
            case SHIP_MANAGER -> "CRUISE_PREPARATION_READ";
            case OPERATIONS_MANAGER -> "CRUISE_VALIDATE";
            default -> "";
        });
    }

    private void authorize(CasmsPrincipal actor, Role role) {
        if (!allowed(actor, role)) {
            throw forbidden("STAFFING_ROLE_REQUIRED");
        }
    }

    private BusinessException forbidden(String code) {
        return new BusinessException(HttpStatus.FORBIDDEN, code, "Không có quyền xử lý yêu cầu nhân sự.");
    }

    private record CruiseState(String status, long revision, UUID coordinator) {}

    private CruiseState lockCruise(UUID id) {
        var state = jdbc.query("""
                SELECT status::text, revision, preparation_coordinator_id
                FROM "Cruise" WHERE cruise_id=? FOR NO KEY UPDATE
                """, (rs, i) -> new CruiseState(rs.getString(1), rs.getLong(2), rs.getObject(3, UUID.class)), id)
                .stream().findFirst().orElseThrow(() -> conflict("STAFFING_CRUISE_NOT_FOUND"));
        if (List.of("COMPLETED", "CANCELLED").contains(state.status())) {
            throw conflict("STAFFING_CRUISE_CLOSED");
        }
        return state;
    }

    private boolean isShipManager(UUID cruiseId, UUID actorId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM "CruiseStaff" WHERE cruise_id=? AND user_id=?
                    AND status='ACTIVE' AND role_in_cruise='SHIP_MANAGER')
                """, Boolean.class, cruiseId, actorId));
    }

    private BusinessException conflict(String code) {
        return new BusinessException(HttpStatus.CONFLICT, code, "Yêu cầu nhân sự chưa đáp ứng điều kiện xử lý.");
    }

    @Transactional
    public Snapshot submit(UUID cruiseId, CreateRequest input, CasmsPrincipal actor) {
        authorize(actor, Role.CRUISE_MANAGER);
        if (input == null || input.scheduleRevision() == null || input.scheduleRevision() < 0 || input.requirements() == null
                || input.requirements().isEmpty()) throw conflict("STAFFING_REQUIREMENTS_REQUIRED");
        CruiseState cruise = lockCruise(cruiseId);
        if (!actor.getUserId().equals(cruise.coordinator())) {
            throw forbidden("STAFFING_PLANNER_SCOPE_FORBIDDEN");
        }
        if (cruise.revision() != input.scheduleRevision()) {
            throw conflict("STAFFING_SOURCE_REVISION_STALE");
        }
        Set<UUID> unique = new HashSet<>();
        for (Requirement requirement : input.requirements()) {
            if (requirement == null || requirement.serviceId() == null || requirement.requiredCount() < 1
                    || !unique.add(requirement.serviceId())) throw conflict("STAFFING_REQUIREMENT_INVALID");
            if (!Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM \"Service\" WHERE service_id=? AND status='ACTIVE')",
                    Boolean.class, requirement.serviceId()))) throw conflict("STAFFING_SERVICE_NOT_ACTIVE");
        }
        if (Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM "StaffingRequest" WHERE cruise_id=?
                 AND status NOT IN ('CANCELLED','RETURNED'))
                """, Boolean.class, cruiseId))) throw conflict("STAFFING_OPEN_REQUEST_EXISTS");
        UUID requestId = jdbc.queryForObject("""
                INSERT INTO "StaffingRequest" (cruise_id, schedule_revision, requested_by)
                VALUES (?,?,?) RETURNING request_id
                """, UUID.class, cruiseId, input.scheduleRevision(), actor.getUserId());
        for (Requirement requirement : input.requirements()) jdbc.update("""
                INSERT INTO "StaffingRequirement" (request_id,service_id,required_count) VALUES (?,?,?)
                """, requestId, requirement.serviceId(), requirement.requiredCount());
        decision(requestId, 0, "SENT_TO_HR", actor, null);
        return read(requestId);
    }

    private Snapshot read(UUID id) {
        var requests = jdbc.query("""
                SELECT request_id,cruise_id,status,revision,schedule_revision
                FROM "StaffingRequest" WHERE request_id=?
                """, (rs, i) -> new RequestState(rs.getObject("request_id", UUID.class),
                    rs.getObject("cruise_id", UUID.class), rs.getString("status"),
                    rs.getLong("revision"), rs.getLong("schedule_revision")), id);
        RequestState request = requests.stream().findFirst()
                .orElseThrow(() -> conflict("STAFFING_REQUEST_NOT_FOUND"));
        List<RequirementProgress> progress = jdbc.query("""
                SELECT sr.service_id,sr.required_count,
                    count(DISTINCT cs.user_id) FILTER (WHERE d.duty_id IS NOT NULL
                        AND u.user_id IS NOT NULL) AS assigned
                FROM "StaffingRequirement" sr JOIN "StaffingRequest" req ON req.request_id=sr.request_id
                LEFT JOIN "CruiseStaff" cs ON cs.cruise_id=req.cruise_id AND cs.status='ACTIVE'
                LEFT JOIN "User" u ON u.user_id=cs.user_id AND u.status='ACTIVE'
                JOIN "Service" s ON s.service_id=sr.service_id
                LEFT JOIN "CruiseStaffDuty" d ON s.status='ACTIVE' AND d.cruise_staff_id=cs.cruise_staff_id
                    AND d.service_id=sr.service_id AND d.ended_at IS NULL
                WHERE sr.request_id=? GROUP BY sr.service_id,sr.required_count ORDER BY sr.service_id
                """, (rs,i) -> new RequirementProgress(rs.getObject(1,UUID.class),rs.getInt(2),rs.getLong(3)),id);
        return new Snapshot(request.id(), request.cruiseId(), request.status(),
                request.revision(), request.scheduleRevision(), progress);
    }

    @Transactional(readOnly = true)
    public Snapshot detail(UUID id, CasmsPrincipal actor) {
        boolean hr = allowed(actor, Role.HR_MANAGER);
        boolean ops = allowed(actor, Role.OPERATIONS_MANAGER);
        boolean ship = allowed(actor, Role.SHIP_MANAGER);
        boolean planner = allowed(actor, Role.CRUISE_MANAGER);
        if (!(hr || ops || ship || planner)) {
            throw forbidden("STAFFING_READ_FORBIDDEN");
        }
        Snapshot snapshot = read(id);
        if (hr || ops || (ship && isShipManager(snapshot.cruiseId(), actor.getUserId()))
                || (planner && Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM "Cruise" WHERE cruise_id=? AND preparation_coordinator_id=?)
                    """, Boolean.class, snapshot.cruiseId(), actor.getUserId())))) {
            return snapshot;
        }
        throw forbidden("STAFFING_READ_FORBIDDEN");
    }

    @Transactional(readOnly = true)
    public List<Snapshot> inbox(CasmsPrincipal actor) {
        boolean hr = allowed(actor, Role.HR_MANAGER);
        boolean ship = allowed(actor, Role.SHIP_MANAGER);
        boolean planner = allowed(actor, Role.CRUISE_MANAGER);
        boolean ops = allowed(actor, Role.OPERATIONS_MANAGER);
        if (!(hr || ship || planner || ops)) {
            throw forbidden("STAFFING_INBOX_FORBIDDEN");
        }
        return jdbc.queryForList("""
                SELECT req.request_id FROM "StaffingRequest" req JOIN "Cruise" c USING (cruise_id)
                WHERE c.status NOT IN ('COMPLETED', 'CANCELLED') AND (
                    (? AND req.status='SENT_TO_HR')
                    OR (? AND req.status='HR_SUBMITTED' AND EXISTS (
                        SELECT 1 FROM "CruiseStaff" cs WHERE cs.cruise_id=req.cruise_id
                        AND cs.user_id=? AND cs.status='ACTIVE' AND cs.role_in_cruise='SHIP_MANAGER'))
                    OR (? AND req.status='SHIP_CONFIRMED' AND c.preparation_coordinator_id=?)
                    OR (? AND req.status='PLANNER_CONFIRMED')
                )
                ORDER BY req.requested_at, req.request_id
                """, UUID.class, hr, ship, actor.getUserId(), planner, actor.getUserId(), ops)
                .stream().map(this::read).toList();
    }

    private record ReviewState(String status, long revision, UUID requester, UUID hr, UUID ship, UUID planner) {}

    @Transactional
    public Snapshot transition(UUID id, String action, ReviewRequest input, CasmsPrincipal actor) {
        Role role = switch (action) {
            case "HR_SUBMITTED" -> Role.HR_MANAGER;
            case "SHIP_CONFIRMED" -> Role.SHIP_MANAGER;
            case "PLANNER_CONFIRMED" -> Role.CRUISE_MANAGER;
            case "OPS_APPROVED" -> Role.OPERATIONS_MANAGER;
            case "RETURNED" -> Role.SHIP_MANAGER;
            default -> throw conflict("STAFFING_INVALID_ACTION");
        };
        authorize(actor, role);
        UUID cruiseId = jdbc.queryForList("""
                SELECT cruise_id FROM "StaffingRequest" WHERE request_id=?
                """, UUID.class, id).stream().findFirst()
                .orElseThrow(() -> conflict("STAFFING_REQUEST_NOT_FOUND"));
        CruiseState cruise = lockCruise(cruiseId);
        UUID actorId = actor.getUserId();
        if (role == Role.CRUISE_MANAGER && !actorId.equals(cruise.coordinator())) {
            throw forbidden("STAFFING_PLANNER_SCOPE_FORBIDDEN");
        }
        if (role == Role.SHIP_MANAGER && !isShipManager(cruiseId, actorId)) {
            throw forbidden("STAFFING_SHIP_SCOPE_FORBIDDEN");
        }
        ReviewState row = jdbc.query("""
                SELECT status,revision,requested_by,hr_submitted_by,ship_confirmed_by,planner_confirmed_by
                FROM "StaffingRequest" WHERE request_id=? FOR UPDATE
                """, (rs, i) -> new ReviewState(rs.getString(1), rs.getLong(2), rs.getObject(3, UUID.class),
                        rs.getObject(4, UUID.class), rs.getObject(5, UUID.class), rs.getObject(6, UUID.class)), id)
                .stream().findFirst().orElseThrow(() -> conflict("STAFFING_REQUEST_NOT_FOUND"));
        if (input == null || input.expectedRevision() == null || row.revision() != input.expectedRevision()) {
            throw conflict("STAFFING_REVISION_CONFLICT");
        }
        String expected = switch (action) {
            case "HR_SUBMITTED" -> "SENT_TO_HR";
            case "SHIP_CONFIRMED", "RETURNED" -> "HR_SUBMITTED";
            case "PLANNER_CONFIRMED" -> "SHIP_CONFIRMED";
            case "OPS_APPROVED" -> "PLANNER_CONFIRMED";
            default -> "";
        };
        if (!row.status().equals(expected)) {
            throw conflict("STAFFING_INVALID_TRANSITION");
        }
        if (role == Role.CRUISE_MANAGER && !actorId.equals(row.requester())) {
            throw forbidden("STAFFING_NOT_REQUEST_OWNER");
        }
        if (actorId.equals(row.hr()) || actorId.equals(row.ship()) || actorId.equals(row.planner())
                || (role != Role.CRUISE_MANAGER && actorId.equals(row.requester()))) {
            throw conflict("STAFFING_SELF_APPROVAL");
        }
        if ("RETURNED".equals(action) && (input.comment()==null || input.comment().isBlank()))
            throw conflict("STAFFING_RETURN_REASON_REQUIRED");
        if (!action.equals("RETURNED")) {
            Snapshot before = read(id);
            if (before.requirements().isEmpty() || before.requirements().stream().anyMatch(r -> r.assigned() < r.required()))
                throw conflict("STAFFING_CREW_SHORTAGE");
        }
        String column = switch (action) {
            case "HR_SUBMITTED" -> "hr_submitted_by";
            case "SHIP_CONFIRMED" -> "ship_confirmed_by";
            case "PLANNER_CONFIRMED" -> "planner_confirmed_by";
            case "OPS_APPROVED" -> "ops_approved_by";
            default -> null;
        };
        if (column == null) {
            jdbc.update("""
                    UPDATE "StaffingRequest" SET status=?, revision=revision+1, updated_at=now()
                    WHERE request_id=?
                    """, action, id);
        } else {
            jdbc.update("""
                    UPDATE "StaffingRequest" SET status=?, revision=revision+1, updated_at=now(), %s=?
                    WHERE request_id=?
                    """.formatted(column), action, actorId, id);
        }
        decision(id, input.expectedRevision() + 1, action, actor, input.comment());
        return read(id);
    }

    @Transactional(readOnly = true)
    public boolean ready(UUID cruiseId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM "StaffingRequest" req JOIN "Cruise" c USING (cruise_id)
                    WHERE req.cruise_id=? AND req.status='OPS_APPROVED'
                    AND c.preparation_coordinator_id=req.planner_confirmed_by
                    AND EXISTS (SELECT 1 FROM "StaffingRequirement" r WHERE r.request_id=req.request_id)
                    AND NOT EXISTS (
                        SELECT 1 FROM "StaffingRequirement" r JOIN "Service" s ON s.service_id=r.service_id
                        WHERE r.request_id=req.request_id AND (
                            s.status <> 'ACTIVE' OR r.required_count > (
                                SELECT count(DISTINCT cs.user_id) FROM "CruiseStaff" cs
                                JOIN "CruiseStaffDuty" d USING (cruise_staff_id)
                                JOIN "User" u ON u.user_id=cs.user_id
                                WHERE cs.cruise_id=req.cruise_id AND cs.status='ACTIVE' AND u.status='ACTIVE'
                                AND d.service_id=r.service_id AND d.ended_at IS NULL)))
                )
                """, Boolean.class, cruiseId));
    }

    private void decision(UUID id, long revision, String action, CasmsPrincipal actor, String comment) {
        jdbc.update("""
            INSERT INTO "StaffingDecision" (request_id,revision,action,actor_id,comment)
            VALUES (?,?,?,?,?)
            """, id, revision, action, actor.getUserId(), comment);
    }
}
