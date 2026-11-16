package com.casms.ship.dto.response;

import com.casms.ship.enums.ShipStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EligibleCruiseShipsResponse(
        UUID sourceVersionId, Long sourceVersionRevision, Instant departureDatetime,
        Instant returnDatetime, int expectedPassengerCount, List<Candidate> ships) {
    public record Candidate(UUID shipId, String shipName, String shipCode, Integer totalCapacity, ShipStatus status) {}
}
