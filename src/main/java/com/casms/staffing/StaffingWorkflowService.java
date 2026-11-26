package com.casms.staffing;

import com.casms.common.exception.BusinessException;
import com.casms.identity.enums.Role;
import com.casms.pos.PosAccessService;
import com.casms.pos.PosService;
import com.casms.ship.dto.request.CruiseStaffRequest;
import com.casms.ship.service.CruiseStaffService;
import com.casms.ship.service.CruiseWorkHandoffService;
import com.casms.ship.service.DeckServiceAssignmentService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.notification.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The staffing demand is separate from the existing CruiseStaffDuty and POS permission grants. */
@Service
public class StaffingWorkflowService {
    private final JdbcTemplate jdbc;
    private final CruiseWorkHandoffService handoffs;
    private final PosAccessService posAccess;
    private final PosService pos;
    private final DeckServiceAssignmentService deckServices;
    private final CruiseStaffService cruiseStaff;
    @Autowired(required = false)
    private NotificationService notificationService;

    public StaffingWorkflowService(JdbcTemplate jdbc, CruiseWorkHandoffService handoffs, PosAccessService posAccess,
                                   PosService pos, DeckServiceAssignmentService deckServices,
                                   @Lazy CruiseStaffService cruiseStaff) {
        this.jdbc = jdbc;
        this.handoffs = handoffs;
        this.posAccess = posAccess;
        this.pos = pos;
        this.deckServices = deckServices;
        this.cruiseStaff = cruiseStaff;
    }

    public record Requirement(UUID serviceId, int requiredCount) {}
    public record ShipManagerNeeded(UUID cruiseId, String cruiseName, String cruiseCode) {}
    public record CreateRequest(Long scheduleRevision, List<Requirement> requirements) {}
    public record ReviewRequest(Long expectedRevision, String comment) {}
    public record ServicePlacementInput(@NotNull UUID areaId, @NotNull UUID terminalId,
                                        @NotNull @PositiveOrZero Long expectedRevision) {}
    public record AreaPosInput(@NotNull UUID terminalId, @NotNull @PositiveOrZero Long expectedRevision) {}
    public record NeedInput(UUID areaId, String positionCode, int requiredCount) {}
    public record ShipPlanInput(@NotNull @PositiveOrZero Long expectedRevision,
                                @NotNull List<UUID> activeAreaIds, @NotNull List<NeedInput> needs) {}
    public record NeedAssignmentInput(@NotNull @PositiveOrZero Long expectedRevision, @NotNull Boolean assigned) {}
    public record NeedProgress(UUID needId, UUID areaId, String areaName, String positionCode,
                               int requiredCount, long planRevision, List<UUID> assignedUserIds) {}
    public record ServiceNeedProgress(UUID serviceId, String serviceName, UUID areaId, String areaName,
                                      UUID terminalId, String terminalName, int requiredCount,
                                      List<UUID> assignedUserIds) {}
    private static final Set<String> AREA_POSITIONS = Set.of("ONBOARD_STAFF", "SHIP_MANAGER", "FINANCE_OFFICER");
    private record RequestState(UUID id, UUID cruiseId, String status, long revision, long scheduleRevision,
                                boolean shipReview, boolean asmProgram, String workflowVersion) {}

    public record Snapshot(UUID id, UUID cruiseId, String status, long revision, long scheduleRevision,
                           List<RequirementProgress> requirements, String cruiseName, String cruiseCode,
                           OffsetDateTime departure, OffsetDateTime returning, String operatingBudgetVnd,
                           String returnReason, boolean shipReviewWorkflow, boolean asmProgramWorkflow,
                           String workflowVersion, List<UUID> activeAreaIds, List<NeedProgress> needs,
                           List<ServiceNeedProgress> serviceNeeds,
                           boolean shipPlanStale, PlanResetImpact planResetImpact) {
        boolean v2() { return "V2".equals(workflowVersion); }
    }
    public record PlanResetImpact(long needsToReplaceOnSave, long assignmentsToClear) {}
    public record RequirementProgress(UUID serviceId, int required, long assigned, String serviceName) {}
    public record ShipReview(UUID shipId, UUID modelId, String name, String status, List<CabinReview> cabins) {}
    public record CabinReview(UUID cabinId, String number, String status) {}
    public record AreaReview(UUID areaId, String name, List<PosReview> terminals, List<UUID> staffIds,
                             List<PosReview> eligibleTerminals) {}
    public record PosReview(UUID terminalId, String name) {}
    public record ActivityReview(UUID activityId, String kind, String name, List<UUID> staffIds,
                                 int requiredStaffCount) {}
    public record StaffReview(UUID userId, String username, String role, List<ServiceOption> services,
                              List<ServiceOption> areas, List<ActivityReview> activities) {}
    public record Placement(UUID areaId, UUID terminalId) {}
    public record ServiceProposal(String deckName, String note, String status) {}
    public record PlannedService(UUID serviceId, String name, List<UUID> eligibleAreaIds,
                                 List<Placement> placements, ServiceProposal proposal,
                                 List<Placement> eligiblePlacements, List<Placement> configuredPlacements,
                                 String placementBlocker, int requiredStaffCount) {}
    public record ReviewDetails(Snapshot request, ShipReview ship, List<AreaReview> areas,
                                List<ActivityReview> activities, List<StaffReview> staff,
                                List<PosReview> terminalOptions, List<PlannedService> plannedServices,
                                List<String> positionOptions, boolean canReturnToAsm, Boolean shipPlanReady) {}
    public record ServiceOption(UUID id, String name) {}
    public record Preparation(long scheduleRevision, Snapshot request, List<ServiceOption> services,
                              boolean hasOperatingBudget, boolean requiresReplanning) {}

    private record PreparationSource(long revision, boolean hasOperatingBudget) {}

    @Transactional(readOnly = true)
    public Preparation preparation(UUID cruiseId, CasmsPrincipal actor) {
        authorize(actor, Role.CRUISE_MANAGER);
        var source = jdbc.query("""
                SELECT revision, operating_budget_vnd IS NOT NULL
                FROM "Cruise" WHERE cruise_id=? AND preparation_coordinator_id=?
                  AND status NOT IN ('CANCELLED','COMPLETED')
                """, (rs, i) -> new PreparationSource(rs.getLong(1), rs.getBoolean(2)), cruiseId, actor.getUserId())
                .stream().findFirst().orElseThrow(() -> forbidden("STAFFING_PLANNER_SCOPE_FORBIDDEN"));
        Snapshot request = jdbc.queryForList("""
                SELECT request_id FROM "StaffingRequest" WHERE cruise_id=?
                  AND status <> 'CANCELLED' ORDER BY requested_at DESC, request_id DESC LIMIT 1
                """, UUID.class, cruiseId).stream().findFirst().map(this::read).orElse(null);
        List<ServiceOption> services = jdbc.query("""
                SELECT DISTINCT s.service_id, s.service_name FROM "Cruise" c JOIN "Deck" d USING(ship_id)
                JOIN "DeckServiceAssignment" a USING(deck_id) JOIN "Service" s USING(service_id)
                WHERE c.cruise_id=? AND s.status='ACTIVE' ORDER BY s.service_name, s.service_id
                """, (rs, i) -> new ServiceOption(rs.getObject(1, UUID.class), rs.getString(2)), cruiseId);
        boolean requiresReplanning = request == null && Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM "StaffingRequest" WHERE cruise_id=? AND status='CANCELLED')
                """, Boolean.class, cruiseId));
        return new Preparation(source.revision(), request, services, source.hasOperatingBudget(), requiresReplanning);
    }

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

    private record CruiseState(String status, long revision, UUID coordinator, boolean hasBudget) {}

    private CruiseState lockCruise(UUID id) {
        var state = jdbc.query("""
                SELECT status::text, revision, preparation_coordinator_id, operating_budget_vnd IS NOT NULL
                FROM "Cruise" WHERE cruise_id=? FOR NO KEY UPDATE
                """, (rs, i) -> new CruiseState(rs.getString(1), rs.getLong(2), rs.getObject(3, UUID.class),
                        rs.getBoolean(4)), id)
                .stream().findFirst().orElseThrow(() -> conflict("STAFFING_CRUISE_NOT_FOUND"));
        if (List.of("COMPLETED", "CANCELLED").contains(state.status())) {
            throw conflict("STAFFING_CRUISE_CLOSED");
        }
        return state;
    }

    private boolean isShipManager(UUID cruiseId, UUID actorId, boolean shipReview) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM "CruiseStaff" WHERE cruise_id=? AND user_id=?
                    AND status='ACTIVE' AND role_in_cruise='SHIP_MANAGER')
                """, Boolean.class, cruiseId, actorId)) || shipReview && Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM "Cruise" c JOIN "Ship" s USING (ship_id)
                    WHERE c.cruise_id=? AND s.created_by_ship_manager_id=?)
                """, Boolean.class, cruiseId, actorId));
    }

    private BusinessException conflict(String code) {
        return new BusinessException(HttpStatus.CONFLICT, code, "Yêu cầu nhân sự chưa đáp ứng điều kiện xử lý.");
    }

    @Transactional
    public Snapshot submit(UUID cruiseId, CreateRequest input, CasmsPrincipal actor) {
        authorize(actor, Role.CRUISE_MANAGER);
        if (input == null || input.scheduleRevision() == null || input.scheduleRevision() < 0) {
            throw conflict("STAFFING_SCHEDULE_REVISION_REQUIRED");
        }
        CruiseState cruise = lockCruise(cruiseId);
        if (!actor.getUserId().equals(cruise.coordinator())) {
            throw forbidden("STAFFING_PLANNER_SCOPE_FORBIDDEN");
        }
        if (cruise.revision() != input.scheduleRevision()) {
            throw conflict("STAFFING_SOURCE_REVISION_STALE");
        }
        if (!cruise.hasBudget()) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "PREPARATION_BUDGET_REQUIRED",
                    "Lưu ngân sách vận hành dự kiến trước khi gửi yêu cầu nhân sự.");
        }
        if (input.requirements() != null && !input.requirements().isEmpty()) {
            throw conflict("STAFFING_DEMAND_OWNED_BY_SHIP");
        }
        if (Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM "StaffingRequest" WHERE cruise_id=?
                 AND status NOT IN ('CANCELLED','RETURNED'))
                """, Boolean.class, cruiseId))) throw conflict("STAFFING_OPEN_REQUEST_EXISTS");
        String initialStatus = handoffs.ensureAsmForStaffing(cruiseId, actor)
                ? "PENDING_SHIP_REVIEW" : "PENDING_ASM";
        UUID requestId = jdbc.queryForObject("""
                INSERT INTO "StaffingRequest"
                    (cruise_id,schedule_revision,requested_by,status,ship_review_workflow,asm_program_workflow,workflow_version)
                VALUES (?,?,?,?,true,true,'V2') RETURNING request_id
                """, UUID.class, cruiseId, input.scheduleRevision(), actor.getUserId(), initialStatus);
        decision(requestId, 0, initialStatus, actor, null);
        notifyNext(requestId, cruiseId, initialStatus);
        return read(requestId);
    }

    private Snapshot read(UUID id) {
        var requests = jdbc.query("""
                SELECT request_id,cruise_id,status,revision,schedule_revision,ship_review_workflow,
                    asm_program_workflow,workflow_version
                FROM "StaffingRequest" WHERE request_id=?
                """, (rs, i) -> new RequestState(rs.getObject("request_id", UUID.class),
                    rs.getObject("cruise_id", UUID.class), rs.getString("status"),
                    rs.getLong("revision"), rs.getLong("schedule_revision"),
                    rs.getBoolean("ship_review_workflow"), rs.getBoolean("asm_program_workflow"),
                    rs.getString("workflow_version")), id);
        RequestState request = requests.stream().findFirst()
                .orElseThrow(() -> conflict("STAFFING_REQUEST_NOT_FOUND"));
        boolean v2 = "V2".equals(request.workflowVersion());
        List<RequirementProgress> progress = v2 ? List.of() : jdbc.query("""
                SELECT sr.service_id,sr.required_count,
                    count(DISTINCT cs.user_id) FILTER (WHERE d.duty_id IS NOT NULL
                        AND u.user_id IS NOT NULL) AS assigned, s.service_name
                FROM "StaffingRequirement" sr JOIN "StaffingRequest" req ON req.request_id=sr.request_id
                LEFT JOIN "CruiseStaff" cs ON cs.cruise_id=req.cruise_id AND cs.status='ACTIVE'
                LEFT JOIN "User" u ON u.user_id=cs.user_id AND u.status='ACTIVE'
                JOIN "Service" s ON s.service_id=sr.service_id
                LEFT JOIN "CruiseStaffDuty" d ON s.status='ACTIVE' AND d.cruise_staff_id=cs.cruise_staff_id
                    AND d.service_id=sr.service_id AND d.ended_at IS NULL
                WHERE sr.request_id=? GROUP BY sr.service_id,sr.required_count,s.service_name ORDER BY s.service_name,sr.service_id
                """, (rs,i) -> new RequirementProgress(rs.getObject(1,UUID.class),rs.getInt(2),rs.getLong(3),rs.getString(4)),id);
        String reason = jdbc.queryForList("""
                SELECT comment FROM "StaffingDecision" WHERE request_id=? AND revision=?
                    AND action IN ('RETURNED','HR_RETURN_SHIP','HR_RETURN_STAFF','CRM_RETURN_HR',
                        'OPS_RETURN_CRM','CRM_RETURN_SHIP','SHIP_RETURN_ASM')
                """, String.class, id, request.revision()).stream()
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        List<UUID> activeAreas = v2 ? jdbc.queryForList("""
                SELECT area_id FROM "StaffingActiveArea" WHERE request_id=? ORDER BY area_id
                """, UUID.class, id) : List.of();
        long assignments = v2 ? jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM "StaffingNeedAssignment" WHERE request_id=?)
                     + (SELECT count(*) FROM "StaffingServiceAssignment" WHERE request_id=?)
                """, Long.class, id, id) : 0;
        boolean shipPlanStale = v2 && Set.of("SHIP_REVIEWED", "SENT_TO_HR", "HR_SUBMITTED",
                "PLANNER_CONFIRMED", "OPS_APPROVED").contains(request.status())
                && !isCurrentShipPlan(id);
        List<NeedProgress> needs = v2 ? readNeeds(id, !shipPlanStale) : List.of();
        List<ServiceNeedProgress> serviceNeeds = v2 ? readServiceNeeds(id, !shipPlanStale) : List.of();
        var resetImpact = v2 ? new PlanResetImpact(needs.size(), assignments) : null;
        return jdbc.queryForObject("""
                SELECT cruise_name,cruise_code,departure_datetime,return_datetime,operating_budget_vnd::text
                FROM "Cruise" WHERE cruise_id=?
                """, (rs,i) -> new Snapshot(request.id(), request.cruiseId(), request.status(),
                request.revision(), request.scheduleRevision(), progress, rs.getString(1), rs.getString(2),
                rs.getObject(3, OffsetDateTime.class), rs.getObject(4, OffsetDateTime.class),
                rs.getString(5), reason, request.shipReview(), request.asmProgram(),
                request.workflowVersion(), activeAreas, needs, serviceNeeds, shipPlanStale, resetImpact), request.cruiseId());
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
        if (snapshot.v2()) {
            if (planner && coordinator(snapshot.cruiseId(), actor)
                    || ship && "PENDING_SHIP_REVIEW".equals(snapshot.status())
                        && isShipManager(snapshot.cruiseId(), actor.getUserId(), false)
                    || hr && "SENT_TO_HR".equals(snapshot.status())
                    || ops && "PLANNER_CONFIRMED".equals(snapshot.status())) {
                return snapshot;
            }
            throw forbidden("STAFFING_READ_FORBIDDEN");
        }
        if (hr && !Set.of("PENDING_ASM", "PENDING_SHIP_REVIEW").contains(snapshot.status())
                || ops && Set.of("PLANNER_CONFIRMED", "HR_APPROVED", "OPS_APPROVED").contains(snapshot.status())
                || (ship && !"PENDING_ASM".equals(snapshot.status())
                    && isShipManager(snapshot.cruiseId(), actor.getUserId(), snapshot.shipReviewWorkflow()))
                || (planner && Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM "Cruise" WHERE cruise_id=? AND preparation_coordinator_id=?)
                    """, Boolean.class, snapshot.cruiseId(), actor.getUserId())))) {
            return snapshot;
        }
        throw forbidden("STAFFING_READ_FORBIDDEN");
    }

    @Transactional(readOnly = true)
    public List<ShipManagerNeeded> cruisesWithoutShipManager(CasmsPrincipal actor) {
        authorize(actor, Role.HR_MANAGER);
        return jdbc.query("""
                SELECT c.cruise_id,c.cruise_name,c.cruise_code FROM "Cruise" c
                WHERE c.status NOT IN ('CANCELLED','COMPLETED') AND c.ship_id IS NOT NULL
                    AND NOT EXISTS (SELECT 1 FROM "CruiseStaff" crew WHERE crew.cruise_id=c.cruise_id
                        AND crew.status='ACTIVE' AND crew.role_in_cruise='SHIP_MANAGER')
                    AND (EXISTS (SELECT 1 FROM "StaffingRequest" r WHERE r.cruise_id=c.cruise_id
                        AND r.asm_program_workflow AND r.status IN ('PENDING_ASM','PENDING_SHIP_REVIEW'))
                      OR (c.status='DRAFT' AND EXISTS (
                        SELECT 1 FROM "CruiseServiceLocationRequest" r
                        JOIN "Deck" d ON d.deck_id=r.deck_id AND d.ship_id=c.ship_id
                        WHERE r.cruise_id=c.cruise_id AND r.status='PENDING')))
                ORDER BY c.departure_datetime,c.cruise_id
                """, (rs, row) -> new ShipManagerNeeded(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)));
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
                  (req.workflow_version='V2' AND (
                    (? AND req.status='SENT_TO_HR')
                    OR (? AND req.status='PENDING_SHIP_REVIEW' AND EXISTS (
                        SELECT 1 FROM "CruiseStaff" cs WHERE cs.cruise_id=req.cruise_id
                            AND cs.user_id=? AND cs.status='ACTIVE' AND cs.role_in_cruise='SHIP_MANAGER'))
                    OR (? AND req.status IN ('SHIP_REVIEWED','HR_SUBMITTED') AND c.preparation_coordinator_id=?)
                    OR (? AND req.status='PLANNER_CONFIRMED')
                  )) OR (req.workflow_version='LEGACY' AND (
                    (? AND (req.status='SENT_TO_HR'
                        OR (req.ship_review_workflow AND req.status='SHIP_CONFIRMED')
                        OR (req.ship_review_workflow AND req.status IN ('HR_APPROVED','OPS_APPROVED') AND EXISTS (
                            SELECT 1 FROM "StaffingShipInspection" inspection
                            JOIN "StaffingProgramInspection" program USING (cruise_id)
                            WHERE inspection.cruise_id=req.cruise_id
                              AND (inspection.fingerprint IS DISTINCT FROM req.ship_review_fingerprint
                                OR NOT inspection.ship_ready OR NOT inspection.pos_covered
                                OR req.asm_program_workflow AND (NOT program.asm_completed OR NOT program.services_covered
                                OR program.fingerprint IS DISTINCT FROM req.service_review_fingerprint))
                        ))))
                    OR (? AND req.status IN ('PENDING_SHIP_REVIEW','HR_SUBMITTED') AND (
                        EXISTS (SELECT 1 FROM "CruiseStaff" cs WHERE cs.cruise_id=req.cruise_id
                            AND cs.user_id=? AND cs.status='ACTIVE' AND cs.role_in_cruise='SHIP_MANAGER')
                        OR (req.ship_review_workflow AND EXISTS (SELECT 1 FROM "Ship" s
                            WHERE s.ship_id=c.ship_id AND s.created_by_ship_manager_id=?))))
                    OR (? AND NOT req.ship_review_workflow AND req.status='SHIP_CONFIRMED' AND c.preparation_coordinator_id=?)
                    OR (? AND req.status IN ('PLANNER_CONFIRMED','HR_APPROVED'))
                )))
                ORDER BY req.requested_at, req.request_id
                """, UUID.class, hr, ship, actor.getUserId(), planner, actor.getUserId(), ops,
                hr, ship, actor.getUserId(), actor.getUserId(), planner, actor.getUserId(), ops)
                .stream().map(this::read).toList();
    }

    @Transactional(readOnly = true)
    public List<Snapshot> pipeline(CasmsPrincipal actor) {
        boolean hr = allowed(actor, Role.HR_MANAGER);
        boolean ship = allowed(actor, Role.SHIP_MANAGER);
        boolean planner = allowed(actor, Role.CRUISE_MANAGER);
        boolean ops = allowed(actor, Role.OPERATIONS_MANAGER);
        if (!(hr || ship || planner || ops)) {
            throw forbidden("STAFFING_INBOX_FORBIDDEN");
        }
        return jdbc.queryForList("""
                SELECT req.request_id FROM "StaffingRequest" req JOIN "Cruise" c USING (cruise_id)
                WHERE c.status NOT IN ('COMPLETED', 'CANCELLED')
                  AND req.status <> 'CANCELLED'
                  AND (
                    ? OR ?
                    OR (? AND c.preparation_coordinator_id=?)
                    OR (? AND EXISTS (
                        SELECT 1 FROM "CruiseStaff" cs WHERE cs.cruise_id=req.cruise_id
                            AND cs.user_id=? AND cs.status='ACTIVE' AND cs.role_in_cruise='SHIP_MANAGER'))
                  )
                ORDER BY req.updated_at DESC, req.requested_at DESC, req.request_id DESC
                """, UUID.class, hr, ops, planner, actor.getUserId(), ship, actor.getUserId())
                .stream().map(this::read).toList();
    }

    private record ReviewState(String status, long revision, UUID requester, UUID hr, UUID ship, UUID planner,
                               boolean shipReview, UUID hrApprover, String shipFingerprint,
                               boolean asmProgram, String serviceFingerprint, String workflowVersion,
                               UUID shipReviewer) {
        boolean v2() { return "V2".equals(workflowVersion); }
    }

    @Transactional
    public Snapshot transition(UUID id, String action, ReviewRequest input, CasmsPrincipal actor) {
        Role role = switch (action) {
            case "HR_SUBMITTED", "HR_APPROVED", "HR_RETURN_SHIP", "HR_RETURN_STAFF" -> Role.HR_MANAGER;
            case "SHIP_CHECKED", "SHIP_CONFIRMED", "SHIP_RETURN_ASM" -> Role.SHIP_MANAGER;
            case "PLANNER_CONFIRMED", "SEND_TO_HR", "CRM_RETURN_HR", "CRM_RETURN_SHIP" -> Role.CRUISE_MANAGER;
            case "OPS_APPROVED", "OPS_RETURN_CRM" -> Role.OPERATIONS_MANAGER;
            case "RETURNED" -> Role.SHIP_MANAGER;
            default -> throw conflict("STAFFING_INVALID_ACTION");
        };
        authorize(actor, role);
        UUID cruiseId = requestCruise(id);
        CruiseState cruise = lockCruise(cruiseId);
        UUID actorId = actor.getUserId();
        if (role == Role.CRUISE_MANAGER && !actorId.equals(cruise.coordinator())) {
            throw forbidden("STAFFING_PLANNER_SCOPE_FORBIDDEN");
        }
        ReviewState row = lockRequest(id);
        if (row.v2()) return transitionV2(id, cruiseId, row, action, input, actor, role);
        if (role == Role.SHIP_MANAGER && !isShipManager(cruiseId, actorId, row.shipReview())) {
            throw forbidden("STAFFING_SHIP_SCOPE_FORBIDDEN");
        }
        checkRevision(row, input == null ? null : input.expectedRevision());
        String expected = switch (action) {
            case "SHIP_CHECKED" -> row.shipReview() ? "PENDING_SHIP_REVIEW" : null;
            case "HR_SUBMITTED" -> "SENT_TO_HR";
            case "SHIP_CONFIRMED", "RETURNED" -> "HR_SUBMITTED";
            case "PLANNER_CONFIRMED" -> row.shipReview() ? null : "SHIP_CONFIRMED";
            case "HR_APPROVED", "HR_RETURN_STAFF" -> row.shipReview() ? "SHIP_CONFIRMED" : null;
            case "HR_RETURN_SHIP" -> canReturnShipReview(cruiseId, row) ? row.status() : null;
            case "OPS_APPROVED" -> row.shipReview() ? "HR_APPROVED" : "PLANNER_CONFIRMED";
            default -> null;
        };
        if (!row.status().equals(expected)) {
            throw conflict("STAFFING_INVALID_TRANSITION");
        }
        if (role == Role.CRUISE_MANAGER && !actorId.equals(row.requester())) {
            throw forbidden("STAFFING_NOT_REQUEST_OWNER");
        }
        boolean hrFinal = Set.of("HR_APPROVED", "HR_RETURN_SHIP", "HR_RETURN_STAFF").contains(action);
        if ((!hrFinal && actorId.equals(row.hr())) || actorId.equals(row.ship()) || actorId.equals(row.planner())
                || (!"HR_RETURN_SHIP".equals(action) && actorId.equals(row.hrApprover()))
                || (role != Role.CRUISE_MANAGER && actorId.equals(row.requester()))) {
            throw conflict("STAFFING_SELF_APPROVAL");
        }
        boolean returning = Set.of("RETURNED", "HR_RETURN_SHIP", "HR_RETURN_STAFF").contains(action);
        if (returning && (input.comment() == null || input.comment().isBlank())) {
            throw conflict("STAFFING_RETURN_REASON_REQUIRED");
        }
        ShipInspection checked = null;
        if (row.shipReview() && !returning) {
            var inspection = validateShipReview(cruiseId, row.asmProgram());
            if ("SHIP_CHECKED".equals(action)) {
                checked = inspection;
            } else if (!inspection.matches(row.shipFingerprint(), row.asmProgram(), row.serviceFingerprint())) {
                throw conflict("STAFFING_SHIP_REVIEW_STALE");
            }
        }
        if (!returning && !action.equals("SHIP_CHECKED")) {
            Snapshot before = read(id);
            if (!serviceDemandCovered(before, row.asmProgram())) {
                throw conflict("STAFFING_CREW_SHORTAGE");
            }
            if (row.shipReview()) {
                validateStaffCoverage(cruiseId);
            } else if (!activitiesCovered(cruiseId)) {
                throw conflict("STAFFING_ACTIVITY_COVERAGE_REQUIRED");
            }
        }
        String nextStatus = switch (action) {
            case "SHIP_CHECKED", "HR_RETURN_STAFF" -> "SENT_TO_HR";
            case "HR_RETURN_SHIP" -> "PENDING_SHIP_REVIEW";
            case "RETURNED" -> row.shipReview() ? "SENT_TO_HR" : "RETURNED";
            default -> action;
        };
        String column = switch (action) {
            case "HR_SUBMITTED" -> "hr_submitted_by";
            case "SHIP_CONFIRMED" -> "ship_confirmed_by";
            case "PLANNER_CONFIRMED" -> "planner_confirmed_by";
            case "HR_APPROVED" -> "hr_approved_by";
            case "OPS_APPROVED" -> "ops_approved_by";
            default -> null;
        };
        if (returning) {
            jdbc.update("""
                    UPDATE "StaffingRequest" SET status=?, revision=revision+1, updated_at=now(),
                        hr_submitted_by=NULL, ship_confirmed_by=NULL, hr_approved_by=NULL,
                        ops_approved_by=NULL, ship_review_fingerprint=CASE WHEN ? THEN NULL ELSE ship_review_fingerprint END,
                        service_review_fingerprint=CASE WHEN ? THEN NULL ELSE service_review_fingerprint END
                    WHERE request_id=?
                    """, nextStatus, "HR_RETURN_SHIP".equals(action), "HR_RETURN_SHIP".equals(action), id);
        } else if ("SHIP_CHECKED".equals(action)) {
            jdbc.update("""
                    UPDATE "StaffingRequest" SET status=?, revision=revision+1, updated_at=now(),
                        ship_review_fingerprint=?, service_review_fingerprint=? WHERE request_id=?
                    """, nextStatus, checked.fingerprint(), row.asmProgram() ? checked.serviceFingerprint() : null, id);
        } else {
            jdbc.update("""
                    UPDATE "StaffingRequest" SET status=?, revision=revision+1, updated_at=now(), %s=?
                    WHERE request_id=?
                    """.formatted(column), nextStatus, actorId, id);
        }
        decision(id, input.expectedRevision() + 1, action, actor, input.comment());
        if ("HR_RETURN_SHIP".equals(action) && "OPS_APPROVED".equals(row.status())) {
            jdbc.queryForList("SELECT invalidate_staffing_preparation(?)", cruiseId);
        }
        if ("SHIP_CHECKED".equals(action)) {
            if (row.asmProgram()) handoffs.completeShipForStaffing(cruiseId, actor);
            String label = cruiseDisplayLabel(cruiseId);
            sendUserNotice(
                    cruise.coordinator(),
                    "STAFFING_SENT_TO_HR",
                    "Chuyến " + cruiseCodeOf(cruiseId) + " đã xác nhận phương án tàu",
                    "Quản lý tàu đã xác nhận phương án tàu cho chuyến " + label + " và chuyển bước bố trí nhân sự.");
        }
        return read(id);
    }

    private boolean coordinator(UUID cruiseId, CasmsPrincipal actor) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM "Cruise" WHERE cruise_id=? AND preparation_coordinator_id=?
                    AND status NOT IN ('COMPLETED','CANCELLED'))
                """, Boolean.class, cruiseId, actor.getUserId()));
    }

    private List<NeedProgress> readNeeds(UUID requestId, boolean includeAssignments) {
        return jdbc.query("""
                SELECT n.need_id, n.area_id, o.display_name, n.position_code, n.required_count,
                    n.plan_revision, ARRAY(SELECT a.user_id FROM "StaffingV2QualifiedAssignment" a
                        WHERE ? AND a.need_id=n.need_id ORDER BY a.user_id)
                FROM "StaffingNeed" n JOIN "ShipModelObject" o ON o.ship_model_object_id=n.area_id
                WHERE n.request_id=? ORDER BY o.display_name,n.area_id,n.position_code
                """, (rs, i) -> new NeedProgress(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getString(3), rs.getString(4), rs.getInt(5), rs.getLong(6),
                Arrays.asList((UUID[]) rs.getArray(7).getArray())), includeAssignments, requestId);
    }

    private List<ServiceNeedProgress> readServiceNeeds(UUID requestId, boolean includeAssignments) {
        return jdbc.query("""
                SELECT sp.service_id, s.service_name, sp.area_id, o.display_name AS area_name,
                    a.terminal_id, t.terminal_name,
                    GREATEST(1, COALESCE((
                        SELECT MAX(w.required_staff_count) FROM "ServiceSchedule" w
                        WHERE w.cruise_id=req.cruise_id AND w.service_id=sp.service_id
                          AND w.cruise_stop_id IS NULL AND w.status<>'CANCELLED'
                    ), s.default_required_staff, 1)) AS required_count,
                    ARRAY(
                        SELECT cs.user_id FROM "StaffingServiceAssignment" sa
                        JOIN "CruiseStaff" cs ON cs.cruise_staff_id=sa.cruise_staff_id
                            AND cs.cruise_id=req.cruise_id AND cs.status='ACTIVE'
                            AND cs.role_in_cruise='ONBOARD_STAFF'
                        JOIN "User" u ON u.user_id=cs.user_id AND u.status='ACTIVE'
                        WHERE ? AND sa.request_id=sp.request_id AND sa.service_id=sp.service_id
                          AND EXISTS (SELECT 1 FROM "UserRole" ur WHERE ur.user_id=u.user_id AND ur.role='ONBOARD_STAFF')
                          AND NOT EXISTS (SELECT 1 FROM "UserRole" ur WHERE ur.user_id=u.user_id AND ur.role='ADMIN')
                        ORDER BY cs.user_id
                    )
                FROM "StaffingServicePlacement" sp
                JOIN "StaffingRequest" req ON req.request_id=sp.request_id
                JOIN "Service" s ON s.service_id=sp.service_id
                JOIN "StaffingActiveArea" a ON a.request_id=sp.request_id AND a.area_id=sp.area_id
                JOIN "ShipModelObject" o ON o.ship_model_object_id=sp.area_id
                LEFT JOIN "PosTerminal" t ON t.terminal_id=a.terminal_id
                WHERE sp.request_id=?
                ORDER BY s.service_name, sp.service_id
                """, (rs, i) -> new ServiceNeedProgress(
                        rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getObject(3, UUID.class), rs.getString(4),
                        rs.getObject(5, UUID.class), rs.getString(6),
                        rs.getInt(7), Arrays.asList((UUID[]) rs.getArray(8).getArray())),
                includeAssignments, requestId);
    }

    @Transactional
    public ReviewDetails saveShipPlan(UUID id, ShipPlanInput input, CasmsPrincipal actor) {
        authorize(actor, Role.SHIP_MANAGER);
        UUID cruise = requestCruise(id);
        lockCruise(cruise);
        ReviewState row = lockRequest(id);
        requireShipEditor(cruise, row, actor);
        checkRevision(row, input.expectedRevision());
        if (input.activeAreaIds() == null || input.needs() == null) {
            throw conflict("STAFFING_SHIP_PLAN_REQUIRED");
        }
        Set<UUID> areas = new HashSet<>(input.activeAreaIds());
        if (areas.contains(null) || areas.size() != input.activeAreaIds().size()
                || !jdbc.queryForList("SELECT area_id FROM \"StaffingV2EligibleArea\" WHERE cruise_id=?",
                    UUID.class, cruise).containsAll(areas)) {
            throw conflict("STAFFING_AREA_OUT_OF_SCOPE");
        }
        Map<UUID, Set<String>> positions = new LinkedHashMap<>();
        for (NeedInput need : input.needs()) {
            if (need == null || !areas.contains(need.areaId()) || need.positionCode() == null
                    || !AREA_POSITIONS.contains(need.positionCode()) || need.requiredCount() < 1
                    || !positions.computeIfAbsent(need.areaId(), ignored -> new HashSet<>()).add(need.positionCode())) {
                throw conflict("STAFFING_NEED_INVALID");
            }
        }
        for (UUID previous : jdbc.queryForList("SELECT area_id FROM \"StaffingActiveArea\" WHERE request_id=?",
                UUID.class, id)) {
            if (!areas.contains(previous)) jdbc.update("""
                    DELETE FROM "StaffingActiveArea" WHERE request_id=? AND area_id=?
                    """, id, previous);
        }
        for (UUID area : areas) jdbc.update("""
                INSERT INTO "StaffingActiveArea" (request_id,area_id) VALUES (?,?) ON CONFLICT DO NOTHING
                """, id, area);
        jdbc.update("DELETE FROM \"StaffingNeed\" WHERE request_id=?", id);
        long revision = row.revision() + 1;
        for (NeedInput need : input.needs()) jdbc.update("""
                INSERT INTO "StaffingNeed" (request_id,area_id,position_code,required_count,plan_revision)
                VALUES (?,?,?,?,?)
                """, id, need.areaId(), need.positionCode(), need.requiredCount(), revision);
        bump(id);
        decision(id, revision, "SHIP_PLAN_SAVED", actor, "Areas " + areas + " / Needs " + input.needs());
        return reviewDetails(id, actor);
    }

    private void requireShipEditor(UUID cruise, ReviewState row, CasmsPrincipal actor) {
        if (!isShipManager(cruise, actor.getUserId(), false) || actor.getUserId().equals(row.requester())) {
            throw forbidden("STAFFING_SHIP_SCOPE_FORBIDDEN");
        }
        if (!row.v2() || !"PENDING_SHIP_REVIEW".equals(row.status())) {
            throw conflict("STAFFING_INVALID_TRANSITION");
        }
    }

    @Transactional
    public Snapshot assignNeed(UUID id, UUID needId, UUID userId, NeedAssignmentInput input, CasmsPrincipal actor) {
        authorize(actor, Role.HR_MANAGER);
        UUID cruise = requestCruise(id);
        lockCruise(cruise);
        ReviewState row = lockRequest(id);
        checkRevision(row, input.expectedRevision());
        if (!row.v2() || !"SENT_TO_HR".equals(row.status())) throw conflict("STAFFING_INVALID_TRANSITION");
        requireIndependentReviewer(row, actor, Role.HR_MANAGER);
        requireCurrentShipPlan(id, row);
        if (input.assigned() == null) throw conflict("STAFFING_NEED_ASSIGNMENT_INVALID");
        if (input.assigned()) {
            String position = jdbc.queryForList("""
                    SELECT position_code FROM "StaffingNeed" WHERE request_id=? AND need_id=?
                    """, String.class, id, needId).stream().findFirst()
                    .orElseThrow(() -> conflict("STAFFING_NEED_ASSIGNMENT_INVALID"));
            if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM "CruiseStaff" WHERE cruise_id=? AND user_id=? AND status='ACTIVE')
                    """, Boolean.class, cruise, userId))) {
                var membership = new CruiseStaffRequest();
                membership.setUserId(userId);
                membership.setRoleInCruise(position);
                cruiseStaff.assignStaff(cruise, membership, actor);
            }
            int changed = jdbc.update("""
                    INSERT INTO "StaffingNeedAssignment" (request_id,need_id,cruise_staff_id)
                    SELECT n.request_id,n.need_id,cs.cruise_staff_id
                    FROM "StaffingNeed" n JOIN "StaffingRequest" req USING(request_id)
                    JOIN "CruiseStaff" cs ON cs.cruise_id=req.cruise_id AND cs.user_id=?
                        AND cs.status='ACTIVE' AND cs.role_in_cruise=n.position_code
                    JOIN "User" u ON u.user_id=cs.user_id AND u.status='ACTIVE'
                    WHERE n.request_id=? AND n.need_id=?
                      AND EXISTS (SELECT 1 FROM "UserRole" ur WHERE ur.user_id=u.user_id AND ur.role=n.position_code)
                      AND NOT EXISTS (SELECT 1 FROM "UserRole" ur WHERE ur.user_id=u.user_id AND ur.role='ADMIN')
                      AND NOT EXISTS (SELECT 1 FROM "StaffingServiceAssignment" sa WHERE sa.request_id=n.request_id AND sa.cruise_staff_id=cs.cruise_staff_id)
                      AND n.required_count > (SELECT count(*) FROM "StaffingV2QualifiedAssignment" a WHERE a.need_id=n.need_id)
                    ON CONFLICT DO NOTHING
                    """, userId, id, needId);
            if (changed != 1) throw conflict("STAFFING_NEED_ASSIGNMENT_INVALID");
            syncAreaAssignmentForNeed(cruise, id, needId, userId, true, actor);
        } else {
            if (jdbc.update("""
                    DELETE FROM "StaffingNeedAssignment" a USING "CruiseStaff" cs
                    WHERE a.request_id=? AND a.need_id=? AND cs.cruise_staff_id=a.cruise_staff_id AND cs.user_id=?
                    """, id, needId, userId) != 1) {
                throw conflict("STAFFING_NEED_ASSIGNMENT_INVALID");
            }
            syncAreaAssignmentForNeed(cruise, id, needId, userId, false, actor);
        }
        bump(id);
        decision(id, row.revision() + 1, input.assigned() ? "NEED_ASSIGNED" : "NEED_UNASSIGNED",
                actor, "Need " + needId + " / User " + userId);
        return read(id);
    }

    @Transactional
    public Snapshot assignServiceNeed(UUID id, UUID serviceId, UUID userId, NeedAssignmentInput input, CasmsPrincipal actor) {
        authorize(actor, Role.HR_MANAGER);
        UUID cruise = requestCruise(id);
        lockCruise(cruise);
        ReviewState row = lockRequest(id);
        checkRevision(row, input.expectedRevision());
        if (!row.v2() || !"SENT_TO_HR".equals(row.status())) throw conflict("STAFFING_INVALID_TRANSITION");
        requireIndependentReviewer(row, actor, Role.HR_MANAGER);
        requireCurrentShipPlan(id, row);
        if (input.assigned() == null) throw conflict("STAFFING_NEED_ASSIGNMENT_INVALID");

        UUID areaId = jdbc.queryForList("""
                SELECT sp.area_id FROM "StaffingServicePlacement" sp
                WHERE sp.request_id=? AND sp.service_id=?
                """, UUID.class, id, serviceId).stream().findFirst()
                .orElseThrow(() -> conflict("STAFFING_NEED_ASSIGNMENT_INVALID"));

        if (input.assigned()) {
            if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM "CruiseStaff" WHERE cruise_id=? AND user_id=? AND status='ACTIVE')
                    """, Boolean.class, cruise, userId))) {
                var membership = new CruiseStaffRequest();
                membership.setUserId(userId);
                membership.setRoleInCruise("ONBOARD_STAFF");
                cruiseStaff.assignStaff(cruise, membership, actor);
            }
            int changed = jdbc.update("""
                    INSERT INTO "StaffingServiceAssignment" (request_id, service_id, cruise_staff_id)
                    SELECT sp.request_id, sp.service_id, cs.cruise_staff_id
                    FROM "StaffingServicePlacement" sp
                    JOIN "StaffingRequest" req USING (request_id)
                    JOIN "Service" s USING (service_id)
                    JOIN "CruiseStaff" cs ON cs.cruise_id=req.cruise_id AND cs.user_id=?
                        AND cs.status='ACTIVE' AND cs.role_in_cruise='ONBOARD_STAFF'
                    JOIN "User" u ON u.user_id=cs.user_id AND u.status='ACTIVE'
                    WHERE sp.request_id=? AND sp.service_id=?
                      AND EXISTS (SELECT 1 FROM "UserRole" ur WHERE ur.user_id=u.user_id AND ur.role='ONBOARD_STAFF')
                      AND NOT EXISTS (SELECT 1 FROM "UserRole" ur WHERE ur.user_id=u.user_id AND ur.role='ADMIN')
                      AND NOT EXISTS (SELECT 1 FROM "StaffingNeedAssignment" na WHERE na.request_id=sp.request_id AND na.cruise_staff_id=cs.cruise_staff_id)
                      AND GREATEST(1, COALESCE((
                            SELECT MAX(w.required_staff_count) FROM "ServiceSchedule" w
                            WHERE w.cruise_id=req.cruise_id AND w.service_id=sp.service_id
                              AND w.cruise_stop_id IS NULL AND w.status<>'CANCELLED'
                          ), s.default_required_staff, 1))
                        > (SELECT count(*) FROM "StaffingServiceAssignment" sa
                           JOIN "CruiseStaff" cs2 ON cs2.cruise_staff_id=sa.cruise_staff_id AND cs2.status='ACTIVE'
                           JOIN "User" u2 ON u2.user_id=cs2.user_id AND u2.status='ACTIVE'
                           WHERE sa.request_id=sp.request_id AND sa.service_id=sp.service_id)
                    ON CONFLICT DO NOTHING
                    """, userId, id, serviceId);
            if (changed != 1) throw conflict("STAFFING_NEED_ASSIGNMENT_INVALID");
            syncServiceAreaAndDuty(cruise, areaId, serviceId, userId, true, actor);
        } else {
            if (jdbc.update("""
                    DELETE FROM "StaffingServiceAssignment" sa USING "CruiseStaff" cs
                    WHERE sa.request_id=? AND sa.service_id=? AND cs.cruise_staff_id=sa.cruise_staff_id AND cs.user_id=?
                    """, id, serviceId, userId) != 1) {
                throw conflict("STAFFING_NEED_ASSIGNMENT_INVALID");
            }
            syncServiceAreaAndDuty(cruise, areaId, serviceId, userId, false, actor);
        }
        bump(id);
        decision(id, row.revision() + 1, input.assigned() ? "SERVICE_STAFF_ASSIGNED" : "SERVICE_STAFF_UNASSIGNED",
                actor, "Service " + serviceId + " / Area " + areaId + " / User " + userId);
        return read(id);
    }

    private void syncAreaAssignmentForNeed(UUID cruiseId, UUID requestId, UUID needId, UUID userId, boolean enabled, CasmsPrincipal actor) {
        UUID areaId = jdbc.queryForList("""
                SELECT area_id FROM "StaffingNeed" WHERE request_id=? AND need_id=?
                """, UUID.class, requestId, needId).stream().findFirst().orElse(null);
        if (areaId == null) return;
        syncAreaAssignment(cruiseId, areaId, userId, enabled, actor);
    }

    private void syncServiceAreaAndDuty(UUID cruiseId, UUID areaId, UUID serviceId, UUID userId, boolean enabled, CasmsPrincipal actor) {
        UUID assignmentId = syncAreaAssignment(cruiseId, areaId, userId, enabled, actor);
        UUID csId = jdbc.queryForList("""
                SELECT cruise_staff_id FROM "CruiseStaff"
                WHERE cruise_id=? AND user_id=? AND status='ACTIVE'
                ORDER BY assigned_at DESC LIMIT 1
                """, UUID.class, cruiseId, userId).stream().findFirst().orElse(null);
        if (csId == null) return;
        if (enabled) {
            jdbc.update("""
                    UPDATE "CruiseStaffDuty" SET ended_at=now(), ended_by=?
                    WHERE cruise_staff_id=? AND service_id IS NOT NULL AND service_id<>? AND ended_at IS NULL
                    """, actor.getUserId(), csId, serviceId);
            if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM "CruiseStaffDuty"
                        WHERE cruise_staff_id=? AND service_id=? AND ended_at IS NULL)
                    """, Boolean.class, csId, serviceId))) {
                jdbc.update("""
                        INSERT INTO "CruiseStaffDuty" (cruise_staff_id, service_id, assigned_by)
                        VALUES (?, ?, ?)
                        """, csId, serviceId, actor.getUserId());
            }
            if (assignmentId != null) {
                jdbc.update("""
                        UPDATE "StaffServiceAssignment" SET enabled=false
                        WHERE assignment_id=? AND service_id<>? AND enabled
                        """, assignmentId, serviceId);
                jdbc.update("""
                        INSERT INTO "StaffServiceAssignment" (assignment_id, service_id, permissions, inherit_permissions, enabled)
                        VALUES (?, ?, '{}', true, true)
                        ON CONFLICT (assignment_id, service_id) DO UPDATE SET
                            inherit_permissions=true, enabled=true
                        """, assignmentId, serviceId);
            }
        } else {
            if (assignmentId != null) {
                jdbc.update("""
                        UPDATE "StaffServiceAssignment" SET enabled=false
                        WHERE assignment_id=? AND service_id=? AND enabled
                        """, assignmentId, serviceId);
            }
            jdbc.update("""
                    UPDATE "CruiseStaffDuty" SET ended_at=now(), ended_by=?
                    WHERE cruise_staff_id=? AND service_id=? AND ended_at IS NULL
                    """, actor.getUserId(), csId, serviceId);
        }
    }

    private UUID syncAreaAssignment(UUID cruiseId, UUID areaId, UUID userId, boolean enabled, CasmsPrincipal actor) {
        UUID csId = jdbc.queryForList("""
                SELECT cruise_staff_id FROM "CruiseStaff"
                WHERE cruise_id=? AND user_id=? AND status='ACTIVE'
                ORDER BY assigned_at DESC LIMIT 1
                """, UUID.class, cruiseId, userId).stream().findFirst().orElse(null);
        if (csId == null) return null;
        if (enabled) {
            jdbc.update("""
                    INSERT INTO "AreaStaffAssignment"
                        (assignment_id, area_object_id, cruise_staff_id, enabled, starts_at, ends_at, assigned_by_user_id, assigned_at, updated_at)
                    SELECT gen_random_uuid(), ?, ?, true, c.departure_datetime, c.return_datetime, ?, now(), now()
                    FROM "Cruise" c WHERE c.cruise_id=?
                    ON CONFLICT (area_object_id, cruise_staff_id) DO UPDATE SET
                        enabled=true, starts_at=excluded.starts_at, ends_at=excluded.ends_at,
                        assigned_by_user_id=excluded.assigned_by_user_id, updated_at=now()
                    """, areaId, csId, actor.getUserId(), cruiseId);
            return jdbc.queryForList("""
                    SELECT assignment_id FROM "AreaStaffAssignment"
                    WHERE area_object_id=? AND cruise_staff_id=?
                    """, UUID.class, areaId, csId).stream().findFirst().orElse(null);
        } else {
            UUID assignmentId = jdbc.queryForList("""
                    SELECT assignment_id FROM "AreaStaffAssignment"
                    WHERE area_object_id=? AND cruise_staff_id=?
                    """, UUID.class, areaId, csId).stream().findFirst().orElse(null);
            jdbc.update("""
                    UPDATE "AreaStaffAssignment" SET enabled=false, updated_at=now()
                    WHERE area_object_id=? AND cruise_staff_id=? AND enabled
                    """, areaId, csId);
            if (assignmentId != null) {
                jdbc.update("""
                        UPDATE "PosSession" SET revoked_at=now()
                        WHERE area_staff_assignment_id=? AND revoked_at IS NULL
                        """, assignmentId);
            }
            return assignmentId;
        }
    }

    private void clearSyncedAssignmentsOnReturn(UUID requestId, CasmsPrincipal actor) {
        jdbc.update("""
                UPDATE "StaffServiceAssignment" ssa SET enabled=false
                FROM "AreaStaffAssignment" asa
                JOIN "StaffingServiceAssignment" sa ON sa.cruise_staff_id=asa.cruise_staff_id
                JOIN "StaffingServicePlacement" sp ON sp.request_id=sa.request_id AND sp.service_id=sa.service_id
                    AND sp.area_id=asa.area_object_id
                WHERE ssa.assignment_id=asa.assignment_id AND ssa.service_id=sa.service_id
                  AND sa.request_id=? AND ssa.enabled
                """, requestId);
        jdbc.update("""
                UPDATE "CruiseStaffDuty" d SET ended_at=now(), ended_by=?
                FROM "StaffingServiceAssignment" sa
                WHERE d.cruise_staff_id=sa.cruise_staff_id AND d.service_id=sa.service_id
                  AND sa.request_id=? AND d.ended_at IS NULL
                """, actor.getUserId(), requestId);
        jdbc.update("""
                UPDATE "PosSession" ps SET revoked_at=now()
                FROM "AreaStaffAssignment" asa
                WHERE ps.area_staff_assignment_id=asa.assignment_id AND ps.revoked_at IS NULL
                  AND (EXISTS (
                        SELECT 1 FROM "StaffingServiceAssignment" sa
                        JOIN "StaffingServicePlacement" sp ON sp.request_id=sa.request_id AND sp.service_id=sa.service_id
                        WHERE sa.request_id=? AND sa.cruise_staff_id=asa.cruise_staff_id AND sp.area_id=asa.area_object_id
                      ) OR EXISTS (
                        SELECT 1 FROM "StaffingNeedAssignment" na
                        JOIN "StaffingNeed" n ON n.request_id=na.request_id AND n.need_id=na.need_id
                        WHERE na.request_id=? AND na.cruise_staff_id=asa.cruise_staff_id AND n.area_id=asa.area_object_id
                      ))
                """, requestId, requestId);
        jdbc.update("""
                UPDATE "AreaStaffAssignment" asa SET enabled=false, updated_at=now()
                WHERE asa.enabled
                  AND (EXISTS (
                        SELECT 1 FROM "StaffingServiceAssignment" sa
                        JOIN "StaffingServicePlacement" sp ON sp.request_id=sa.request_id AND sp.service_id=sa.service_id
                        WHERE sa.request_id=? AND sa.cruise_staff_id=asa.cruise_staff_id AND sp.area_id=asa.area_object_id
                      ) OR EXISTS (
                        SELECT 1 FROM "StaffingNeedAssignment" na
                        JOIN "StaffingNeed" n ON n.request_id=na.request_id AND n.need_id=na.need_id
                        WHERE na.request_id=? AND na.cruise_staff_id=asa.cruise_staff_id AND n.area_id=asa.area_object_id
                      ))
                """, requestId, requestId);
    }

    private void bump(UUID id) {
        jdbc.update("UPDATE \"StaffingRequest\" SET revision=revision+1,updated_at=now() WHERE request_id=?", id);
    }

    private String validShipPlanFingerprint(UUID id) {
        return jdbc.queryForList("""
                SELECT md5(jsonb_build_array(c.ship_id, inspection.ship_model_id, c.departure_datetime,
                    c.return_datetime, (SELECT w.revision FROM "CruiseWorkHandoff" w
                        WHERE w.cruise_id=c.cruise_id AND w.work_group='ASM'),
                    ARRAY(SELECT jsonb_build_array(w.service_schedule_id,w.service_id,w.cruise_stop_id,
                        w.starts_at,w.ends_at,w.location,w.supplier_id,w.status,w.required_staff_count)
                        FROM "ServiceSchedule" w WHERE w.cruise_id=c.cruise_id AND w.status<>'CANCELLED'
                        ORDER BY w.service_schedule_id),
                    ARRAY(SELECT jsonb_build_array(w.kind,w.window_id,w.starts_at,w.ends_at,
                        COALESCE(oa.required_staff_count, se.required_staff_count, 1))
                        FROM "CruiseStaffWorkWindow" w
                        LEFT JOIN "OnboardActivity" oa ON w.kind='ONBOARD' AND oa.onboard_activity_id=w.window_id
                        LEFT JOIN "ShoreExcursion" se ON w.kind='SHORE' AND se.shore_excursion_id=w.window_id
                        WHERE w.cruise_id=c.cruise_id
                            AND w.kind IN ('ONBOARD','SHORE') ORDER BY w.kind,w.window_id),
                    ARRAY(SELECT jsonb_build_array(a.area_id,t.terminal_id,t.permissions)
                        FROM "StaffingActiveArea" a JOIN "PosTerminal" t USING (terminal_id)
                        WHERE a.request_id=req.request_id ORDER BY a.area_id),
                    ARRAY(SELECT jsonb_build_array(p.service_id,p.area_id,p.terminal_id,p.permissions_snapshot)
                        FROM "StaffingV2ServicePlacement" p WHERE p.request_id=req.request_id ORDER BY p.service_id),
                    ARRAY(SELECT jsonb_build_array(n.area_id,n.position_code,n.required_count,n.plan_revision)
                        FROM "StaffingNeed" n WHERE n.request_id=req.request_id ORDER BY n.area_id,n.position_code)
                )::text)
                FROM "StaffingRequest" req JOIN "Cruise" c USING(cruise_id)
                JOIN "StaffingShipInspection" inspection USING(cruise_id)
                JOIN "StaffingProgramInspection" program USING(cruise_id)
                WHERE req.request_id=? AND inspection.ship_ready AND inspection.ship_model_id IS NOT NULL
                  AND program.asm_completed AND NOT EXISTS (
                    SELECT 1 FROM "OnboardActivity" activity
                    WHERE activity.cruise_id=c.cruise_id AND activity.status<>'CANCELLED'
                      AND NOT EXISTS (
                        SELECT 1 FROM "ShipModelObject" o JOIN "Deck" d USING (deck_id)
                        WHERE o.ship_model_object_id=activity.area_object_id AND d.ship_id=c.ship_id
                          AND o.ship_model_id=inspection.ship_model_id AND o.object_type='FACILITY'
                          AND o.area_usage='ACTIVITY_SPACE' AND o.cabin_id IS NULL
                          AND NOT EXISTS (SELECT 1 FROM "AreaServiceAssignment" ass
                              WHERE ass.area_object_id=o.ship_model_object_id AND ass.enabled)))
                  AND NOT EXISTS (
                    SELECT 1 FROM "StaffingActiveArea" a WHERE a.request_id=req.request_id AND (
                        NOT EXISTS (SELECT 1 FROM "StaffingAreaPos" p JOIN "PosTerminal" t USING (terminal_id)
                            WHERE 'SERVICE_READ'=ANY(t.permissions) AND p.cruise_id=c.cruise_id AND p.area_id=a.area_id AND p.terminal_id=a.terminal_id)
                        OR NOT EXISTS (SELECT 1 FROM "StaffingV2EligibleArea" e
                            WHERE e.cruise_id=c.cruise_id AND e.area_id=a.area_id)
                        OR (NOT EXISTS (SELECT 1 FROM "StaffingNeed" n
                                WHERE n.request_id=req.request_id AND n.area_id=a.area_id)
                            AND NOT EXISTS (SELECT 1 FROM "StaffingServicePlacement" sp
                                WHERE sp.request_id=req.request_id AND sp.area_id=a.area_id))))
                  AND NOT EXISTS (
                    SELECT 1 FROM "StaffingPlannedOnboardService" s WHERE s.cruise_id=c.cruise_id AND NOT EXISTS (
                        SELECT 1 FROM "StaffingV2ServicePlacement" p
                        WHERE p.request_id=req.request_id AND p.service_id=s.service_id))
                """, String.class, id).stream().findFirst().orElse(null);
    }

    private String legacyV2ShipPlanFingerprint(UUID id) {
        return jdbc.queryForList("""
                SELECT md5(jsonb_build_array(c.ship_id, inspection.ship_model_id, c.departure_datetime,
                    c.return_datetime, (SELECT w.revision FROM "CruiseWorkHandoff" w
                        WHERE w.cruise_id=c.cruise_id AND w.work_group='ASM'),
                    ARRAY(SELECT jsonb_build_array(w.service_schedule_id,w.service_id,w.cruise_stop_id,
                        w.starts_at,w.ends_at,w.location,w.supplier_id,w.status)
                        FROM "ServiceSchedule" w WHERE w.cruise_id=c.cruise_id AND w.status<>'CANCELLED'
                        ORDER BY w.service_schedule_id),
                    ARRAY(SELECT jsonb_build_array(w.kind,w.window_id,w.starts_at,w.ends_at)
                        FROM "CruiseStaffWorkWindow" w WHERE w.cruise_id=c.cruise_id
                            AND w.kind IN ('ONBOARD','SHORE') ORDER BY w.kind,w.window_id),
                    ARRAY(SELECT jsonb_build_array(a.area_id,t.terminal_id,t.permissions)
                        FROM "StaffingActiveArea" a JOIN "PosTerminal" t USING (terminal_id)
                        WHERE a.request_id=req.request_id ORDER BY a.area_id),
                    ARRAY(SELECT jsonb_build_array(p.service_id,p.area_id,p.terminal_id,p.permissions_snapshot)
                        FROM "StaffingV2ServicePlacement" p WHERE p.request_id=req.request_id ORDER BY p.service_id),
                    ARRAY(SELECT jsonb_build_array(n.area_id,n.position_code,n.required_count,n.plan_revision)
                        FROM "StaffingNeed" n WHERE n.request_id=req.request_id ORDER BY n.area_id,n.position_code)
                )::text)
                FROM "StaffingRequest" req JOIN "Cruise" c USING(cruise_id)
                JOIN "StaffingShipInspection" inspection USING(cruise_id)
                JOIN "StaffingProgramInspection" program USING(cruise_id)
                WHERE req.request_id=? AND req.status='OPS_APPROVED'
                  AND inspection.ship_ready AND inspection.ship_model_id IS NOT NULL
                  AND program.asm_completed
                """, String.class, id).stream().findFirst().orElse(null);
    }

    private void requireCurrentShipPlan(UUID id, ReviewState row) {
        String fingerprint = validShipPlanFingerprint(id);
        if (fingerprint == null || !fingerprint.equals(row.shipFingerprint()) || !currentShipReviewer(id)) {
            throw conflict("STAFFING_SHIP_REVIEW_STALE");
        }
    }

    private boolean isCurrentShipPlan(UUID id) {
        if (!currentShipReviewer(id)) return false;
        String stored = jdbc.queryForList("""
                SELECT ship_review_fingerprint FROM "StaffingRequest" WHERE request_id=?
                """, String.class, id).stream().findFirst().orElse(null);
        if (stored == null) return false;
        String current = validShipPlanFingerprint(id);
        if (current != null && current.equals(stored)) return true;
        String legacy = legacyV2ShipPlanFingerprint(id);
        return legacy != null && legacy.equals(stored);
    }

    private boolean currentShipReviewer(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM "StaffingRequest" r
                    JOIN "CruiseStaff" cs ON cs.cruise_id=r.cruise_id AND cs.user_id=r.ship_reviewed_by
                        AND cs.status='ACTIVE' AND cs.role_in_cruise='SHIP_MANAGER'
                    JOIN "User" u ON u.user_id=cs.user_id AND u.status='ACTIVE'
                    JOIN "UserRole" ur ON ur.user_id=u.user_id AND ur.role='SHIP_MANAGER'
                    WHERE r.request_id=?)
                """, Boolean.class, id));
    }

    private boolean needsCovered(UUID id, UUID cruise) {
        return readServiceNeeds(id, true).stream().allMatch(need -> need.assignedUserIds().size() >= need.requiredCount())
                && readNeeds(id, true).stream().allMatch(need -> need.assignedUserIds().size() >= need.requiredCount())
                && activitiesCovered(cruise);
    }

    private void requireIndependentReviewer(ReviewState row, CasmsPrincipal actor, Role role) {
        UUID user = actor.getUserId();
        if (role != Role.CRUISE_MANAGER && user.equals(row.requester())
                || role != Role.SHIP_MANAGER && user.equals(row.shipReviewer())
                || role != Role.HR_MANAGER && user.equals(row.hr())) {
            throw conflict("STAFFING_SELF_APPROVAL");
        }
    }

    private Snapshot transitionV2(UUID id, UUID cruise, ReviewState row, String action, ReviewRequest input,
                                  CasmsPrincipal actor, Role role) {
        checkRevision(row, input == null ? null : input.expectedRevision());
        if (role == Role.SHIP_MANAGER) requireShipEditor(cruise, row, actor);
        requireIndependentReviewer(row, actor, role);
        Set<String> from = switch (action) {
            case "SHIP_CHECKED", "SHIP_RETURN_ASM" -> Set.of("PENDING_SHIP_REVIEW");
            case "SEND_TO_HR" -> Set.of("SHIP_REVIEWED");
            case "CRM_RETURN_SHIP" -> Set.of("SHIP_REVIEWED", "SENT_TO_HR", "HR_SUBMITTED", "PLANNER_CONFIRMED", "OPS_APPROVED");
            case "HR_SUBMITTED", "HR_RETURN_SHIP" -> Set.of("SENT_TO_HR");
            case "PLANNER_CONFIRMED", "CRM_RETURN_HR" -> Set.of("HR_SUBMITTED");
            case "OPS_APPROVED", "OPS_RETURN_CRM" -> Set.of("PLANNER_CONFIRMED");
            default -> Set.of();
        };
        if (!from.contains(row.status())) throw conflict("STAFFING_INVALID_TRANSITION");
        if ("CRM_RETURN_SHIP".equals(action) && "PLANNER_CONFIRMED".equals(row.status()) && isCurrentShipPlan(id)) {
            throw conflict("STAFFING_INVALID_TRANSITION");
        }
        boolean returned = action.contains("_RETURN_");
        if (returned && (input.comment() == null || input.comment().isBlank())) {
            throw conflict("STAFFING_RETURN_REASON_REQUIRED");
        }
        String fingerprint = row.shipFingerprint();
        if ("SHIP_CHECKED".equals(action)) {
            fingerprint = validShipPlanFingerprint(id);
            if (fingerprint == null) throw conflict("STAFFING_SHIP_PLAN_INCOMPLETE");
        } else if (!returned) {
            requireCurrentShipPlan(id, row);
            if (!"SEND_TO_HR".equals(action) && !needsCovered(id, cruise)) {
                throw conflict("STAFFING_CREW_SHORTAGE");
            }
        }
        String next = switch (action) {
            case "SHIP_CHECKED", "SEND_TO_HR", "CRM_RETURN_HR" -> "SENT_TO_HR";
            case "HR_RETURN_SHIP", "CRM_RETURN_SHIP" -> "PENDING_SHIP_REVIEW";
            case "SHIP_RETURN_ASM" -> "PENDING_ASM";
            case "OPS_RETURN_CRM" -> "HR_SUBMITTED";
            default -> action;
        };
        boolean clearingAssignments = Set.of("PENDING_ASM", "PENDING_SHIP_REVIEW").contains(next);
        if (clearingAssignments) {
            fingerprint = null;
        }
        if ("SHIP_RETURN_ASM".equals(action)) {
            handoffs.returnAsmFromStaffing(cruise, actor, input.comment());
        }
        if ("SHIP_CHECKED".equals(action)) {
            handoffs.completeShipForStaffing(cruise, actor);
        }
        jdbc.update("""
                UPDATE "StaffingRequest" SET status=?, revision=revision+1, updated_at=now(),
                    ship_review_fingerprint=?,
                    ship_reviewed_by=CASE WHEN ?='SHIP_CHECKED' THEN ? WHEN ? THEN NULL ELSE ship_reviewed_by END,
                    dispatched_by=CASE WHEN ? IN ('SEND_TO_HR','SHIP_CHECKED') THEN ? WHEN ? THEN NULL ELSE dispatched_by END,
                    hr_submitted_by=CASE WHEN ?='HR_SUBMITTED' THEN ? WHEN ? IN ('SENT_TO_HR','PENDING_ASM','PENDING_SHIP_REVIEW') THEN NULL ELSE hr_submitted_by END,
                    planner_confirmed_by=CASE WHEN ?='PLANNER_CONFIRMED' THEN ?
                        WHEN ?<>'OPS_APPROVED' THEN NULL ELSE planner_confirmed_by END,
                    ops_approved_by=CASE WHEN ?='OPS_APPROVED' THEN ? ELSE NULL END,
                    staffing_approved_revision=CASE WHEN ?='OPS_APPROVED' THEN revision+1 ELSE NULL END
                WHERE request_id=?
                """, next, fingerprint, action, actor.getUserId(), fingerprint == null,
                action, actor.getUserId(), fingerprint == null,
                action, actor.getUserId(), next, action, actor.getUserId(), next,
                action, actor.getUserId(), action, id);
        if (clearingAssignments) {
            // Run after status moves to PENDING_ASM / PENDING_SHIP_REVIEW so the V134
            // AreaStaffAssignment trigger does not double-bump revision.
            clearSyncedAssignmentsOnReturn(id, actor);
            jdbc.update("DELETE FROM \"StaffingNeedAssignment\" WHERE request_id=?", id);
            jdbc.update("DELETE FROM \"StaffingServiceAssignment\" WHERE request_id=?", id);
        }
        decision(id, row.revision() + 1, action, actor, input.comment());
        if ("OPS_APPROVED".equals(row.status())) {
            jdbc.queryForList("SELECT invalidate_staffing_preparation(?)", cruise);
        }
        notifyNext(id, cruise, next);
        return read(id);
    }

    private String cruiseCodeOf(UUID cruiseId) {
        return jdbc.queryForList("SELECT cruise_code FROM \"Cruise\" WHERE cruise_id=?", String.class, cruiseId)
                .stream().findFirst().orElse("chuyến khởi hành");
    }

    private String cruiseDisplayLabel(UUID cruiseId) {
        return jdbc.query(
                "SELECT cruise_code, cruise_name FROM \"Cruise\" WHERE cruise_id=?",
                (rs, i) -> {
                    String code = rs.getString(1);
                    String name = rs.getString(2);
                    return (name != null && !name.isBlank()) ? code + " — " + name.trim() : code;
                },
                cruiseId).stream().findFirst().orElse("chuyến khởi hành");
    }

    private void sendUserNotice(UUID userId, String type, String title, String content) {
        if (userId == null) return;
        if (notificationService != null) {
            notificationService.notifyUserId(userId, type, title, content);
        } else {
            jdbc.update("""
                    INSERT INTO "Notification" (notification_id,recipient_user_id,notification_type,title,content,created_at)
                    VALUES (gen_random_uuid(),?,?,?,?,now())
                    """, userId, type, title, content);
        }
    }

    private void notifyNext(UUID id, UUID cruise, String status) {
        String code = cruiseCodeOf(cruise);
        String label = cruiseDisplayLabel(cruise);
        String title = switch (status) {
            case "PENDING_ASM" -> "Yêu cầu chuẩn bị chương trình chuyến " + code;
            case "PENDING_SHIP_REVIEW" -> "Chuyến " + code + " chờ lập phương án tàu";
            case "SHIP_REVIEWED" -> "Chuyến " + code + " đã xong phương án tàu";
            case "SENT_TO_HR" -> "Chuyến " + code + " chờ phân công nhân sự";
            case "HR_SUBMITTED" -> "Chuyến " + code + " đã phân công nhân sự";
            case "PLANNER_CONFIRMED" -> "Chuyến " + code + " chờ duyệt phương án nhân sự";
            case "OPS_APPROVED" -> "Phương án nhân sự chuyến " + code + " đã được duyệt";
            default -> "Cập nhật phương án nhân sự chuyến " + code;
        };
        String content = switch (status) {
            case "PENDING_ASM" -> "Quản lý lịch trình đã gửi yêu cầu chuẩn bị chương trình & dịch vụ cho chuyến " + label + ".";
            case "PENDING_SHIP_REVIEW" -> "Chương trình và định biên dịch vụ chuyến " + label + " đã sẵn sàng. Vui lòng mở khu vực, gán máy POS và bố trí dịch vụ.";
            case "SHIP_REVIEWED" -> "Quản lý tàu đã xác nhận phương án tàu cho chuyến " + label + ". Vui lòng kiểm tra và chuyển Quản lý nhân sự phân công.";
            case "SENT_TO_HR" -> "Phương án tàu chuyến " + label + " đã sẵn sàng. Vui lòng phân công nhân sự cho các vị trí và ca hoạt động.";
            case "HR_SUBMITTED" -> "Quản lý nhân sự đã hoàn tất phân công cho chuyến " + label + ". Vui lòng rà soát và trình Quản lý vận hành phê duyệt.";
            case "PLANNER_CONFIRMED" -> "Quản lý lịch trình đã trình phương án nhân sự chuyến " + label + " để Quản lý vận hành phê duyệt.";
            case "OPS_APPROVED" -> "Quản lý vận hành đã phê duyệt hoàn tất phương án tàu và nhân sự cho chuyến " + label + ".";
            default -> "Yêu cầu chuẩn bị nhân sự cho chuyến " + label + " vừa được cập nhật.";
        };

        List<UUID> recipients = jdbc.queryForList("""
                SELECT u.user_id
                FROM "User" u JOIN "Cruise" c ON c.cruise_id=?
                WHERE u.status='ACTIVE' AND CASE ?
                    WHEN 'PENDING_ASM' THEN EXISTS (
                        SELECT 1 FROM "UserRole" ur WHERE ur.user_id=u.user_id AND ur.role='ACTIVITY_SERVICE_MANAGER')
                    WHEN 'SHIP_REVIEWED' THEN u.user_id=c.preparation_coordinator_id
                    WHEN 'HR_SUBMITTED' THEN u.user_id=c.preparation_coordinator_id
                    WHEN 'OPS_APPROVED' THEN u.user_id=c.preparation_coordinator_id
                    WHEN 'PENDING_SHIP_REVIEW' THEN EXISTS (
                        SELECT 1 FROM "CruiseStaff" cs WHERE cs.cruise_id=c.cruise_id AND cs.user_id=u.user_id
                            AND cs.status='ACTIVE' AND cs.role_in_cruise='SHIP_MANAGER')
                    WHEN 'SENT_TO_HR' THEN EXISTS (
                        SELECT 1 FROM "UserRole" ur WHERE ur.user_id=u.user_id AND ur.role='HR_MANAGER')
                    WHEN 'PLANNER_CONFIRMED' THEN EXISTS (
                        SELECT 1 FROM "UserRole" ur WHERE ur.user_id=u.user_id AND ur.role='OPERATIONS_MANAGER')
                    ELSE false END
                """, UUID.class, cruise, status);

        if ("PENDING_SHIP_REVIEW".equals(status) && recipients.isEmpty()) {
            String hrTitle = "Chuyến " + code + " cần phân công Quản lý tàu";
            String hrContent = "Chuyến " + label
                    + " đã sẵn sàng lập phương án tàu nhưng chưa có Quản lý tàu phụ trách. Vui lòng phân công tại mục Phân công nhân sự.";
            if (notificationService != null) {
                notificationService.notifyRole(Role.HR_MANAGER, "STAFFING_SHIP_MANAGER_NEEDED", hrTitle, hrContent);
            } else {
                List<UUID> hrs = jdbc.queryForList("""
                        SELECT u.user_id FROM "User" u JOIN "UserRole" ur USING(user_id)
                        WHERE u.status='ACTIVE' AND ur.role='HR_MANAGER'
                        """, UUID.class);
                hrs.forEach(hrId -> sendUserNotice(hrId, "STAFFING_SHIP_MANAGER_NEEDED", hrTitle, hrContent));
            }
            return;
        }
        recipients.forEach(uid -> sendUserNotice(uid, "STAFFING_WORKFLOW", title, content));
    }

    private UUID requestCruise(UUID requestId) {
        return jdbc.queryForList("""
                SELECT cruise_id FROM "StaffingRequest" WHERE request_id=?
                """, UUID.class, requestId).stream().findFirst()
                .orElseThrow(() -> conflict("STAFFING_REQUEST_NOT_FOUND"));
    }

    private ReviewState lockRequest(UUID requestId) {
        return jdbc.query("""
                SELECT status,revision,requested_by,hr_submitted_by,ship_confirmed_by,planner_confirmed_by,
                    ship_review_workflow,hr_approved_by,ship_review_fingerprint,
                    asm_program_workflow,service_review_fingerprint,workflow_version,ship_reviewed_by
                FROM "StaffingRequest" WHERE request_id=? FOR UPDATE
                """, (rs, i) -> new ReviewState(rs.getString(1), rs.getLong(2), rs.getObject(3, UUID.class),
                        rs.getObject(4, UUID.class), rs.getObject(5, UUID.class), rs.getObject(6, UUID.class),
                        rs.getBoolean(7), rs.getObject(8, UUID.class), rs.getString(9),
                        rs.getBoolean(10), rs.getString(11), rs.getString(12), rs.getObject(13, UUID.class)), requestId)
                .stream().findFirst().orElseThrow(() -> conflict("STAFFING_REQUEST_NOT_FOUND"));
    }

    private void checkRevision(ReviewState request, Long expected) {
        if (expected == null || request.revision() != expected) throw conflict("STAFFING_REVISION_CONFLICT");
    }

    @Transactional
    public ReviewDetails placeService(UUID requestId, UUID serviceId, ServicePlacementInput input, CasmsPrincipal actor) {
        return place(requestId, input.areaId(), input.terminalId(), serviceId, input.expectedRevision(), actor);
    }

    @Transactional
    public ReviewDetails placeAreaPos(UUID requestId, UUID areaId, AreaPosInput input, CasmsPrincipal actor) {
        return place(requestId, areaId, input.terminalId(), null, input.expectedRevision(), actor);
    }

    private record PlacementContext(UUID shipId, UUID deckId) {}

    private ReviewDetails place(UUID requestId, UUID areaId, UUID terminalId, UUID serviceId, Long expectedRevision,
                                CasmsPrincipal actor) {
        authorize(actor, Role.SHIP_MANAGER);
        UUID cruiseId = requestCruise(requestId);
        lockCruise(cruiseId);
        var request = lockRequest(requestId);
        if (request.v2()) {
            requireShipEditor(cruiseId, request, actor);
            checkRevision(request, expectedRevision);
            placeV2(requestId, cruiseId, areaId, terminalId, serviceId);
            bump(requestId);
            decision(requestId, expectedRevision + 1, serviceId == null ? "AREA_POS_PLACED" : "SERVICE_PLACED",
                    actor, "area=" + areaId + " terminal=" + terminalId + " service=" + serviceId);
            return reviewDetails(requestId, actor);
        }
        if (!isShipManager(cruiseId, actor.getUserId(), request.shipReview())
                || actor.getUserId().equals(request.requester())) {
            throw forbidden("STAFFING_SHIP_SCOPE_FORBIDDEN");
        }
        checkRevision(request, expectedRevision);
        if (!request.asmProgram() || !"PENDING_SHIP_REVIEW".equals(request.status())) {
            throw conflict("STAFFING_INVALID_TRANSITION");
        }
        var placement = jdbc.query("""
                SELECT area.ship_id, deck.deck_id FROM "StaffingServingArea" area
                JOIN "ShipModelObject" object ON object.ship_model_object_id=area.area_id AND object.cabin_id IS NULL
                JOIN "Deck" deck ON deck.deck_id=object.deck_id AND deck.ship_id=area.ship_id
                JOIN "PosTerminal" terminal ON terminal.ship_id=area.ship_id AND terminal.enabled
                WHERE area.cruise_id=? AND area.area_id=? AND terminal.terminal_id=?
                """, (rs, row) -> new PlacementContext(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)),
                cruiseId, areaId, terminalId).stream().findFirst()
                .orElseThrow(() -> conflict("STAFFING_POS_PLACEMENT_OUT_OF_SCOPE"));
        if (serviceId != null && !Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM "StaffingPlannedOnboardService" planned
                    JOIN "Service" service USING (service_id)
                    WHERE planned.cruise_id=? AND planned.service_id=? AND service.status='ACTIVE'
                )
                """, Boolean.class, cruiseId, serviceId))) {
            throw conflict("STAFFING_SERVICE_PLACEMENT_OUT_OF_SCOPE");
        }
        posAccess.ensureAreaLinkForPlacement(terminalId, areaId);
        if (serviceId != null) {
            deckServices.assign(placement.deckId(), serviceId, placement.shipId());
            pos.enableAreaServiceForPlacement(placement.shipId(), areaId, serviceId);
            if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS (
                        SELECT 1 FROM "StaffingPlannedServicePlacement"
                        WHERE cruise_id=? AND service_id=? AND area_id=? AND terminal_id=?
                    )
                    """, Boolean.class, cruiseId, serviceId, areaId, terminalId))) {
                throw conflict("STAFFING_SERVICE_POS_PERMISSION_REQUIRED");
            }
        }
        jdbc.update("""
                UPDATE "StaffingRequest" SET revision=revision+1,updated_at=now() WHERE request_id=?
                """, requestId);
        decision(requestId, expectedRevision + 1, serviceId == null ? "AREA_POS_PLACED" : "SERVICE_PLACED",
                actor, "area=" + areaId + " terminal=" + terminalId + (serviceId == null ? "" : " service=" + serviceId));
        return reviewDetails(requestId, actor);
    }

    private void placeV2(UUID id, UUID cruise, UUID area, UUID terminal, UUID service) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM "StaffingActiveArea" a
                JOIN "StaffingV2EligibleArea" eligible ON eligible.area_id=a.area_id AND eligible.cruise_id=?
                JOIN "StaffingAreaPos" p ON p.area_id=a.area_id AND p.cruise_id=eligible.cruise_id
                JOIN "PosTerminal" t ON t.terminal_id=p.terminal_id AND 'SERVICE_READ'=ANY(t.permissions)
                WHERE a.request_id=? AND a.area_id=? AND p.terminal_id=?)
                """, Boolean.class, cruise, id, area, terminal))) {
            throw conflict("STAFFING_POS_PLACEMENT_OUT_OF_SCOPE");
        }
        if (service == null) {
            jdbc.update("""
                    UPDATE "StaffingActiveArea" SET terminal_id=? WHERE request_id=? AND area_id=?
                    """, terminal, id, area);
            return;
        }
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM "StaffingActiveArea"
                    WHERE request_id=? AND area_id=? AND terminal_id=?)
                """, Boolean.class, id, area, terminal))) throw conflict("STAFFING_AREA_POS_REQUIRED");
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM "StaffingPlannedOnboardService" WHERE cruise_id=? AND service_id=?)
                """, Boolean.class, cruise, service))) throw conflict("STAFFING_SERVICE_PLACEMENT_OUT_OF_SCOPE");
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM "StaffingPlannedServicePlacement"
                    WHERE cruise_id=? AND service_id=? AND area_id=? AND terminal_id=?)
                """, Boolean.class, cruise, service, area, terminal))) {
            throw conflict("STAFFING_SERVICE_POS_PERMISSION_REQUIRED");
        }
        jdbc.update("""
                INSERT INTO "StaffingServicePlacement" (request_id,service_id,area_id) VALUES (?,?,?)
                ON CONFLICT (request_id,service_id) DO UPDATE SET area_id=excluded.area_id
                """, id, service, area);
    }

    private record ApprovedRequest(boolean shipReview, String fingerprint, boolean asmProgram, String serviceFingerprint,
                                   String workflowVersion, UUID id) {}

    @Transactional(readOnly = true)
    public boolean ready(UUID cruiseId) {
        var approved = jdbc.query("""
                    SELECT req.ship_review_workflow, req.ship_review_fingerprint,
                        req.asm_program_workflow, req.service_review_fingerprint, req.workflow_version, req.request_id
                    FROM "StaffingRequest" req JOIN "Cruise" c USING (cruise_id)
                    WHERE req.cruise_id=? AND req.status='OPS_APPROVED'
                    AND (req.workflow_version='V2' AND req.staffing_approved_revision=req.revision
                        AND req.planner_confirmed_by=c.preparation_coordinator_id
                        OR req.workflow_version='LEGACY' AND ((req.ship_review_workflow AND req.requested_by=c.preparation_coordinator_id)
                        OR (NOT req.ship_review_workflow AND c.preparation_coordinator_id=req.planner_confirmed_by))
                    AND (req.asm_program_workflow OR EXISTS (
                        SELECT 1 FROM "StaffingRequirement" r WHERE r.request_id=req.request_id))
                    AND NOT EXISTS (
                        SELECT 1 FROM "StaffingRequirement" r JOIN "Service" s ON s.service_id=r.service_id
                        WHERE r.request_id=req.request_id AND (
                            s.status <> 'ACTIVE' OR r.required_count > (
                                SELECT count(DISTINCT cs.user_id) FROM "CruiseStaff" cs
                                JOIN "CruiseStaffDuty" d USING (cruise_staff_id)
                                JOIN "User" u ON u.user_id=cs.user_id
                                WHERE cs.cruise_id=req.cruise_id AND cs.status='ACTIVE' AND u.status='ACTIVE'
                                AND d.service_id=r.service_id AND d.ended_at IS NULL))))
                """, (rs, row) -> new ApprovedRequest(rs.getBoolean(1), rs.getString(2), rs.getBoolean(3),
                    rs.getString(4), rs.getString(5), rs.getObject(6, UUID.class)), cruiseId)
                .stream().findFirst().orElse(null);
        if (approved != null && "V2".equals(approved.workflowVersion())) {
            if (!currentShipReviewer(approved.id())) return false;
            String current = validShipPlanFingerprint(approved.id());
            if (current != null && current.equals(approved.fingerprint())) {
                return needsCovered(approved.id(), cruiseId);
            }
            String legacy = legacyV2ShipPlanFingerprint(approved.id());
            return legacy != null && legacy.equals(approved.fingerprint())
                    && readNeeds(approved.id(), true).stream().allMatch(need -> need.assignedUserIds().size() >= need.requiredCount())
                    && activitiesCovered(cruiseId);
        }
        return approved != null && (!approved.shipReview()
                || inspectShip(cruiseId).matches(approved.fingerprint(), approved.asmProgram(), approved.serviceFingerprint())
                    && areasCovered(cruiseId) && activitiesCovered(cruiseId));
    }

    @Transactional(readOnly = true)
    public ReviewDetails reviewDetails(UUID id, CasmsPrincipal actor) {
        Snapshot request = detail(id, actor);
        UUID cruiseId = request.cruiseId();
        var cabins = jdbc.query("""
                SELECT b.cabin_id, b.cabin_number, b.status::text
                FROM "Cruise" c JOIN "Cabin" b USING (ship_id)
                WHERE c.cruise_id=? ORDER BY b.cabin_number, b.cabin_id
                """, (rs, row) -> new CabinReview(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getString(3)), cruiseId);
        ShipReview ship = jdbc.query("""
                SELECT s.ship_id, m.ship_model_id, s.ship_name, s.status::text
                FROM "Cruise" c JOIN "Ship" s USING (ship_id)
                LEFT JOIN "StaffingShipModel" m USING (cruise_id) WHERE c.cruise_id=?
                """, (rs, row) -> new ShipReview(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getString(3), rs.getString(4), cabins), cruiseId).stream().findFirst().orElse(null);
        Map<UUID, List<PosReview>> terminals = new LinkedHashMap<>();
        Map<UUID, List<PosReview>> eligibleTerminals = new LinkedHashMap<>();
        if (request.v2()) jdbc.query("""
                SELECT p.area_id,p.terminal_id,p.terminal_name FROM "StaffingAreaPos" p
                JOIN "PosTerminal" t USING (terminal_id)
                WHERE p.cruise_id=? AND 'SERVICE_READ'=ANY(t.permissions)
                ORDER BY p.terminal_name,p.terminal_id
                """, rs -> {
                    eligibleTerminals.computeIfAbsent(rs.getObject(1, UUID.class), ignored -> new ArrayList<>())
                            .add(new PosReview(rs.getObject(2, UUID.class), rs.getString(3)));
                }, cruiseId);
        String posQuery = request.v2() ? """
                SELECT a.area_id,t.terminal_id,t.terminal_name FROM "StaffingActiveArea" a
                JOIN "PosTerminal" t USING(terminal_id) WHERE a.request_id=?
                ORDER BY t.terminal_name,t.terminal_id
                """ : """
                SELECT area_id, terminal_id, terminal_name FROM "StaffingAreaPos"
                WHERE cruise_id=? ORDER BY terminal_name, terminal_id
                """;
        jdbc.query(posQuery, rs -> {
                    terminals.computeIfAbsent(rs.getObject(1, UUID.class), ignored -> new ArrayList<>())
                            .add(new PosReview(rs.getObject(2, UUID.class), rs.getString(3)));
                }, request.v2() ? id : cruiseId);
        String areaQuery = request.v2() ? """
                SELECT a.area_id,a.area_name,ARRAY(
                    SELECT DISTINCT uid FROM (
                        SELECT q.user_id AS uid FROM "StaffingV2QualifiedAssignment" q
                            JOIN "StaffingNeed" n USING(request_id,need_id)
                            WHERE q.request_id=? AND ? AND n.area_id=a.area_id
                        UNION
                        SELECT cs.user_id AS uid FROM "StaffingServiceAssignment" sa
                            JOIN "StaffingServicePlacement" sp USING(request_id,service_id)
                            JOIN "CruiseStaff" cs ON cs.cruise_staff_id=sa.cruise_staff_id AND cs.status='ACTIVE'
                            JOIN "User" u ON u.user_id=cs.user_id AND u.status='ACTIVE'
                            WHERE sa.request_id=? AND ? AND sp.area_id=a.area_id
                    ) staff_in_area ORDER BY uid
                ) FROM "StaffingV2EligibleArea" a WHERE a.cruise_id=? ORDER BY a.area_name,a.area_id
                """ : """
                SELECT a.area_id, a.area_name, ARRAY(
                    SELECT ac.user_id FROM "StaffingAreaCrew" ac
                    WHERE ac.cruise_id=a.cruise_id AND ac.area_id=a.area_id ORDER BY ac.user_id
                ) FROM "StaffingServingArea" a WHERE a.cruise_id=? ORDER BY a.area_name, a.area_id
                """;
        var areas = jdbc.query(areaQuery, (rs, row) -> new AreaReview(rs.getObject(1, UUID.class), rs.getString(2),
                terminals.getOrDefault(rs.getObject(1, UUID.class), List.of()),
                Arrays.asList((UUID[]) rs.getArray(3).getArray()),
                eligibleTerminals.getOrDefault(rs.getObject(1, UUID.class), List.of())),
                request.v2() ? new Object[]{id, !request.shipPlanStale(), id, !request.shipPlanStale(), cruiseId} : new Object[]{cruiseId});
        var activities = jdbc.query("""
                SELECT w.window_id, w.kind, w.name, ARRAY(
                    SELECT DISTINCT d.user_id FROM "CruiseStaffDutyWindow" d
                    WHERE d.cruise_id=w.cruise_id AND d.kind=w.kind AND d.window_id=w.window_id
                    ORDER BY d.user_id
                ), COALESCE(oa.required_staff_count, se.required_staff_count, 1)
                FROM "CruiseStaffWorkWindow" w
                LEFT JOIN "OnboardActivity" oa ON w.kind='ONBOARD' AND oa.onboard_activity_id=w.window_id
                LEFT JOIN "ShoreExcursion" se ON w.kind='SHORE' AND se.shore_excursion_id=w.window_id
                WHERE w.cruise_id=? AND w.kind IN ('ONBOARD','SHORE')
                ORDER BY w.starts_at, w.kind, w.window_id
                """, (rs, row) -> new ActivityReview(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getString(3), Arrays.asList((UUID[]) rs.getArray(4).getArray()), rs.getInt(5)), cruiseId);
        List<PosReview> terminalOptions = !request.asmProgramWorkflow() ? List.of() : jdbc.query("""
                SELECT t.terminal_id, t.terminal_name FROM "Cruise" c JOIN "PosTerminal" t USING (ship_id)
                WHERE c.cruise_id=? AND t.enabled ORDER BY t.terminal_name,t.terminal_id
                """, (rs, row) -> new PosReview(rs.getObject(1, UUID.class), rs.getString(2)), cruiseId);
        return new ReviewDetails(request, ship, areas, activities, staffRoster(cruiseId, areas, activities),
                terminalOptions, request.asmProgramWorkflow() ? plannedServices(cruiseId, request) : List.of(),
                request.v2() ? AREA_POSITIONS.stream().sorted().toList() : List.of(),
                request.v2() && "PENDING_SHIP_REVIEW".equals(request.status()) && handoffs.canChange(cruiseId),
                request.v2() ? validShipPlanFingerprint(id) != null : null);
    }

    private List<PlannedService> plannedServices(UUID cruiseId, Snapshot request) {
        Map<UUID, List<Placement>> placements = new LinkedHashMap<>();
        String query = request.v2() ? """
                SELECT v.service_id,v.area_id,v.terminal_id FROM "StaffingV2ServicePlacement" v
                JOIN "StaffingRequest" r USING (request_id)
                JOIN "StaffingPlannedServicePlacement" p ON p.cruise_id=r.cruise_id
                    AND p.service_id=v.service_id AND p.area_id=v.area_id AND p.terminal_id=v.terminal_id
                WHERE v.request_id=? ORDER BY v.area_id,v.terminal_id
                """ : """
                SELECT service_id,area_id,terminal_id FROM "StaffingPlannedServicePlacement"
                WHERE cruise_id=? ORDER BY area_id,terminal_id
                """;
        jdbc.query(query, rs -> {
                    placements.computeIfAbsent(rs.getObject(1, UUID.class), ignored -> new ArrayList<>())
                            .add(new Placement(rs.getObject(2, UUID.class), rs.getObject(3, UUID.class)));
                }, request.v2() ? request.id() : cruiseId);
        Map<UUID, List<Placement>> configured = new LinkedHashMap<>();
        Map<UUID, List<Placement>> eligible = new LinkedHashMap<>();
        if (request.v2()) jdbc.query("""
                SELECT p.service_id,p.area_id,p.terminal_id,a.terminal_id IS NOT NULL
                FROM "StaffingPlannedServicePlacement" p
                LEFT JOIN "StaffingActiveArea" a
                  ON a.request_id=? AND a.area_id=p.area_id AND a.terminal_id=p.terminal_id
                WHERE p.cruise_id=? ORDER BY p.area_id,p.terminal_id
                """, rs -> {
                    UUID service = rs.getObject(1, UUID.class);
                    var candidate = new Placement(rs.getObject(2, UUID.class), rs.getObject(3, UUID.class));
                    configured.computeIfAbsent(service, ignored -> new ArrayList<>()).add(candidate);
                    if (rs.getBoolean(4)) eligible.computeIfAbsent(service, ignored -> new ArrayList<>()).add(candidate);
                }, request.id(), cruiseId);
        return jdbc.query("""
                SELECT service.service_id,service.service_name,ARRAY(
                    SELECT a.area_id FROM "StaffingServingArea" a
                    JOIN "ShipModelObject" o ON o.ship_model_object_id=a.area_id AND o.cabin_id IS NULL
                    JOIN "Deck" d ON d.deck_id=o.deck_id AND d.ship_id=a.ship_id
                    WHERE a.cruise_id=service.cruise_id
                    ORDER BY a.area_id
                ), coalesce(d.deck_name,'Boong ' || d.deck_number), proposal.note, proposal.status,
                CASE
                    WHEN s.status<>'ACTIVE' THEN 'SERVICE_INACTIVE'
                    WHEN NOT EXISTS (SELECT 1 FROM "StaffingShipModel" m WHERE m.cruise_id=c.cruise_id)
                        THEN 'SHIP_MODEL_REQUIRED'
                    WHEN NOT EXISTS (SELECT 1 FROM "DeckServiceAssignment" ds JOIN "Deck" deck USING (deck_id)
                        WHERE deck.ship_id=c.ship_id AND ds.service_id=s.service_id)
                        THEN 'DECK_SERVICE_REQUIRED'
                    WHEN NOT EXISTS (
                        SELECT 1 FROM "StaffingV2EligibleArea" a
                        JOIN "DeckServiceAssignment" ds USING (deck_id)
                        JOIN "AreaServiceAssignment" ass ON ass.area_object_id=a.area_id
                            AND ass.service_id=ds.service_id AND ass.enabled
                        WHERE a.cruise_id=c.cruise_id AND ds.service_id=s.service_id)
                        THEN 'AREA_SERVICE_REQUIRED'
                    WHEN NOT EXISTS (
                        SELECT 1 FROM "StaffingV2EligibleArea" a
                        JOIN "DeckServiceAssignment" ds USING (deck_id)
                        JOIN "AreaServiceAssignment" ass ON ass.area_object_id=a.area_id
                            AND ass.service_id=ds.service_id AND ass.enabled
                        JOIN "StaffingAreaPos" pos ON pos.cruise_id=a.cruise_id AND pos.area_id=a.area_id
                        WHERE a.cruise_id=c.cruise_id AND ds.service_id=s.service_id)
                        THEN 'SERVICE_AREA_POS_REQUIRED'
                    ELSE 'SERVICE_POS_PERMISSION_REQUIRED'
                END AS configuration_blocker,
                GREATEST(1, COALESCE((
                    SELECT MAX(w.required_staff_count) FROM "ServiceSchedule" w
                    WHERE w.cruise_id=service.cruise_id AND w.service_id=service.service_id
                      AND w.cruise_stop_id IS NULL AND w.status<>'CANCELLED'
                ), s.default_required_staff, 1)) AS required_staff_count
                FROM "StaffingPlannedOnboardService" service
                JOIN "Service" s USING (service_id)
                JOIN "Cruise" c ON c.cruise_id=service.cruise_id
                LEFT JOIN ("CruiseServiceLocationRequest" proposal JOIN "Deck" d USING (deck_id))
                    ON proposal.cruise_id=service.cruise_id AND proposal.service_id=service.service_id
                    AND d.ship_id=c.ship_id
                WHERE service.cruise_id=?
                ORDER BY service.service_name,service.service_id
                """, (rs, row) -> {
                    UUID service = rs.getObject(1, UUID.class);
                    var pairs = eligible.getOrDefault(service, List.of());
                    var configuredPairs = configured.getOrDefault(service, List.of());
                    var areas = request.v2() ? pairs.stream().map(Placement::areaId).distinct().toList()
                            : Arrays.asList((UUID[]) rs.getArray(3).getArray());
                    String blocker = !request.v2() || !pairs.isEmpty() ? null
                            : configuredPairs.isEmpty() ? rs.getString(7) : "ACTIVE_AREA_POS_SELECTION_REQUIRED";
                    return new PlannedService(service, rs.getString(2), areas,
                            placements.getOrDefault(service, List.of()),
                            rs.getString(6) == null ? null
                                    : new ServiceProposal(rs.getString(4), rs.getString(5), rs.getString(6)),
                            pairs, configuredPairs, blocker, rs.getInt(8));
                }, cruiseId);
    }

    private List<StaffReview> staffRoster(
            UUID cruiseId, List<AreaReview> areas, List<ActivityReview> activities) {
        Map<UUID, List<ServiceOption>> services = new LinkedHashMap<>();
        jdbc.query("""
                SELECT cs.user_id, s.service_id, s.service_name
                FROM "CruiseStaff" cs JOIN "CruiseStaffDuty" d USING (cruise_staff_id)
                JOIN "Service" s USING (service_id)
                WHERE cs.cruise_id=? AND cs.status='ACTIVE' AND d.ended_at IS NULL AND s.status='ACTIVE'
                ORDER BY s.service_name, s.service_id
                """, rs -> {
                    services.computeIfAbsent(rs.getObject(1, UUID.class), ignored -> new ArrayList<>())
                            .add(new ServiceOption(rs.getObject(2, UUID.class), rs.getString(3)));
                }, cruiseId);
        return jdbc.query("""
                SELECT cs.user_id, u.username, cs.role_in_cruise::text
                FROM "CruiseStaff" cs JOIN "User" u USING (user_id)
                WHERE cs.cruise_id=? AND cs.status='ACTIVE' AND u.status='ACTIVE'
                ORDER BY u.username, cs.user_id
                """, (rs, row) -> {
                    UUID userId = rs.getObject(1, UUID.class);
                    return new StaffReview(userId, rs.getString(2), rs.getString(3),
                            services.getOrDefault(userId, List.of()), areas.stream()
                                    .filter(area -> area.staffIds().contains(userId))
                                    .map(area -> new ServiceOption(area.areaId(), area.name())).toList(),
                            activities.stream().filter(activity -> activity.staffIds().contains(userId)).toList());
                }, cruiseId);
    }

    private boolean serviceDemandCovered(Snapshot request, boolean optional) {
        return (optional || !request.requirements().isEmpty())
                && request.requirements().stream().allMatch(r -> r.assigned() >= r.required());
    }

    private record ShipInspection(UUID modelId, boolean ready, boolean posCovered, String fingerprint,
                                  boolean asmCompleted, boolean servicesCovered, String serviceFingerprint) {
        boolean matches(String checkedFingerprint, boolean requiresProgram, String checkedServices) {
            return modelId != null && ready && posCovered && Objects.equals(fingerprint, checkedFingerprint)
                    && (!requiresProgram || asmCompleted && servicesCovered && Objects.equals(serviceFingerprint, checkedServices));
        }
    }

    private ShipInspection inspectShip(UUID cruiseId) {
        return jdbc.queryForObject("""
                SELECT ship.ship_model_id, ship.ship_ready, ship.pos_covered, ship.fingerprint,
                    program.asm_completed,program.services_covered,program.fingerprint
                FROM "StaffingShipInspection" ship JOIN "StaffingProgramInspection" program USING (cruise_id)
                WHERE cruise_id=?
                """, (rs, row) -> new ShipInspection(rs.getObject(1, UUID.class), rs.getBoolean(2),
                rs.getBoolean(3), rs.getString(4), rs.getBoolean(5), rs.getBoolean(6), rs.getString(7)), cruiseId);
    }

    private ShipInspection validateShipReview(UUID cruiseId, boolean requiresProgram) {
        var inspection = inspectShip(cruiseId);
        if (requiresProgram && !inspection.asmCompleted()) {
            throw conflict("STAFFING_ASM_PROGRAM_INCOMPLETE");
        }
        if (!inspection.ready()) {
            throw conflict("STAFFING_SHIP_NOT_READY");
        }
        if (inspection.modelId() == null) {
            throw conflict("STAFFING_SHIP_MODEL_REQUIRED");
        }
        if (!inspection.posCovered()) {
            throw conflict("STAFFING_POS_COVERAGE_REQUIRED");
        }
        if (requiresProgram && !inspection.servicesCovered()) {
            throw conflict("STAFFING_SERVICE_POS_COVERAGE_REQUIRED");
        }
        return inspection;
    }

    private boolean canReturnShipReview(UUID cruiseId, ReviewState request) {
        if (!request.shipReview()) {
            return false;
        }
        return switch (request.status()) {
            case "SENT_TO_HR", "HR_SUBMITTED", "SHIP_CONFIRMED" -> true;
            case "HR_APPROVED", "OPS_APPROVED" -> !inspectShip(cruiseId).matches(request.shipFingerprint(), request.asmProgram(), request.serviceFingerprint());
            default -> false;
        };
    }

    private void validateStaffCoverage(UUID cruiseId) {
        if (!areasCovered(cruiseId)) {
            throw conflict("STAFFING_AREA_COVERAGE_REQUIRED");
        }
        if (!activitiesCovered(cruiseId)) {
            throw conflict("STAFFING_ACTIVITY_COVERAGE_REQUIRED");
        }
    }

    private boolean areasCovered(UUID cruiseId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT NOT EXISTS (
                    SELECT 1 FROM "StaffingServingArea" a WHERE a.cruise_id=?
                    AND NOT EXISTS (SELECT 1 FROM "StaffingAreaCrew" ac
                                    WHERE ac.cruise_id=a.cruise_id AND ac.area_id=a.area_id)
                )
                """, Boolean.class, cruiseId));
    }

    private boolean activitiesCovered(UUID cruiseId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT NOT EXISTS (
                    SELECT 1 FROM "CruiseStaffWorkWindow" w
                    LEFT JOIN "OnboardActivity" oa ON w.kind='ONBOARD' AND oa.onboard_activity_id=w.window_id
                    LEFT JOIN "ShoreExcursion" se ON w.kind='SHORE' AND se.shore_excursion_id=w.window_id
                    WHERE w.cruise_id=? AND w.kind IN ('ONBOARD','SHORE')
                    AND (SELECT count(DISTINCT d.user_id) FROM "CruiseStaffDutyWindow" d
                         WHERE d.cruise_id=w.cruise_id AND d.kind=w.kind AND d.window_id=w.window_id)
                        < COALESCE(oa.required_staff_count, se.required_staff_count, 1)
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
