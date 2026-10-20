package com.casms.ship.dto.request;

import com.casms.ship.enums.ShipModelObjectType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.UUID;

@Data
public class ShipModelObjectCreateRequest {
    @NotBlank @Size(max = 200) private String nodeKey;
    @Size(max = 200) private String displayName;
    @NotNull private ShipModelObjectType objectType;
    private UUID deckId;
    private UUID cabinId;
    private Double positionX;
    private Double positionY;
    private Double positionZ;
    private Double rotationX;
    private Double rotationY;
    private Double rotationZ;
    private Double scaleX = 1.0;
    private Double scaleY = 1.0;
    private Double scaleZ = 1.0;
    @Size(max = 20000) private String metadataJson;
}
