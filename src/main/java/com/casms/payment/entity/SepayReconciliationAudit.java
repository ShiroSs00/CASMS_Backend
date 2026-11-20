package com.casms.payment.entity;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"SepayReconciliationAudit\"")
public class SepayReconciliationAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sepay_transaction_id", nullable = false)
    private SepayTransaction sepayTransaction;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "from_status", length = 50)
    private String fromStatus;

    @Column(name = "to_status", length = 50)
    private String toStatus;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_username", length = 100)
    private String actorUsername;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(name = "evidence_reference", length = 255)
    private String evidenceReference;

    @Column(name = "related_booking_id")
    private UUID relatedBookingId;

    @Column(name = "related_payment_id")
    private UUID relatedPaymentId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
