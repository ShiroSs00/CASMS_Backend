package com.casms.ship.dto.response;

import com.casms.ship.enums.CabinStatus;
import com.casms.ship.enums.CabinType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.UUID;

@Data
@Schema(description = "Thông tin tóm tắt của phòng/cabin")
public class CabinSummaryResponse {

    @Schema(description = "ID phòng (UUID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID cabinId;

    @Schema(description = "ID tàu", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID shipId;

    @Schema(description = "ID tầng", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID deckId;

    @Schema(description = "Số phòng", example = "301")
    private String cabinNumber;

    @Schema(description = "Loại phòng", example = "BALCONY")
    private CabinType cabinType;

    @Schema(description = "Sức chứa tối đa của phòng", example = "4")
    private Integer capacity;

    @Schema(description = "Trạng thái phòng", example = "AVAILABLE")
    private CabinStatus status;

    @Schema(description = "Có hỗ trợ người khuyết tật", example = "true")
    private Boolean isAccessible;

    private Double modelPositionX;
    private Double modelPositionY;
    private Double modelPositionZ;
    private Double modelRotationX;
    private Double modelRotationY;
    private Double modelRotationZ;
    private Double modelWidth;
    private Double modelHeight;
    private Double modelDepth;
}
