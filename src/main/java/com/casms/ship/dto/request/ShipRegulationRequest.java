package com.casms.ship.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class ShipRegulationRequest {

    @NotNull(message = "Ship ID must not be null")
    private UUID shipId;

    @NotNull(message = "Regulation ID must not be null")
    private UUID regulationId;
}
