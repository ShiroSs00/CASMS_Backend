package com.casms.ship.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ShipModelAssetRegisterRequest(
        @NotBlank @Size(max = 80) @Pattern(regexp = "[a-z0-9-]+") String modelKey,
        @NotBlank @Size(max = 80) @Pattern(regexp = "[a-zA-Z0-9._-]+") String assetVersion) {
}
