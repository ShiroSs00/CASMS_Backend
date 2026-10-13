
package com.casms.ship.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CruiseRegulationRequest {

    @NotNull(message = "Cruise ID is required")
    private UUID cruiseId;

    @NotNull(message = "Regulation ID is required")
    private UUID regulationId;
}
