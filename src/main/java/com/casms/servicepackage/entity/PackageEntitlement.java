package com.casms.servicepackage.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter @Setter
@Entity
@Table(name = "\"PackageEntitlement\"")
public class PackageEntitlement {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "package_entitlement_id")
    private UUID packageEntitlementId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tour_package_id", nullable = false)
    private TourPackage tourPackage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_category_id", nullable = false)
    private ServiceCategory serviceCategory;

    @Column(name = "benefit_type", nullable = false, length = 20)
    private String benefitType;

    @Column(name = "quantity_limit")
    private Integer quantityLimit;

    @Column(name = "unit_code", length = 30)
    private String unitCode;

    @Column(name = "discount_percent", precision = 5, scale = 2)
    private BigDecimal discountPercent;

    @Column(name = "owner_scope", nullable = false, length = 20)
    private String ownerScope;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
