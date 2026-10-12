
package com.casms.ship.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OperationalIssueResolutionRequest {

    @NotBlank
    private String resolution;
}
