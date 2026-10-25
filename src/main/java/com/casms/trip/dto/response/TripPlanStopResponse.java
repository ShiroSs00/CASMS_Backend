package com.casms.trip.dto.response;

import com.casms.ship.enums.CruiseStopArrivalMethod;
import com.casms.ship.enums.CruiseStopCallType;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalTime;
import java.util.UUID;

@Data
@Schema(description = "Chi tiết điểm dừng cảng trong kế hoạch hành trình")
public class TripPlanStopResponse {

    private UUID stopId;
    private String clientKey;
    private UUID portId;
    private String portCode;
    private String portName;
    private String countryCode;
    private String timeZoneId;
    private UUID destinationId;
    private String destinationName;
    private Integer stopOrder;
    private Integer arrivalDayNumber;

    @JsonFormat(pattern = "HH:mm:ss", shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", pattern = "^(0[0-9]|1[0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]$", example = "08:00:00")
    private LocalTime plannedArrivalTime;

    private Integer departureDayNumber;

    @JsonFormat(pattern = "HH:mm:ss", shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", pattern = "^(0[0-9]|1[0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]$", example = "18:00:00")
    private LocalTime plannedDepartureTime;

    private CruiseStopCallType callType;
    private CruiseStopArrivalMethod arrivalMethod;
    private Integer allAboardDayNumber;

    @JsonFormat(pattern = "HH:mm:ss", shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", pattern = "^(0[0-9]|1[0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]$", example = "17:30:00")
    private LocalTime allAboardTime;

    private Boolean isEmbarkation;
    private Boolean isDisembarkation;
}
