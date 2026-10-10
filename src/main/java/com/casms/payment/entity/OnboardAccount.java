package com.casms.payment.entity;

import com.casms.payment.enums.OnboardAccountStatus;
import com.casms.ship.entity.Cruise;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"OnboardAccount\"")
public class OnboardAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "onboard_account_id")
    private UUID onboardAccountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cruise_id", nullable = false)
    private Cruise cruise;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OnboardAccountStatus status;

    @Column(name = "credit_limit", precision = 19, scale = 4)
    private BigDecimal creditLimit;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

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
