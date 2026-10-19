package com.casms.ship.repository;

import com.casms.ship.entity.Ship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ShipRepository extends JpaRepository<Ship, UUID> {
    boolean existsByShipCode(String shipCode);
    boolean existsByShipCodeAndShipIdNot(String shipCode, UUID shipId);
}
