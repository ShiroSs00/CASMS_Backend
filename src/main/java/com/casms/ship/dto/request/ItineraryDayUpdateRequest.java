package com.casms.ship.dto.request;

import com.casms.ship.enums.ItineraryDayType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Schema(description = "Yêu cầu cập nhật ngày trong lịch trình")
public class ItineraryDayUpdateRequest {

    @Schema(description = "Số thứ tự ngày mới", example = "2")
    private Integer dayNumber;

    @Schema(description = "Ngày mới", example = "2026-01-16")
    private LocalDate date;

    @Schema(description = "Loại ngày mới", example = "SEA_DAY")
    private ItineraryDayType dayType;

    @Schema(description = "Múi giờ tàu mới", example = "Asia/Ho_Chi_Minh")
    @Size(max = 100, message = "Ship time zone ID must not exceed 100 characters")
    private String shipTimeZoneId;

    @Schema(description = "Mô tả ngày mới", example = "Ngày nghỉ tại biển")
    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;
}