package com.casms.servicepackage.repository;

import com.casms.servicepackage.entity.PackageEntitlement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PackageEntitlementRepository extends JpaRepository<PackageEntitlement, UUID> {
    List<PackageEntitlement> findByTourPackage_TourPackageId(UUID packageId);
}
