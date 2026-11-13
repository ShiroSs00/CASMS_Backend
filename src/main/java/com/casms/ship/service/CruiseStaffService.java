package com.casms.ship.service;

import com.casms.audit.AuditLog;
import com.casms.audit.AuditRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.identity.entity.User;
import com.casms.identity.enums.Role;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.repository.UserRepository;
import com.casms.notification.service.StaffAssignmentEmailService;
import com.casms.notification.entity.Notification;
import com.casms.notification.repository.NotificationRepository;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.dto.request.CruiseStaffRequest;
import com.casms.ship.dto.response.CruiseStaffResponse;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.CruiseStaff;
import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.repository.CruiseRepository;
import com.casms.ship.repository.CruiseStaffRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CruiseStaffService {

    private static final List<String> SHIPBOARD_ROLES =
            List.of("SHIP_MANAGER", "ONBOARD_STAFF", "FINANCE_OFFICER");

    private static final Set<String> ALLOWED_ASSIGNMENT_ROLES =
            Set.of(
                    "CAPTAIN",
                    "DECK_OFFICER",
                    "SHIP_MANAGER",
                    "ONBOARD_STAFF",
                    "ACTIVITY_SERVICE_MANAGER",
                    "TOUR_GUIDE",
                    "PORT_COORDINATOR",
                    "BOOKING_AGENT",
                    "FINANCE_OFFICER");
    private static final Set<Role> STAFF_ROLES =
            Set.of(
                    Role.OPERATIONS_MANAGER,
                    Role.BOOKING_AGENT,
                    Role.SHIP_MANAGER,
                    Role.ONBOARD_STAFF,
                    Role.FINANCE_OFFICER,
                    Role.ACTIVITY_SERVICE_MANAGER);

    private final CruiseStaffRepository cruiseStaffRepository;
    private final CruiseRepository cruiseRepository;
    private final UserRepository userRepository;
    private final CruisePreparationService cruisePreparationService;
    private final StaffAssignmentEmailService assignmentEmail;
    private final NotificationRepository notifications;
    private final AuditRepository audit;

    public record StaffCandidate(UUID userId, String username, List<String> allowedRoles) {}

    public record StaffOptions(List<String> roles, List<StaffCandidate> users) {}

    @Transactional(readOnly = true)
    public StaffOptions staffOptions(UUID cruiseId, boolean shipboardOnly) {
        Cruise target = cruiseRepository.findById(cruiseId).orElseThrow(
                () -> new ResourceNotFoundException("Cruise not found with id: " + cruiseId));
        List<String> roles = shipboardOnly
                ? SHIPBOARD_ROLES
                : ALLOWED_ASSIGNMENT_ROLES.stream().sorted().toList();
        var assignedUsers = new HashSet<>(cruiseStaffRepository.findUnavailableUserIds(
                cruiseId, target.getDepartureDatetime(), target.getReturnDatetime()));
        List<StaffCandidate> candidates = userRepository.findByStatus(UserStatus.ACTIVE).stream()
                .filter(CruiseStaffService::canReceiveAssignment)
                .filter(user -> !assignedUsers.contains(user.getUserId()))
                .sorted(Comparator.comparing(User::getUsername, String.CASE_INSENSITIVE_ORDER))
                .map(user -> new StaffCandidate(user.getUserId(), user.getUsername(),
                        roles.stream().filter(role -> canReceiveRole(user, role)).toList()))
                .filter(candidate -> !candidate.allowedRoles().isEmpty())
                .toList();
        return new StaffOptions(roles, candidates);
    }

    private static boolean canReceiveAssignment(User user) {
        return user.getRoles() != null && !user.hasRole(Role.ADMIN) && user.isStaff()
                && user.getRoles().stream().anyMatch(STAFF_ROLES::contains);
    }

    private static boolean canReceiveRole(User user, String role) {
        return !SHIPBOARD_ROLES.contains(role) || user.hasRole(Role.valueOf(role));
    }

    @Transactional
    public CruiseStaffResponse assignStaff(UUID cruiseId, CruiseStaffRequest request, CasmsPrincipal actor) {
        requirePermission(actor, "CRUISE_STAFF_ASSIGN");
        if (!actor.hasRole(Role.HR_MANAGER)) {
            throw new AccessDeniedException("Only HR can manage cruise staffing");
        }
        return assignStaff(cruiseId, request, actor.getUserId());
    }

    @Transactional
    CruiseStaffResponse assignStaff(
            UUID cruiseId, CruiseStaffRequest request, UUID actorId) {
        log.info(
                "Assigning staff user {} to cruise {} with role {}",
                request.getUserId(),
                cruiseId,
                request.getRoleInCruise());

        Cruise cruise = lockCruise(cruiseId);
        if (cruise.getStatus() == CruiseStatus.COMPLETED || cruise.getStatus() == CruiseStatus.CANCELLED) {
            throw conflict("CRUISE_STAFF_CRUISE_CLOSED", "Chuyến đã hoàn thành hoặc đã bị hủy.");
        }

        userRepository.lockForStaffAssignment(request.getUserId());
        User user =
                userRepository
                        .findById(request.getUserId())
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "User not found with id: " + request.getUserId()));

        if (user.hasRole(Role.ADMIN)) {
            throw new AccessDeniedException("Admin account cannot be managed through cruise staff assignment");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(
                    "INACTIVE_USER", "Cannot assign inactive user as cruise staff");
        }

        if (cruiseStaffRepository.existsByCruise_CruiseIdAndUser_UserIdAndStatus(
                cruiseId, user.getUserId(), "ACTIVE")) {
            throw new BusinessException(
                    "DUPLICATE_STAFF_ASSIGNMENT",
                    "Staff member is already assigned to this cruise");
        }

        String assignmentRole = request.getRoleInCruise().trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_ASSIGNMENT_ROLES.contains(assignmentRole)) {
            throw new BusinessException(
                    "INVALID_CRUISE_STAFF_ROLE",
                    "Unsupported operational role in cruise assignment");
        }
        if (!user.isStaff()
                || user.getRoles() == null
                || user.getRoles().stream().noneMatch(STAFF_ROLES::contains)) {
            throw new BusinessException(
                    "INVALID_CRUISE_STAFF_ROLE",
                    "Only an existing staff account can receive a cruise assignment; assignment"
                        + " does not grant system roles");
        }

        if ("ONBOARD_STAFF".equals(assignmentRole)
                && !user.hasRole(com.casms.identity.enums.Role.ONBOARD_STAFF)) {
            throw new BusinessException(
                    "INVALID_CRUISE_STAFF_ROLE",
                    "Shipboard assignment requires the matching account role: " + assignmentRole);
        }

        // Validate schedule conflict across other active cruises
        List<CruiseStaff> conflicts =
                cruiseStaffRepository.findConflictingAssignments(
                        user.getUserId(),
                        cruise.getDepartureDatetime(),
                        cruise.getReturnDatetime(),
                        cruiseId);

        if (!conflicts.isEmpty()) {
            Cruise conflictCruise = conflicts.get(0).getCruise();
            throw new BusinessException(
                    "SHIP_STAFF_SCHEDULE_CONFLICT",
                    String.format(
                            "Staff member %s is already assigned to conflicting cruise %s (%s to"
                                + " %s)",
                            user.getUsername(),
                            conflictCruise.getCruiseCode(),
                            conflictCruise.getDepartureDatetime(),
                            conflictCruise.getReturnDatetime()));
        }

        CruiseStaff staff = new CruiseStaff();
        staff.setCruise(cruise);
        staff.setUser(user);
        staff.setRoleInCruise(assignmentRole);
        staff.setAssignedAt(Instant.now());
        staff.setStatus("ACTIVE");
        staff.setNotes(request.getNotes());
        staff.setAssignedBy(
                userRepository
                        .findById(actorId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Assigning user not found")));

        CruiseStaff saved = cruiseStaffRepository.saveAndFlush(staff);
        return toResponse(saved, assignmentEmail.enqueue(saved));
    }

    @Transactional(readOnly = true)
    public List<CruiseStaffResponse> listStaff(UUID cruiseId, CasmsPrincipal actor) {
        requirePermission(actor, "CRUISE_STAFF_READ");
        return listStaff(cruiseId, !actor.hasRole(Role.HR_MANAGER) || actor.hasRole(Role.ADMIN));
    }

    @Transactional(readOnly = true)
    public List<CruiseStaffResponse> listStaff(UUID cruiseId) {
        return listStaff(cruiseId, true);
    }

    private List<CruiseStaffResponse> listStaff(UUID cruiseId, boolean includeAdmins) {
        if (!cruiseRepository.existsById(cruiseId)) {
            throw new ResourceNotFoundException("Cruise not found with id: " + cruiseId);
        }
        return cruiseStaffRepository.findByCruise_CruiseId(cruiseId).stream()
                .filter(staff -> includeAdmins || !staff.getUser().hasRole(Role.ADMIN))
                .map(staff -> toResponse(staff, null))
                .toList();
    }

    @Transactional
    public void removeStaff(UUID cruiseId, UUID userId, UUID assignmentId, String reason, CasmsPrincipal actor) {
        requirePermission(actor, "CRUISE_STAFF_REMOVE");
        if (!actor.hasRole(Role.HR_MANAGER)) {
            throw new AccessDeniedException("Only HR can manage cruise staffing");
        }
        removeStaff(cruiseId, userId, assignmentId, reason, actor.getUserId());
    }

    @Transactional
    public void removeForInactivation(UUID cruiseId, UUID userId, String reason, CasmsPrincipal actor) {
        requirePermission(actor, "USER_STATUS_CHANGE");
        if (!actor.hasAnyRole(Role.HR_MANAGER, Role.ADMIN)) {
            throw new AccessDeniedException("Employee administration permission required");
        }
        var active = cruiseStaffRepository.findFirstByCruise_CruiseIdAndUser_UserIdAndStatusOrderByAssignedAtDesc(
                cruiseId, userId, "ACTIVE");
        active.ifPresent(row -> removeStaff(cruiseId, userId, row.getCruiseStaffId(), reason, actor.getUserId()));
    }

    void removeStaff(UUID cruiseId, UUID userId, UUID assignmentId, String reason, UUID actorId) {
        Cruise cruise = lockCruise(cruiseId);
        userRepository.lockForStaffAssignment(userId);
        var assignment = assignmentId == null
                ? cruiseStaffRepository.findFirstByCruise_CruiseIdAndUser_UserIdOrderByAssignedAtDesc(cruiseId, userId)
                : cruiseStaffRepository.findById(assignmentId).filter(row ->
                        cruiseId.equals(row.getCruise().getCruiseId()) && userId.equals(row.getUser().getUserId()));
        CruiseStaff staff = assignment.orElseThrow(() -> new ResourceNotFoundException("Cruise staff assignment", userId));
        if (staff.getUser().hasRole(Role.ADMIN)) {
            throw new AccessDeniedException("Admin account cannot be managed through cruise staff assignment");
        }
        if ("REMOVED".equals(staff.getStatus())) return;
        if (!"ACTIVE".equals(staff.getStatus()) || cruise.getActualDepartureDatetime() != null
                || (cruise.getStatus() != CruiseStatus.DRAFT && cruise.getStatus() != CruiseStatus.PUBLISHED)) {
            throw conflict("CRUISE_STAFF_REMOVAL_NOT_ALLOWED",
                    "Chỉ gỡ phân công đang hiệu lực khi chuyến chưa khởi hành.");
        }
        if (assignmentId == null && cruiseStaffRepository.existsByCruise_CruiseIdAndUser_UserIdAndStatus(
                cruiseId, userId, "REMOVED")) {
            throw conflict("CRUISE_STAFF_ASSIGNMENT_ID_REQUIRED",
                    "Cần assignmentId khi nhân viên đã từng được gỡ khỏi chuyến này.");
        }
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("Removing user not found"));
        String removalReason = reason == null || reason.isBlank() ? null : reason.trim();
        if (removalReason != null && removalReason.length() > 500) {
            throw new BusinessException("STAFF_REMOVAL_REASON_TOO_LONG", "Lý do gỡ tối đa 500 ký tự.");
        }
        staff.setStatus("REMOVED");
        staff.setEndedAt(Instant.now());
        staff.setEndedBy(actor);
        staff.setRemovalReason(removalReason);
        cruiseStaffRepository.saveAndFlush(staff);
        recordRemoval(staff, actor);
        if (!cruiseStaffRepository.existsActiveOnboardStaff(cruiseId)) {
            cruisePreparationService.invalidateSignoffs(
                    cruiseId,
                    "SAFETY_CREW",
                    "Gỡ bỏ phân công nhân sự: "
                            + staff.getUser().getUsername()
                            + " dẫn đến thiếu Onboard Staff");
        }
        log.info("Relieved staff user {} from cruise {}", userId, cruiseId);
    }

    private void recordRemoval(CruiseStaff staff, User actor) {
        String detail = "Nhân viên " + staff.getUser().getUsername() + " đã được gỡ khỏi chuyến "
                + staff.getCruise().getCruiseCode() + " lúc " + staff.getEndedAt() + " bởi "
                + actor.getUsername() + ". "
                + (staff.getRemovalReason() == null ? "" : "Lý do: " + staff.getRemovalReason() + ". ")
                + "Vui lòng bổ sung nhân viên thay thế cho vị trí " + staff.getRoleInCruise() + ".";
        var event = new AuditLog();
        event.setActorUserId(actor.getUserId());
        event.setEntityType("CruiseStaff");
        event.setEntityId(staff.getCruiseStaffId());
        event.setAction("CRUISE_STAFF_REMOVED");
        event.setResult("SUCCESS");
        event.setDetail(detail);
        audit.save(event);
        var recipients = actor.hasRole(Role.HR_MANAGER) ? List.of(actor)
                : userRepository.findByStatus(UserStatus.ACTIVE).stream()
                        .filter(user -> user.hasRole(Role.HR_MANAGER) && !user.hasRole(Role.ADMIN)).toList();
        for (User recipient : recipients) {
            var notice = new Notification();
            notice.setRecipientUser(recipient);
            notice.setNotificationType("EMPLOYEE_REPLACEMENT_REQUIRED");
            notice.setTitle("Cần bổ sung nhân viên cho chuyến");
            notice.setContent(detail);
            notifications.saveAndFlush(notice);
        }
    }

    private Cruise lockCruise(UUID cruiseId) {
        return cruiseRepository.findByIdForUpdate(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));
    }

    private void requirePermission(CasmsPrincipal actor, String permission) {
        if (actor == null || !actor.isEnabled() || !actor.hasAuthority(permission)) {
            throw new AccessDeniedException("Cruise staff permission required");
        }
    }

    private BusinessException conflict(String code, String message) {
        return new BusinessException(HttpStatus.CONFLICT, code, message);
    }

    private CruiseStaffResponse toResponse(CruiseStaff staff, StaffAssignmentEmailService.Status emailStatus) {
        return CruiseStaffResponse.builder()
                .cruiseStaffId(staff.getCruiseStaffId())
                .cruiseId(staff.getCruise().getCruiseId())
                .cruiseCode(staff.getCruise().getCruiseCode())
                .userId(staff.getUser().getUserId())
                .username(staff.getUser().getUsername())
                .fullName(staff.getUser().getUsername())
                .roleInCruise(staff.getRoleInCruise())
                .assignedAt(staff.getAssignedAt())
                .assignedBy(staff.getAssignedBy() == null ? null : staff.getAssignedBy().getUserId())
                .assignedByName(staff.getAssignedBy() == null ? null : staff.getAssignedBy().getUsername())
                .endedAt(staff.getEndedAt())
                .endedBy(staff.getEndedBy() == null ? null : staff.getEndedBy().getUserId())
                .endedByName(staff.getEndedBy() == null ? null : staff.getEndedBy().getUsername())
                .removalReason(staff.getRemovalReason())
                .status(staff.getStatus())
                .notes(staff.getNotes())
                .assignmentEmailStatus(emailStatus)
                .build();
    }
}
