package com.casms.payment.entity;

import com.casms.identity.entity.Passenger;
import com.casms.identity.entity.User;
import com.casms.payment.enums.TransactionStatus;
import com.casms.payment.enums.TransactionSyncStatus;
import com.casms.payment.enums.TransactionType;

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
@Table(name = "\"OnboardTransaction\"")
public class OnboardTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "transaction_id")
    private UUID transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "onboard_account_id", nullable = false)
    private OnboardAccount onboardAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passenger_id")
    private Passenger passenger;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", nullable = false, columnDefinition = "char(3)")
    private String currencyCode;

    @Column(name = "transaction_time", nullable = false)
    private Instant transactionTime;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransactionStatus status;

    @Column(name = "source_type", length = 255)
    private String sourceType;

    @Column(name = "source_reference_id")
    private UUID sourceReferenceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_transaction_id")
    private OnboardTransaction originalTransaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status")
    private TransactionSyncStatus syncStatus;

    @Column(name = "synced_at")
    private Instant syncedAt;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "pos_terminal_id")
    private UUID posTerminalId;

    @Column(name = "device_reference", length = 100)
    private String deviceReference;

    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "service_reference", length = 255)
    private String serviceReference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
