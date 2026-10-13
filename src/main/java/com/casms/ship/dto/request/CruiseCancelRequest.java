package com.casms.ship.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Yêu cầu hủy chuyến đi")
public class CruiseCancelRequest {

    @Schema(description = "Lý do hủy chuyến", example = "Điều kiện thời tiết xấu do bão", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Cancellation reason is required")
    private String reason;
}
