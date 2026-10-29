package com.casms.activity.repository;

import com.casms.activity.entity.ShoreExcursion;
import com.casms.activity.enums.ShoreExcursionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShoreExcursionRepository
        extends JpaRepository<ShoreExcursion, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ShoreExcursion e where e.shoreExcursionId = :id")
    Optional<ShoreExcursion> lockById(@Param("id") UUID id);

    boolean existsBySupplier_SupplierId(UUID supplierId);
    boolean existsByExcursionCode(String excursionCode);
    Optional<ShoreExcursion> findByExcursionCode(String excursionCode);

    boolean existsByExcursionCodeIgnoreCaseAndCruiseStop_CruiseStopId(
            String excursionCode,
            UUID cruiseStopId
    );

    boolean existsByExcursionCodeIgnoreCaseAndCruiseStop_CruiseStopIdAndShoreExcursionIdNot(
            String excursionCode,
            UUID cruiseStopId,
            UUID shoreExcursionId
    );

    List<ShoreExcursion> findByCruiseStop_CruiseStopId(UUID cruiseStopId);

    List<ShoreExcursion> findByPort_PortId(UUID portId);

    List<ShoreExcursion> findBySupplier_SupplierId(UUID supplierId);

    List<ShoreExcursion> findByStatus(ShoreExcursionStatus status);

    @Query("SELECT COUNT(se) FROM ShoreExcursion se WHERE se.cruiseStop.itinerary.cruise.cruiseId = :cruiseId")
    long countByCruiseId(@Param("cruiseId") UUID cruiseId);

    List<ShoreExcursion> findByCruiseStop_Itinerary_Cruise_CruiseId(UUID cruiseId);

    @Query("""
        SELECT COUNT(se)
        FROM ShoreExcursion se
        WHERE se.cruiseStop.cruiseStopId = :cruiseStopId
          AND se.status IN :statuses
          AND se.startTime < :returnTime
          AND se.plannedReturnTime > :startTime
          AND (:excludeId IS NULL OR se.shoreExcursionId <> :excludeId)
        """)
    long countScheduleConflicts(
            @Param("cruiseStopId") UUID cruiseStopId,
            @Param("startTime") Instant startTime,
            @Param("returnTime") Instant returnTime,
            @Param("statuses") List<ShoreExcursionStatus> statuses,
            @Param("excludeId") UUID excludeId
    );

    @Modifying
    @Query("DELETE FROM ShoreExcursion se WHERE se.cruiseStop.itinerary.cruise.cruiseId = :cruiseId")
    void deleteByCruiseId(@Param("cruiseId") UUID cruiseId);
}
