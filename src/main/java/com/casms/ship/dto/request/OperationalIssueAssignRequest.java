
package com.casms.ship.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class OperationalIssueAssignRequest {

    @NotNull
    private UUID assignedToId;
}
