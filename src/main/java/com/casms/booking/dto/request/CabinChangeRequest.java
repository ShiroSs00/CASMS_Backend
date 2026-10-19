package com.casms.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CabinChangeRequest {

    @Schema(description = "ID bản ghi hành khách trong booking (BookingPassenger ID)", example = "33333333-4444-5555-6666-777777777777", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Booking passenger ID is required")
    private UUID bookingPassengerId;

    @Schema(description = "ID phòng mới muốn chuyển sang", example = "44444444-5555-6666-7777-888888888888", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "New cabin ID is required")
    private UUID newCabinId;

    @Schema(description = "Lý do thay đổi phòng", example = "Nâng cấp lên phòng ban công")
    private String changeReason;
}
