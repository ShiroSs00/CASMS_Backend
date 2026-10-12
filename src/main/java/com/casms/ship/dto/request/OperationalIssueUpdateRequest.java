
package com.casms.ship.dto.request;

import com.casms.ship.enums.OperationalIssueSeverity;
import com.casms.ship.enums.OperationalIssueType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class OperationalIssueUpdateRequest {

    private UUID cruiseId;

    @NotNull
    private UUID shipId;

    private UUID cabinId;

    @NotNull
    private OperationalIssueType issueType;

    @NotNull
    private OperationalIssueSeverity severity;

    @NotBlank
    private String description;
}
