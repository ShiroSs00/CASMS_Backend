package com.casms.ship.dto.response;

import java.util.List;
import java.util.UUID;

/** Current dependency evidence, never a transferable preview authorization token. */
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
public record ShipAssignmentSafetyResponse(
        UUID cruiseId, UUID currentShipId, UUID requestedShipId, String operation,
        boolean assignmentChanged, boolean canProceed,
        List<Dependency> dependencies, List<String> blockingCategories, List<String> blockers,
        ApprovalImpact approvalImpact) {
    public record Dependency(String category, long count, boolean blocking, boolean clarificationRequired, String reason) {}
    public record ApprovalImpact(String planStatus, boolean readyRecorded, long confirmedSignoffs,
                                 long submittedSections, boolean fingerprintWillChange, String reason) {}
}
