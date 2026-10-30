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
public class CruiseDownstreamHandoffResponse {
    private UUID cruiseId;
    private String cruiseCode;
    private String cruiseName;
    private Instant departureDatetime;
    private Instant returnDatetime;
    private String cruiseStatus;

    // Source context
    private UUID sourceTripId;
    private String sourceTripCode;
    private UUID sourceVersionId;
    private Integer sourceVersionNumber;

    // Ship context
    private UUID shipId;
    private String shipName;
    private Integer totalCapacity;

    // Ports context
    private String departurePortName;
    private String returnPortName;

    // Manifest metrics (strictly differentiated)
    private long totalBookingsCount;
    private long confirmedBookingsCount;
    private long draftBookingsCount;
    private long cancelledBookingsCount;
    private long totalPassengersCount;
    private long checkedInPassengersCount;
    private long boardedPassengersCount;

    // Readiness & clearance
    private boolean captainAssigned;
    private boolean onboardStaffAssigned;
    private boolean departureClearanceReady;
    private boolean manifestLocked;

    @Builder.Default
    private List<CruiseManifestResponse.PassengerManifestItem> passengerManifest = new ArrayList<>();

    @Builder.Default
    private List<CruiseStaffResponse> staffAssignments = new ArrayList<>();
}
