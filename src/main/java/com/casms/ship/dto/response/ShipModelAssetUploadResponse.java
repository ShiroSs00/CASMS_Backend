package com.casms.ship.dto.response;

public record ShipModelAssetUploadResponse(
        String modelKey, int fileCount, long expandedBytes) {}
