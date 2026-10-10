package com.casms.ship.dto;

import com.casms.ship.entity.ShipStatus;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class ShipResponse {
    private UUID shipId;
    private String shipCode;
    private String shipName;
    private ShipStatus status;
    private Integer totalCapacity;
    private Integer totalDecks;
    private LocalDate commissionDate;
    private Instant createdAt;
    private Instant updatedAt;
}
