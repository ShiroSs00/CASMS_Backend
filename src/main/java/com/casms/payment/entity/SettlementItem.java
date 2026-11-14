package com.casms.payment.entity;

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
@Table(name = "\"SettlementItem\"")
public class SettlementItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "settlement_item_id")
    private UUID settlementItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "final_settlement_id", nullable = false)
    private FinalSettlement finalSettlement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_transaction_id")
    private OnboardTransaction sourceTransaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_payment_id")
    private Payment sourcePayment;

    @Column(name = "item_type", nullable = false, length = 40)
    private String itemType; // CHARGE, REFUND, PAYMENT, ADJUSTMENT, REVERSAL

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, columnDefinition = "char(3)")
    private String currency = "USD";

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

