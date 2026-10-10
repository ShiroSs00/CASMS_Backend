package com.casms.ship.dto;

import com.casms.ship.entity.CabinStatus;
import com.casms.ship.entity.CabinType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CabinCreateRequest {
    @NotNull(message = "Ship ID is required")
    private UUID shipId;

    @NotNull(message = "Deck ID is required")
    private UUID deckId;

    @NotBlank(message = "Cabin number is required")
    private String cabinNumber;

    @NotNull(message = "Cabin type is required")
    private CabinType cabinType;

    @NotNull(message = "Capacity is required")
    @Min(value = 1, message = "Capacity must be at least 1")
    private Integer capacity;

    @NotNull(message = "Status is required")
    private CabinStatus status;

    @NotNull(message = "Accessibility flag is required")
    private Boolean isAccessible;
}