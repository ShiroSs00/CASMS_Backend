package com.casms.payment.entity;

import com.casms.payment.enums.FinalSettlementStatus;

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
@Table(name = "\"FinalSettlement\"")
public class FinalSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "final_settlement_id")
    private UUID finalSettlementId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "onboard_account_id", nullable = false)
    private OnboardAccount onboardAccount;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "final_invoice_id", unique = true)
    private Invoice finalInvoice;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "base_currency", nullable = false, columnDefinition = "char(3)")
    private String baseCurrency = "USD";

    @Column(name = "source_cutoff_at", nullable = false)
    private Instant sourceCutoffAt;

    @Column(name = "total_charges_snapshot", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalChargesSnapshot = BigDecimal.ZERO;

    @Column(name = "total_refunds_snapshot", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalRefundsSnapshot = BigDecimal.ZERO;

    @Column(name = "total_payments_snapshot", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalPaymentsSnapshot = BigDecimal.ZERO;

    @Column(name = "paid_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "outstanding_balance_snapshot", nullable = false, precision = 19, scale = 4)
    private BigDecimal outstandingBalanceSnapshot = BigDecimal.ZERO;

    @Column(name = "amount_due_snapshot", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountDueSnapshot;

    @Column(name = "refund_due_snapshot", nullable = false, precision = 19, scale = 4)
    private BigDecimal refundDueSnapshot;

    @Column(name = "statement_confirmed_at")
    private Instant statementConfirmedAt;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private FinalSettlementStatus status;

    @Column(name = "handled_by_user_id")
    private UUID handledByUserId;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

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
