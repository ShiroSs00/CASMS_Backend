package com.casms.ship.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "Yêu cầu cập nhật thông tin tàu")
public class ShipUpdateRequest {

    @Schema(description = "Tên tàu mới", example = "Ocean Voyager II")
    private String shipName;

    @Schema(description = "Tổng sức chứa cập nhật", example = "2800")
    @Min(value = 1, message = "Total capacity must be greater than 0")
    private Integer totalCapacity;

    @Schema(description = "Tổng số boong cập nhật", example = "14")
    @Min(value = 1, message = "Total decks must be greater than 0")
    private Integer totalDecks;

    @Schema(description = "Ngày đưa vào hoạt động cập nhật", example = "2024-06-01")
    private LocalDate commissionDate;
}
