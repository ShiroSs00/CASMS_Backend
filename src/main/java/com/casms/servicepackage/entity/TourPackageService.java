package com.casms.servicepackage.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
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

    @Getter
    @Setter
    public static class TourPackageServiceId implements Serializable {
        private UUID tourPackage;
        private UUID service;
    }
}
