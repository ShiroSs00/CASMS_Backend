package com.casms.trip.dto.request;

import com.casms.ship.enums.ItineraryDayType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Dự thảo ngày tương đối trong lịch trình (1..N)")
public class TripPlanDayDraftDto {

    @NotNull(message = "Day number is required")
    @Min(value = 1, message = "Day number must be >= 1")
    @Schema(description = "Số thứ tự ngày tương đối (1..N)", example = "1")
    private Integer dayNumber;

    @Schema(description = "Loại ngày (SEA_DAY, PORT_DAY; draft cho phép để trống/null chưa chốt)", example = "PORT_DAY")
    private ItineraryDayType dayType;

    @Schema(description = "Múi giờ tàu áp dụng cho ngày này (IANA)", example = "Asia/Ho_Chi_Minh")
    private String shipTimeZoneId;

    @Size(max = 200)
    @Schema(description = "Tiêu đề ngày", example = "Khởi hành từ Cảng Hải Phòng")
    private String title;

    @Schema(description = "Mô tả hành trình trong ngày")
    private String description;
}
