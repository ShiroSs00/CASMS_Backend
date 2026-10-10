package com.casms.servicepackage.entity;

import com.casms.activity.entity.OnboardActivity;
import com.casms.servicepackage.enums.BenefitType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "\"TourPackageActivity\"")
@IdClass(TourPackageActivity.TourPackageActivityId.class)
public class TourPackageActivity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tour_package_id", nullable = false)
    private TourPackage tourPackage;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "onboard_activity_id", nullable = false)
    private OnboardActivity onboardActivity;

    @Enumerated(EnumType.STRING)
    @Column(name = "benefit_type", nullable = false)
    private BenefitType benefitType;

    @Getter
    @Setter
    public static class TourPackageActivityId implements Serializable {
        private UUID tourPackage;
        private UUID onboardActivity;
    }
}
