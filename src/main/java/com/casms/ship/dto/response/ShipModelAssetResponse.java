package com.casms.ship.dto.response;

import java.util.List;
import java.util.UUID;

public record ShipModelAssetResponse(
        UUID id,
        String modelKey,
        String revision,
        String assetVersion,
        String status,
        String baseUrl,
        String manifestUrl,
        String units,
        String upAxis,
        int cabinCount,
        int mappedCabinCount,
        int deckCount,
        int mappedDeckCount,
        List<Integer> deckNumbers,
        String shipManufacturerName,
        String modelProvider) {
}
