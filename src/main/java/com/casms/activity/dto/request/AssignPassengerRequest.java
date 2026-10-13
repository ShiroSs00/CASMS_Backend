package com.casms.activity.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignPassengerRequest(
        @NotNull UUID passengerId
) {
}