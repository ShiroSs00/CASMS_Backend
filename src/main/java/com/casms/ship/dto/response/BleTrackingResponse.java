package com.casms.ship.dto.response;

import java.time.Instant;
import java.util.UUID;

public record BleTrackingResponse(UUID shipId, BleAnchorResponse receiver, Zone zone,
        Proximity proximity, String positionKind, String source) {
    public record Zone(String id, String name, int deckNumber, String meshName,
                       BleAnchorResponse.Vector3 position) {}
    public record Proximity(Integer rawRssi,
            Double filteredRssi, Double distanceMeters, boolean distanceClamped,
            String status, Instant lastSeen, int samples, boolean calibrated,
            Double modelUnitsPerMeter, String positionKind) {}
}
