package com.casms.checkin.dto.request;

import com.casms.checkin.enums.GuidanceSessionParticipantAttendanceStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class GuidanceAttendanceRequest {

    @NotNull(message = "Booking Passenger ID is required")
    private UUID bookingPassengerId;

    @NotNull(message = "Attendance status is required")
    private GuidanceSessionParticipantAttendanceStatus attendanceStatus;
}
