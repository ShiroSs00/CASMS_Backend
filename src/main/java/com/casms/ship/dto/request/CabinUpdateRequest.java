package com.casms.ship.dto.request;

import com.casms.ship.enums.CabinType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
@Schema(description = "Yêu cầu cập nhật thông tin phòng/cabin")
public class CabinUpdateRequest {

    @Schema(description = "Loại phòng mới (INTERIOR, OCEAN_VIEW, BALCONY, SUITE)", example = "SUITE")
    private CabinType cabinType;

    @Schema(description = "Sức chứa tối đa mới của phòng", example = "6")
    @Min(value = 1, message = "Capacity must be at least 1")
    private Integer capacity;

    @Schema(description = "Cập nhật cờ hỗ trợ người khuyết tật", example = "false")
    private Boolean isAccessible;
}
