package com.casms.ship.dto.response;

import java.util.List;
import java.util.UUID;

/** Structural ship readiness only; it does not establish cruise-specific availability or cabin vacancy. */
public record ShipReadinessAssessmentResponse(
        UUID shipId,
        String lifecycleStatus,
        String readiness,
        long deckCount,
        long cabinCount,
        List<Issue> issues,
        String scope) {
    public record Issue(String code, String message) { }
}
