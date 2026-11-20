package com.casms.payment.dto.response;

import com.casms.booking.enums.BookingStatus;
import com.casms.payment.enums.SepayReconciliationAction;
import com.casms.payment.enums.SepayReconciliationStatus;
import com.casms.payment.enums.SepayTransactionStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SepayTransactionResponse {

    private UUID id;
    private Long sepayId;
    private String gateway;
    private Instant transactionDate;
    private String accountNumber;
    private String transferType;
    private BigDecimal transferAmount;
    private String currency;
    private BigDecimal expectedAmount;
    private BigDecimal varianceAmount;
    private String code;
    private String content;
    private String referenceCode;
    private String description;

    private UUID bookingId;
    private String bookingReference;
    private BookingStatus bookingStatus;
    private UUID paymentId;
    private String paymentReceiptReference;

    private SepayTransactionStatus status;
    private String exceptionCode;
    private String exceptionReason;
    private SepayReconciliationStatus reconciliationStatus;
    private SepayReconciliationAction reconciliationAction;
    private String reconciliationReason;
    private String reconciliationEvidenceRef;
    private UUID reconciledByUserId;
    private String reconciledByUsername;
    private Instant reconciledAt;

    private Instant matchedAt;
    private Instant postedAt;
    private Integer retryCount;
    private String lastError;
    private Instant lastAttemptAt;
    private Instant createdAt;

    private List<AuditEntry> auditHistory;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditEntry {
        private UUID id;
        private String action;
        private String fromStatus;
        private String toStatus;
        private UUID actorUserId;
        private String actorUsername;
        private String reason;
        private String evidenceReference;
        private UUID relatedBookingId;
        private UUID relatedPaymentId;
        private Instant createdAt;
    }
}
