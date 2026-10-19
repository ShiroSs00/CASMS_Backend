package com.casms.servicepackage.repository;

import com.casms.servicepackage.entity.TourPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TourPackageRepository extends JpaRepository<TourPackage, UUID> {
    boolean existsByPackageCodeAndTourPackageIdNot(String packageCode, UUID id);
}
