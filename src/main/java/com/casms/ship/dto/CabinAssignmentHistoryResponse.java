package com.casms.ship.dto;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
public class CabinAssignmentHistoryResponse {
    private UUID cabinAssignmentId;
    private UUID bookingPassengerId;
    private Instant validFrom;
    private Instant validTo;
    private UUID assignedByUserId;
    private String changeReason;
}