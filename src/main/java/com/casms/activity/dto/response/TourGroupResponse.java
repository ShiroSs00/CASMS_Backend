package com.casms.activity.dto.response;

import com.casms.activity.entity.TourGroup;
import com.casms.activity.enums.TourGroupStatus;

import java.time.Instant;
import java.util.UUID;

public record TourGroupResponse(
        UUID tourGroupId,
        UUID shoreExcursionId,
        String excursionName,
        String groupCode,
        Integer maximumCapacity,
        long assignedPassengers,
        long availableCapacity,
        UUID guideId,
        UUID vehicleId,
        String meetingPoint,
        Instant assemblyTime,
        Instant plannedDepartureTime,
        Instant plannedReturnTime,
        Instant actualDepartureTime,
        Instant actualReturnTime,
        TourGroupStatus status,
        String notes
) {
    public static TourGroupResponse from(
            TourGroup group,
            long assignedPassengers
    ) {
        return new TourGroupResponse(
                group.getTourGroupId(),
                group.getShoreExcursion().getShoreExcursionId(),
                group.getShoreExcursion().getExcursionName(),
                group.getGroupCode(),
                group.getMaximumCapacity(),
                assignedPassengers,
                Math.max(0, group.getMaximumCapacity() - assignedPassengers),
                group.getGuideId(),
                group.getVehicleId(),
                group.getMeetingPoint(),
                group.getAssemblyTime(),
                group.getPlannedDepartureTime(),
                group.getPlannedReturnTime(),
                group.getActualDepartureTime(),
                group.getActualReturnTime(),
                group.getStatus(),
                group.getNotes()
        );
    }
}