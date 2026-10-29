package com.casms.trip.dto.response;

import com.casms.ship.enums.ItineraryDayType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.UUID;

@Data
@Schema(description = "Chi tiết ngày trong kế hoạch hành trình")
public class TripPlanDayResponse {

    private UUID dayId;
    private Integer dayNumber;
    private ItineraryDayType dayType;
    private String shipTimeZoneId;
    private String title;
    private String description;
}
