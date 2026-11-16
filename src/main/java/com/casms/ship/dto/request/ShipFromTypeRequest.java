package com.casms.ship.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShipFromTypeRequest(
        @NotBlank @Size(max = 100) String shipName,
        @Valid ShipOperationalProfile profile) {}
