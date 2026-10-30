package com.casms.ship.dto.response;

import com.casms.ship.dto.request.ShipModelCabinMappingRequest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;

/** Published physical layout only; never includes staff, tracking or operational configuration. */
public record PublicShipModelResponse(UUID shipId, UUID modelId, JsonNode manifest,
                                     List<ShipModelCabinMappingRequest> cabinMappings) {}
