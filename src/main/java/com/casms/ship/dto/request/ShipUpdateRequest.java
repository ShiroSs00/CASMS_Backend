package com.casms.ship.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Max;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

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

    @Positive private Double lengthMeters;

    @Positive private Double beamMeters;

    @Schema(description = "Ngày đưa vào hoạt động cập nhật", example = "2024-06-01")
    private LocalDate commissionDate;

    @Schema(description = "Nhà máy hoặc đơn vị đóng tàu")
    @Size(max = 150)
    private String manufacturerName;

    @Schema(description = "Nơi xuất xứ của tàu")
    @Size(max = 100)
    private String origin;

    @Schema(description = "Năm đóng tàu")
    @Min(1)
    @Max(9999)
    private Integer productionYear;

    @Schema(description = "Loại động cơ")
    @Size(max = 100)
    private String engineType;
}
