package com.casms.ship.entity;

import com.casms.identity.entity.User;
import com.casms.ship.enums.CabinType;
import com.casms.ship.enums.PricingPolicyStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "\"CruisePricingPolicy\"",
       uniqueConstraints = @UniqueConstraint(columnNames = {"cruise_id"}))
public class CruisePricingPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "pricing_policy_id")
    private UUID pricingPolicyId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cruise_id")
    private Cruise cruise;

    @Builder.Default
    @Column(name = "policy_version", nullable = false, length = 50)
    private String policyVersion = "PRC-2026.10-V1";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private PricingPolicyStatus status = PricingPolicyStatus.DRAFT;

    @Builder.Default
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "interior_base_fare", precision = 19, scale = 4)
    private BigDecimal interiorBaseFare;

    @Column(name = "ocean_view_base_fare", precision = 19, scale = 4)
    private BigDecimal oceanViewBaseFare;

    @Column(name = "balcony_base_fare", precision = 19, scale = 4)
    private BigDecimal balconyBaseFare;

    @Column(name = "suite_base_fare", precision = 19, scale = 4)
    private BigDecimal suiteBaseFare;

    @Builder.Default
    @Column(name = "port_fee_per_passenger", precision = 19, scale = 4)
    private BigDecimal portFeePerPassenger = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "tax_rate", precision = 5, scale = 4)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "deposit_percentage", precision = 5, scale = 4)
    private BigDecimal depositPercentage = BigDecimal.valueOf(0.3000);

    @Builder.Default
    @Column(name = "final_payment_due_days", nullable = false)
    private int finalPaymentDueDays = 14;

    @Builder.Default
    @Column(name = "full_payment_cutoff_days", nullable = false)
    private int fullPaymentCutoffDays = 7;

    @Column(name = "cancellation_policy", columnDefinition = "TEXT")
    private String cancellationPolicy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
        if (status == null) status = PricingPolicyStatus.DRAFT;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Authoritative base fare lookup. Returns exact base fare configured.
     * Returns null if unpriced (strictly distinguishes 0 from NULL).
     */
    public BigDecimal getBaseFareForCabinType(CabinType type) {
        if (type == null) return null;
        return switch (type) {
            case INTERIOR -> interiorBaseFare;
            case OCEAN_VIEW -> oceanViewBaseFare;
            case BALCONY -> balconyBaseFare;
            case SUITE -> suiteBaseFare;
        };
    }
}
