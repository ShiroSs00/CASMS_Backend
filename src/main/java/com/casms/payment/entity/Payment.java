package com.casms.payment.entity;

import com.casms.booking.entity.Booking;
import com.casms.payment.enums.PaymentMethod;
import com.casms.payment.enums.PaymentOperationType;
import com.casms.payment.enums.PaymentStatus;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"Payment\"")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "payment_id")
    private UUID paymentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "onboard_account_id")
    private OnboardAccount onboardAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "installment_id")
    private Installment installment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "final_settlement_id")
    private FinalSettlement finalSettlement;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false)
    private PaymentOperationType operationType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "retry_of_payment_id")
    private Payment retryOfPayment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_operation_id")
    private Payment parentOperation;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "requested_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal requestedAmount;

    @Column(name = "confirmed_amount", precision = 19, scale = 4)
    private BigDecimal confirmedAmount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, columnDefinition = "char(3)")
    private String currency;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "processor_reference", length = 255)
    private String processorReference;

    @Column(name = "internal_receipt_reference", length = 255)
    private String internalReceiptReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registered_payment_method_id")
    private RegisteredPaymentMethod registeredPaymentMethod;

    @Column(name = "provider", length = 50)
    private String provider;

    @Column(name = "payment_token", length = 255)
    private String paymentToken;

    @Column(name = "authorized_amount", precision = 19, scale = 4)
    private BigDecimal authorizedAmount;

    @Column(name = "captured_amount", precision = 19, scale = 4)
    private BigDecimal capturedAmount;

    @Column(name = "refunded_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(name = "original_amount", precision = 19, scale = 4)
    private BigDecimal originalAmount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "original_currency", columnDefinition = "char(3)")
    private String originalCurrency;

    @Column(name = "converted_amount", precision = 19, scale = 4)
    private BigDecimal convertedAmount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "settlement_currency", columnDefinition = "char(3)")
    private String settlementCurrency;

    @Column(name = "exchange_rate_applied", precision = 19, scale = 6)
    private BigDecimal exchangeRateApplied;

    @Column(name = "exchange_rate_source", length = 100)
    private String exchangeRateSource;

    @Column(name = "exchange_rate_timestamp")
    private Instant exchangeRateTimestamp;

    @Column(name = "authorized_at")
    private Instant authorizedAt;

    @Column(name = "captured_at")
    private Instant capturedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PrePersist
    protected void onCreate() {
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
