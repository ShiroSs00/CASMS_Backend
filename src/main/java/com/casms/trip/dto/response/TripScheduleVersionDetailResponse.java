package com.casms.trip.dto.response;

import com.casms.trip.enums.TripScheduleVersionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Schema(description = "Chi tiết toàn diện phiên bản kế hoạch lịch trình (kèm days, stops, entries)")
public class TripScheduleVersionDetailResponse {

    private UUID versionId;
    private UUID tripId;
    private String tripCode;
    private Integer versionNumber;
    private TripScheduleVersionStatus status;
    private Long revision;
    private String contentHash;
    private UUID creatorId;
    private String creatorName;
    private UUID submitterId;
    private String submitterName;
    private Long submittedRevision;
    private Instant submittedAt;
    private UUID publishedBy;
    private String publishedByName;
    private Instant publishedAt;
    private String rejectionReason;
    private UUID sourceVersionId;
    private String timePolicyVersion;
    private String planningPolicySnapshot;
    private Instant createdAt;
    private Instant updatedAt;

    private List<TripPlanDayResponse> days = new ArrayList<>();
    private List<TripPlanStopResponse> stops = new ArrayList<>();
    private List<TripPlanEntryResponse> entries = new ArrayList<>();
}
