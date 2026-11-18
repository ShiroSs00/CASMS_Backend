package com.casms.staffing;

import com.casms.common.exception.BusinessException;
import com.casms.identity.enums.Role;
import com.casms.pos.PosAccessService;
import com.casms.pos.PosService;
import com.casms.ship.service.CruiseWorkHandoffService;
import com.casms.ship.service.DeckServiceAssignmentService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import com.casms.security.principal.CasmsPrincipal;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class StaffingWorkflowService {
    private final JdbcTemplate jdbc;
    private final CruiseWorkHandoffService handoffs;
    private final PosAccessService posAccess;
    private final PosService pos;
    private final DeckServiceAssignmentService deckServices;

    public record Requirement(UUID serviceId, int requiredCount) {}
    public record CreateRequest(Long scheduleRevision, List<Requirement> requirements) {}
    public record ReviewRequest(Long expectedRevision, String comment) {}
    public record ServicePlacementInput(@NotNull UUID areaId, @NotNull UUID terminalId,
                                        @NotNull @PositiveOrZero Long expectedRevision) {}
    public record AreaPosInput(@NotNull UUID terminalId, @NotNull @PositiveOrZero Long expectedRevision) {}
    private record RequestState(UUID id, UUID cruiseId, String status, long revision, long scheduleRevision,
                                boolean shipReview, boolean asmProgram) {}

    public record Snapshot(UUID id, UUID cruiseId, String status, long revision, long scheduleRevision,
                           List<RequirementProgress> requirements, String cruiseName, String cruiseCode,
                           OffsetDateTime departure, OffsetDateTime returning, String operatingBudgetVnd,
                           String returnReason, boolean shipReviewWorkflow, boolean asmProgramWorkflow) {}
    public record RequirementProgress(UUID serviceId, int required, long assigned, String serviceName) {}
    public record ShipReview(UUID shipId, UUID modelId, String name, String status, List<CabinReview> cabins) {}
    public record CabinReview(UUID cabinId, String number, String status) {}
    public record AreaReview(UUID areaId, String name, List<PosReview> terminals, List<UUID> staffIds) {}
    public record PosReview(UUID terminalId, String name) {}
    public record ActivityReview(UUID activityId, String kind, String name, List<UUID> staffIds) {}
    public record StaffReview(UUID userId, String username, String role, List<ServiceOption> services,
                              List<ServiceOption> areas, List<ActivityReview> activities) {}
    public record Placement(UUID areaId, UUID terminalId) {}
    public record PlannedService(UUID serviceId, String name, List<UUID> eligibleAreaIds,
                                 List<Placement> placements) {}
    public record ReviewDetails(Snapshot request, ShipReview ship, List<AreaReview> areas,
                                List<ActivityReview> activities, List<StaffReview> staff,
                                List<PosReview> terminalOptions, List<PlannedService> plannedServices) {}
    public record ServiceOption(UUID id, String name) {}
    public record Preparation(long scheduleRevision, Snapshot request, List<ServiceOption> services,
                              boolean hasOperatingBudget) {}

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
        return new Preparation(source.revision(), request, services, source.hasOperatingBudget());
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
        List<Requirement> requirements = input.requirements() == null ? List.of() : input.requirements();
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
        Set<UUID> unique = new HashSet<>();
        for (Requirement requirement : requirements) {
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
        String initialStatus = handoffs.ensureAsmForStaffing(cruiseId, actor)
                ? "PENDING_SHIP_REVIEW" : "PENDING_ASM";
        UUID requestId = jdbc.queryForObject("""
                INSERT INTO "StaffingRequest"
                    (cruise_id,schedule_revision,requested_by,status,ship_review_workflow,asm_program_workflow)
                VALUES (?,?,?,?,true,true) RETURNING request_id
                """, UUID.class, cruiseId, input.scheduleRevision(), actor.getUserId(), initialStatus);
        for (Requirement requirement : requirements) jdbc.update("""
                INSERT INTO "StaffingRequirement" (request_id,service_id,required_count) VALUES (?,?,?)
                """, requestId, requirement.serviceId(), requirement.requiredCount());
        decision(requestId, 0, initialStatus, actor, null);
        return read(requestId);
    }

    private Snapshot read(UUID id) {
        var requests = jdbc.query("""
                SELECT request_id,cruise_id,status,revision,schedule_revision,ship_review_workflow,asm_program_workflow
                FROM "StaffingRequest" WHERE request_id=?
                """, (rs, i) -> new RequestState(rs.getObject("request_id", UUID.class),
                    rs.getObject("cruise_id", UUID.class), rs.getString("status"),
                    rs.getLong("revision"), rs.getLong("schedule_revision"),
                    rs.getBoolean("ship_review_workflow"), rs.getBoolean("asm_program_workflow")), id);
        RequestState request = requests.stream().findFirst()
                .orElseThrow(() -> conflict("STAFFING_REQUEST_NOT_FOUND"));
        List<RequirementProgress> progress = jdbc.query("""
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
                    AND action IN ('RETURNED','HR_RETURN_SHIP','HR_RETURN_STAFF')
                """, String.class, id, request.revision()).stream()
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        return jdbc.queryForObject("""
                SELECT cruise_name,cruise_code,departure_datetime,return_datetime,operating_budget_vnd::text
                FROM "Cruise" WHERE cruise_id=?
                """, (rs,i) -> new Snapshot(request.id(), request.cruiseId(), request.status(),
                request.revision(), request.scheduleRevision(), progress, rs.getString(1), rs.getString(2),
                rs.getObject(3, OffsetDateTime.class), rs.getObject(4, OffsetDateTime.class),
                rs.getString(5), reason, request.shipReview(), request.asmProgram()), request.cruiseId());
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
                )
                ORDER BY req.requested_at, req.request_id
                """, UUID.class, hr, ship, actor.getUserId(), actor.getUserId(), planner, actor.getUserId(), ops)
                .stream().map(this::read).toList();
    }

    private record ReviewState(String status, long revision, UUID requester, UUID hr, UUID ship, UUID planner,
                               boolean shipReview, UUID hrApprover, String shipFingerprint,
                               boolean asmProgram, String serviceFingerprint) {}

    @Transactional
    public Snapshot transition(UUID id, String action, ReviewRequest input, CasmsPrincipal actor) {
        Role role = switch (action) {
            case "HR_SUBMITTED", "HR_APPROVED", "HR_RETURN_SHIP", "HR_RETURN_STAFF" -> Role.HR_MANAGER;
            case "SHIP_CHECKED", "SHIP_CONFIRMED" -> Role.SHIP_MANAGER;
            case "PLANNER_CONFIRMED" -> Role.CRUISE_MANAGER;
            case "OPS_APPROVED" -> Role.OPERATIONS_MANAGER;
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
            jdbc.update("""
                    INSERT INTO "Notification" (notification_id,recipient_user_id,notification_type,title,content,created_at)
                    VALUES (gen_random_uuid(),?,'STAFFING_SENT_TO_HR','Yêu cầu đang chờ bố trí nhân sự',?,now())
                    """, cruise.coordinator(), "Quản lý tàu đã xác nhận. Mã yêu cầu: " + id);
        }
        return read(id);
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
                    asm_program_workflow,service_review_fingerprint
                FROM "StaffingRequest" WHERE request_id=? FOR UPDATE
                """, (rs, i) -> new ReviewState(rs.getString(1), rs.getLong(2), rs.getObject(3, UUID.class),
                        rs.getObject(4, UUID.class), rs.getObject(5, UUID.class), rs.getObject(6, UUID.class),
                        rs.getBoolean(7), rs.getObject(8, UUID.class), rs.getString(9),
                        rs.getBoolean(10), rs.getString(11)), requestId)
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

    private record ApprovedRequest(boolean shipReview, String fingerprint, boolean asmProgram, String serviceFingerprint) {}

    @Transactional(readOnly = true)
    public boolean ready(UUID cruiseId) {
        var approved = jdbc.query("""
                    SELECT req.ship_review_workflow, req.ship_review_fingerprint,
                        req.asm_program_workflow, req.service_review_fingerprint
                    FROM "StaffingRequest" req JOIN "Cruise" c USING (cruise_id)
                    WHERE req.cruise_id=? AND req.status='OPS_APPROVED'
                    AND ((req.ship_review_workflow AND req.requested_by=c.preparation_coordinator_id)
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
                                AND d.service_id=r.service_id AND d.ended_at IS NULL)))
                """, (rs, row) -> new ApprovedRequest(rs.getBoolean(1), rs.getString(2), rs.getBoolean(3), rs.getString(4)), cruiseId)
                .stream().findFirst().orElse(null);
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
        jdbc.query("""
                SELECT area_id, terminal_id, terminal_name FROM "StaffingAreaPos"
                WHERE cruise_id=? ORDER BY terminal_name, terminal_id
                """, rs -> {
                    terminals.computeIfAbsent(rs.getObject(1, UUID.class), ignored -> new ArrayList<>())
                            .add(new PosReview(rs.getObject(2, UUID.class), rs.getString(3)));
                }, cruiseId);
        var areas = jdbc.query("""
                SELECT a.area_id, a.area_name, ARRAY(
                    SELECT ac.user_id FROM "StaffingAreaCrew" ac
                    WHERE ac.cruise_id=a.cruise_id AND ac.area_id=a.area_id ORDER BY ac.user_id
                ) FROM "StaffingServingArea" a WHERE a.cruise_id=? ORDER BY a.area_name, a.area_id
                """, (rs, row) -> new AreaReview(rs.getObject(1, UUID.class), rs.getString(2),
                terminals.getOrDefault(rs.getObject(1, UUID.class), List.of()),
                Arrays.asList((UUID[]) rs.getArray(3).getArray())), cruiseId);
        var activities = jdbc.query("""
                SELECT w.window_id, w.kind, w.name, ARRAY(
                    SELECT DISTINCT d.user_id FROM "CruiseStaffDutyWindow" d
                    WHERE d.cruise_id=w.cruise_id AND d.kind=w.kind AND d.window_id=w.window_id
                    ORDER BY d.user_id
                ) FROM "CruiseStaffWorkWindow" w WHERE w.cruise_id=? AND w.kind IN ('ONBOARD','SHORE')
                ORDER BY w.starts_at, w.kind, w.window_id
                """, (rs, row) -> new ActivityReview(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getString(3), Arrays.asList((UUID[]) rs.getArray(4).getArray())), cruiseId);
        List<PosReview> terminalOptions = !request.asmProgramWorkflow() ? List.of() : jdbc.query("""
                SELECT t.terminal_id, t.terminal_name FROM "Cruise" c JOIN "PosTerminal" t USING (ship_id)
                WHERE c.cruise_id=? AND t.enabled ORDER BY t.terminal_name,t.terminal_id
                """, (rs, row) -> new PosReview(rs.getObject(1, UUID.class), rs.getString(2)), cruiseId);
        return new ReviewDetails(request, ship, areas, activities, staffRoster(cruiseId, areas, activities),
                terminalOptions, request.asmProgramWorkflow() ? plannedServices(cruiseId) : List.of());
    }

    private List<PlannedService> plannedServices(UUID cruiseId) {
        Map<UUID, List<Placement>> placements = new LinkedHashMap<>();
        jdbc.query("""
                SELECT service_id,area_id,terminal_id FROM "StaffingPlannedServicePlacement"
                WHERE cruise_id=? ORDER BY area_id,terminal_id
                """, rs -> {
                    placements.computeIfAbsent(rs.getObject(1, UUID.class), ignored -> new ArrayList<>())
                            .add(new Placement(rs.getObject(2, UUID.class), rs.getObject(3, UUID.class)));
                }, cruiseId);
        return jdbc.query("""
                SELECT service.service_id,service.service_name,ARRAY(
                    SELECT a.area_id FROM "StaffingServingArea" a
                    JOIN "ShipModelObject" o ON o.ship_model_object_id=a.area_id AND o.cabin_id IS NULL
                    JOIN "Deck" d ON d.deck_id=o.deck_id AND d.ship_id=a.ship_id
                    WHERE a.cruise_id=service.cruise_id
                    ORDER BY a.area_id
                )
                FROM "StaffingPlannedOnboardService" service WHERE service.cruise_id=?
                ORDER BY service.service_name,service.service_id
                """, (rs, row) -> {
                    UUID service = rs.getObject(1, UUID.class);
                    return new PlannedService(service, rs.getString(2), Arrays.asList((UUID[]) rs.getArray(3).getArray()),
                            placements.getOrDefault(service, List.of()));
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
                    WHERE w.cruise_id=? AND w.kind IN ('ONBOARD','SHORE')
                    AND NOT EXISTS (SELECT 1 FROM "CruiseStaffDutyWindow" d
                                    WHERE d.cruise_id=w.cruise_id AND d.kind=w.kind AND d.window_id=w.window_id)
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
