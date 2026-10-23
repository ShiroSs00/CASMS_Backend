package com.casms.ship.dto.response;

import java.util.UUID;

public record ShipModelManifestRegistrationResponse(UUID shipId, UUID shipModelId, boolean shipCreated) { }
