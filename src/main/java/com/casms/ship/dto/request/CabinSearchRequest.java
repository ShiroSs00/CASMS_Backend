package com.casms.ship.dto.request;

import com.casms.ship.enums.CabinType;
import lombok.Data;

import java.util.UUID;

@Data
public class CabinSearchRequest {
    private UUID shipId;
    private UUID deckId;
    private CabinType cabinType;
    private Integer minCapacity;
    private Boolean isAccessible;
}