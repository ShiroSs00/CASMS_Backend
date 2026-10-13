package com.casms.ship.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class ShipRegulationResponse {

    private UUID shipId;

    private UUID regulationId;
}
