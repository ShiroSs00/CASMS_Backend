package com.casms.servicepackage.repository;

import com.casms.servicepackage.entity.TourPackageActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.io.Serializable;

@Repository
public interface TourPackageActivityRepository extends JpaRepository<TourPackageActivity, TourPackageActivity.TourPackageActivityId> {
    java.util.List<TourPackageActivity> findByTourPackage_TourPackageId(java.util.UUID id);
    long countByTourPackage_TourPackageId(java.util.UUID id);
    boolean existsByOnboardActivity_OnboardActivityId(java.util.UUID id);
}
