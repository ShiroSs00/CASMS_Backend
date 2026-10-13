package com.casms.ship.dto.request;

import com.casms.ship.enums.ItineraryChangeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ItineraryChangeRequest(
        @NotNull UUID itineraryId,
        UUID cruiseStopId,

        @NotBlank
        @Size(max = 50)
        String changedEntityType,

        @NotNull UUID changedEntityId,

        @NotNull
        ItineraryChangeType changeType,

        String beforeData,
        String afterData,

        @NotBlank
        @Size(max = 1000)
        String changeSummary,

        @NotBlank
        @Size(max = 1000)
        String reason,

        @NotNull UUID changedByUserId
) {
}