package com.casms.checkin.dto.response;

import com.casms.checkin.enums.GuidanceSessionParticipantAttendanceStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class GuidanceParticipantResponse {
    private UUID guidanceSessionParticipantId;
    private UUID sessionId;
    private UUID bookingPassengerId;
    private String passengerFullName;
    private String identificationNumber;
    private GuidanceSessionParticipantAttendanceStatus attendanceStatus;
    private Instant attendedAt;
}
