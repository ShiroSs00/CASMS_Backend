package com.casms.ship.dto.request;

import com.casms.ship.enums.ShipStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "Yêu cầu tạo mới tàu")
public class ShipCreateRequest {

    @Schema(description = "Tên tàu", example = "Ocean Voyager", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Ship name is required")
    private String shipName;

    @Schema(description = "Trạng thái hoạt động ban đầu (mặc định ACTIVE)", example = "ACTIVE")
    private ShipStatus status;

    @Schema(description = "Tổng sức chứa hành khách tối đa", example = "2500", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Total capacity is required")
    @Min(value = 1, message = "Total capacity must be greater than 0")
    private Integer totalCapacity;

    @Schema(description = "Tổng số tầng (boong) của tàu", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Total decks is required")
    @Min(value = 1, message = "Total decks must be greater than 0")
    private Integer totalDecks;

    @Positive
    private Double lengthMeters;

    @Positive
    private Double beamMeters;

    @Schema(description = "Ngày đưa vào hoạt động / hạ thủy", example = "2024-01-15")
    private LocalDate commissionDate;
}
