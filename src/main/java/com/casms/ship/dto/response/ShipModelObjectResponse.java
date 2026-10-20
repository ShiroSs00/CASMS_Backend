package com.casms.ship.dto.response;

import com.casms.ship.enums.ShipModelObjectType;
import java.time.Instant;
import java.util.UUID;

public record ShipModelObjectResponse(UUID objectId, UUID shipModelId, String nodeKey,
        String displayName, ShipModelObjectType objectType, UUID deckId, UUID cabinId,
        Ship3dConfigurationResponse.Vector3 position, Ship3dConfigurationResponse.Vector3 rotation,
        Ship3dConfigurationResponse.Vector3 scale, String metadataJson, Instant createdAt) {}
