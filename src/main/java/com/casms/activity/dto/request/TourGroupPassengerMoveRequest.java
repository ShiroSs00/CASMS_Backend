package com.casms.activity.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TourGroupPassengerMoveRequest(
        @NotNull UUID targetTourGroupId
) {
}