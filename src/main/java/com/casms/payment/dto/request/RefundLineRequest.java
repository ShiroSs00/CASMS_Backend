package com.casms.payment.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class RefundLineRequest {
    @NotNull(message = "Invoice Line ID is required")
    private UUID invoiceLineId;

    @NotNull(message = "Base refund amount is required")
    @DecimalMin(value = "0.0", message = "Base refund amount must be non-negative")
    private BigDecimal baseRefundAmount;

    @NotNull(message = "Tax refund amount is required")
    @DecimalMin(value = "0.0", message = "Tax refund amount must be non-negative")
    private BigDecimal taxRefundAmount;
}

