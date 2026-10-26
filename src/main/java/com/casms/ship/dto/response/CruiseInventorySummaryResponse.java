package com.casms.ship.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CruiseInventorySummaryResponse {
    private UUID cruiseId;
    private String cruiseCode;
    private UUID shipId;
    private String shipName;
    private int physicalCapacity;
    private long totalCabins;
    private long operationalCabins;
    private long bookedPassengersCount;
    private long heldPassengersCount;
    private long remainingCapacity;
    private long activePackagesCount;
    private List<CabinTypeInventoryItem> cabinTypeInventory;
}
