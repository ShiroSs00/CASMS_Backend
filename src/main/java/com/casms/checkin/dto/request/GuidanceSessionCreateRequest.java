package com.casms.checkin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class GuidanceSessionCreateRequest {

    @NotNull(message = "Cruise ID is required")
    private UUID cruiseId;

    @NotBlank(message = "Session name is required")
    private String sessionName;

    @NotBlank(message = "Location is required")
    private String location;

    @NotNull(message = "Start time is required")
    private Instant startAt;

    @NotNull(message = "End time is required")
    private Instant endAt;

    @NotNull(message = "Instructor user ID is required")
    private UUID instructorUserId;
}
