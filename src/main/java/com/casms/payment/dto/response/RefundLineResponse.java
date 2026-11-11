package com.casms.payment.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class RefundLineResponse {
    private UUID refundLineId;
    private UUID refundId;
    private UUID invoiceLineId;
    private BigDecimal baseRefundAmount;
    private BigDecimal taxRefundAmount;
    private UUID reversalChargeId;
}

