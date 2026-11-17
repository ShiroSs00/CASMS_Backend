package com.casms.ship.dto.response;

import com.casms.ship.dto.request.ShipOperationalProfile;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

public record ShipOperationalProfileResponse(
        @JsonUnwrapped ShipOperationalProfile profile,
        boolean designMetadataImported) {}
