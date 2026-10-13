package com.casms.ship.dto.request;

import com.casms.ship.enums.CabinType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.UUID;

@Data
@Schema(description = "Bộ lọc tìm kiếm phòng/cabin còn trống")
public class CabinSearchRequest {

    @Schema(description = "Lọc theo ID tàu", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID shipId;

    @Schema(description = "Lọc theo ID tầng", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID deckId;

    @Schema(description = "Lọc theo loại phòng", example = "BALCONY")
    private CabinType cabinType;

    @Schema(description = "Sức chứa tối thiểu yêu cầu", example = "2")
    private Integer minCapacity;

    @Schema(description = "Yêu cầu phòng hỗ trợ người khuyết tật (true/false)", example = "false")
    private Boolean isAccessible;
}
