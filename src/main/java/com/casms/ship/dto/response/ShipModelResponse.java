package com.casms.ship.dto.response;

import com.casms.ship.enums.ShipModelStatus;
import java.time.Instant;
import java.util.UUID;

public record ShipModelResponse(UUID shipModelId, UUID shipId, String version, String assetUrl,
        String fileFormat, String checksumSha256, String coordinateSystem, String coordinateUnit,
        Double modelUnitsPerMeter, ShipModelStatus status, Instant createdAt, Instant updatedAt) {}
