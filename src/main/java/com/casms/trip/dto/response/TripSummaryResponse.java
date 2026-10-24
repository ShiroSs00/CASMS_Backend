package com.casms.trip.dto.response;

import com.casms.ship.enums.ItineraryType;
import com.casms.trip.enums.TripScheduleVersionStatus;
import com.casms.trip.enums.TripStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Schema(description = "Tóm tắt thông tin Trip")
public class TripSummaryResponse {

    private UUID tripId;
    private String tripCode;
    private String name;
    private String description;
    private Integer durationDays;
    private Integer durationNights;
    private ItineraryType tripType;
    private String plannedScope;
    private TripStatus status;
    private Instant structuralLockedAt;
    private Long revision;
    private Integer latestVersionNumber;
    private TripScheduleVersionStatus latestVersionStatus;
    private Instant createdAt;
    private Instant updatedAt;
}
