package com.casms.identity.dto;

import com.casms.identity.enums.PassengerBleIdentityStatus;
import java.time.Instant;
import java.util.UUID;

public record PassengerBleIdentityResponse(UUID identityId, UUID cruiseId,
        UUID bookingPassengerId, UUID passengerId, String bleIdentifier, String deviceName,
        PassengerBleIdentityStatus status, Instant assignedAt, String assignedBy,
        Instant revokedAt, String revokedBy, String revocationReason) {}
