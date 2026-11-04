package com.casms.trip.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.*;

import java.util.UUID;

public record DestinationVisitDraftDto(
        @Schema(description = "Existing ID must belong to this stop; omit for new visit")
                UUID visitId,
        @NotBlank @Size(max = 100) String clientKey,
        @NotNull UUID destinationId,
        @NotNull @Positive Integer sequence) {}
