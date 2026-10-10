package com.casms.ship.dto;

import com.casms.ship.entity.CabinType;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class CabinUpdateRequest {
    private CabinType cabinType;

    @Min(value = 1, message = "Capacity must be at least 1")
    private Integer capacity;

    private Boolean isAccessible;
}