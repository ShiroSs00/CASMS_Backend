package com.casms.payment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"OfflineSyncLog\"")
public class OfflineSyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "sync_log_id")
    private UUID syncLogId;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "pos_terminal_id")
    private UUID posTerminalId;

    @Column(name = "device_reference", length = 100)
    private String deviceReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private OnboardTransaction transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "onboard_account_id")
    private OnboardAccount onboardAccount;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "sync_outcome", nullable = false, length = 30)
    private String syncOutcome; // SYNCED, DUPLICATE_IGNORED, CONFLICT, SYNC_FAILED

    @Column(name = "conflict_code", length = 100)
    private String conflictCode;

    @Column(name = "error_detail", columnDefinition = "text")
    private String errorDetail;

    @Column(name = "occurrence_time", nullable = false)
    private Instant occurrenceTime;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    @PrePersist
    protected void onCreate() {
        if (syncedAt == null) {
            syncedAt = Instant.now();
        }
    }
}

