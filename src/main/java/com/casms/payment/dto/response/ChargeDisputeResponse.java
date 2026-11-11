package com.casms.payment.dto.response;

import com.casms.payment.enums.ChargeDisputeReasonCode;
import com.casms.payment.enums.ChargeDisputeResolutionType;
import com.casms.payment.enums.ChargeDisputeStatus;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class ChargeDisputeResponse {
    private UUID chargeDisputeId;
    private UUID chargeId;
    private UUID passengerId;
    private String passengerName;
    private BigDecimal disputedAmount;
    private ChargeDisputeReasonCode reasonCode;
    private String description;
    private ChargeDisputeStatus status;
    private Instant reportedAt;
    private UUID assignedToUserId;
    private ChargeDisputeResolutionType resolutionType;
    private String resolutionNote;
    private BigDecimal approvedAmount;
    private UUID resolutionTransactionId;
}

