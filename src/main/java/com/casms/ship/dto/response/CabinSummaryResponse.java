package com.casms.ship.dto.response;

import com.casms.ship.enums.CabinStatus;
import com.casms.ship.enums.CabinType;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class CabinSummaryResponse {
    private UUID cabinId;
    private UUID shipId;
    private UUID deckId;
    private String cabinNumber;
    private CabinType cabinType;
    private Integer capacity;
    private CabinStatus status;
    private Boolean isAccessible;
}