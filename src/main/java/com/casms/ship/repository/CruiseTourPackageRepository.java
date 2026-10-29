package com.casms.ship.repository;

import com.casms.ship.entity.CruiseTourPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CruiseTourPackageRepository extends JpaRepository<CruiseTourPackage, UUID> {

    List<CruiseTourPackage> findByCruise_CruiseId(UUID cruiseId);

    List<CruiseTourPackage> findByCruise_CruiseIdAndActiveTrue(UUID cruiseId);

    long countByCruise_CruiseIdAndActiveTrue(UUID cruiseId);

    Optional<CruiseTourPackage> findByCruise_CruiseIdAndTourPackage_TourPackageId(UUID cruiseId, UUID tourPackageId);

    boolean existsByCruise_CruiseIdAndTourPackage_TourPackageId(UUID cruiseId, UUID tourPackageId);

    boolean existsByCruise_CruiseIdAndTourPackage_TourPackageIdAndActiveTrue(UUID cruiseId, UUID tourPackageId);

    void deleteByCruise_CruiseIdAndTourPackage_TourPackageId(UUID cruiseId, UUID tourPackageId);

    void deleteByCruise_CruiseId(UUID cruiseId);
}
