package com.casms.activity.service;

import com.casms.activity.dto.request.TourGroupPassengerAssignRequest;
import com.casms.activity.dto.response.EligiblePassengerResponse;
import com.casms.activity.dto.response.TourGroupPassengerResponse;
import com.casms.activity.entity.Registration;
import com.casms.activity.entity.ShoreExcursion;
import com.casms.activity.entity.TourGroup;
import com.casms.activity.entity.TourGroupPassenger;
import com.casms.activity.enums.RegistrationStatus;
import com.casms.activity.enums.ShoreExcursionStatus;
import com.casms.activity.enums.TourGroupPassengerAssignmentStatus;
import com.casms.activity.enums.TourGroupStatus;
import com.casms.activity.repository.RegistrationRepository;
import com.casms.activity.repository.TourGroupPassengerRepository;
import com.casms.activity.repository.TourGroupRepository;
import com.casms.identity.entity.Passenger;
import com.casms.identity.repository.PassengerRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class TourGroupPassengerService {

    private final TourGroupPassengerRepository assignmentRepository;
    private final TourGroupRepository tourGroupRepository;
    private final PassengerRepository passengerRepository;
    private final RegistrationRepository registrationRepository;

    public TourGroupPassengerService(
            TourGroupPassengerRepository assignmentRepository,
            TourGroupRepository tourGroupRepository,
            PassengerRepository passengerRepository,
            RegistrationRepository registrationRepository
    ) {
        this.assignmentRepository = assignmentRepository;
        this.tourGroupRepository = tourGroupRepository;
        this.passengerRepository = passengerRepository;
        this.registrationRepository = registrationRepository;
    }

    // CREATE / ASSIGN
    public TourGroupPassengerResponse assign(
            TourGroupPassengerAssignRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }

        TourGroup group = findGroupForUpdate(request.tourGroupId());
        Passenger passenger = passengerRepository.findById(
                request.passengerId()
        ).orElseThrow(() -> new EntityNotFoundException(
                "Passenger not found: " + request.passengerId()
        ));

        validateAssignment(group, passenger);

        if (assignmentRepository
                .existsByTourGroup_TourGroupIdAndPassenger_PassengerIdAndAssignmentStatusNot(
                        group.getTourGroupId(),
                        passenger.getPassengerId(),
                        TourGroupPassengerAssignmentStatus.CANCELLED
                )) {
            throw new IllegalStateException(
                    "Passenger already has an active assignment in this group"
            );
        }

        checkGroupCapacity(group);
        checkExcursionCapacity(group.getShoreExcursion());

        TourGroupPassenger assignment = new TourGroupPassenger();
        assignment.setTourGroup(group);
        assignment.setPassenger(passenger);
        assignment.setAssignmentStatus(
                TourGroupPassengerAssignmentStatus.ASSIGNED
        );
        assignment.setAssignedAt(Instant.now());

        return toResponse(assignmentRepository.save(assignment));
    }

    // READ / LIST
    @Transactional(readOnly = true)
    public List<TourGroupPassengerResponse> list(
            UUID groupId,
            UUID passengerId,
            TourGroupPassengerAssignmentStatus status
    ) {
        List<TourGroupPassenger> assignments;
        if (groupId == null && passengerId == null && status == null) {
            assignments = assignmentRepository.findAllWithDetails();
        } else {
            assignments = assignmentRepository.search(groupId, passengerId, status);
        }

        return assignments.stream()
                .map(this::toResponse)
                .toList();
    }

    // READ / DETAIL
    @Transactional(readOnly = true)
    public TourGroupPassengerResponse detail(UUID assignmentId) {
        return toResponse(findAssignment(assignmentId));
    }

    // MOVE TO ANOTHER GROUP; PRESERVE THE OLD RECORD
    public TourGroupPassengerResponse move(
            UUID assignmentId,
            UUID targetGroupId
    ) {
        TourGroupPassenger current = findAssignmentForUpdate(assignmentId);

        if (targetGroupId == null) {
            throw new IllegalArgumentException(
                    "Target tour group ID is required"
            );
        }

        if (current.getAssignmentStatus()
                != TourGroupPassengerAssignmentStatus.ASSIGNED) {
            throw new IllegalStateException(
                    "Only ASSIGNED passengers can be moved"
            );
        }

        TourGroup sourceGroup = current.getTourGroup();
        TourGroup targetGroup = findGroupForUpdate(targetGroupId);

        if (sourceGroup.getTourGroupId().equals(targetGroupId)) {
            throw new IllegalArgumentException(
                    "Target group must differ from the current group"
            );
        }

        if (!sourceGroup.getShoreExcursion().getShoreExcursionId()
                .equals(targetGroup.getShoreExcursion().getShoreExcursionId())) {
            throw new IllegalStateException(
                    "Passenger can only move between groups of the same shore excursion"
            );
        }

        validateAssignment(targetGroup, current.getPassenger());

        if (assignmentRepository
                .existsByTourGroup_TourGroupIdAndPassenger_PassengerIdAndAssignmentStatusNot(
                        targetGroupId,
                        current.getPassenger().getPassengerId(),
                        TourGroupPassengerAssignmentStatus.CANCELLED
                )) {
            throw new IllegalStateException(
                    "Passenger already has an active assignment in the target group"
            );
        }

        checkGroupCapacity(targetGroup);

        /*
         * Cancel the old assignment and create a new one.
         * Both changes are in the same transaction.
         */
        current.setAssignmentStatus(
                TourGroupPassengerAssignmentStatus.CANCELLED
        );
        assignmentRepository.save(current);

        TourGroupPassenger moved = new TourGroupPassenger();
        moved.setTourGroup(targetGroup);
        moved.setPassenger(current.getPassenger());
        moved.setAssignmentStatus(
                TourGroupPassengerAssignmentStatus.ASSIGNED
        );
        moved.setAssignedAt(Instant.now());

        return toResponse(assignmentRepository.save(moved));
    }

    // REMOVE / SOFT CANCEL
    public TourGroupPassengerResponse remove(UUID assignmentId) {
        TourGroupPassenger assignment =
                findAssignmentForUpdate(assignmentId);

        if (assignment.getAssignmentStatus()
                == TourGroupPassengerAssignmentStatus.CANCELLED) {
            return toResponse(assignment);
        }

        if (assignment.getAssignmentStatus()
                != TourGroupPassengerAssignmentStatus.ASSIGNED) {
            throw new IllegalStateException(
                    "Only ASSIGNED records can be cancelled"
            );
        }

        assignment.setAssignmentStatus(
                TourGroupPassengerAssignmentStatus.CANCELLED
        );

        return toResponse(assignmentRepository.save(assignment));
    }

    // RECORD PASSENGER CHECK-IN BEFORE DEPARTURE
    public TourGroupPassengerResponse recordDeparture(UUID assignmentId) {
        TourGroupPassenger assignment =
                findAssignmentForUpdate(assignmentId);

        if (assignment.getAssignmentStatus()
                != TourGroupPassengerAssignmentStatus.ASSIGNED) {
            throw new IllegalStateException(
                    "Only ASSIGNED passengers can be checked in"
            );
        }

        TourGroup group = assignment.getTourGroup();

        if (group.getStatus() != TourGroupStatus.ASSEMBLING) {
            throw new IllegalStateException(
                    "Passenger check-in requires the group to be ASSEMBLING"
            );
        }

        assignment.setAssignmentStatus(
                TourGroupPassengerAssignmentStatus.CHECKED_IN
        );
        assignment.setCheckedInAt(Instant.now());

        return toResponse(assignmentRepository.save(assignment));
    }

    // RECORD PASSENGER RETURN
    public TourGroupPassengerResponse recordReturn(UUID assignmentId) {
        TourGroupPassenger assignment =
                findAssignmentForUpdate(assignmentId);

        if (assignment.getAssignmentStatus()
                != TourGroupPassengerAssignmentStatus.CHECKED_IN) {
            throw new IllegalStateException(
                    "Only CHECKED_IN passengers can be marked COMPLETED"
            );
        }

        TourGroup group = assignment.getTourGroup();

        if (group.getStatus() != TourGroupStatus.DEPARTED
                && group.getStatus() != TourGroupStatus.RETURNED) {
            throw new IllegalStateException(
                    "The group must have departed before passenger return is recorded"
            );
        }

        assignment.setAssignmentStatus(
                TourGroupPassengerAssignmentStatus.COMPLETED
        );
        assignment.setReturnedAt(Instant.now());

        return toResponse(assignmentRepository.save(assignment));
    }

    // VALIDATE ASSIGNMENT RULES
    @Transactional(readOnly = true)
    public void validateAssignment(UUID groupId, UUID passengerId) {
        TourGroup group = findGroup(groupId);
        Passenger passenger = passengerRepository.findById(passengerId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Passenger not found: " + passengerId
                ));

        validateAssignment(group, passenger);
    }

    private void validateAssignment(
            TourGroup group,
            Passenger passenger
    ) {
        if (group.getStatus() != TourGroupStatus.PLANNED
                && group.getStatus() != TourGroupStatus.ASSEMBLING) {
            throw new IllegalStateException(
                    "Passenger assignment requires a PLANNED or ASSEMBLING group"
            );
        }

        ShoreExcursion excursion = group.getShoreExcursion();

        if (excursion.getStatus() != ShoreExcursionStatus.OPEN) {
            throw new IllegalStateException(
                    "Shore excursion must be OPEN to assign passengers"
            );
        }

        checkPassengerEligibility(passenger, excursion);
    }

    // CHECK REGISTRATION ELIGIBILITY
    @Transactional(readOnly = true)
    public boolean checkPassengerEligibility(
            UUID passengerId,
            UUID excursionId
    ) {
        Passenger passenger = passengerRepository.findById(passengerId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Passenger not found: " + passengerId
                ));

        ShoreExcursion excursion = findExcursionFromGroupContext(excursionId);

        return registrationRepository
                .existsByPassenger_PassengerIdAndShoreExcursion_ShoreExcursionIdAndStatus(
                        passenger.getPassengerId(),
                        excursion.getShoreExcursionId(),
                        RegistrationStatus.CONFIRMED
                );
    }

    private void checkPassengerEligibility(
            Passenger passenger,
            ShoreExcursion excursion
    ) {
        boolean eligible = registrationRepository
                .existsByPassenger_PassengerIdAndShoreExcursion_ShoreExcursionIdAndStatus(
                        passenger.getPassengerId(),
                        excursion.getShoreExcursionId(),
                        RegistrationStatus.CONFIRMED
                );

        if (!eligible) {
            throw new IllegalStateException(
                    "Passenger must have a CONFIRMED registration for this shore excursion"
            );
        }
    }

    // CHECK GROUP CAPACITY
    @Transactional(readOnly = true)
    public long checkGroupCapacity(UUID groupId) {
        TourGroup group = findGroup(groupId);
        long assigned = assignmentRepository.countActiveAssignments(
                groupId,
                TourGroupPassengerAssignmentStatus.CANCELLED
        );

        return Math.max(0L, group.getMaximumCapacity() - assigned);
    }

    private void checkGroupCapacity(TourGroup group) {
        long assigned = assignmentRepository.countActiveAssignments(
                group.getTourGroupId(),
                TourGroupPassengerAssignmentStatus.CANCELLED
        );

        if (assigned >= group.getMaximumCapacity()) {
            throw new IllegalStateException(
                    "Tour group has reached maximum capacity"
            );
        }
    }

    private void checkExcursionCapacity(ShoreExcursion excursion) {
        long assigned = assignmentRepository.countActiveAssignmentsByExcursion(
                excursion.getShoreExcursionId(),
                TourGroupPassengerAssignmentStatus.CANCELLED
        );

        if (assigned >= excursion.getMaximumCapacity()) {
            throw new IllegalStateException(
                    "Shore excursion has reached maximum capacity"
            );
        }
    }

    private TourGroup findGroup(UUID groupId) {
        if (groupId == null) {
            throw new IllegalArgumentException("Tour group ID is required");
        }

        return tourGroupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Tour group not found: " + groupId
                ));
    }

    private TourGroup findGroupForUpdate(UUID groupId) {
        if (groupId == null) {
            throw new IllegalArgumentException("Tour group ID is required");
        }

        return tourGroupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Tour group not found: " + groupId
                ));
    }

    private TourGroupPassenger findAssignment(UUID assignmentId) {
        if (assignmentId == null) {
            throw new IllegalArgumentException(
                    "Assignment ID is required"
            );
        }

        return assignmentRepository.findDetailById(assignmentId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Tour group passenger assignment not found: "
                                + assignmentId
                ));
    }

    private TourGroupPassenger findAssignmentForUpdate(UUID assignmentId) {
        if (assignmentId == null) {
            throw new IllegalArgumentException(
                    "Assignment ID is required"
            );
        }

        return assignmentRepository.findByIdForUpdate(assignmentId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Tour group passenger assignment not found: "
                                + assignmentId
                ));
    }

    /*
     * Resolve the excursion from an existing group.
     * For direct eligibility checks, the service should instead use
     * ShoreExcursionRepository.findById(excursionId).
     */
    private ShoreExcursion findExcursionFromGroupContext(UUID excursionId) {
        return tourGroupRepository.findAll().stream()
                .map(TourGroup::getShoreExcursion)
                .filter(e -> e.getShoreExcursionId().equals(excursionId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException(
                        "Shore excursion not found: " + excursionId
                ));
    }

    @Transactional(readOnly = true)
    public List<EligiblePassengerResponse> listEligiblePassengers(UUID groupId) {
        TourGroup group = tourGroupRepository.findById(groupId)
                .orElseThrow(() -> new EntityNotFoundException("TourGroup not found: " + groupId));

        UUID excursionId = group.getShoreExcursion().getShoreExcursionId();

        List<Registration> confirmedRegistrations = registrationRepository
                .findConfirmedRegistrationsByExcursion(excursionId, RegistrationStatus.CONFIRMED);

        List<TourGroupPassengerAssignmentStatus> activeStatuses = List.of(
                TourGroupPassengerAssignmentStatus.ASSIGNED,
                TourGroupPassengerAssignmentStatus.CHECKED_IN,
                TourGroupPassengerAssignmentStatus.COMPLETED
        );

        List<UUID> assignedPassengerIds = assignmentRepository
                .findAssignedPassengerIdsByExcursion(excursionId, activeStatuses);
        Set<UUID> assignedSet = new HashSet<>(assignedPassengerIds);

        return confirmedRegistrations.stream()
                .filter(r -> r.getPassenger() != null && !assignedSet.contains(r.getPassenger().getPassengerId()))
                .map(r -> EligiblePassengerResponse.builder()
                        .passengerId(r.getPassenger().getPassengerId())
                        .fullName(r.getPassenger().getFullName())
                        .email(r.getPassenger().getEmail())
                        .phoneNumber(r.getPassenger().getPhoneNumber())
                        .nationality(r.getPassenger().getNationality())
                        .registrationId(r.getRegistrationId())
                        .quantity(r.getQuantity())
                        .registrationTime(r.getRegistrationTime())
                        .build())
                .toList();
    }

    private TourGroupPassengerResponse toResponse(
            TourGroupPassenger assignment
    ) {
        TourGroup group = assignment.getTourGroup();

        return new TourGroupPassengerResponse(
                assignment.getTourGroupPassengerId(),
                group.getTourGroupId(),
                group.getGroupCode(),
                group.getShoreExcursion().getShoreExcursionId(),
                assignment.getPassenger().getPassengerId(),
                assignment.getAssignmentStatus(),
                assignment.getAssignedAt(),
                assignment.getCheckedInAt(),
                assignment.getReturnedAt(),
                assignment.getCreatedAt()
        );
    }
}