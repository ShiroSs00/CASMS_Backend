package com.casms.ship.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ModelImportIssueResponse(UUID issueId, String objectIdentifier, String objectType,
        String errorCode, String message, Instant createdAt) { }
