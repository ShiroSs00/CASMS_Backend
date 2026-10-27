package com.casms.servicepackage.repository;

import com.casms.servicepackage.entity.TourPackageService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

@Repository
public interface TourPackageServiceRepository extends JpaRepository<TourPackageService, TourPackageService.TourPackageServiceId> {
    List<TourPackageService> findByTourPackage_TourPackageId(UUID id);
    long countByTourPackage_TourPackageId(UUID id);
    boolean existsByService_ServiceId(UUID id);
}
