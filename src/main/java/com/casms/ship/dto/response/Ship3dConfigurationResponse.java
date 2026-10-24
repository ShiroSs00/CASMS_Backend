package com.casms.ship.dto.response;

import com.casms.ship.enums.CabinType;
import com.casms.ship.enums.LocationDeviceStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Ship3dConfigurationResponse(UUID shipId, String shipCode, String shipName,
        String configurationVersion, Instant updatedAt, Model model, List<Deck> decks) {
    public record Model(UUID modelId, String assetUrl, String format, String version,
            String coordinateSystem, String unit, Double modelUnitsPerMeter) {}
    public record Vector3(Double x, Double y, Double z) {}
    public record Deck(UUID deckId, String code, String name, Integer deckNumber,
            String modelNodeKey, Double elevation, Vector3 position, Vector3 rotation, List<Room> rooms,
            List<Anchor> bleAnchors, boolean serviceLevel) {}
    public record Room(UUID roomId, String code, CabinType type, UUID deckId,
            Vector3 position, Vector3 rotation, Vector3 dimensions, String modelNodeKey) {}
    public record Anchor(UUID anchorId, String code, String name, String identifier,
            LocationDeviceStatus status, Vector3 position, Vector3 rotation, Double txPowerDbm,
            Double pathLossExponent, boolean calibrated, String modelNodeKey, Instant installedAt,
            Instant lastSeenAt) {}
}
