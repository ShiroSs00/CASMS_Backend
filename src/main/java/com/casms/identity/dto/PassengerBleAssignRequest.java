package com.casms.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.UUID;

@Data
public class PassengerBleAssignRequest {
    @NotNull private UUID bookingPassengerId;
    @NotBlank @Size(max = 150) private String bleIdentifier;
    @Size(max = 80) private String deviceName;
}
