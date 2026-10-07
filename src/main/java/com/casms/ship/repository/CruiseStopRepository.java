package com.casms.ship.repository;

import com.casms.ship.entity.CruiseStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CruiseStopRepository extends JpaRepository<CruiseStop, UUID> {
}
