package com.casms.ship.repository;

import com.casms.ship.entity.Ship;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.time.Instant;
import java.util.UUID;

@Repository
public interface ShipRepository extends JpaRepository<Ship, UUID> {
    @Query("""
            SELECT s FROM Ship s WHERE EXISTS (SELECT crew.cruiseStaffId FROM CruiseStaff crew
                WHERE crew.cruise.ship=s AND crew.user.userId=:user AND crew.status='ACTIVE'
                AND crew.roleInCruise='SHIP_MANAGER' AND crew.cruise.status NOT IN ('CANCELLED','COMPLETED'))
            """)
    Page<Ship> findManagedShips(@Param("user") UUID user, Pageable pageable);
    boolean existsByShipCode(String shipCode);

    boolean existsByShipCodeAndShipIdNot(String shipCode, UUID shipId);

    Optional<Ship> findByShipCode(String shipCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Ship s WHERE s.shipId = :shipId")
    Optional<Ship> findByIdForUpdate(@Param("shipId") UUID shipId);

    @Query("""
            SELECT s FROM Ship s
            WHERE s.status IN ('READY', 'IN_SERVICE') AND s.totalCapacity >= :passengers
              AND NOT EXISTS (SELECT c.cruiseId FROM Cruise c WHERE c.ship = s
                  AND c.status NOT IN ('CANCELLED', 'COMPLETED')
                  AND c.departureDatetime < :end AND c.returnDatetime > :start)
            ORDER BY s.totalCapacity, s.shipName, s.shipId
            """)
    List<Ship> findEligibleForCruise(@Param("passengers") int passengers, @Param("start") Instant start, @Param("end") Instant end);
}
