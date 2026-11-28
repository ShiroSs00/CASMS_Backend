package com.casms.ship.service;

import com.casms.staffing.StaffingWorkflowService;

import com.casms.activity.repository.OnboardActivityRepository;
import com.casms.activity.repository.ShoreExcursionRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.identity.repository.UserRepository;
import com.casms.identity.enums.Role;
import com.casms.security.principal.CasmsPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import com.casms.ship.dto.request.CruisePreparationConfirmRequest;
import com.casms.ship.dto.request.CruisePreparationReturnRequest;
import com.casms.ship.dto.request.CruisePreparationSubmitRequest;
import com.casms.ship.dto.response.CruisePreparationResponse;
import com.casms.ship.dto.response.CruisePreparationSectionResponse;
import com.casms.ship.dto.response.CruisePreparationSectionResponse.PreparationCheckItemDto;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.CruisePreparationSection;
import com.casms.ship.entity.Ship;
import com.casms.ship.enums.CabinStatus;
import com.casms.ship.enums.ShipStatus;
import com.casms.ship.repository.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CruisePreparationService {

    private final CruiseRepository cruiseRepository;
    private final CruisePreparationSectionRepository preparationSectionRepository;
    private final CruiseStaffRepository cruiseStaffRepository;
    private final StaffingWorkflowService staffingWorkflow;
    private final DeckRepository deckRepository;
    private final CabinRepository cabinRepository;
    private final ShoreExcursionRepository shoreExcursionRepository;
    private final OnboardActivityRepository onboardActivityRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ItineraryProgramPolicy programPolicy;

    private CasmsPrincipal currentActor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof CasmsPrincipal p ? p : null;
    }

    private boolean permitted(CasmsPrincipal actor, boolean submit) {
        return actor != null
                && actor.hasRole(
                        submit
                                ? com.casms.identity.enums.Role.CRUISE_MANAGER
                                : com.casms.identity.enums.Role.OPERATIONS_MANAGER)
                && actor.hasAuthority(
                        submit ? "CRUISE_PREPARATION_SUBMIT" : "CRUISE_PREPARATION_CONFIRM");
    }

    private boolean submissionScope(Cruise cruise, CasmsPrincipal actor) {
        return permitted(actor, true)
                && cruise.getPreparationCoordinator() != null
                && actor.getUserId().equals(cruise.getPreparationCoordinator().getUserId());
    }

    private boolean sectionSubmissionScope(Cruise cruise, String sectionCode, CasmsPrincipal actor) {
        if (actor == null || !actor.hasAuthority("CRUISE_PREPARATION_SUBMIT")) return false;
        return switch (sectionCode) {
            case "HOTEL_CABIN", "ENTERTAINMENT_SHORE" -> submissionScope(cruise, actor);
            case "TECHNICAL_NAUTICAL" -> actor.hasRole(Role.SHIP_MANAGER)
                    && cruiseStaffRepository
                            .findFirstByCruise_CruiseIdAndUser_UserIdAndStatusOrderByAssignedAtDesc(
                                    cruise.getCruiseId(), actor.getUserId(), "ACTIVE")
                            .filter(member -> "SHIP_MANAGER".equals(member.getRoleInCruise()))
                            .isPresent();
            case "SAFETY_CREW" -> submissionScope(cruise, actor);
            default -> false;
        };
    }

    private static final Set<String> SUBMITTABLE = Set.of("NOT_STARTED", "IN_PROGRESS", "RETURNED", "REJECTED", "STALE");

    private String submissionBlockReason(CruisePreparationSection section) {
        if (!SUBMITTABLE.contains(section.getStatus())) return null;
        var actor = currentActor();
        if (actor == null || !actor.hasAuthority("CRUISE_PREPARATION_SUBMIT")) return "SUBMIT_PERMISSION_REQUIRED";
        String role = sectionSubmitterRole(section.getSectionCode());
        if (!actor.hasRole(Role.valueOf(role))) return "SECTION_SUBMITTER_ROLE_REQUIRED";
        if (sectionSubmissionScope(section.getCruise(), section.getSectionCode(), actor)) return null;
        return switch (section.getSectionCode()) {
            case "TECHNICAL_NAUTICAL" -> "ASSIGNED_SHIP_MANAGER_REQUIRED";
            default -> "PREPARATION_COORDINATOR_REQUIRED";
        };
    }

    private List<String> allowedActions(
            CruisePreparationSection section, List<PreparationCheckItemDto> checks) {
        var actor = currentActor();
        var actions = new ArrayList<String>();
        if (sectionSubmissionScope(section.getCruise(), section.getSectionCode(), actor)
                && SUBMITTABLE.contains(section.getStatus()))
            actions.add("SUBMIT");
        if (permitted(actor, false)) {
            if ("SUBMITTED".equals(section.getStatus())
                    && !actor.getUserId().equals(section.getSubmittedByUserId())
                    && checks.stream()
                            .noneMatch(i -> Boolean.TRUE.equals(i.getRequired()) && !i.isReady()))
                actions.add("CONFIRM");
            if (Set.of("SUBMITTED", "CONFIRMED").contains(section.getStatus())
                    && !actor.getUserId().equals(section.getSubmittedByUserId()))
                actions.add("RETURN");
            if ("SUBMITTED".equals(section.getStatus())
                    && !actor.getUserId().equals(section.getSubmittedByUserId()))
                actions.add("REJECT");
        }
        return actions;
    }

    private Cruise lockedCruise(UUID id) {
        return cruiseRepository
                .findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", id));
    }

    private static final String BUDGET_SECTION = "OPERATING_BUDGET";
    private record BudgetReview(long revision, String status, UUID submittedBy, String reason) {}

    public enum BudgetDecision { APPROVE, REJECT }

    public record OperatingBudget(Long revision, String amountVnd, boolean canEdit,
            long reviewRevision, String status, String rejectionReason) {}

    public record SubmittedBudget(UUID cruiseId, String cruiseCode, String cruiseName,
            String amountVnd, String status, long reviewRevision, String reason,
            String submittedByName, boolean canReview) {}

    public record BudgetEvent(UUID eventId, String action, Instant occurredAt, String actorName,
            String reason, String amountVnd, String status) {}

    @Transactional(readOnly = true)
    public List<BudgetEvent> operatingBudgetHistory(UUID cruiseId, CasmsPrincipal actor) {
        if (!permitted(actor, false))
            throw new BusinessException(HttpStatus.FORBIDDEN, "PREPARATION_ACTION_FORBIDDEN",
                    "Chỉ người có quyền xét duyệt ngân sách được xem lịch sử.");
        operatingBudget(cruiseId, actor); // Reuse the independent, submitted-budget object scope.
        return jdbc.query("""
                SELECT p.event_id,p.action,p.occurred_at,p.reason,
                       p.snapshot->>'amountVnd' AS amount_vnd,p.snapshot->>'status' AS budget_status,
                       COALESCE(NULLIF(e.full_name,''),u.username) AS actor_name
                FROM "CruisePreparationEvent" p
                LEFT JOIN "User" u ON u.user_id=p.actor_id
                LEFT JOIN "Employee" e ON e.user_id=p.actor_id
                WHERE p.cruise_id=? AND p.section_code=?
                ORDER BY p.occurred_at DESC,p.event_id
                """, (rs,i) -> new BudgetEvent(rs.getObject("event_id",UUID.class),
                        rs.getString("action"),rs.getTimestamp("occurred_at").toInstant(),
                        rs.getString("actor_name"),rs.getString("reason"),rs.getString("amount_vnd"),
                        rs.getString("budget_status")),cruiseId,BUDGET_SECTION);
    }

    @Transactional(readOnly = true)
    public List<SubmittedBudget> operatingBudgetReviews(CasmsPrincipal actor) {
        if (!permitted(actor, false))
            throw new BusinessException(HttpStatus.FORBIDDEN, "PREPARATION_ACTION_FORBIDDEN",
                    "Chỉ người có quyền xét duyệt ngân sách được xem hàng chờ.");
        // Budget submission has its own scope and must not depend on plan submission.
        return jdbc.query("""
                SELECT c.cruise_id,c.cruise_code,c.cruise_name,c.operating_budget_vnd,
                       r.status,r.revision,r.reason,COALESCE(NULLIF(e.full_name,''),u.username) AS submitter_name,
                       r.status='SUBMITTED' AND c.status='DRAFT' AND r.submitted_by<>? AS can_review
                FROM "CruiseOperatingBudgetReview" r JOIN "Cruise" c USING(cruise_id)
                LEFT JOIN "User" u ON u.user_id=r.submitted_by
                LEFT JOIN "Employee" e ON e.user_id=r.submitted_by
                WHERE r.revision>0 AND r.submitted_by IS NOT NULL
                ORDER BY CASE WHEN r.status='SUBMITTED' THEN 0 ELSE 1 END,c.cruise_code
                """, (rs, i) -> new SubmittedBudget(rs.getObject("cruise_id", UUID.class),
                        rs.getString("cruise_code"), rs.getString("cruise_name"),
                        rs.getBigDecimal("operating_budget_vnd") == null ? null
                                : rs.getBigDecimal("operating_budget_vnd").toPlainString(),
                        rs.getString("status"), rs.getLong("revision"), rs.getString("reason"),
                        rs.getString("submitter_name"), rs.getBoolean("can_review")), actor.getUserId());
    }

    private BudgetReview budgetReview(UUID cruiseId) {
        return jdbc.query("""
                SELECT revision,status,submitted_by,reason FROM "CruiseOperatingBudgetReview"
                WHERE cruise_id=?
                """, (rs, i) -> new BudgetReview(rs.getLong(1), rs.getString(2),
                        rs.getObject(3, UUID.class), rs.getString(4)), cruiseId)
                .stream().findFirst().orElse(new BudgetReview(0L, "DRAFT", null, null));
    }

    private boolean canEditOperatingBudget(Cruise cruise, CasmsPrincipal actor, BudgetReview review) {
        return submissionScope(cruise, actor) && "DRAFT".equals(cruise.getStatus().name())
                && Set.of("DRAFT", "REJECTED").contains(review.status());
    }

    private OperatingBudget budgetView(Cruise cruise, CasmsPrincipal actor, BudgetReview review) {
        var amount = cruise.getOperatingBudgetVnd();
        return new OperatingBudget(cruise.getRevision(), amount == null ? null : amount.toPlainString(),
                canEditOperatingBudget(cruise, actor, review), review.revision(), review.status(), review.reason());
    }

    @Transactional(readOnly = true)
    public OperatingBudget operatingBudget(UUID cruiseId, CasmsPrincipal actor) {
        Cruise cruise = cruiseRepository.findById(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));
        var review = budgetReview(cruiseId);
        if (!submissionScope(cruise, actor) && !(permitted(actor, false) && review.revision() > 0)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "PREPARATION_SCOPE_FORBIDDEN",
                    "Ngân sách chỉ hiển thị cho người phụ trách tour.");
        }
        return budgetView(cruise, actor, review);
    }

    @Transactional
    public OperatingBudget saveOperatingBudget(
            UUID cruiseId, Long expectedRevision, BigDecimal amount, CasmsPrincipal actor) {
        Cruise cruise = lockedCruise(cruiseId);
        var review = budgetReview(cruiseId);
        if (!canEditOperatingBudget(cruise, actor, review)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "PREPARATION_BUDGET_EDIT_FORBIDDEN",
                    "Người phụ trách chỉ có thể cập nhật ngân sách khi đang chuẩn bị tour.");
        }
        if (!Objects.equals(cruise.getRevision(), expectedRevision)) {
            throw new BusinessException(HttpStatus.CONFLICT, "STALE_REVISION",
                    "Tour đã được cập nhật. Tải lại thông tin trước khi lưu ngân sách.");
        }
        if (amount == null || amount.signum() <= 0 || amount.precision() > 18 || amount.scale() != 0) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "PREPARATION_BUDGET_INVALID",
                    "Ngân sách phải là số VND nguyên dương, tối đa 18 chữ số.");
        }
        Long beforeRevision = cruise.getRevision();
        var beforeAmount = cruise.getOperatingBudgetVnd();
        cruise.setOperatingBudgetVnd(amount);
        cruiseRepository.saveAndFlush(cruise);
        var view = budgetView(cruise, actor, review);
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("previousAmountVnd", beforeAmount);
        snapshot.put("amountVnd", amount);
        snapshot.put("status", view.status());
        event(cruise, BUDGET_SECTION, cruise.getRevision(), "BUDGET_SAVE", actor.getUserId(), null,
                snapshot, review.status(), beforeRevision);
        return view;
    }

    @Transactional
    public OperatingBudget submitOperatingBudget(
            UUID cruiseId, long expectedRevision, long expectedReviewRevision, UUID actorId) {
        var actor = requireActor(actorId, true);
        var cruise = lockedCruise(cruiseId);
        var before = budgetReview(cruiseId);
        if (!canEditOperatingBudget(cruise, actor, before))
            throw new BusinessException(HttpStatus.FORBIDDEN, "PREPARATION_BUDGET_EDIT_FORBIDDEN",
                    "Chỉ người phụ trách có thể nộp ngân sách đang soạn hoặc bị từ chối.");
        if (!Objects.equals(cruise.getRevision(), expectedRevision) || before.revision() != expectedReviewRevision)
            throw new BusinessException(HttpStatus.CONFLICT, "STALE_REVISION", "Ngân sách đã thay đổi.");
        if (cruise.getOperatingBudgetVnd() == null)
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "PREPARATION_BUDGET_REQUIRED",
                    "Lưu ngân sách trước khi nộp.");
        var after = new BudgetReview(before.revision() + 1, "SUBMITTED", actorId, null);
        saveBudgetReview(cruise, before, after, "BUDGET_SUBMIT", actorId);
        return budgetView(cruise, actor, after);
    }

    @Transactional
    public OperatingBudget reviewOperatingBudget(UUID cruiseId, long expectedReviewRevision,
            BudgetDecision decision, String reason, UUID actorId) {
        var actor = requireActor(actorId, false);
        var cruise = lockedCruise(cruiseId);
        var before = budgetReview(cruiseId);
        if (before.revision() != expectedReviewRevision)
            throw new BusinessException(HttpStatus.CONFLICT, "STALE_REVISION", "Ngân sách đã thay đổi.");
        if (!"SUBMITTED".equals(before.status()) || !"DRAFT".equals(cruise.getStatus().name()))
            throw new BusinessException(HttpStatus.CONFLICT, "PREPARATION_BUDGET_NOT_SUBMITTED",
                    "Chỉ duyệt ngân sách đang chờ xem xét.");
        if (actorId.equals(before.submittedBy()))
            throw new BusinessException(HttpStatus.FORBIDDEN, "SELF_APPROVAL_FORBIDDEN",
                    "Người nộp không được tự duyệt ngân sách.");
        if (decision == null || decision == BudgetDecision.REJECT && (reason == null || reason.isBlank()))
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "PREPARATION_BUDGET_REASON_REQUIRED",
                    "Chọn quyết định và nhập lý do khi từ chối ngân sách.");
        var after = new BudgetReview(before.revision() + 1,
                decision == BudgetDecision.APPROVE ? "APPROVED" : "REJECTED", before.submittedBy(), reason);
        saveBudgetReview(cruise, before, after, "BUDGET_" + decision.name(), actorId);
        return budgetView(cruise, actor, after);
    }

    private void saveBudgetReview(Cruise cruise, BudgetReview before, BudgetReview after, String action, UUID actorId) {
        jdbc.update("""
                INSERT INTO "CruiseOperatingBudgetReview"(cruise_id,revision,status,submitted_by,reason)
                VALUES(?,?,?,?,?) ON CONFLICT (cruise_id) DO UPDATE SET revision=excluded.revision,
                    status=excluded.status,submitted_by=excluded.submitted_by,reason=excluded.reason
                """, cruise.getCruiseId(), after.revision(), after.status(), after.submittedBy(), after.reason());
        event(cruise, BUDGET_SECTION, after.revision(), action, actorId, after.reason(),
                Map.of("amountVnd", cruise.getOperatingBudgetVnd(), "status", after.status()),
                before.status(), before.revision());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> coordinatorCandidates(
            UUID cruiseId, CasmsPrincipal principal) {
        var actor = currentActor();
        if (actor == null
                || principal == null
                || !actor.getUserId().equals(principal.getUserId())
                || !actor.hasRole(com.casms.identity.enums.Role.OPERATIONS_MANAGER)
                || !actor.hasAuthority("CRUISE_STAFF_ASSIGN"))
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "PREPARATION_ACTION_FORBIDDEN",
                    "OPS coordinator assignment permission required");
        cruiseRepository
                .findById(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));
        return jdbc.queryForList(
                "SELECT u.user_id AS id,u.username AS name FROM \"User\" u WHERE u.status='ACTIVE'"
                    + " AND EXISTS(SELECT 1 FROM \"UserRole\" r WHERE r.user_id=u.user_id AND"
                    + " r.role='CRUISE_MANAGER') ORDER BY u.username,u.user_id");
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> events(UUID cruiseId, CasmsPrincipal principal) {
        var actor = currentActor();
        if (actor == null
                || principal == null
                || !actor.getUserId().equals(principal.getUserId())
                || !actor.hasAuthority("CRUISE_PREPARATION_READ"))
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "PREPARATION_ACTION_FORBIDDEN",
                    "Preparation read permission required");
        var cruise = cruiseRepository
                .findById(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));
        boolean budgetVisible = submissionScope(cruise, actor)
                || permitted(actor, false) && budgetReview(cruiseId).revision() > 0;
        return jdbc.queryForList("""
                SELECT event_id AS "eventId", section_code AS "sectionCode", revision,
                    previous_revision AS "previousRevision", previous_status AS "previousStatus",
                    action, actor_id AS "actorId", occurred_at AS "occurredAt", reason,
                    snapshot::text AS snapshot
                FROM "CruisePreparationEvent"
                WHERE cruise_id=? AND (? OR section_code IS DISTINCT FROM ?)
                ORDER BY occurred_at,event_id
                """, cruiseId, budgetVisible, BUDGET_SECTION).stream().map(row -> {
            var result = new LinkedHashMap<>(row);
            try {
                result.put("snapshot", mapper.readTree((String) row.get("snapshot")));
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                throw new IllegalStateException("Không đọc được lịch sử hồ sơ chuẩn bị", ex);
            }
            return (Map<String, Object>) result;
        }).toList();
    }

    private void event(
            Cruise cruise,
            String section,
            Long revision,
            String action,
            UUID actor,
            String reason,
            Object snapshot) {
        event(cruise, section, revision, action, actor, reason, snapshot, null, null);
    }

    private void event(Cruise cruise, String section, Long revision, String action, UUID actor,
            String reason, Object snapshot, String previousStatus, Long previousRevision) {
        try {
            jdbc.update(
                    """
                    INSERT INTO "CruisePreparationEvent"
                    (event_id,cruise_id,section_code,revision,action,actor_id,reason,snapshot,
                        previous_status,previous_revision)
                    VALUES(?,?,?,?,?,?,?,?::jsonb,?,?)
                    """,
                    UUID.randomUUID(),
                    cruise.getCruiseId(),
                    section,
                    revision,
                    action,
                    actor,
                    reason,
                    mapper.writeValueAsString(snapshot),
                    previousStatus,
                    previousRevision);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Transactional
    public CruisePreparationResponse assignCoordinator(
            UUID cruiseId, UUID coordinatorId, Long expectedRevision, UUID actorId) {
        var actor = currentActor();
        if (actor == null
                || !Objects.equals(actorId, actor.getUserId())
                || !actor.hasRole(com.casms.identity.enums.Role.OPERATIONS_MANAGER)
                || !actor.hasAuthority("CRUISE_STAFF_ASSIGN"))
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "PREPARATION_ACTION_FORBIDDEN",
                    "Only OPS may assign preparation coordinator");
        Cruise cruise = lockedCruise(cruiseId);
        if (expectedRevision == null || !expectedRevision.equals(cruise.getRevision()))
            throw new BusinessException(
                    HttpStatus.CONFLICT, "STALE_REVISION", "Cruise revision changed");
        var coordinator =
                userRepository
                        .findById(coordinatorId)
                        .orElseThrow(() -> new ResourceNotFoundException("User", coordinatorId));
        if (!coordinator.hasRole(com.casms.identity.enums.Role.CRUISE_MANAGER)
                || coordinator.getStatus() != com.casms.identity.enums.UserStatus.ACTIVE)
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "PREPARATION_COORDINATOR_INVALID",
                    "Coordinator must be an active CRM");
        if (cruise.getPreparationCoordinator() != null
                && coordinatorId.equals(cruise.getPreparationCoordinator().getUserId()))
            return getPreparationDashboard(cruiseId, actor);
        UUID previous =
                cruise.getPreparationCoordinator() == null
                        ? null
                        : cruise.getPreparationCoordinator().getUserId();
        cruise.setPreparationCoordinator(coordinator);
        cruiseRepository.saveAndFlush(cruise);
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("previousCoordinatorId", previous);
        snapshot.put("coordinatorId", coordinatorId);
        event(cruise, null, cruise.getRevision(), "ASSIGN_COORDINATOR", actorId, null, snapshot);
        return getPreparationDashboard(cruiseId, actor);
    }

    private CasmsPrincipal requireActor(UUID actorId, boolean submission) {
        var authentication =
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                        .getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof CasmsPrincipal principal)
                || actorId == null
                || !actorId.equals(principal.getUserId()))
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED,
                    "AUTH_REQUIRED",
                    "Authenticated preparation actor required");
        String permission = submission ? "CRUISE_PREPARATION_SUBMIT" : "CRUISE_PREPARATION_CONFIRM";
        if ((!submission && !principal.hasRole(Role.OPERATIONS_MANAGER))
                || !principal.hasAuthority(permission))
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "PREPARATION_ACTION_FORBIDDEN",
                    "Preparation role and effective permission required");
        return principal;
    }

    private static final List<SectionTemplate> TEMPLATES =
            List.of(
                    new SectionTemplate(
                            "TECHNICAL_NAUTICAL", "Kỹ thuật Hàng hải & Tàu", "SHIP_MANAGER"),
                    new SectionTemplate("HOTEL_CABIN", "Cabin phục vụ khách", "CRUISE_MANAGER"),
                    new SectionTemplate(
                            "ENTERTAINMENT_SHORE",
                            "Hoạt động Giải trí & Tour bờ",
                            "CRUISE_MANAGER"),
                    new SectionTemplate(
                            "SAFETY_CREW", "Nhân sự phục vụ trên tàu", "OPERATIONS_MANAGER"));

    private record SectionTemplate(String code, String name, String ownerRole) {}

    private static final Set<String> REVIEW_STATES = Set.of("SUBMITTED", "CONFIRMED", "RETURNED", "REJECTED", "STALE");

    private static String sectionSubmitterRole(String sectionCode) {
        return "TECHNICAL_NAUTICAL".equals(sectionCode) ? "SHIP_MANAGER" : "CRUISE_MANAGER";
    }

    public record GroupReviewSummary(UUID cruiseId, String cruiseCode, String cruiseName,
            String sectionCode, String sectionName, String status, Instant submittedAt,
            UUID shipId, String shipName, Instant departureDatetime) {}

    public record SubmittedCheck(String name, boolean ready, boolean required) {}

    public record GroupReview(GroupReviewSummary summary, long revision, String evidence,
            String returnReason, List<SubmittedCheck> checks, List<String> allowedActions) {}

    @Transactional(readOnly = true)
    public List<GroupReviewSummary> submittedGroupReviews(UUID actorId) {
        requireActor(actorId, false);
        return preparationSectionRepository.findSubmittedReviews(
                TEMPLATES.stream().map(SectionTemplate::code).toList(), REVIEW_STATES)
                .stream().map(this::reviewSummary).toList();
    }

    @Transactional(readOnly = true)
    public GroupReview submittedGroupReview(UUID cruiseId, String sectionCode, UUID actorId) {
        requireActor(actorId, false);
        var section = preparationSectionRepository.findByCruiseCruiseIdAndSectionCode(cruiseId, sectionCode)
                .filter(s -> s.getSubmittedAt() != null && REVIEW_STATES.contains(s.getStatus()))
                .filter(s -> TEMPLATES.stream().anyMatch(template -> template.code().equals(sectionCode)))
                .orElseThrow(() -> new ResourceNotFoundException("Hồ sơ nhóm đã gửi", sectionCode));
        var checks = jdbc.query("""
                SELECT snapshot FROM "CruisePreparationEvent"
                WHERE cruise_id = ? AND section_code = ? AND action = 'SUBMIT'
                ORDER BY occurred_at DESC, revision DESC LIMIT 1
                """, (rs, index) -> submittedChecks(rs.getString(1)), cruiseId, sectionCode)
                .stream().findFirst().orElse(List.of());
        return new GroupReview(reviewSummary(section), section.getRevision(), section.getEvidence(),
                section.getReturnReason(), checks, allowedActions(section,
                        computeCheckItems(section.getCruise(), sectionCode)).stream()
                        .filter(action -> !action.equals("SUBMIT")).toList());
    }

    private List<SubmittedCheck> submittedChecks(String snapshot) {
        if (snapshot == null) return List.of();
        try {
            var checks = new ArrayList<SubmittedCheck>();
            for (var item : mapper.readTree(snapshot).path("checkItems")) {
                checks.add(new SubmittedCheck(item.path("itemName").asText(),
                        item.path("ready").asBoolean(), item.path("required").asBoolean()));
            }
            return checks;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Không đọc được hồ sơ nhóm đã gửi", e);
        }
    }

    private GroupReviewSummary reviewSummary(CruisePreparationSection section) {
        var cruise = section.getCruise();
        var ship = cruise.getShip();
        return new GroupReviewSummary(cruise.getCruiseId(), cruise.getCruiseCode(), cruise.getCruiseName(),
                section.getSectionCode(), section.getSectionName(), section.getStatus(), section.getSubmittedAt(),
                ship != null ? ship.getShipId() : null, ship != null ? ship.getShipName() : null,
                cruise.getDepartureDatetime());
    }

    @Transactional(readOnly = true)
    public CruisePreparationResponse getPreparationDashboard(UUID cruiseId) {
        return getPreparationDashboard(cruiseId, null);
    }

    @Transactional(readOnly = true)
    public CruisePreparationResponse getPreparationDashboard(
            UUID cruiseId, CasmsPrincipal principal) {
        Cruise cruise =
                cruiseRepository
                        .findById(cruiseId)
                        .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));

        // A legacy Cruise can predate preparation records. The read model still exposes the
        // standard work areas, with current checks and NOT_STARTED defaults, without writes.
        List<CruisePreparationSection> sections =
                new ArrayList<>(
                        preparationSectionRepository
                                .findByCruiseCruiseIdOrderBySectionCodeAsc(cruiseId)
                                .stream()
                                .filter(
                                        section ->
                                                TEMPLATES.stream()
                                                        .anyMatch(
                                                                template ->
                                                                        template.code()
                                                                                .equals(
                                                                                        section
                                                                                                .getSectionCode())))
                                .toList());
        Set<String> existingCodes = new HashSet<>();
        sections.forEach(section -> existingCodes.add(section.getSectionCode()));
        for (SectionTemplate template : TEMPLATES) {
            if (!existingCodes.contains(template.code()))
                sections.add(newSection(cruise, template));
        }
        sections.sort(Comparator.comparing(CruisePreparationSection::getSectionCode));
        List<CruisePreparationSectionResponse> sectionResponses = new ArrayList<>();

        boolean allConfirmed = sections.size() == TEMPLATES.size();
        for (CruisePreparationSection section : sections) {
            if (!"CONFIRMED".equalsIgnoreCase(section.getStatus())) {
                allConfirmed = false;
            }
            List<PreparationCheckItemDto> checkItems =
                    computeCheckItems(cruise, section.getSectionCode());
            if (checkItems.stream()
                    .anyMatch(i -> Boolean.TRUE.equals(i.getRequired()) && !i.isReady()))
                allConfirmed = false;
            sectionResponses.add(mapToResponse(section, checkItems));
        }

        String overallStatus =
                allConfirmed
                        ? "CONFIRMED"
                        : (sections.size() == TEMPLATES.size()
                                        && sectionResponses.stream()
                                                .allMatch(
                                                        s ->
                                                                "SUBMITTED".equals(s.getStatus())
                                                                        || "CONFIRMED"
                                                                                .equals(
                                                                                        s
                                                                                                .getStatus()))
                                ? "READY_FOR_SIGNOFF"
                                : "INCOMPLETE");

        return CruisePreparationResponse.builder()
                .cruiseId(cruise.getCruiseId())
                .cruiseCode(cruise.getCruiseCode())
                .cruiseName(cruise.getCruiseName())
                .cruiseStatus(cruise.getStatus().name())
                .coordinatorUserId(
                        cruise.getPreparationCoordinator() == null
                                ? null
                                : cruise.getPreparationCoordinator().getUserId())
                .coordinatorUsername(
                        cruise.getPreparationCoordinator() == null
                                ? null
                                : cruise.getPreparationCoordinator().getUsername())
                .cruiseRevision(cruise.getRevision())
                .operatingBudgetVnd(cruise.getOperatingBudgetVnd() == null
                        ? null : cruise.getOperatingBudgetVnd().toPlainString())
                .canAssignCoordinator(
                        principal != null
                                && principal.hasRole(
                                        com.casms.identity.enums.Role.OPERATIONS_MANAGER)
                                && principal.hasAuthority("CRUISE_STAFF_ASSIGN"))
                .overallStatus(overallStatus)
                .allConfirmed(allConfirmed)
                .sections(sectionResponses)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    @Transactional
    public void initializeSections(Cruise cruise) {
        ensureSectionsInitialized(cruise);
    }

    @Transactional
    public void submitDossier(
            UUID cruiseId, String evidence, Map<String, Long> expectedRevisions, UUID actorId) {
        var actor = requireActor(actorId, true);
        Cruise cruise = lockedCruise(cruiseId);
        if (!submissionScope(cruise, actor))
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "PREPARATION_CRUISE_SCOPE_FORBIDDEN",
                    "Only the assigned preparation coordinator may submit the dossier");
        var coordinatorSections = TEMPLATES.stream()
                .filter(t -> Role.CRUISE_MANAGER.name().equals(t.ownerRole())).toList();
        if (expectedRevisions == null
                || coordinatorSections.stream().anyMatch(t -> !expectedRevisions.containsKey(t.code())))
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "PREPARATION_REVISIONS_REQUIRED",
                    "Read the coordinator's preparation sections before submitting");
        programPolicy.requireComplete(cruiseId);
        ensureSectionsInitialized(cruise);
        List<CruisePreparationSection> pending = new ArrayList<>();
        for (var template : coordinatorSections) {
            var section =
                    preparationSectionRepository
                            .findByCruiseCruiseIdAndSectionCodeForUpdate(cruiseId, template.code())
                            .orElseThrow(
                                    () ->
                                            new ResourceNotFoundException(
                                                    "CruisePreparationSection", template.code()));
            if (!Objects.equals(expectedRevisions.get(template.code()), section.getRevision()))
                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "STALE_REVISION",
                        "Preparation evidence changed before submission");
            if (!Set.of("SUBMITTED", "CONFIRMED").contains(section.getStatus())) {
                if (!SUBMITTABLE.contains(section.getStatus()))
                    throw new BusinessException(
                            HttpStatus.CONFLICT,
                            "INVALID_SECTION_STATUS",
                            "Preparation section cannot be submitted");
                pending.add(section);
            }
        }
        if (!pending.isEmpty() && (evidence == null || evidence.isBlank()))
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "PREPARATION_EVIDENCE_REQUIRED",
                    "Preparation evidence required");
        // Joins the plan submission transaction: any later failure rolls back every section and its
        // event.
        // Existing submitted/confirmed groups retain their original evidence and decisions.
        for (var section : pending) {
            var request = new CruisePreparationSubmitRequest();
            request.setExpectedRevision(section.getRevision());
            request.setEvidence(evidence.trim());
            submitSection(cruiseId, section.getSectionCode(), request, actorId, Set.of());
        }
    }

    @Transactional
    public CruisePreparationSectionResponse submitSection(
            UUID cruiseId,
            String sectionCode,
            CruisePreparationSubmitRequest request,
            UUID actorId,
            Set<String> roles) {
        if (TEMPLATES.stream().noneMatch(template -> template.code().equals(sectionCode)))
            throw new ResourceNotFoundException("Active CruisePreparationSection", sectionCode);
        var actor = requireActor(actorId, true);
        Cruise cruise = lockedCruise(cruiseId);
        if (!sectionSubmissionScope(cruise, sectionCode, actor))
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "PREPARATION_CRUISE_SCOPE_FORBIDDEN",
                    "Only the assigned preparation section owner can submit");
        ensureSectionsInitialized(cruise);
        CruisePreparationSection section =
                preparationSectionRepository
                        .findByCruiseCruiseIdAndSectionCodeForUpdate(cruiseId, sectionCode)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "CruisePreparationSection", sectionCode));

        if (request == null
                || request.getExpectedRevision() == null
                || !request.getExpectedRevision().equals(section.getRevision())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "STALE_REVISION",
                    String.format(
                            "Phân hệ chuẩn bị đã bị cập nhật (revision hiện tại: %d, mong đợi: %d)",
                            section.getRevision(),
                            request != null ? request.getExpectedRevision() : null));
        }

        if (request.getEvidence() == null || request.getEvidence().isBlank())
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "PREPARATION_EVIDENCE_REQUIRED",
                    "Preparation evidence required");
        if ("SUBMITTED".equalsIgnoreCase(section.getStatus()))
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "SECTION_ALREADY_SUBMITTED",
                    "Preparation section already submitted");

        if ("CONFIRMED".equalsIgnoreCase(section.getStatus())) {
            throw new BusinessException(
                    "SECTION_ALREADY_CONFIRMED",
                    "Phân hệ này đã được phê duyệt xác nhận (CONFIRMED)");
        }

        if (!SUBMITTABLE.contains(section.getStatus()))
            throw new BusinessException(HttpStatus.CONFLICT, "INVALID_SECTION_STATUS",
                    "Preparation section cannot be submitted");
        String previousStatus = section.getStatus();
        Long previousRevision = section.getRevision();
        section.setStatus("SUBMITTED");
        section.setEvidence(request.getEvidence());
        if (request.getNotes() != null) {
            section.setNotes(request.getNotes());
        }
        section.setSubmittedByUserId(actorId);
        section.setSubmittedAt(Instant.now());
        section.setReturnReason(null);
        section.setReturnedAt(null);
        section.setReturnedByUserId(null);
        section.setConfirmedByUserId(null);
        section.setConfirmedByUsername(null);
        section.setConfirmedAt(null);

        section = preparationSectionRepository.saveAndFlush(section);
        event(
                cruise,
                sectionCode,
                section.getRevision(),
                "SUBMIT",
                actorId,
                null,
                mapToResponse(section, computeCheckItems(cruise, sectionCode)),
                previousStatus, previousRevision);
        log.info(
                "Submitted preparation section {} for cruise {} by user {}",
                sectionCode,
                cruiseId,
                actorId);
        return mapToResponse(section, computeCheckItems(section.getCruise(), sectionCode));
    }

    @Transactional
    public CruisePreparationSectionResponse confirmSection(
            UUID cruiseId,
            String sectionCode,
            CruisePreparationConfirmRequest request,
            UUID actorId,
            String actorUsername) {
        if (TEMPLATES.stream().noneMatch(template -> template.code().equals(sectionCode)))
            throw new ResourceNotFoundException("Active CruisePreparationSection", sectionCode);
        CasmsPrincipal actor = requireActor(actorId, false);
        lockedCruise(cruiseId);
        CruisePreparationSection section =
                preparationSectionRepository
                        .findByCruiseCruiseIdAndSectionCodeForUpdate(cruiseId, sectionCode)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "CruisePreparationSection", sectionCode));

        if (request == null
                || request.getExpectedRevision() == null
                || !request.getExpectedRevision().equals(section.getRevision())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "STALE_REVISION",
                    String.format(
                            "Phân hệ chuẩn bị đã bị cập nhật (revision hiện tại: %d, mong đợi: %d)",
                            section.getRevision(),
                            request != null ? request.getExpectedRevision() : null));
        }

        if (!"SUBMITTED".equalsIgnoreCase(section.getStatus())) {
            throw new BusinessException(
                    "SECTION_NOT_SUBMITTED",
                    "Chỉ có thể xác nhận phân hệ đang ở trạng thái SUBMITTED (hiện tại: "
                            + section.getStatus()
                            + ")");
        }

        // Maker-Checker: người nộp bằng chứng không được tự duyệt cho chính mình
        if (section.getSubmittedByUserId() != null
                && section.getSubmittedByUserId().equals(actorId)) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "SELF_APPROVAL_FORBIDDEN",
                    "Người nộp hồ sơ chuẩn bị không được tự phê duyệt xác nhận phân hệ này");
        }

        // BE-H05: Chặn confirm khi item bắt buộc chưa đạt tiêu chuẩn
        List<PreparationCheckItemDto> checkItems =
                computeCheckItems(section.getCruise(), sectionCode);
        List<String> failedRequired =
                checkItems.stream()
                        .filter(item -> Boolean.TRUE.equals(item.getRequired()) && !item.isReady())
                        .map(PreparationCheckItemDto::getItemName)
                        .toList();
        if (!failedRequired.isEmpty()) {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "SECTION_PREREQUISITES_NOT_MET",
                    "Không thể xác nhận phân hệ "
                            + section.getSectionName()
                            + " do còn hạng mục bắt buộc chưa đạt tiêu chuẩn: "
                            + String.join(", ", failedRequired));
        }

        String previousStatus = section.getStatus();
        Long previousRevision = section.getRevision();
        section.setStatus("CONFIRMED");
        section.setConfirmedByUserId(actorId);
        section.setConfirmedByUsername(actor.getUsername());
        section.setConfirmedAt(Instant.now());
        if (request != null && request.getNotes() != null) {
            String existingNotes = section.getNotes() != null ? section.getNotes() + " | " : "";
            section.setNotes(existingNotes + "Xác nhận: " + request.getNotes());
        }

        section = preparationSectionRepository.saveAndFlush(section);
        var response = mapToResponse(section, checkItems, previousRevision);
        event(
                section.getCruise(),
                sectionCode,
                section.getRevision(),
                "CONFIRM",
                actorId,
                request.getNotes(),
                response,
                previousStatus, previousRevision);
        log.info(
                "Confirmed preparation section {} for cruise {} by user {}",
                sectionCode,
                cruiseId,
                actorUsername);
        return response;
    }

    @Transactional
    public CruisePreparationSectionResponse returnSection(
            UUID cruiseId,
            String sectionCode,
            CruisePreparationReturnRequest request,
            UUID actorId) {
        return decideSection(cruiseId, sectionCode, request, actorId, false);
    }

    @Transactional
    public CruisePreparationSectionResponse rejectSection(
            UUID cruiseId, String sectionCode, CruisePreparationReturnRequest request, UUID actorId) {
        return decideSection(cruiseId, sectionCode, request, actorId, true);
    }

    private CruisePreparationSectionResponse decideSection(
            UUID cruiseId, String sectionCode, CruisePreparationReturnRequest request,
            UUID actorId, boolean reject) {
        if (TEMPLATES.stream().noneMatch(template -> template.code().equals(sectionCode)))
            throw new ResourceNotFoundException("Active CruisePreparationSection", sectionCode);
        requireActor(actorId, false);
        lockedCruise(cruiseId);
        CruisePreparationSection section =
                preparationSectionRepository
                        .findByCruiseCruiseIdAndSectionCodeForUpdate(cruiseId, sectionCode)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "CruisePreparationSection", sectionCode));

        if (request == null
                || request.getExpectedRevision() == null
                || !request.getExpectedRevision().equals(section.getRevision())) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "STALE_REVISION",
                    String.format(
                            "Phân hệ chuẩn bị đã bị cập nhật (revision hiện tại: %d, mong đợi: %d)",
                            section.getRevision(),
                            request != null ? request.getExpectedRevision() : null));
        }

        if (request.getReason() == null || request.getReason().isBlank())
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "PREPARATION_RETURN_REASON_REQUIRED",
                    "Return reason required");

        if (actorId.equals(section.getSubmittedByUserId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "SELF_APPROVAL_FORBIDDEN",
                    "Người nộp hồ sơ không thể tự quyết định nhóm mình đã gửi.");
        }

        if (!"SUBMITTED".equals(section.getStatus())
                && (reject || !"CONFIRMED".equals(section.getStatus()))) {
            throw new BusinessException(
                    HttpStatus.CONFLICT, "INVALID_SECTION_STATUS",
                    "Không thể quyết định hồ sơ ở trạng thái " + section.getStatus());
        }

        String previousStatus = section.getStatus();
        Long previousRevision = section.getRevision();
        Long submittedRevision = submittedRevision(section);
        section.setStatus(reject ? "REJECTED" : "RETURNED");
        section.setReturnedByUserId(actorId);
        section.setReturnReason(request.getReason());
        section.setReturnedAt(Instant.now());
        if (request.getNotes() != null) {
            section.setNotes(request.getNotes());
        }

        section = preparationSectionRepository.saveAndFlush(section);
        var response = mapToResponse(section, computeCheckItems(section.getCruise(), sectionCode), submittedRevision);
        event(
                section.getCruise(),
                sectionCode,
                section.getRevision(),
                reject ? "REJECT" : "RETURN",
                actorId,
                request.getReason(),
                response,
                previousStatus, previousRevision);
        log.info(
                "Reviewed preparation section {} for cruise {} by user {}",
                sectionCode,
                cruiseId,
                actorId);
        return response;
    }

    @Transactional
    public void invalidateSignoffs(UUID cruiseId, String reason) {
        invalidateSignoffs(cruiseId, null, reason);
    }

    @Transactional
    public void invalidateSignoffs(UUID cruiseId, String targetSectionCode, String reason) {
        List<CruisePreparationSection> sections =
                preparationSectionRepository.findByCruiseCruiseIdOrderBySectionCodeAsc(cruiseId);
        for (CruisePreparationSection section : sections) {
            if (targetSectionCode != null
                    && !targetSectionCode.equalsIgnoreCase(section.getSectionCode())) {
                continue;
            }
            if (Set.of("SUBMITTED", "CONFIRMED").contains(section.getStatus())) {
                String previousStatus = section.getStatus();
                Long previousRevision = section.getRevision();
                section.setStatus("STALE");
                section.setReturnReason(
                        "Hủy xác nhận tự động do thay đổi thông tin chuyến khởi hành: " + reason);
                section.setReturnedAt(Instant.now());
                var actor = currentActor();
                section.setReturnedByUserId(actor == null ? null : actor.getUserId());
                preparationSectionRepository.saveAndFlush(section);
                event(
                        section.getCruise(),
                        section.getSectionCode(),
                        section.getRevision(),
                        "INVALIDATE",
                        actor == null ? null : actor.getUserId(),
                        reason,
                        mapToResponse(section, computeCheckItems(section.getCruise(), section.getSectionCode())),
                        previousStatus, previousRevision);
                log.info(
                        "Invalidated confirmation for preparation section {} on cruise {}",
                        section.getSectionCode(),
                        cruiseId);
            }
        }
    }

    private CruisePreparationSection newSection(Cruise cruise, SectionTemplate template) {
        CruisePreparationSection section = new CruisePreparationSection();
        section.setCruise(cruise);
        section.setSectionCode(template.code());
        section.setSectionName(template.name());
        section.setOwnerRole(template.ownerRole());
        section.setStatus("NOT_STARTED");
        section.setRevision(0L);
        return section;
    }

    private void ensureSectionsInitialized(Cruise cruise) {
        Set<String> existingCodes = new HashSet<>();
        preparationSectionRepository
                .findByCruiseCruiseIdOrderBySectionCodeAsc(cruise.getCruiseId())
                .forEach(section -> existingCodes.add(section.getSectionCode()));
        for (SectionTemplate template : TEMPLATES) {
            if (!existingCodes.contains(template.code())) {
                preparationSectionRepository.save(newSection(cruise, template));
            }
        }
        preparationSectionRepository.flush();
    }

    private List<PreparationCheckItemDto> computeCheckItems(Cruise cruise, String sectionCode) {
        List<PreparationCheckItemDto> items = new ArrayList<>();
        UUID cruiseId = cruise.getCruiseId();
        Ship ship = cruise.getShip();

        switch (sectionCode) {
            case "TECHNICAL_NAUTICAL" -> {
                boolean shipAssigned = (ship != null);
                items.add(
                        PreparationCheckItemDto.builder()
                                .itemCode("SHIP_ASSIGNED")
                                .itemName("Gán tàu hợp lệ")
                                .ready(shipAssigned)
                                .required(true)
                                .state(shipAssigned ? "PASSED" : "BLOCKED")
                                .evidence(
                                        shipAssigned
                                                ? "Đã gán: " + ship.getShipName()
                                                : "Chưa gán tàu")
                                .details(
                                        shipAssigned
                                                ? "Tàu ID: " + ship.getShipId()
                                                : "Chưa có tàu được gán")
                                .build());
                boolean shipActive = (ship != null && ShipStatus.isOperational(ship.getStatus()));
                items.add(
                        PreparationCheckItemDto.builder()
                                .itemCode("SHIP_STATUS_ACTIVE")
                                .itemName("Tàu ở trạng thái ACTIVE")
                                .ready(shipActive)
                                .required(true)
                                .state(shipActive ? "PASSED" : "BLOCKED")
                                .evidence(
                                        ship != null
                                                ? "Trạng thái: " + shipStatusLabel(ship.getStatus() != null ? ship.getStatus().name() : null)
                                                : "Chưa có tàu")
                                .details(
                                        shipActive
                                                ? "Tàu đang hoạt động"
                                                : "Tàu không ở trạng thái ACTIVE")
                                .build());
                long decks = ship != null ? deckRepository.countByShipShipId(ship.getShipId()) : 0;
                long cabins =
                        ship != null ? cabinRepository.countByShipShipId(ship.getShipId()) : 0;
                boolean configReady = decks > 0 && cabins > 0;
                items.add(
                        PreparationCheckItemDto.builder()
                                .itemCode("SHIP_CONFIG")
                                .itemName("Cấu hình boong và cabin")
                                .ready(configReady)
                                .required(true)
                                .state(configReady ? "PASSED" : "BLOCKED")
                                .evidence("Tầng boong: " + decks + ", Cabin: " + cabins)
                                .details(
                                        configReady
                                                ? "Boong và phòng đã được cấu hình"
                                                : "Thiếu boong hoặc phòng")
                                .build());
            }
            case "HOTEL_CABIN" -> {
                long totalCabins =
                        ship != null ? cabinRepository.countByShipShipId(ship.getShipId()) : 0;
                long availableCabins =
                        ship != null
                                ? cabinRepository.countByShipShipIdAndStatus(
                                        ship.getShipId(), CabinStatus.AVAILABLE)
                                : 0;
                boolean cabinReady = totalCabins > 0 && availableCabins > 0;
                items.add(
                        PreparationCheckItemDto.builder()
                                .itemCode("CABIN_INVENTORY")
                                .itemName("Cabin khả dụng trên tàu")
                                .ready(cabinReady)
                                .required(true)
                                .state(cabinReady ? "PASSED" : "BLOCKED")
                                .evidence(
                                        cabinReady
                                                ? "Khả dụng: "
                                                        + availableCabins
                                                        + "/"
                                                        + totalCabins
                                                        + " phòng"
                                                : "Không có phòng khả dụng trên tàu")
                                .details(
                                        "Tổng phòng: "
                                                + totalCabins
                                                + ", Khả dụng: "
                                                + availableCabins)
                                .build());
            }
            case "ENTERTAINMENT_SHORE" -> {
                long excursions = shoreExcursionRepository.countByCruiseId(cruiseId);
                long activities = onboardActivityRepository.countByCruise_CruiseId(cruiseId);
                boolean entReady = excursions > 0 || activities > 0;
                items.add(
                        PreparationCheckItemDto.builder()
                                .itemCode("SHORE_EXCURSIONS")
                                .itemName("Hoạt động Giải trí & Tour bờ")
                                .ready(entReady)
                                .required(true)
                                .state(entReady ? "PASSED" : "BLOCKED")
                                .evidence(
                                        entReady
                                                ? "Tour bờ: "
                                                        + excursions
                                                        + ", Hoạt động onboard: "
                                                        + activities
                                                : "Chưa cấu hình tour bờ hoặc hoạt động giải trí")
                                .details(
                                        "Tour bờ liên kết: "
                                                + excursions
                                                + ", Hoạt động trên tàu: "
                                                + activities)
                                .build());
            }
            case "SAFETY_CREW" -> {
                boolean onboardAssigned = cruiseStaffRepository.existsActiveOnboardStaff(cruiseId);
                long activeCrew =
                        cruiseStaffRepository.countByCruise_CruiseIdAndStatus(cruiseId, "ACTIVE");
                boolean crewReady = onboardAssigned && staffingWorkflow.ready(cruiseId);
                items.add(
                        PreparationCheckItemDto.builder()
                                .itemCode("ONBOARD_STAFF_ASSIGNED")
                                .itemName("Phân công nhân viên phục vụ trên tàu")
                                .ready(crewReady)
                                .required(true)
                                .state(crewReady ? "PASSED" : "BLOCKED")
                                .evidence(
                                        onboardAssigned
                                                ? "Đã phân công nhân viên phục vụ trên tàu (Tổng thuyền viên"
                                                      + " hoạt động: "
                                                        + activeCrew
                                                        + ")"
                                                : "Chưa phân công nhân viên phục vụ trên tàu")
                                .details(
                                        "Nhân viên phục vụ trên tàu: "
                                                + (onboardAssigned ? "SẴN SÀNG" : "CHƯA PHÂN CÔNG")
                                                + ", Thuyền viên: "
                                                + activeCrew + ", Nhu cầu nhân sự đã được duyệt: " + crewReady)
                                .build());
            }
            default -> {}
        }
        return items;
    }

    private static String shipStatusLabel(String status) {
        if (status == null) return "Chưa xác định";
        return switch (status) {
            case "DRAFT" -> "Đang thiết lập";
            case "READY" -> "Sẵn sàng hoạt động";
            case "IN_SERVICE" -> "Đang hoạt động";
            case "MAINTENANCE" -> "Đang bảo trì";
            case "SUSPENDED" -> "Tạm dừng hoạt động";
            case "RETIRED" -> "Đã ngừng hoạt động";
            default -> status;
        };
    }

    private Long submittedRevision(CruisePreparationSection section) {
        if (section.getSubmittedByUserId() == null) return null;
        if ("SUBMITTED".equals(section.getStatus())) return section.getRevision();
        return jdbc.queryForList("""
                SELECT revision FROM "CruisePreparationEvent"
                WHERE cruise_id=? AND section_code=? AND action='SUBMIT' AND revision<=?
                ORDER BY revision DESC LIMIT 1
                """, Long.class, section.getCruise().getCruiseId(), section.getSectionCode(), section.getRevision())
                .stream().findFirst().orElse(null);
    }

    private CruisePreparationSectionResponse mapToResponse(
            CruisePreparationSection section, List<PreparationCheckItemDto> checkItems) {
        return mapToResponse(section, checkItems, submittedRevision(section));
    }

    private CruisePreparationSectionResponse mapToResponse(
            CruisePreparationSection section, List<PreparationCheckItemDto> checkItems, Long submittedRevision) {
        var missing = checkItems.stream()
                .filter(item -> Boolean.TRUE.equals(item.getRequired()) && !item.isReady())
                .map(PreparationCheckItemDto::getItemCode).toList();
        String approval = switch (section.getStatus()) {
            case "SUBMITTED" -> "PENDING";
            case "CONFIRMED", "RETURNED", "REJECTED", "STALE" -> section.getStatus();
            default -> "NOT_REVIEWED";
        };
        return CruisePreparationSectionResponse.builder()
                .sectionCode(section.getSectionCode())
                .sectionName(TEMPLATES.stream().filter(template -> template.code().equals(section.getSectionCode()))
                        .map(SectionTemplate::name).findFirst().orElse(section.getSectionName()))
                .ownerRole(section.getOwnerRole())
                .submitterRole(sectionSubmitterRole(section.getSectionCode()))
                .allowedActions(allowedActions(section, checkItems))
                .submissionBlockReason(submissionBlockReason(section))
                .status(section.getStatus())
                .dataReadiness(missing.isEmpty() ? "READY" : "INCOMPLETE")
                .submissionStatus(section.getSubmittedByUserId() == null ? "NOT_SUBMITTED" : "SUBMITTED")
                .approvalStatus(approval)
                .submittedRevision(submittedRevision)
                .missingRequiredCheckCodes(missing)
                .evidence(section.getEvidence())
                .notes(section.getNotes())
                .submittedByUserId(section.getSubmittedByUserId())
                .submittedAt(section.getSubmittedAt())
                .confirmedByUserId(section.getConfirmedByUserId())
                .confirmedByUsername(section.getConfirmedByUsername())
                .confirmedAt(section.getConfirmedAt())
                .returnReason(section.getReturnReason())
                .returnedAt(section.getReturnedAt())
                .returnedByUserId(section.getReturnedByUserId())
                .revision(section.getRevision())
                .checkItems(checkItems)
                .build();
    }
}
