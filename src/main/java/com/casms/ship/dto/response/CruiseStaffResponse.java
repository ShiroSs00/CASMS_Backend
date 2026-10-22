package com.casms.ship.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class CruiseStaffResponse {
    private UUID cruiseStaffId;
    private UUID cruiseId;
    private String cruiseCode;
    private UUID userId;
    private String username;
    private String fullName;
    private String roleInCruise;
    private Instant assignedAt;
    private String status;
    private String notes;
}
