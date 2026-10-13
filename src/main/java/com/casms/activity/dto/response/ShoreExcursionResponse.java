package com.casms.activity.dto.response;

import com.casms.activity.entity.ShoreExcursion;
import com.casms.activity.enums.ShoreExcursionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ShoreExcursionResponse(
        UUID shoreExcursionId,
        UUID cruiseStopId,
        String excursionCode,
        String excursionName,
        UUID supplierId,
        String supplierName,
        Instant startTime,
        Instant plannedReturnTime,
        String meetingPoint,
        Integer minimumParticipants,
        Integer maximumCapacity,
        long registeredParticipants,
        long availableCapacity,
        BigDecimal price,
        String currencyCode,
        ShoreExcursionStatus status,
        String cancellationReason,
        Instant createdAt,
        Instant updatedAt
) {
    public static ShoreExcursionResponse from(
            ShoreExcursion excursion,
            long registeredParticipants
    ) {
        long available = Math.max(
                0,
                excursion.getMaximumCapacity() - registeredParticipants
        );

        return new ShoreExcursionResponse(
                excursion.getShoreExcursionId(),
                excursion.getCruiseStop().getCruiseStopId(),
                excursion.getExcursionCode(),
                excursion.getExcursionName(),
                excursion.getSupplier().getSupplierId(),
                excursion.getSupplier().getSupplierName(),
                excursion.getStartTime(),
                excursion.getPlannedReturnTime(),
                excursion.getMeetingPoint(),
                excursion.getMinimumParticipants(),
                excursion.getMaximumCapacity(),
                registeredParticipants,
                available,
                excursion.getPrice(),
                excursion.getCurrencyCode() == null
                        ? null
                        : excursion.getCurrencyCode().trim(),
                excursion.getStatus(),
                excursion.getCancellationReason(),
                excursion.getCreatedAt(),
                excursion.getUpdatedAt()
        );
    }
}