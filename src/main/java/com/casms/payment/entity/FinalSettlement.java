package com.casms.payment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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
    @JoinColumn(name = "final_invoice_id", nullable = false, unique = true)
    private Invoice finalInvoice;

    @Column(name = "source_cutoff_at", nullable = false)
    private Instant sourceCutoffAt;

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
}
