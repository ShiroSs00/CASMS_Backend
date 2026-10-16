package com.casms.ship.repository;

import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.Ship;
import com.casms.ship.enums.CruiseCruiseType;
import com.casms.ship.enums.CruiseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CruiseRepository extends JpaRepository<Cruise, UUID>, JpaSpecificationExecutor<Cruise> {

    boolean existsByCruiseCode(String cruiseCode);

    boolean existsByShip(Ship ship);

    @Query("SELECT COUNT(c) > 0 FROM Cruise c WHERE c.ship.shipId = :shipId " +
           "AND c.status NOT IN ('CANCELLED', 'COMPLETED') " +
           "AND (c.departureDatetime <= :end AND c.returnDatetime >= :start)")
    boolean existsOverlappingCruises(@Param("shipId") UUID shipId,
                                     @Param("start") Instant start,
                                     @Param("end") Instant end);

    @Query("SELECT COUNT(c) > 0 FROM Cruise c WHERE c.ship.shipId = :shipId " +
           "AND c.cruiseId != :excludeCruiseId " +
           "AND c.status NOT IN ('CANCELLED', 'COMPLETED') " +
           "AND (c.departureDatetime <= :end AND c.returnDatetime >= :start)")
    boolean existsOverlappingCruisesExcluding(@Param("shipId") UUID shipId,
                                             @Param("excludeCruiseId") UUID excludeCruiseId,
                                             @Param("start") Instant start,
                                             @Param("end") Instant end);

    @Query("SELECT c FROM Cruise c WHERE c.ship.shipId = :shipId " +
           "AND c.status NOT IN ('CANCELLED') " +
           "AND (c.departureDatetime <= :end AND c.returnDatetime >= :start)")
    List<Cruise> findAffectedCruises(@Param("shipId") UUID shipId,
                                     @Param("start") Instant start,
                                     @Param("end") Instant end);

    @Query("SELECT c FROM Cruise c WHERE c.ship.shipId = :shipId " +
           "AND c.status = 'IN_PROGRESS' AND c.departureDatetime <= :now AND c.returnDatetime >= :now")
    Optional<Cruise> findCurrentActiveCruise(@Param("shipId") UUID shipId, @Param("now") Instant now);

    @Query("SELECT c FROM Cruise c WHERE " +
           "(:status IS NULL OR c.status = :status) AND " +
           "(:shipId IS NULL OR (c.ship IS NOT NULL AND c.ship.shipId = :shipId)) AND " +
           "(:cruiseType IS NULL OR c.cruiseType = :cruiseType)")
    Page<Cruise> findAllWithFilters(@Param("status") CruiseStatus status,
                                   @Param("shipId") UUID shipId,
                                   @Param("cruiseType") CruiseCruiseType cruiseType,
                                   Pageable pageable);
}
