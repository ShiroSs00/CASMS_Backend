package com.casms.ship.dto.request;

import com.casms.ship.enums.ShipStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

@Schema(description="Optional supplements used only where the uploaded model lacks ship metadata; creation timestamps are automatic")
public record ShipModelProvisionRequest(@Size(max=100) String shipName, ShipStatus status,
        @Min(1) Integer totalCapacity, @Min(1) Integer totalDecks,
        @Positive Double lengthMeters, @Positive Double beamMeters, @PastOrPresent LocalDate commissionDate) {
    public ShipCreateRequest supplements() {
        var request=new ShipCreateRequest();request.setShipName(shipName);request.setStatus(status);
        request.setTotalCapacity(totalCapacity);request.setTotalDecks(totalDecks);
        request.setLengthMeters(lengthMeters);request.setBeamMeters(beamMeters);request.setCommissionDate(commissionDate);
        return request;
    }
}
