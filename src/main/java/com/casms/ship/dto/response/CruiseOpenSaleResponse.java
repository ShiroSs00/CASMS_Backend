package com.casms.ship.dto.response;

import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.enums.ItineraryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CruiseOpenSaleResponse {
    private UUID cruiseId;
    private String cruiseCode;
    private CruiseStatus cruiseStatus;
    private ItineraryStatus itineraryStatus;
    private String saleScope; // WHOLE_CRUISE, CABIN_ONLY, SELECTED_PACKAGES
    private long activePackagesCount;
    private UUID openedBy;
    private Instant openedAt;
    private String fingerprint;
    private Long revision;
    private String eventIdentity;
    private boolean idempotentRetry;
}
