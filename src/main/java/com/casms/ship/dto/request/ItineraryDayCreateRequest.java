package com.casms.ship.dto.request;

import com.casms.ship.enums.ItineraryDayType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Schema(description = "Yêu cầu tạo mới ngày trong lịch trình")
public class ItineraryDayCreateRequest {

    @Schema(description = "ID lịch trình cha", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Itinerary ID is required")
    private UUID itineraryId;

    @Schema(description = "Số thứ tự ngày (Nếu để trống, tự động tăng)", example = "1")
    private Integer dayNumber;

    @Schema(description = "Ngày (Nếu để trống, tự tính = itinerary.startDate + dayNumber - 1)", example = "2026-01-15")
    private LocalDate date;

    @Schema(description = "Loại ngày (mặc định PORT_DAY)", example = "PORT_DAY")
    private ItineraryDayType dayType;

    @Schema(description = "Múi giờ tàu", example = "Asia/Ho_Chi_Minh")
    @Size(max = 100, message = "Ship time zone ID must not exceed 100 characters")
    private String shipTimeZoneId;

    @Schema(description = "Mô tả ngày", example = "Khám phá Vịnh Hạ Long")
    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;
}