package com.casms.checkin.dto.response;

import com.casms.checkin.enums.GuidanceSessionStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class GuidanceSessionResponse {
    private UUID guidanceSessionId;
    private UUID cruiseId;
    private String cruiseCode;
    private String sessionName;
    private String location;
    private Instant startAt;
    private Instant endAt;
    private UUID instructorUserId;
    private String instructorUsername;
    private GuidanceSessionStatus status;
    private long totalParticipants;
    private long attendedCount;
}
