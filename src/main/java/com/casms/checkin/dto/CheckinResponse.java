package com.casms.checkin.dto;

import com.casms.checkin.entity.CruiseCheckin;
import com.casms.checkin.enums.CruiseCheckinStatus;

import java.time.Instant;
import java.util.UUID;

/** Only check-in fields are serialized; JPA account/booking graphs are never exposed. */
public record CheckinResponse(
        UUID cruiseCheckinId,
        UUID bookingPassengerId,
        CruiseCheckinStatus checkinStatus,
        String identityDocumentSnapshot,
        String emergencyContactName,
        String emergencyContactPhone,
        Boolean locationTrackingAllowed,
        Boolean locationSharingAllowed,
        Instant startedAt,
        Instant completedAt,
        Instant boardingReadyAt,
        String boardingBlockReason) {
    public static CheckinResponse of(CruiseCheckin c) {
        return new CheckinResponse(
                c.getCruiseCheckinId(),
                c.getBookingPassenger().getBookingPassengerId(),
                c.getCheckinStatus(),
                c.getIdentityDocumentSnapshot(),
                c.getEmergencyContactName(),
                c.getEmergencyContactPhone(),
                c.getLocationTrackingAllowed(),
                c.getLocationSharingAllowed(),
                c.getStartedAt(),
                c.getCompletedAt(),
                c.getBoardingReadyAt(),
                c.getBoardingBlockReason());
    }
}
