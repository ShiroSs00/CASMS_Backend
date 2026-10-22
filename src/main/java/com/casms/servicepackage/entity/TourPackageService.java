package com.casms.servicepackage.entity;

import com.casms.servicepackage.enums.BenefitType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"TourPackageService\"")
@IdClass(TourPackageService.TourPackageServiceId.class)
public class TourPackageService {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tour_package_id", nullable = false)
    private TourPackage tourPackage;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;

    @Enumerated(EnumType.STRING)
    @Column(name = "benefit_type", nullable = false)
    private BenefitType benefitType;

    @Column(name = "quantity_limit")
    private Integer quantityLimit;

    @Column(name = "unit_code", length = 30)
    private String unitCode;

    @Column(name = "discount_percent", precision = 5, scale = 2)
    private BigDecimal discountPercent;

    @Column(name = "owner_scope", nullable = false, length = 20)
    private String ownerScope = "PASSENGER";

    @Getter
    @Setter
    @EqualsAndHashCode
    public static class TourPackageServiceId implements Serializable {
        private UUID tourPackage;
        private UUID service;
    }
}
