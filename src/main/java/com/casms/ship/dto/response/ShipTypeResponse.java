package com.casms.ship.dto.response;

import com.casms.ship.entity.ShipType;

import java.util.UUID;

public record ShipTypeResponse(UUID shipTypeId, String code, String name, String status,
        String modelKey, String assetVersion, String manufacturerName, String engineType,
        int totalCapacity, int totalDecks, double lengthMeters, double beamMeters, long shipCount,
        ShipDesignMetadata manufacturingMetadata) {

    public static ShipTypeResponse from(ShipType type, long shipCount, ShipDesignMetadata metadata) {
        return new ShipTypeResponse(type.getShipTypeId(), type.getCode(), type.getName(),
                type.isActive() ? "ACTIVE" : "INACTIVE", type.getModelKey(), type.getAssetVersion(),
                type.getManufacturerName(), type.getEngineType(), type.getTotalCapacity(),
                type.getTotalDecks(), type.getLengthMeters(), type.getBeamMeters(), shipCount, metadata);
    }
}
