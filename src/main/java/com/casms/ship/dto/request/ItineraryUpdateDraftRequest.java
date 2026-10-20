package com.casms.ship.dto.request;

import com.casms.ship.enums.ItineraryType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Schema(description = "Yêu cầu cập nhật bản nháp lịch trình")
public class ItineraryUpdateDraftRequest {

    @Schema(description = "Tên lịch trình mới", example = "Lịch trình Hạ Long - Cát Bà 3N2Đ")
    @Size(max = 200, message = "Itinerary name must not exceed 200 characters")
    private String itineraryName;

    @Schema(description = "Ngày bắt đầu mới", example = "2026-01-20")
    private LocalDate startDate;

    @Schema(description = "Ngày kết thúc mới", example = "2026-01-22")
    private LocalDate endDate;

    @Schema(description = "Loại lịch trình mới", example = "ONE_WAY")
    private ItineraryType itineraryType;
}