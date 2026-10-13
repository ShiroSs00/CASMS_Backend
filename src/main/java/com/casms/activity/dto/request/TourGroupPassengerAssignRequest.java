package com.casms.activity.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TourGroupPassengerAssignRequest(
        @NotNull UUID tourGroupId,
        @NotNull UUID passengerId
) {
}