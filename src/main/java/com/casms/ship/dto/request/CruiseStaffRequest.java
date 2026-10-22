package com.casms.ship.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CruiseStaffRequest {
    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotBlank(message = "Role in cruise is required")
    private String roleInCruise;

    private String notes;
}
