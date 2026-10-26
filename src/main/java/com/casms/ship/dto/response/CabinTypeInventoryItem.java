package com.casms.ship.dto.response;

import com.casms.ship.enums.CabinType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CabinTypeInventoryItem {
    private CabinType cabinType;
    private long totalCabins;
    private long operationalCabins;
    private long totalSlots;
    private long occupiedSlots;
    private long remainingSlots;
    private BigDecimal baseFare;
}
