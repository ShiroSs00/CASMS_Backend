package com.casms.ship.dto.request;

import com.casms.ship.enums.LocationDeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.UUID;

@Data
public class BleAnchorCreateRequest {
    @Size(max = 100) private String deviceCode;
    @Size(max = 120) private String deviceName;
    @Size(max = 150) private String bleIdentifier;
    @Size(max = 200) private String modelNodeKey;
    @NotNull private LocationDeviceType deviceType;
    @NotNull private UUID deckId;
    private Double x = 0.0;
    private Double y = 0.0;
    private Double z = 0.0;
    private Double rotationX;
    private Double rotationY;
    private Double rotationZ;
    private Double txPowerDbm = -59.0;
    private Double pathLossExponent = 2.2;
    @Positive private Double modelUnitsPerMeter = 1.0;
}
