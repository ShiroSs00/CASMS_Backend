package com.casms.activity.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record TourGroupRequest(
        @NotNull UUID shoreExcursionId,

        @NotBlank
        @Size(max = 50)
        String groupCode,

        @NotNull
        @Min(1)
        Integer maximumCapacity,

        UUID guideId,
        UUID vehicleId,

        @Size(max = 250)
        String meetingPoint,

        Instant assemblyTime,
        Instant plannedDepartureTime,
        Instant plannedReturnTime,

        @Size(max = 500)
        String notes
) {
}