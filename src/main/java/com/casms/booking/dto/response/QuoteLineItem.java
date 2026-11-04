package com.casms.booking.dto.response;

import com.casms.ship.enums.CabinType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteLineItem {
    private UUID cabinId;
    private String cabinNumber;
    private CabinType cabinType;
    private Integer deckNumber;
    private BigDecimal baseFare;
    private BigDecimal priceMultiplier;
    private BigDecimal cabinPrice;

    private UUID tourPackageId;
    private String packageName;
    private BigDecimal packagePrice;

    private BigDecimal itemTotal;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private com.casms.booking.enums.CabinSelectionMode cabinSelectionMode;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String cabinGroupKey;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private com.casms.cruisepackage.dto.CruisePackagePurchaseSelection fareSelection;
}
