package com.casms.activity.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ShoreExcursionRequest(
        @NotNull UUID cruiseStopId,
        @NotNull UUID supplierId,

        @Size(max = 50)
        String excursionCode,

        @NotBlank
        @Size(max = 250)
        String excursionName,

        @NotNull Instant startTime,
        @NotNull Instant plannedReturnTime,

        @NotBlank
        @Size(max = 250)
        String meetingPoint,

        @Min(1)
        Integer minimumParticipants,

        @NotNull
        @Min(1)
        Integer maximumCapacity,

        @DecimalMin("0.0")
        @Digits(integer = 15, fraction = 4)
        BigDecimal price,

        @Pattern(regexp = "^[A-Z]{3}$")
        String currencyCode
) {
}