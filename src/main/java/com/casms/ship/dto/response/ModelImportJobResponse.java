package com.casms.ship.dto.response;

import com.casms.ship.enums.ModelImportStatus;

import java.time.Instant;
import java.util.UUID;

public record ModelImportJobResponse(UUID jobId, UUID shipId, UUID modelId, ModelImportStatus status,
        Instant startedAt, Instant finishedAt, String requestedBy, int totalObjects, int decksImported,
        int cabinsImported, int objectsSucceeded, int objectsFailed, int objectsSkipped, String errorMessage) { }
