package com.casms.ship.repository;

import com.casms.ship.entity.Cruise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CruiseRepository extends JpaRepository<Cruise, UUID> {
}
