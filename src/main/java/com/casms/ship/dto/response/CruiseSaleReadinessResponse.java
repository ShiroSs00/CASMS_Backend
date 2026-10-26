package com.casms.ship.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CruiseSaleReadinessResponse {
    private UUID cruiseId;
    private boolean ready;
    private boolean operationalReady;
    private boolean packagesConfigured;
    private long activePackagesCount;
    private boolean inventoryAvailable;
    private long remainingCapacity;
    private int physicalCapacity;
    private boolean departureInFuture;
    private boolean pricingConfigured;

    @Builder.Default
    private List<String> blockers = new ArrayList<>();

    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    @Builder.Default
    private List<String> issues = new ArrayList<>();

    private Instant evaluatedAt;
    private Long cruiseRevision;
}
