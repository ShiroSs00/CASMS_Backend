package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.identity.entity.User;
import com.casms.identity.enums.Role;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.repository.UserRepository;
import com.casms.ship.dto.request.CruiseStaffRequest;
import com.casms.ship.dto.response.CruiseStaffResponse;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.CruiseStaff;
import com.casms.ship.repository.CruiseRepository;
import com.casms.ship.repository.CruiseStaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CruiseStaffService {

    private static final Set<String> ALLOWED_ASSIGNMENT_ROLES = Set.of(
            "CAPTAIN", "DECK_OFFICER", "ONBOARD_STAFF",
            "ACTIVITY_SERVICE_MANAGER", "TOUR_GUIDE", "PORT_COORDINATOR",
            "BOOKING_AGENT", "FINANCE_OFFICER"
    );
    private static final Set<Role> STAFF_ROLES = Set.of(
            Role.OPERATIONS_MANAGER,
            Role.BOOKING_AGENT,
            Role.ONBOARD_STAFF,
            Role.FINANCE_OFFICER,
            Role.ACTIVITY_SERVICE_MANAGER
    );

    private final CruiseStaffRepository cruiseStaffRepository;
    private final CruiseRepository cruiseRepository;
    private final UserRepository userRepository;
    private final CruisePreparationService cruisePreparationService;
    private final jakarta.persistence.EntityManager entityManager;

    private void requireActor(UUID actorId, String permission) {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof com.casms.security.principal.CasmsPrincipal principal)
                || !java.util.Objects.equals(actorId, principal.getUserId())
                || !principal.hasRole(Role.OPERATIONS_MANAGER) || !principal.hasAuthority(permission))
            throw new org.springframework.security.access.AccessDeniedException("Cruise staffing requires Operations Manager and effective permission");
    }

    private boolean eligibleAccount(User user) {
        return user.getStatus() == UserStatus.ACTIVE && user.isStaff() && user.getRoles() != null
                && user.getRoles().stream().anyMatch(STAFF_ROLES::contains);
    }

    public record StaffOption(UUID userId, String username, List<String> allowedRoles) {}
    public record AssignmentOptions(List<String> roles, List<StaffOption> users) {}

    /** Expose existing assignment semantics; this is not staff availability or coverage. */
    @Transactional(readOnly = true)
    public AssignmentOptions assignmentOptions(UUID cruiseId, UUID actorId) {
        requireActor(actorId, "CRUISE_STAFF_ASSIGN");
        if (!cruiseRepository.existsById(cruiseId)) throw new ResourceNotFoundException("Cruise", cruiseId);
        var roles = ALLOWED_ASSIGNMENT_ROLES.stream().sorted().toList();
        var users = userRepository.findByStatus(UserStatus.ACTIVE).stream().filter(this::eligibleAccount)
                .map(user -> new StaffOption(user.getUserId(), user.getUsername(), roles.stream()
                        .filter(role -> !"ONBOARD_STAFF".equals(role) || user.hasRole(Role.ONBOARD_STAFF)).toList()))
                .sorted(java.util.Comparator.comparing(StaffOption::username)).toList();
        return new AssignmentOptions(roles, users);
    }

    @Transactional
    public CruiseStaffResponse assignStaff(UUID cruiseId, CruiseStaffRequest request, UUID actorId) {
        requireActor(actorId, "CRUISE_STAFF_ASSIGN");
        log.info("Assigning staff user {} to cruise {} with role {}", request.getUserId(), cruiseId, request.getRoleInCruise());

        Cruise cruise = cruiseRepository.findByIdForUpdate(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise not found with id: " + cruiseId));

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + request.getUserId()));
        // Serialize duplicate/conflict checks for this account across different Cruises.
        entityManager.lock(user, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(user);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("INACTIVE_USER", "Cannot assign inactive user as cruise staff");
        }

        if (cruiseStaffRepository.existsByCruise_CruiseIdAndUser_UserIdAndStatus(cruiseId, user.getUserId(), "ACTIVE")) {
            throw new BusinessException("DUPLICATE_STAFF_ASSIGNMENT", "Staff member is already assigned to this cruise");
        }

        String assignmentRole = request.getRoleInCruise().trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_ASSIGNMENT_ROLES.contains(assignmentRole)) {
            throw new BusinessException("INVALID_CRUISE_STAFF_ROLE", "Unsupported operational role in cruise assignment");
        }
        if (!user.isStaff() || user.getRoles() == null || user.getRoles().stream().noneMatch(STAFF_ROLES::contains)) {
            throw new BusinessException("INVALID_CRUISE_STAFF_ROLE",
                    "Only an existing staff account can receive a cruise assignment; assignment does not grant system roles");
        }

        if ("ONBOARD_STAFF".equals(assignmentRole) && !user.hasRole(com.casms.identity.enums.Role.ONBOARD_STAFF)) {
            throw new BusinessException("INVALID_CRUISE_STAFF_ROLE", "Onboard assignment requires an Onboard Staff account");
        }

        // Validate schedule conflict across other active cruises
        List<CruiseStaff> conflicts = cruiseStaffRepository.findConflictingAssignments(
                user.getUserId(),
                cruise.getDepartureDatetime(),
                cruise.getReturnDatetime(),
                cruiseId
        );

        if (!conflicts.isEmpty()) {
            Cruise conflictCruise = conflicts.get(0).getCruise();
            throw new BusinessException("SHIP_STAFF_SCHEDULE_CONFLICT",
                    String.format("Staff member %s is already assigned to conflicting cruise %s (%s to %s)",
                            user.getUsername(), conflictCruise.getCruiseCode(),
                            conflictCruise.getDepartureDatetime(), conflictCruise.getReturnDatetime()));
        }

        CruiseStaff staff = new CruiseStaff();
        staff.setCruise(cruise);
        staff.setUser(user);
        staff.setRoleInCruise(assignmentRole);
        staff.setAssignedAt(Instant.now());
        staff.setStatus("ACTIVE");
        staff.setNotes(request.getNotes());
        staff.setAssignedBy(userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("Assigning user not found")));

        CruiseStaff saved = cruiseStaffRepository.save(staff);
        cruisePreparationService.invalidateSignoffs(cruiseId, "Phân công nhân sự mới: " + user.getUsername() + " (" + assignmentRole + ")");
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CruiseStaffResponse> listStaff(UUID cruiseId) {
        if (!cruiseRepository.existsById(cruiseId)) {
            throw new ResourceNotFoundException("Cruise not found with id: " + cruiseId);
        }
        return cruiseStaffRepository.findByCruise_CruiseId(cruiseId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void removeStaff(UUID cruiseId, UUID userId, UUID actorId) {
        requireActor(actorId, "CRUISE_STAFF_REMOVE");
        cruiseRepository.findByIdForUpdate(cruiseId).orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));
        var account = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        entityManager.lock(account, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        CruiseStaff staff = cruiseStaffRepository
                .findFirstByCruise_CruiseIdAndUser_UserIdAndStatusOrderByAssignedAtDesc(cruiseId, userId, "ACTIVE")
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Staff assignment not found for user %s on cruise %s", userId, cruiseId)));
        staff.setStatus("CANCELLED");
        staff.setEndedAt(Instant.now());
        staff.setEndedBy(userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("Removing user not found")));
        cruiseStaffRepository.save(staff);
        cruisePreparationService.invalidateSignoffs(cruiseId, "Gỡ bỏ phân công nhân sự: " + staff.getUser().getUsername());
        log.info("Relieved staff user {} from cruise {}", userId, cruiseId);
    }

    private CruiseStaffResponse toResponse(CruiseStaff staff) {
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
                .status(staff.getStatus())
                .notes(staff.getNotes())
                .build();
    }
}
