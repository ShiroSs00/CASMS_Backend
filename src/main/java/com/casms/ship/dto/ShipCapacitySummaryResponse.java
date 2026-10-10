package com.casms.ship.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class ShipCapacitySummaryResponse {
    private UUID shipId;
    private String shipName;
    private Integer configuredTotalCapacity;
    private Integer configuredTotalDecks;
    private Long actualCabinCount;
    private Long availableCabinCount;
    private Long actualDeckCount;
}
