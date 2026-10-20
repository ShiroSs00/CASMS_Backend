package com.casms.ship.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ShipModelCreateRequest {
    @NotBlank @Size(max = 50) private String version;
    @NotBlank @Size(max = 1000) private String fileUri;
    @NotBlank @Size(max = 10) private String fileFormat;
    @Size(max = 64) private String checksumSha256;
    @NotBlank @Size(max = 30) private String coordinateSystem = "MODEL_LOCAL";
    @NotBlank @Size(max = 30) private String coordinateUnit = "model_unit";
    @NotNull @Positive private Double modelUnitsPerMeter = 1.0;
}
