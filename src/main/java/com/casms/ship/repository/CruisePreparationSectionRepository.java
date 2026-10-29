package com.casms.ship.repository;

import com.casms.ship.entity.CruisePreparationSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CruisePreparationSectionRepository extends JpaRepository<CruisePreparationSection, UUID> {

    List<CruisePreparationSection> findByCruiseCruiseIdOrderBySectionCodeAsc(UUID cruiseId);

    Optional<CruisePreparationSection> findByCruiseCruiseIdAndSectionCode(UUID cruiseId, String sectionCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM CruisePreparationSection s WHERE s.cruise.cruiseId = :cruiseId AND s.sectionCode = :sectionCode")
    Optional<CruisePreparationSection> findByCruiseCruiseIdAndSectionCodeForUpdate(
            @Param("cruiseId") UUID cruiseId,
            @Param("sectionCode") String sectionCode);

    boolean existsByCruiseCruiseId(UUID cruiseId);
}
