package com.casms.servicepackage.repository;

import com.casms.servicepackage.entity.TourPackageActivity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TourPackageActivityRepository
        extends JpaRepository<TourPackageActivity, TourPackageActivity.TourPackageActivityId> {
    List<TourPackageActivity> findByTourPackage_TourPackageId(UUID id);

    long countByTourPackage_TourPackageId(UUID id);

    boolean existsByOnboardActivity_OnboardActivityId(UUID id);

    boolean existsByTourPackage_TourPackageIdAndOnboardActivity_OnboardActivityId(
            UUID tourPackageId, UUID onboardActivityId);
}
