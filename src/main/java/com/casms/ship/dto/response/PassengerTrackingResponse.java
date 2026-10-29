package com.casms.ship.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Receiver proximity, never a computed passenger position. No documents or contact data. */
public record PassengerTrackingResponse(UUID shipId, UUID cruiseId, Instant generatedAt,
        String positionKind, List<Passenger> passengers) {
    public record Passenger(UUID identityId, UUID bookingPassengerId, String fullName,
            String tagId, String tagName, String status, BleAnchorResponse receiver,
            BleTrackingResponse.Proximity proximity) { }
}
