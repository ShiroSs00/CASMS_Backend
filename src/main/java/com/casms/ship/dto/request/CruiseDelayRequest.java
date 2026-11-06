package com.casms.ship.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CruiseDelayRequest(
        @NotNull @Positive Integer delayMinutes,
        @NotBlank @Size(max = 1000) String reason,
        @NotNull @Pattern(regexp = "[A-Z][A-Z0-9_]{0,49}") String category,
        @NotNull UUID requestId,
        @NotNull @Positive Long expectedRevision) {}
