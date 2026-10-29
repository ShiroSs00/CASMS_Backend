package com.casms.ship.dto.response;

import com.casms.servicepackage.enums.ServiceStatus;
import java.time.Instant;
import java.util.UUID;

public record DeckServiceAssignmentResponse(UUID assignmentId, UUID shipId, UUID deckId, UUID serviceId,
        String serviceCode, String serviceName, String serviceType, ServiceStatus serviceStatus, Instant createdAt) {}
