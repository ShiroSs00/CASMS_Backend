package com.casms.ship.dto.response;

import com.casms.ship.enums.ItineraryChangeType;

import java.time.Instant;
import java.util.UUID;

public record ItineraryChangeHistoryResponse(
        UUID changeHistoryId,
        UUID itineraryId,
        UUID cruiseStopId,
        String changedEntityType,
        UUID changedEntityId,
        ItineraryChangeType changeType,
        String beforeData,
        String afterData,
        String changeSummary,
        String reason,
        UUID changedByUserId,
        Instant changedAt
) {
}