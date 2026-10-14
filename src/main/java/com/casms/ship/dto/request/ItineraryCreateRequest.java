package com.casms.ship.dto.request;

import com.casms.ship.enums.ItineraryType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Schema(description = "Yêu cầu tạo mới lịch trình")
public class ItineraryCreateRequest {

    @Schema(description = "ID của chuyến đi (Cruise)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Cruise ID is required")
    private UUID cruiseId;

    @Schema(description = "Tên lịch trình", example = "Lịch trình Hạ Long 3N2Đ", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Itinerary name is required")
    @Size(max = 200, message = "Itinerary name must not exceed 200 characters")
    private String itineraryName;

    @Schema(description = "Ngày bắt đầu", example = "2026-01-15", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @Schema(description = "Ngày kết thúc", example = "2026-01-17", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "End date is required")
    private LocalDate endDate;

    @Schema(description = "Loại lịch trình", example = "ROUND_TRIP", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Itinerary type is required")
    private ItineraryType itineraryType;

    @Schema(description = "ID người tạo", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID createdByUserId;
}