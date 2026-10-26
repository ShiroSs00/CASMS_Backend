package com.casms.ship.repository;

import com.casms.ship.entity.Ship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShipRepository extends JpaRepository<Ship, UUID> {
    boolean existsByShipCode(String shipCode);
    boolean existsByShipCodeAndShipIdNot(String shipCode, UUID shipId);
    Optional<Ship> findByShipCode(String shipCode);
    Optional<Ship> findByManifestModelId(String manifestModelId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Ship s where s.manifestModelId = :manifestModelId")
    Optional<Ship> lockByManifestModelId(@Param("manifestModelId") String manifestModelId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Ship s WHERE s.shipId = :shipId")
    Optional<Ship> findByIdForUpdate(@Param("shipId") UUID shipId);
}

