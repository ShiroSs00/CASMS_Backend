package com.casms.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BookingCancelRequest {

    @Schema(description = "Lý do hủy đặt chỗ", example = "Thay đổi lịch trình cá nhân", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Cancellation reason is required")
    private String reason;
}
