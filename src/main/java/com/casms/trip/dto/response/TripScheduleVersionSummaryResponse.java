package com.casms.trip.dto.response;

import com.casms.trip.enums.TripScheduleVersionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Schema(description = "Tóm tắt phiên bản mẫu lịch trình")
public class TripScheduleVersionSummaryResponse {

    private UUID versionId;
    private Integer versionNumber;
    private TripScheduleVersionStatus status;
    private Long revision;
    private String contentHash;
    private UUID creatorId;
    private UUID submitterId;
    private Long submittedRevision;
    private UUID publishedBy;
    private Instant publishedAt;
    private String rejectionReason;
    private UUID sourceVersionId;
    private String timePolicyVersion;
    private Instant createdAt;
    private Instant updatedAt;
}
