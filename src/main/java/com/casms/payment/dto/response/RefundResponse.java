package com.casms.payment.dto.response;

import com.casms.payment.enums.RefundStatus;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class RefundResponse {
    private UUID refundId;
    private UUID originalPaymentId;
    private UUID finalSettlementId;
    private String refundReason;
    private BigDecimal requestedAmount;
    private BigDecimal approvedAmount;
    private String currency;
    private Instant requestedAt;
    private UUID approvedByUserId;
    private Instant approvedAt;
    private Instant completedAt;
    private String processorOrReceiptReference;
    private RefundStatus status;
    private String idempotencyKey;
    private List<RefundLineResponse> lines;
}

