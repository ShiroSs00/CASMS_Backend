package com.casms.activity.repository;

import com.casms.activity.entity.ShoreExcursion;
import com.casms.activity.enums.ShoreExcursionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ShoreExcursionRepository
        extends JpaRepository<ShoreExcursion, UUID> {

    boolean existsBySupplier_SupplierId(UUID supplierId);

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

    List<ShoreExcursion> findBySupplier_SupplierId(UUID supplierId);

    List<ShoreExcursion> findByStatus(ShoreExcursionStatus status);

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
}