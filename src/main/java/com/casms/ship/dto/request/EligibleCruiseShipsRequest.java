package com.casms.ship.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.UUID;

public record EligibleCruiseShipsRequest(
        @NotNull UUID sourceVersionId,
        @NotNull @Positive Long expectedSourceVersionRevision,
        @NotNull Instant departureDatetime,
        @NotNull @Positive @JsonDeserialize(using = PassengerCountDeserializer.class) Integer expectedPassengerCount) {}
