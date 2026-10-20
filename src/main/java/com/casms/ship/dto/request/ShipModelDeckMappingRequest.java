package com.casms.ship.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ShipModelDeckMappingRequest(@NotBlank @Size(max = 160) String modelDeckId, @NotNull UUID deckId) {
}
