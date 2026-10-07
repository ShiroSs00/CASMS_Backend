package com.casms.payment.entity;

import com.casms.identity.entity.Passenger;
import com.casms.identity.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"ChargeDispute\"")
public class ChargeDispute {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "charge_dispute_id")
    private UUID chargeDisputeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charge_id", nullable = false)
    private Charge charge;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passenger_id", nullable = false)
    private Passenger passenger;

    @Column(name = "disputed_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal disputedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false)
    private ChargeDisputeReasonCode reasonCode;

    @Column(name = "description", nullable = false, length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ChargeDisputeStatus status;

    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_user_id")
    private User assignedToUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_type")
    private ChargeDisputeResolutionType resolutionType;

    @Column(name = "resolution_note", length = 1000)
    private String resolutionNote;

    @Column(name = "approved_amount", precision = 19, scale = 4)
    private BigDecimal approvedAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolution_transaction_id")
    private OnboardTransaction resolutionTransaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_user_id")
    private User resolvedByUser;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
