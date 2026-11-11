package com.casms.booking.dto.request;

import com.casms.booking.enums.CabinSelectionMode;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassengerQuoteItem {
    private UUID cabinId;

    private UUID tourPackageId;
    private UUID farePackageOfferId;
    private UUID farePackagePriceRevisionId;
    private CabinSelectionMode cabinSelectionMode;
    @Size(max = 100)
    private String cabinGroupKey;

    public PassengerQuoteItem(UUID cabinId, UUID tourPackageId) {
        this.cabinId = cabinId;
        this.tourPackageId = tourPackageId;
    }
}
