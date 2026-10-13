package com.casms.activity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShoreExcursionCancellationRequest(
        @NotBlank
        @Size(max = 500)
        String reason
) {
}