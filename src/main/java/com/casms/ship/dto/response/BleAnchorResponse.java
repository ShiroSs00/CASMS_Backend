package com.casms.ship.dto.response;

import com.casms.ship.enums.LocationDeviceStatus;
import com.casms.ship.enums.LocationDeviceType;
import java.time.Instant;
import java.util.UUID;

public record BleAnchorResponse(UUID anchorId, UUID shipId, UUID deckId, String deviceCode,
        String deviceName, String identifier, LocationDeviceType deviceType, LocationDeviceStatus status,
        Vector3 position, Vector3 rotation, Double txPowerDbm, Double pathLossExponent,
        Double modelUnitsPerMeter, boolean calibrated, String modelNodeKey, Instant installedAt,
        String installedBy, Instant lastSeenAt, Instant updatedAt) {
    public record Vector3(Double x, Double y, Double z) {}
}
