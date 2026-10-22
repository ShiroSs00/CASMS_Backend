package com.casms.activity.service;

import com.casms.activity.dto.request.TourGroupRequest;
import com.casms.activity.dto.response.TourGroupResponse;
import com.casms.activity.entity.Registration;
import com.casms.activity.entity.ShoreExcursion;
import com.casms.activity.entity.TourGroup;
import com.casms.activity.entity.TourGroupPassenger;
import com.casms.activity.enums.RegistrationStatus;
import com.casms.activity.enums.ShoreExcursionStatus;
import com.casms.activity.enums.TourGroupPassengerAssignmentStatus;
import com.casms.activity.enums.TourGroupStatus;
import com.casms.activity.repository.RegistrationRepository;
import com.casms.activity.repository.ShoreExcursionRepository;
import com.casms.activity.repository.TourGroupPassengerRepository;
import com.casms.activity.repository.TourGroupRepository;
import com.casms.common.service.BusinessCodeGenerator;
import com.casms.identity.entity.Passenger;
import com.casms.identity.entity.User;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.repository.PassengerRepository;
import com.casms.identity.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class TourGroupService {

    private final TourGroupRepository tourGroupRepository;
    private final TourGroupPassengerRepository passengerAssignmentRepository;
    private final ShoreExcursionRepository shoreExcursionRepository;
    private final RegistrationRepository registrationRepository;
    private final PassengerRepository passengerRepository;
    private final UserRepository userRepository;
    private final BusinessCodeGenerator businessCodeGenerator;

    private static final List<TourGroupPassengerAssignmentStatus>
            CAPACITY_STATUSES = List.of(
            TourGroupPassengerAssignmentStatus.ASSIGNED,
            TourGroupPassengerAssignmentStatus.CHECKED_IN,
            TourGroupPassengerAssignmentStatus.COMPLETED
    );

    // ==================== CRUD ====================

    public TourGroupResponse create(TourGroupRequest request) {
        TourGroup group = new TourGroup();

        applyRequest(group, request);
        group.setStatus(TourGroupStatus.PLANNED);

        validateGroup(group, null);

        return toResponse(tourGroupRepository.save(group));
    }

    @Transactional(readOnly = true)
    public List<TourGroupResponse> list(
            UUID excursionId,
            UUID guideId,
            TourGroupStatus status
    ) {
        List<TourGroup> groups;

        if (excursionId != null && guideId != null) {
            groups = tourGroupRepository
                    .findByShoreExcursion_ShoreExcursionIdAndGuideId(
                            excursionId, guideId
                    );
        } else if (excursionId != null && status != null) {
            groups = tourGroupRepository
                    .findByShoreExcursion_ShoreExcursionIdAndStatus(
                            excursionId, status
                    );
        } else if (excursionId != null) {
            groups = tourGroupRepository
                    .findByShoreExcursion_ShoreExcursionId(excursionId);
        } else if (guideId != null) {
            groups = tourGroupRepository.findByGuideId(guideId);
        } else if (status != null) {
            groups = tourGroupRepository.findByStatus(status);
        } else {
            groups = tourGroupRepository.findAll();
        }

        return groups.stream()
                .filter(g -> status == null || g.getStatus() == status)
                .filter(g -> guideId == null
                        || guideId.equals(g.getGuideId()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TourGroupResponse detail(UUID id) {
        return toResponse(getById(id));
    }

    public TourGroupResponse update(
            UUID id,
            TourGroupRequest request
    ) {
        TourGroup group = getById(id);

        if (group.getStatus() != TourGroupStatus.PLANNED) {
            throw new IllegalStateException(
                    "Only PLANNED groups can be updated."
            );
        }

        applyRequest(group, request);
        validateGroup(group, id);

        return toResponse(tourGroupRepository.save(group));
    }

    // ==================== PASSENGER ASSIGNMENT ====================

    public TourGroupResponse assignPassenger(
            UUID groupId,
            UUID passengerId
    ) {
        TourGroup group = getById(groupId);
        requireStatus(group, TourGroupStatus.PLANNED);

        Passenger passenger = passengerRepository.findById(passengerId)
                .orElseThrow(() ->
                        new EntityNotFoundException("Passenger not found.")
                );

        UUID excursionId = group.getShoreExcursion()
                .getShoreExcursionId();

        boolean hasConfirmedRegistration =
                registrationRepository
                        .existsByPassenger_PassengerIdAndShoreExcursion_ShoreExcursionIdAndStatus(
                                passengerId,
                                excursionId,
                                RegistrationStatus.CONFIRMED
                        );

        if (!hasConfirmedRegistration) {
            throw new IllegalStateException(
                    "Passenger must have a CONFIRMED registration "
                            + "for this shore excursion."
            );
        }

        if (passengerAssignmentRepository
                .existsByPassenger_PassengerIdAndTourGroup_ShoreExcursion_ShoreExcursionIdAndAssignmentStatusIn(
                        passengerId,
                        excursionId,
                        CAPACITY_STATUSES
                )) {
            throw new IllegalStateException(
                    "Passenger is already assigned to a group "
                            + "for this excursion."
            );
        }

        checkCapacity(groupId);

        TourGroupPassenger assignment = new TourGroupPassenger();
        assignment.setTourGroup(group);
        assignment.setPassenger(passenger);
        assignment.setAssignmentStatus(
                TourGroupPassengerAssignmentStatus.ASSIGNED
        );
        assignment.setAssignedAt(Instant.now());

        passengerAssignmentRepository.save(assignment);

        return toResponse(group);
    }

    public TourGroupResponse removePassenger(
            UUID groupId,
            UUID passengerId
    ) {
        TourGroup group = getById(groupId);
        requireStatus(group, TourGroupStatus.PLANNED);

        TourGroupPassenger assignment =
                passengerAssignmentRepository.findByTourGroup_TourGroupId(
                                groupId
                        ).stream()
                        .filter(a -> a.getPassenger().getPassengerId()
                                .equals(passengerId))
                        .filter(a -> a.getAssignmentStatus()
                                != TourGroupPassengerAssignmentStatus.CANCELLED)
                        .findFirst()
                        .orElseThrow(() ->
                                new EntityNotFoundException(
                                        "Passenger assignment not found."
                                )
                        );

        assignment.setAssignmentStatus(
                TourGroupPassengerAssignmentStatus.CANCELLED
        );

        passengerAssignmentRepository.save(assignment);
        return toResponse(group);
    }

    // ==================== GUIDE ====================

    public TourGroupResponse assignGuide(UUID groupId, UUID guideId) {
        TourGroup group = getById(groupId);
        requireStatus(group, TourGroupStatus.PLANNED);

        if (guideId == null) {
            throw new IllegalArgumentException("Guide ID is required.");
        }

        // Verify that the guide exists as an active user
        User guide = userRepository.findById(guideId)
                .orElseThrow(() -> new EntityNotFoundException("Tour guide user not found with ID: " + guideId));
        if (guide.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("Tour guide user is not active.");
        }

        validateGuideSchedule(group, guideId);

        group.setGuideId(guideId);
        return toResponse(tourGroupRepository.save(group));
    }

    private void validateGuideSchedule(TourGroup group, UUID guideId) {
        if (group.getPlannedDepartureTime() == null
                || group.getPlannedReturnTime() == null) {
            return;
        }

        List<TourGroup> existingGroups =
                tourGroupRepository.findByGuideId(guideId);

        boolean conflict = existingGroups.stream()
                .filter(other -> !other.getTourGroupId()
                        .equals(group.getTourGroupId()))
                .filter(other -> other.getStatus()
                        != TourGroupStatus.CANCELLED
                        && other.getStatus() != TourGroupStatus.RETURNED)
                .anyMatch(other ->
                        other.getPlannedDepartureTime() != null
                                && other.getPlannedReturnTime() != null
                                && group.getPlannedDepartureTime()
                                .isBefore(other.getPlannedReturnTime())
                                && group.getPlannedReturnTime()
                                .isAfter(other.getPlannedDepartureTime())
                );

        if (conflict) {
            throw new IllegalStateException(
                    "Guide has another group scheduled during this time."
            );
        }
    }

    // ==================== VALIDATION / CAPACITY ====================

    public void validateGroup(UUID groupId) {
        validateGroup(getById(groupId), groupId);
    }

    private void validateGroup(TourGroup group, UUID excludeId) {
        if (group.getShoreExcursion() == null
                || group.getShoreExcursion().getShoreExcursionId() == null) {
            throw new IllegalArgumentException("Shore excursion is required.");
        }

        ShoreExcursion excursion = shoreExcursionRepository
                .findById(group.getShoreExcursion()
                        .getShoreExcursionId())
                .orElseThrow(() ->
                        new EntityNotFoundException("Excursion not found.")
                );

        if (excursion.getStatus() == ShoreExcursionStatus.CANCELLED
                || excursion.getStatus() == ShoreExcursionStatus.COMPLETED
                || excursion.getStatus() == ShoreExcursionStatus.CLOSED) {
            throw new IllegalStateException(
                    "Cannot create or prepare a group for this excursion."
            );
        }

        if (group.getGroupCode() == null
                || group.getGroupCode().isBlank()) {
            throw new IllegalArgumentException("Group code is required.");
        }

        if (group.getMaximumCapacity() == null
                || group.getMaximumCapacity() <= 0) {
            throw new IllegalArgumentException(
                    "Maximum capacity must be greater than zero."
            );
        }

        if (group.getMaximumCapacity() > excursion.getMaximumCapacity()) {
            throw new IllegalArgumentException(
                    "Group capacity cannot exceed excursion capacity."
            );
        }

        boolean duplicate = excludeId == null
                ? tourGroupRepository
                .existsByShoreExcursion_ShoreExcursionIdAndGroupCodeIgnoreCase(
                        excursion.getShoreExcursionId(),
                        group.getGroupCode()
                )
                : tourGroupRepository
                .existsByShoreExcursion_ShoreExcursionIdAndGroupCodeIgnoreCaseAndTourGroupIdNot(
                        excursion.getShoreExcursionId(),
                        group.getGroupCode(),
                        excludeId
                );

        if (duplicate) {
            throw new IllegalStateException(
                    "Group code already exists for this excursion."
            );
        }

        validateTimes(group, excursion);
        checkCapacityAgainstExcursion(group);
    }

    public long checkCapacity(UUID groupId) {
        TourGroup group = getById(groupId);

        long assigned = countAssigned(groupId);

        if (assigned >= group.getMaximumCapacity()) {
            throw new IllegalStateException(
                    "Tour group has reached its maximum capacity."
            );
        }

        return group.getMaximumCapacity() - assigned;
    }

    private void checkCapacityAgainstExcursion(TourGroup group) {
        long totalAssignedAcrossGroups = tourGroupRepository
                .findByShoreExcursion_ShoreExcursionId(
                        group.getShoreExcursion().getShoreExcursionId()
                )
                .stream()
                .filter(other -> !other.getStatus()
                        .equals(TourGroupStatus.CANCELLED))
                .mapToLong(other -> countAssigned(
                        other.getTourGroupId()
                ))
                .sum();

        if (totalAssignedAcrossGroups > group.getShoreExcursion()
                .getMaximumCapacity()) {
            throw new IllegalStateException(
                    "Assigned passengers exceed excursion capacity."
            );
        }
    }

    private long countAssigned(UUID groupId) {
        return passengerAssignmentRepository.countAssignedPassengers(
                groupId,
                CAPACITY_STATUSES
        );
    }

    private void validateTimes(TourGroup group, ShoreExcursion excursion) {
        Instant assembly = group.getAssemblyTime();
        Instant departure = group.getPlannedDepartureTime();
        Instant returning = group.getPlannedReturnTime();

        if (departure != null && returning != null
                && !returning.isAfter(departure)) {
            throw new IllegalArgumentException(
                    "Planned return time must be after departure time."
            );
        }

        if (assembly != null && departure != null
                && assembly.isAfter(departure)) {
            throw new IllegalArgumentException(
                    "Assembly time must not be after departure time."
            );
        }

        if (departure != null && departure.isBefore(excursion.getStartTime())) {
            throw new IllegalArgumentException(
                    "Group departure cannot be before excursion start time."
            );
        }

        if (returning != null
                && returning.isAfter(excursion.getPlannedReturnTime())) {
            throw new IllegalArgumentException(
                    "Group return cannot be after excursion return time."
            );
        }

        if (group.getMeetingPoint() == null
                || group.getMeetingPoint().isBlank()) {
            throw new IllegalArgumentException(
                    "Meeting point is required."
            );
        }
    }

    // ==================== LIFECYCLE ====================

    public TourGroupResponse confirm(UUID groupId) {
        TourGroup group = getById(groupId);
        requireStatus(group, TourGroupStatus.PLANNED);

        validateGroup(group, groupId);

        if (group.getGuideId() == null) {
            throw new IllegalStateException(
                    "A guide must be assigned before confirming the group."
            );
        }

        if (group.getMeetingPoint() == null
                || group.getMeetingPoint().isBlank()
                || group.getAssemblyTime() == null
                || group.getPlannedDepartureTime() == null
                || group.getPlannedReturnTime() == null) {
            throw new IllegalStateException(
                    "Meeting point and group schedule must be completed."
            );
        }

        group.setStatus(TourGroupStatus.ASSEMBLING);
        return toResponse(tourGroupRepository.save(group));
    }

    public TourGroupResponse cancel(UUID groupId, String reason) {
        TourGroup group = getById(groupId);

        if (group.getStatus() == TourGroupStatus.DEPARTED
                || group.getStatus() == TourGroupStatus.RETURNED
                || group.getStatus() == TourGroupStatus.CANCELLED) {
            throw new IllegalStateException(
                    "This group cannot be cancelled in its current status."
            );
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "Cancellation reason is required."
            );
        }

        group.setStatus(TourGroupStatus.CANCELLED);

        String existingNotes = group.getNotes();
        String cancellationNote = "Cancellation reason: " + reason.trim();

        group.setNotes(existingNotes == null || existingNotes.isBlank()
                ? cancellationNote
                : existingNotes + "\n" + cancellationNote);

        List<TourGroupPassenger> assignments =
                passengerAssignmentRepository
                        .findByTourGroup_TourGroupId(groupId);

        for (TourGroupPassenger assignment : assignments) {
            if (assignment.getAssignmentStatus()
                    != TourGroupPassengerAssignmentStatus.CANCELLED) {
                assignment.setAssignmentStatus(
                        TourGroupPassengerAssignmentStatus.CANCELLED
                );
            }
        }

        passengerAssignmentRepository.saveAll(assignments);
        return toResponse(tourGroupRepository.save(group));
    }

    public TourGroupResponse start(UUID groupId) {
        TourGroup group = getById(groupId);
        requireStatus(group, TourGroupStatus.ASSEMBLING);

        validateGroup(group, groupId);

        long assigned = countAssigned(groupId);
        if (assigned == 0) {
            throw new IllegalStateException(
                    "Cannot start a group without assigned passengers."
            );
        }

        Instant now = Instant.now();

        if (group.getPlannedDepartureTime() != null
                && now.isBefore(group.getPlannedDepartureTime())) {
            throw new IllegalStateException(
                    "Cannot depart before planned departure time."
            );
        }

        group.setActualDepartureTime(now);
        group.setStatus(TourGroupStatus.DEPARTED);

        return toResponse(tourGroupRepository.save(group));
    }

    public TourGroupResponse complete(UUID groupId) {
        TourGroup group = getById(groupId);
        requireStatus(group, TourGroupStatus.DEPARTED);

        Instant now = Instant.now();

        if (group.getActualDepartureTime() != null
                && now.isBefore(group.getActualDepartureTime())) {
            throw new IllegalStateException(
                    "Return time cannot be before departure time."
            );
        }

        group.setActualReturnTime(now);
        group.setStatus(TourGroupStatus.RETURNED);

        List<TourGroupPassenger> assignments =
                passengerAssignmentRepository
                        .findByTourGroup_TourGroupId(groupId);

        for (TourGroupPassenger assignment : assignments) {
            if (assignment.getAssignmentStatus()
                    == TourGroupPassengerAssignmentStatus.CHECKED_IN) {
                assignment.setAssignmentStatus(
                        TourGroupPassengerAssignmentStatus.COMPLETED
                );
                assignment.setReturnedAt(now);
            }
        }

        passengerAssignmentRepository.saveAll(assignments);
        return toResponse(tourGroupRepository.save(group));
    }

    // ==================== HELPERS ====================

    private void applyRequest(
            TourGroup group,
            TourGroupRequest request
    ) {
        ShoreExcursion excursion = shoreExcursionRepository
                .findById(request.shoreExcursionId())
                .orElseThrow(() ->
                        new EntityNotFoundException("Excursion not found.")
                );

        group.setShoreExcursion(excursion);
        if (group.getTourGroupId() == null) group.setGroupCode(businessCodeGenerator.next("TG"));
        group.setMaximumCapacity(request.maximumCapacity() == null || request.maximumCapacity() < 1 ? excursion.getMaximumCapacity() : request.maximumCapacity());
        group.setGuideId(request.guideId());
        group.setVehicleId(request.vehicleId());
        group.setMeetingPoint((request.meetingPoint() == null || request.meetingPoint().isBlank()) ? excursion.getMeetingPoint() : request.meetingPoint());
        group.setAssemblyTime(request.assemblyTime() == null ? excursion.getStartTime() : request.assemblyTime());
        group.setPlannedDepartureTime(request.plannedDepartureTime() == null ? excursion.getStartTime() : request.plannedDepartureTime());
        group.setPlannedReturnTime(request.plannedReturnTime() == null ? excursion.getPlannedReturnTime() : request.plannedReturnTime());
        group.setNotes(request.notes());
    }

    private TourGroup getById(UUID id) {
        return tourGroupRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Tour group not found: " + id
                        )
                );
    }

    private TourGroupResponse toResponse(TourGroup group) {
        return TourGroupResponse.from(
                group,
                countAssigned(group.getTourGroupId())
        );
    }

    private void requireStatus(
            TourGroup group,
            TourGroupStatus expected
    ) {
        if (group.getStatus() != expected) {
            throw new IllegalStateException(
                    "Expected status " + expected
                            + ", actual status " + group.getStatus()
            );
        }
    }
}
