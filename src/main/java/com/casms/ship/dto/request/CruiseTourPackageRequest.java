package com.casms.ship.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CruiseTourPackageRequest {
    @NotNull(message = "Tour Package ID is required")
    private UUID tourPackageId;
}
