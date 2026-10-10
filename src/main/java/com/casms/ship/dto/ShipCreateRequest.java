package com.casms.ship.dto;

import com.casms.ship.entity.ShipStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ShipCreateRequest {
    @NotBlank(message = "Ship code is required")
    private String shipCode;

    @NotBlank(message = "Ship name is required")
    private String shipName;

    @NotNull(message = "Status is required")
    private ShipStatus status;

    @NotNull(message = "Total capacity is required")
    @Min(value = 1, message = "Total capacity must be greater than 0")
    private Integer totalCapacity;

    @NotNull(message = "Total decks is required")
    @Min(value = 1, message = "Total decks must be greater than 0")
    private Integer totalDecks;

    private LocalDate commissionDate;
}
