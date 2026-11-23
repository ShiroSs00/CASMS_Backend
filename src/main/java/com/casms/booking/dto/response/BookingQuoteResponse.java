package com.casms.booking.dto.response;

import com.casms.booking.enums.PaymentPlan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingQuoteResponse {
    private UUID quoteId;
    private String quoteCode;
    private UUID cruiseId;
    private String cruiseCode;
    private Instant departureDatetime;
    private String policyVersion;
    private String currency;

    private List<QuoteLineItem> lineItems;

    private BigDecimal subtotalAmount;
    private BigDecimal portFeesAmount;
    private BigDecimal taxesAmount;
    private BigDecimal taxesAndFeesAmount;
    private BigDecimal totalAmount;

    private PaymentPlan paymentPlan;
    private BigDecimal depositAmount;
    private BigDecimal remainingAmount;
    private Instant finalPaymentDueAt;
    private List<QuoteInstallmentPreview> installments;
    private List<PaymentPlan> eligiblePaymentPlans;

    private String fingerprint;
    private Instant issuedAt;
    private Instant expiresAt;
    private String status;
    private String guestToken;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuoteInstallmentPreview {
        private Integer sequenceNo;
        private String installmentType;
        private String label;
        private Integer percentage;
        private BigDecimal amount;
        private Instant dueAt;
    }
}
