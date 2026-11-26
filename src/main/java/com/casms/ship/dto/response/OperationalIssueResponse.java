package com.casms.ship.dto.response;

import com.casms.ship.enums.OperationalIssueSeverity;
import com.casms.ship.enums.OperationalIssueStatus;
import com.casms.ship.enums.OperationalIssueType;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class OperationalIssueResponse {

    private UUID issueId;
    private String issueCode;

    private UUID cruiseId;
    private String cruiseCode;
    private String cruiseName;
    private UUID shipId;
    private String shipName;
    private UUID cabinId;
    private String cabinNumber;

    private OperationalIssueType issueType;
    private OperationalIssueSeverity severity;
    private String description;

    private Instant reportedAt;
    private Instant resolvedAt;
    private OperationalIssueStatus status;

    private UUID reportedById;
    private String reporterName;
    private UUID resolvedById;
    private String resolvedByName;
    private UUID assignedToId;
    private String assignedToName;
    private UUID verifiedById;
    private String verifiedByName;
    private Instant verifiedAt;

    private String resolution;

    private Instant createdAt;
    private Instant updatedAt;
}
