package com.casms.payment.dto.response;

import com.casms.payment.enums.PaymentMethod;
import com.casms.payment.enums.PaymentOperationType;
import com.casms.payment.enums.PaymentStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class PaymentResponse {
    private UUID paymentId;
    private UUID bookingId;
    private String bookingReference;
    private PaymentOperationType operationType;
    private PaymentMethod paymentMethod;
    private BigDecimal requestedAmount;
    private BigDecimal confirmedAmount;
    private String currency;
    private Instant requestedAt;
    private Instant confirmedAt;
    private String processorReference;
    private String internalReceiptReference;
    private PaymentStatus status;
    private String failureReason;
    private String idempotencyKey;
}
