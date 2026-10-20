package com.casms.ship.dto.response;

public record ShipModelAssetUploadResponse(String modelKey, String assetVersion, int fileCount, long expandedBytes) { }
