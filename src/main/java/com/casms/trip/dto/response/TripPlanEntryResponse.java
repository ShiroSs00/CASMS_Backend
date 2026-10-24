package com.casms.trip.dto.response;

import com.casms.trip.enums.TripPlanEntryKind;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalTime;
import java.util.UUID;

@Data
@Schema(description = "Chi tiết mục kế hoạch (hoạt động / tour / serving window)")
public class TripPlanEntryResponse {

    private UUID entryId;
    private String clientKey;
    private UUID dayId;
    private Integer dayNumber;
    private TripPlanEntryKind kind;
    private UUID stopId;
    private String stopClientKey;
    private UUID serviceId;
    private UUID destinationId;
    private String destinationName;
    private String title;
    private Integer startDayNumber;
    private LocalTime plannedStartTime;
    private Integer endDayNumber;
    private LocalTime plannedEndTime;
    private String timeZoneId;
    private String locationText;
    private String meetingPoint;
    private String notes;
}
