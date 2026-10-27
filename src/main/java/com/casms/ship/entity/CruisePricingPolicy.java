package com.casms.ship.entity;

import com.casms.ship.enums.CabinType;
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

    @Builder.Default
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Builder.Default
    @Column(name = "interior_base_fare", nullable = false, precision = 19, scale = 4)
    private BigDecimal interiorBaseFare = BigDecimal.valueOf(300);

    @Builder.Default
    @Column(name = "ocean_view_base_fare", nullable = false, precision = 19, scale = 4)
    private BigDecimal oceanViewBaseFare = BigDecimal.valueOf(450);

    @Builder.Default
    @Column(name = "balcony_base_fare", nullable = false, precision = 19, scale = 4)
    private BigDecimal balconyBaseFare = BigDecimal.valueOf(650);

    @Builder.Default
    @Column(name = "suite_base_fare", nullable = false, precision = 19, scale = 4)
    private BigDecimal suiteBaseFare = BigDecimal.valueOf(1100);

    @Builder.Default
    @Column(name = "port_fee_per_passenger", nullable = false, precision = 19, scale = 4)
    private BigDecimal portFeePerPassenger = BigDecimal.valueOf(50);

    @Builder.Default
    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal taxRate = BigDecimal.valueOf(0.0800);

    @Builder.Default
    @Column(name = "deposit_percentage", nullable = false, precision = 5, scale = 4)
    private BigDecimal depositPercentage = BigDecimal.valueOf(0.3000);

    @Builder.Default
    @Column(name = "final_payment_due_days", nullable = false)
    private int finalPaymentDueDays = 14;

    @Builder.Default
    @Column(name = "full_payment_cutoff_days", nullable = false)
    private int fullPaymentCutoffDays = 7;

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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public BigDecimal getBaseFareForCabinType(CabinType type) {
        if (type == null) return interiorBaseFare != null ? interiorBaseFare : BigDecimal.valueOf(300);
        return switch (type) {
            case INTERIOR -> interiorBaseFare != null ? interiorBaseFare : BigDecimal.valueOf(300);
            case OCEAN_VIEW -> oceanViewBaseFare != null ? oceanViewBaseFare : BigDecimal.valueOf(450);
            case BALCONY -> balconyBaseFare != null ? balconyBaseFare : BigDecimal.valueOf(650);
            case SUITE -> suiteBaseFare != null ? suiteBaseFare : BigDecimal.valueOf(1100);
        };
    }
}
