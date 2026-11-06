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
        Instant createdAt,
        String passengerName
) {
    public TourGroupPassengerResponse(UUID id,UUID groupId,String code,UUID excursionId,UUID passengerId,
        TourGroupPassengerAssignmentStatus status,Instant assigned,Instant checked,Instant returned,Instant created) {
        this(id,groupId,code,excursionId,passengerId,status,assigned,checked,returned,created,null);
    }
}