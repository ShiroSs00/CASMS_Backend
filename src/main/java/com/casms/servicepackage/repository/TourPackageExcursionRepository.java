package com.casms.servicepackage.repository;

import com.casms.servicepackage.entity.TourPackageExcursion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

@Repository
public interface TourPackageExcursionRepository extends JpaRepository<TourPackageExcursion, TourPackageExcursion.TourPackageExcursionId> {
    List<TourPackageExcursion> findByTourPackage_TourPackageId(UUID id);
    long countByTourPackage_TourPackageId(UUID id);
}
