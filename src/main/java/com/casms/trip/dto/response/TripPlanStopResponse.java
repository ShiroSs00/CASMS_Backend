package com.casms.trip.dto.response;

import com.casms.ship.enums.CruiseStopArrivalMethod;
import com.casms.ship.enums.CruiseStopCallType;
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
    private LocalTime plannedArrivalTime;
    private Integer departureDayNumber;
    private LocalTime plannedDepartureTime;
    private CruiseStopCallType callType;
    private CruiseStopArrivalMethod arrivalMethod;
    private Integer allAboardDayNumber;
    private LocalTime allAboardTime;
    private Boolean isEmbarkation;
    private Boolean isDisembarkation;
}
