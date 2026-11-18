package com.casms.payment.entity;

import com.casms.identity.entity.User;
import com.casms.payment.enums.RefundStatus;

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
@Table(name = "\"Refund\"")
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "refund_id")
    private UUID refundId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_payment_id", nullable = false)
    private Payment originalPayment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "final_settlement_id")
    private FinalSettlement finalSettlement;

    @Column(name = "refund_reason", nullable = false, columnDefinition = "text")
    private String refundReason;

    @Column(name = "requested_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal requestedAmount;

    @Column(name = "approved_amount", precision = 19, scale = 4)
    private BigDecimal approvedAmount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, columnDefinition = "char(3)")
    private String currency;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private User approvedByUser;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @Column(name = "rejected_by_user_id")
    private UUID rejectedByUserId;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "adjustment_invoice_id", unique = true)
    private UUID adjustmentInvoiceId;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "processor_or_receipt_reference", length = 255)
    private String processorOrReceiptReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RefundStatus status;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;
}
