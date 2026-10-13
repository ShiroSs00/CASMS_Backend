package com.casms.activity.dto.response;

import com.casms.activity.enums.TourGroupPassengerAssignmentStatus;

import java.time.Instant;
import java.util.UUID;

public record TourGroupPassengerResponse(
        UUID tourGroupPassengerId,
        UUID tourGroupId,
        String groupCode,
        UUID shoreExcursionId,
        UUID passengerId,
        TourGroupPassengerAssignmentStatus assignmentStatus,
        Instant assignedAt,
        Instant checkedInAt,
        Instant returnedAt,
        Instant createdAt
) {
}