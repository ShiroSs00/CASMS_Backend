package com.casms.ship.repository;

import com.casms.ship.entity.ShipRegulation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.io.Serializable;

@Repository
public interface ShipRegulationRepository extends JpaRepository<ShipRegulation, Serializable> {
}
