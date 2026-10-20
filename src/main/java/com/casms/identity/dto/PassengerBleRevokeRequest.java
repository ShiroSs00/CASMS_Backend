package com.casms.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PassengerBleRevokeRequest {
    @NotBlank @Size(max = 500) private String reason;
}
