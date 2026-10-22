package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.identity.entity.User;
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
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CruiseStaffService {

    private final CruiseStaffRepository cruiseStaffRepository;
    private final CruiseRepository cruiseRepository;
    private final UserRepository userRepository;

    @Transactional
    public CruiseStaffResponse assignStaff(UUID cruiseId, CruiseStaffRequest request) {
        log.info("Assigning staff user {} to cruise {} with role {}", request.getUserId(), cruiseId, request.getRoleInCruise());

        Cruise cruise = cruiseRepository.findById(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise not found with id: " + cruiseId));

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + request.getUserId()));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("INACTIVE_USER", "Cannot assign inactive user as cruise staff");
        }

        if (cruiseStaffRepository.existsByCruise_CruiseIdAndUser_UserId(cruiseId, user.getUserId())) {
            throw new BusinessException("DUPLICATE_STAFF_ASSIGNMENT", "Staff member is already assigned to this cruise");
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
        staff.setRoleInCruise(request.getRoleInCruise().trim().toUpperCase());
        staff.setAssignedAt(Instant.now());
        staff.setStatus("ACTIVE");
        staff.setNotes(request.getNotes());

        CruiseStaff saved = cruiseStaffRepository.save(staff);
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
    public void removeStaff(UUID cruiseId, UUID userId) {
        CruiseStaff staff = cruiseStaffRepository.findByCruise_CruiseIdAndUser_UserId(cruiseId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Staff assignment not found for user %s on cruise %s", userId, cruiseId)));
        cruiseStaffRepository.delete(staff);
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
                .status(staff.getStatus())
                .notes(staff.getNotes())
                .build();
    }
}
