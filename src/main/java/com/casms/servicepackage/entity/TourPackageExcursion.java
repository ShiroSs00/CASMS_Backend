package com.casms.servicepackage.entity;

import com.casms.activity.entity.ShoreExcursion;
import com.casms.servicepackage.enums.BenefitType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"TourPackageExcursion\"")
@IdClass(TourPackageExcursion.TourPackageExcursionId.class)
public class TourPackageExcursion {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tour_package_id", nullable = false)
    private TourPackage tourPackage;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shore_excursion_id", nullable = false)
    private ShoreExcursion shoreExcursion;

    @Enumerated(EnumType.STRING)
    @Column(name = "benefit_type", nullable = false)
    private BenefitType benefitType;

    @Getter
    @Setter
    @EqualsAndHashCode
    public static class TourPackageExcursionId implements Serializable {
        private UUID tourPackage;
        private UUID shoreExcursion;
    }
}
