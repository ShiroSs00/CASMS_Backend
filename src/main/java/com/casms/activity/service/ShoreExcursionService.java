package com.casms.activity.service;

import com.casms.activity.dto.request.ShoreExcursionRequest;
import com.casms.activity.dto.response.ShoreExcursionResponse;
import com.casms.activity.entity.Registration;
import com.casms.activity.entity.ShoreExcursion;
import com.casms.activity.entity.Supplier;
import com.casms.activity.enums.RegistrationStatus;
import com.casms.activity.enums.ShoreExcursionStatus;
import com.casms.activity.enums.SupplierStatus;
import com.casms.activity.enums.SupplierVerificationStatus;
import com.casms.activity.repository.RegistrationRepository;
import com.casms.activity.repository.ShoreExcursionRepository;
import com.casms.activity.repository.SupplierRepository;
import com.casms.ship.entity.CruiseStop;
import com.casms.ship.repository.CruiseStopRepository;
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
public class ShoreExcursionService {

    private final ShoreExcursionRepository excursionRepository;
    private final RegistrationRepository registrationRepository;
    private final SupplierRepository supplierRepository;
    private final CruiseStopRepository cruiseStopRepository;

    private static final List<RegistrationStatus> CAPACITY_STATUSES =
            List.of(
                    RegistrationStatus.CONFIRMED,
                    RegistrationStatus.PENDING,
                    RegistrationStatus.COMPLETED
            );

    private static final List<ShoreExcursionStatus> SCHEDULE_STATUSES =
            List.of(
                    ShoreExcursionStatus.PLANNED,
                    ShoreExcursionStatus.OPEN,
                    ShoreExcursionStatus.FULL,
                    ShoreExcursionStatus.CLOSED,
                    ShoreExcursionStatus.IN_PROGRESS
            );

    // ==================== CRUD ====================

    public ShoreExcursionResponse create(ShoreExcursionRequest request) {
        ShoreExcursion excursion = new ShoreExcursion();
        applyRequest(excursion, request);

        excursion.setStatus(ShoreExcursionStatus.PLANNED);
        excursion.setCancellationReason(null);

        validateExcursion(excursion, null);

        ShoreExcursion saved = excursionRepository.save(excursion);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ShoreExcursionResponse> list(
            UUID cruiseStopId,
            UUID supplierId,
            ShoreExcursionStatus status
    ) {
        List<ShoreExcursion> excursions;

        if (cruiseStopId != null) {
            excursions = excursionRepository
                    .findByCruiseStop_CruiseStopId(cruiseStopId);
        } else if (supplierId != null) {
            excursions = excursionRepository
                    .findBySupplier_SupplierId(supplierId);
        } else if (status != null) {
            excursions = excursionRepository.findByStatus(status);
        } else {
            excursions = excursionRepository.findAll();
        }

        return excursions.stream()
                .filter(e -> supplierId == null
                        || e.getSupplier().getSupplierId().equals(supplierId))
                .filter(e -> status == null || e.getStatus() == status)
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ShoreExcursionResponse detail(UUID id) {
        return toResponse(getById(id));
    }

    public ShoreExcursionResponse update(
            UUID id,
            ShoreExcursionRequest request
    ) {
        ShoreExcursion excursion = getById(id);

        if (excursion.getStatus() != ShoreExcursionStatus.PLANNED
                && excursion.getStatus() != ShoreExcursionStatus.OPEN
                && excursion.getStatus() != ShoreExcursionStatus.FULL
                && excursion.getStatus() != ShoreExcursionStatus.CLOSED) {
            throw new IllegalStateException(
                    "Excursion cannot be edited in status "
                            + excursion.getStatus()
            );
        }

        applyRequest(excursion, request);
        validateExcursion(excursion, id);

        // Chỉ tự cập nhật sức chứa; không tự mở lại excursion đã CLOSED.
        refreshCapacityStatus(excursion);

        return toResponse(excursionRepository.save(excursion));
    }

    public void deleteDraft(UUID id) {
        ShoreExcursion excursion = getById(id);

        if (excursion.getStatus() != ShoreExcursionStatus.PLANNED) {
            throw new IllegalStateException(
                    "Only PLANNED excursions can be deleted."
            );
        }

        if (registrationRepository
                .existsByShoreExcursion_ShoreExcursionId(id)) {
            throw new IllegalStateException(
                    "Cannot delete an excursion with registrations."
            );
        }

        excursionRepository.delete(excursion);
    }

    // ==================== VALIDATION ====================

    public void validateExcursion(UUID id) {
        ShoreExcursion excursion = getById(id);
        validateExcursion(excursion, id);
    }

    private void validateExcursion(
            ShoreExcursion excursion,
            UUID excludeId
    ) {
        if (excursion.getCruiseStop() == null
                || excursion.getCruiseStop().getCruiseStopId() == null) {
            throw new IllegalArgumentException("Cruise stop is required.");
        }

        if (excursion.getSupplier() == null
                || excursion.getSupplier().getSupplierId() == null) {
            throw new IllegalArgumentException("Supplier is required.");
        }

        if (excursion.getExcursionName() == null
                || excursion.getExcursionName().isBlank()) {
            throw new IllegalArgumentException("Excursion name is required.");
        }

        if (excursion.getStartTime() == null
                || excursion.getPlannedReturnTime() == null) {
            throw new IllegalArgumentException("Excursion times are required.");
        }

        if (excursion.getMeetingPoint() == null
                || excursion.getMeetingPoint().isBlank()) {
            throw new IllegalArgumentException("Meeting point is required.");
        }

        if (excursion.getMaximumCapacity() == null
                || excursion.getMaximumCapacity() <= 0) {
            throw new IllegalArgumentException(
                    "Maximum capacity must be greater than zero."
            );
        }

        if (excursion.getMinimumParticipants() != null
                && (excursion.getMinimumParticipants() < 1
                || excursion.getMinimumParticipants()
                > excursion.getMaximumCapacity())) {
            throw new IllegalArgumentException(
                    "Minimum participants must be between 1 "
                            + "and maximum capacity."
            );
        }

        if (!excursion.getPlannedReturnTime()
                .isAfter(excursion.getStartTime())) {
            throw new IllegalArgumentException(
                    "Return time must be after start time."
            );
        }

        validateSupplier(excursion.getSupplier().getSupplierId());
        validateSchedule(excursion, excludeId);
        validateReturnTime(excursion);
        validateUniqueCode(excursion, excludeId);
    }

    private void validateSupplier(UUID supplierId) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Supplier not found: " + supplierId
                        )
                );

        if (supplier.getStatus() != SupplierStatus.ACTIVE) {
            throw new IllegalStateException("Supplier is not active.");
        }

        if (supplier.getVerificationStatus()
                != SupplierVerificationStatus.VERIFIED) {
            throw new IllegalStateException(
                    "Supplier must be verified before use."
            );
        }
    }

    private void validateSchedule(
            ShoreExcursion excursion,
            UUID excludeId
    ) {
        CruiseStop stop = cruiseStopRepository
                .findById(excursion.getCruiseStop().getCruiseStopId())
                .orElseThrow(() ->
                        new EntityNotFoundException("Cruise stop not found.")
                );

        if (excursion.getStartTime().isBefore(stop.getPlannedArrival())) {
            throw new IllegalArgumentException(
                    "Excursion cannot start before cruise stop arrival."
            );
        }

        Instant latestReturn = stop.getAllAboardTime() != null
                ? stop.getAllAboardTime()
                : stop.getPlannedDeparture();

        if (!excursion.getPlannedReturnTime().isBefore(latestReturn)) {
            throw new IllegalArgumentException(
                    "Excursion must return before all-aboard/departure time."
            );
        }

        long conflicts = excursionRepository.countScheduleConflicts(
                stop.getCruiseStopId(),
                excursion.getStartTime(),
                excursion.getPlannedReturnTime(),
                SCHEDULE_STATUSES,
                excludeId
        );

        if (conflicts > 0) {
            throw new IllegalStateException(
                    "Excursion schedule overlaps another excursion "
                            + "at the same cruise stop."
            );
        }

        if (stop.getTenderTransferMinutes() != null
                && stop.getTenderTransferMinutes() > 0) {
            Instant earliestStart = stop.getPlannedArrival()
                    .plusSeconds(stop.getTenderTransferMinutes() * 60L);

            if (excursion.getStartTime().isBefore(earliestStart)) {
                throw new IllegalArgumentException(
                        "Excursion starts too soon after arrival "
                                + "for tender transfer."
                );
            }
        }
    }

    private void validateReturnTime(ShoreExcursion excursion) {
        CruiseStop stop = excursion.getCruiseStop();

        Instant deadline = stop.getAllAboardTime() != null
                ? stop.getAllAboardTime()
                : stop.getPlannedDeparture();

        if (!excursion.getPlannedReturnTime().isBefore(deadline)) {
            throw new IllegalArgumentException(
                    "Planned return time must be before all-aboard time."
            );
        }
    }

    private void validateUniqueCode(
            ShoreExcursion excursion,
            UUID excludeId
    ) {
        String code = excursion.getExcursionCode();

        if (code == null || code.isBlank()) {
            return;
        }

        UUID stopId = excursion.getCruiseStop().getCruiseStopId();

        boolean exists = excludeId == null
                ? excursionRepository
                .existsByExcursionCodeIgnoreCaseAndCruiseStop_CruiseStopId(
                        code, stopId)
                : excursionRepository
                .existsByExcursionCodeIgnoreCaseAndCruiseStop_CruiseStopIdAndShoreExcursionIdNot(
                        code, stopId, excludeId);

        if (exists) {
            throw new IllegalStateException(
                    "Excursion code already exists at this cruise stop."
            );
        }
    }

    // ==================== CAPACITY ====================

    @Transactional(readOnly = true)
    public ShoreExcursionResponse checkAvailability(
            UUID id,
            int requestedParticipants
    ) {
        if (requestedParticipants <= 0) {
            throw new IllegalArgumentException(
                    "Requested participants must be greater than zero."
            );
        }

        ShoreExcursion excursion = getById(id);
        long registered = countRegisteredParticipants(id);
        long available = excursion.getMaximumCapacity() - registered;

        if (excursion.getStatus() != ShoreExcursionStatus.OPEN
                || requestedParticipants > available) {
            throw new IllegalStateException(
                    "Excursion is unavailable or has insufficient capacity. "
                            + "Available capacity: "
                            + Math.max(0, available)
            );
        }

        return toResponse(excursion);
    }

    public ShoreExcursionResponse refreshCapacity(UUID id) {
        ShoreExcursion excursion = getById(id);
        refreshCapacityStatus(excursion);
        return toResponse(excursionRepository.save(excursion));
    }

    private void refreshCapacityStatus(ShoreExcursion excursion) {
        if (excursion.getStatus() != ShoreExcursionStatus.OPEN
                && excursion.getStatus() != ShoreExcursionStatus.FULL) {
            return;
        }

        long registered = countRegisteredParticipants(
                excursion.getShoreExcursionId()
        );

        if (registered >= excursion.getMaximumCapacity()) {
            excursion.setStatus(ShoreExcursionStatus.FULL);
        } else if (excursion.getStatus() == ShoreExcursionStatus.FULL) {
            excursion.setStatus(ShoreExcursionStatus.OPEN);
        }
    }

    private long countRegisteredParticipants(UUID excursionId) {
        Long total = registrationRepository.sumParticipants(
                excursionId,
                CAPACITY_STATUSES
        );

        return total == null ? 0 : total;
    }

    // ==================== LIFECYCLE ====================

    public ShoreExcursionResponse confirm(UUID id) {
        ShoreExcursion excursion = getById(id);

        requireStatus(excursion, ShoreExcursionStatus.PLANNED);
        validateExcursion(excursion, id);

        excursion.setStatus(ShoreExcursionStatus.OPEN);
        refreshCapacityStatus(excursion);

        return toResponse(excursionRepository.save(excursion));
    }

    public ShoreExcursionResponse close(UUID id) {
        ShoreExcursion excursion = getById(id);

        if (excursion.getStatus() != ShoreExcursionStatus.OPEN
                && excursion.getStatus() != ShoreExcursionStatus.FULL) {
            throw new IllegalStateException(
                    "Only OPEN or FULL excursions can be closed."
            );
        }

        excursion.setStatus(ShoreExcursionStatus.CLOSED);
        return toResponse(excursionRepository.save(excursion));
    }

    public ShoreExcursionResponse start(UUID id) {
        ShoreExcursion excursion = getById(id);

        if (excursion.getStatus() != ShoreExcursionStatus.OPEN
                && excursion.getStatus() != ShoreExcursionStatus.FULL) {
            throw new IllegalStateException(
                    "Only OPEN or FULL excursions can start."
            );
        }

        Instant now = Instant.now();

        if (now.isBefore(excursion.getStartTime())
                || !now.isBefore(excursion.getPlannedReturnTime())) {
            throw new IllegalStateException(
                    "Current time is outside the excursion time window."
            );
        }

        excursion.setStatus(ShoreExcursionStatus.IN_PROGRESS);
        return toResponse(excursionRepository.save(excursion));
    }

    public ShoreExcursionResponse complete(UUID id) {
        ShoreExcursion excursion = getById(id);
        requireStatus(excursion, ShoreExcursionStatus.IN_PROGRESS);

        excursion.setStatus(ShoreExcursionStatus.COMPLETED);
        return toResponse(excursionRepository.save(excursion));
    }

    public ShoreExcursionResponse cancel(UUID id, String reason) {
        ShoreExcursion excursion = getById(id);

        if (excursion.getStatus() == ShoreExcursionStatus.COMPLETED
                || excursion.getStatus() == ShoreExcursionStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Completed or cancelled excursion cannot be cancelled."
            );
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "Cancellation reason is required."
            );
        }

        excursion.setStatus(ShoreExcursionStatus.CANCELLED);
        excursion.setCancellationReason(reason.trim());

        return toResponse(excursionRepository.save(excursion));
    }

    // ==================== HELPERS ====================

    private void applyRequest(
            ShoreExcursion excursion,
            ShoreExcursionRequest request
    ) {
        CruiseStop stop = cruiseStopRepository.findById(request.cruiseStopId())
                .orElseThrow(() ->
                        new EntityNotFoundException("Cruise stop not found.")
                );

        Supplier supplier = supplierRepository.findById(request.supplierId())
                .orElseThrow(() ->
                        new EntityNotFoundException("Supplier not found.")
                );

        excursion.setCruiseStop(stop);
        excursion.setSupplier(supplier);
        excursion.setExcursionCode(blankToNull(request.excursionCode()));
        excursion.setExcursionName(request.excursionName().trim());
        excursion.setStartTime(request.startTime());
        excursion.setPlannedReturnTime(request.plannedReturnTime());
        excursion.setMeetingPoint(request.meetingPoint().trim());
        excursion.setMinimumParticipants(request.minimumParticipants());
        excursion.setMaximumCapacity(request.maximumCapacity());
        excursion.setPrice(request.price());
        excursion.setCurrencyCode(
                request.currencyCode() == null
                        ? null
                        : request.currencyCode().trim()
        );
    }

    private ShoreExcursion getById(UUID id) {
        return excursionRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Shore excursion not found: " + id
                        )
                );
    }

    private ShoreExcursionResponse toResponse(ShoreExcursion excursion) {
        return ShoreExcursionResponse.from(
                excursion,
                countRegisteredParticipants(excursion.getShoreExcursionId())
        );
    }

    private void requireStatus(
            ShoreExcursion excursion,
            ShoreExcursionStatus expected
    ) {
        if (excursion.getStatus() != expected) {
            throw new IllegalStateException(
                    "Expected status " + expected
                            + ", actual status " + excursion.getStatus()
            );
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
