package com.casms.ship.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class CruiseTourPackageResponse {
    private UUID cruiseTourPackageId;
    private UUID cruiseId;
    private UUID tourPackageId;
    private String packageCode;
    private String packageName;
    private BigDecimal defaultPrice;
    private boolean active;
    private Instant createdAt;
}
