package com.casms.trip.dto.request;

import com.casms.ship.enums.ItineraryDayType;
import com.casms.ship.enums.ItineraryDayRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalTime;
import com.fasterxml.jackson.annotation.JsonFormat;

@Data
@Schema(description = "Dự thảo ngày tương đối trong lịch trình (1..N)")
public class TripPlanDayDraftDto {

    @NotNull(message = "Day number is required")
    @Min(value = 1, message = "Day number must be >= 1")
    @Schema(description = "Số thứ tự ngày tương đối (1..N)", example = "1")
    private Integer dayNumber;

    @Schema(description = "Loại ngày (SEA_DAY, PORT_DAY; draft cho phép để trống/null chưa chốt)", example = "PORT_DAY")
    private ItineraryDayType dayType;

    @Schema(description = "Cảng khởi hành riêng của ngày 1, kể cả khi ở trên biển")
    private java.util.UUID departurePortId;

    @JsonFormat(pattern = "HH:mm:ss", shape = JsonFormat.Shape.STRING)
    @Schema(description = "Giờ khởi hành dự kiến ngày 1 theo giờ Việt Nam", type = "string", example = "08:00:00")
    private LocalTime plannedDepartureTime;

    @Schema(description = "Vai trò ngày: DEPARTURE, REGULAR, ARRIVAL, DEPARTURE_AND_ARRIVAL; BE xác định theo vị trí ngày")
    private ItineraryDayRole dayRole;

    @Schema(description = "Múi giờ tàu áp dụng cho ngày này (IANA)", example = "Asia/Ho_Chi_Minh")
    private String shipTimeZoneId;

    @Size(max = 200)
    @Schema(description = "Tiêu đề ngày", example = "Khởi hành từ Cảng Hải Phòng")
    private String title;

    @Schema(description = "Mô tả hành trình trong ngày")
    private String description;
}
