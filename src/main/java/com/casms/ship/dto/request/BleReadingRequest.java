package com.casms.ship.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.Instant;

@Data
public class BleReadingRequest {
    @NotBlank @Size(max = 150) private String deviceId;
    @NotBlank @Size(max = 80) private String deviceName;
    @NotNull @Min(-127) @Max(-1) private Integer rssi;
    @NotNull private Instant timestamp;
}
