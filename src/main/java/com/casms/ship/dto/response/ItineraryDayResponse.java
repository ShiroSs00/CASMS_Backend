package com.casms.ship.dto.response;

import com.casms.ship.enums.ItineraryDayType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Schema(description = "Thông tin chi tiết ngày trong lịch trình")
public class ItineraryDayResponse {

    @Schema(description = "ID ngày lịch trình", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID itineraryDayId;

    @Schema(description = "ID lịch trình cha", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID itineraryId;

    @Schema(description = "Số thứ tự ngày", example = "1")
    private Integer dayNumber;

    @Schema(description = "Ngày", example = "2026-01-15")
    private java.time.LocalDate date;

    @Schema(description = "Loại ngày", example = "PORT_DAY")
    private ItineraryDayType dayType;

    @Schema(description = "Múi giờ tàu", example = "Asia/Ho_Chi_Minh")
    private String shipTimeZoneId;

    @Schema(description = "Mô tả ngày", example = "Khám phá Vịnh Hạ Long")
    private String description;

    @Schema(description = "Danh sách điểm dừng trong ngày")
    private List<CruiseStopResponse> stops;
}