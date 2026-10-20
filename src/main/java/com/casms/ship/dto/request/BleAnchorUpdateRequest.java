package com.casms.ship.dto.request;

import com.casms.ship.enums.LocationDeviceStatus;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.UUID;

@Data
public class BleAnchorUpdateRequest {
    @Size(max = 120) private String deviceName;
    @Size(max = 150) private String bleIdentifier;
    @Size(max = 200) private String modelNodeKey;
    private UUID deckId;
    private Double x;
    private Double y;
    private Double z;
    private Double rotationX;
    private Double rotationY;
    private Double rotationZ;
    private Double txPowerDbm;
    private Double pathLossExponent;
    @Positive private Double modelUnitsPerMeter;
    private LocationDeviceStatus status;
}
