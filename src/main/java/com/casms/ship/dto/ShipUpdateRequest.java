package com.casms.ship.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ShipUpdateRequest {
    private String shipName;
    
    @Min(value = 1, message = "Total capacity must be greater than 0")
    private Integer totalCapacity;

    @Min(value = 1, message = "Total decks must be greater than 0")
    private Integer totalDecks;

    private LocalDate commissionDate;
}
