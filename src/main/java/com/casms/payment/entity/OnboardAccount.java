package com.casms.payment.entity;

import com.casms.identity.entity.Passenger;
import com.casms.payment.enums.OnboardAccountStatus;
import com.casms.ship.entity.Cruise;

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
@Table(name = "\"OnboardAccount\"")
public class OnboardAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "onboard_account_id")
    private UUID onboardAccountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cruise_id", nullable = false)
    private Cruise cruise;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_passenger_id")
    private Passenger primaryPassenger;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "base_currency", nullable = false, columnDefinition = "char(3)")
    private String baseCurrency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OnboardAccountStatus status;

    @Column(name = "credit_limit", precision = 19, scale = 4)
    private BigDecimal creditLimit;

    @Column(name = "pre_authorized_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal preAuthorizedAmount = BigDecimal.ZERO;

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
