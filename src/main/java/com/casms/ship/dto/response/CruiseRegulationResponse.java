
package com.casms.ship.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class CruiseRegulationResponse {

    private UUID cruiseId;
    private UUID regulationId;
}
