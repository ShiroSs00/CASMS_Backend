package com.casms.trip.dto.response;

import com.casms.ship.enums.ItineraryDayType;
import com.casms.ship.enums.ItineraryDayRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalTime;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.UUID;

@Data
@Schema(description = "Chi tiết ngày trong kế hoạch hành trình")
public class TripPlanDayResponse {

    private UUID dayId;
    private Integer dayNumber;
    private ItineraryDayType dayType;

    @Schema(description = "Cảng khởi hành riêng của ngày 1, kể cả khi ở trên biển")
    private java.util.UUID departurePortId;
    private String departurePortName;

    @JsonFormat(pattern = "HH:mm:ss", shape = JsonFormat.Shape.STRING)
    @Schema(description = "Giờ khởi hành dự kiến ngày 1 theo giờ Việt Nam", type = "string", example = "08:00:00")
    private LocalTime plannedDepartureTime;

    @Schema(description = "Vai trò ngày: DEPARTURE, REGULAR, ARRIVAL, DEPARTURE_AND_ARRIVAL; BE xác định theo vị trí ngày")
    private ItineraryDayRole dayRole;
    private String shipTimeZoneId;
    private String title;
    private String description;
}
