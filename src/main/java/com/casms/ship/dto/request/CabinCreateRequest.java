package com.casms.ship.dto.request;

import com.casms.ship.enums.CabinStatus;
import com.casms.ship.enums.CabinType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
@Schema(description = "Yêu cầu tạo mới phòng/cabin")
public class CabinCreateRequest {

    @Schema(description = "ID của tàu sở hữu phòng", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Ship ID is required")
    private UUID shipId;

    @Schema(description = "ID của tầng chứa phòng này", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Deck ID is required")
    private UUID deckId;

    @Schema(description = "Số phòng (duy nhất trên cùng một tàu)", example = "301", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Cabin number is required")
    private String cabinNumber;

    @Schema(description = "Loại phòng (INTERIOR, OCEAN_VIEW, BALCONY, SUITE)", example = "BALCONY", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Cabin type is required")
    private CabinType cabinType;

    @Schema(description = "Sức chứa hành khách tối đa của phòng", example = "4", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Capacity is required")
    @Min(value = 1, message = "Capacity must be at least 1")
    private Integer capacity;

    @Schema(description = "Trạng thái ban đầu của phòng (AVAILABLE, OCCUPIED, MAINTENANCE, OUT_OF_SERVICE)", example = "AVAILABLE", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Status is required")
    private CabinStatus status;

    @Schema(description = "Phòng có hỗ trợ người khuyết tật/tiếp cận xe lăn hay không", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Accessibility flag is required")
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
