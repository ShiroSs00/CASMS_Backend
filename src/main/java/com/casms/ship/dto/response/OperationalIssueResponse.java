
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
    private UUID shipId;
    private UUID cabinId;

    private OperationalIssueType issueType;
    private OperationalIssueSeverity severity;
    private String description;

    private Instant reportedAt;
    private Instant resolvedAt;
    private OperationalIssueStatus status;

    private UUID reportedById;
    private UUID resolvedById;
    private UUID assignedToId;
    private UUID verifiedById;
    private Instant verifiedAt;

    private String resolution;

    private Instant createdAt;
    private Instant updatedAt;
}
