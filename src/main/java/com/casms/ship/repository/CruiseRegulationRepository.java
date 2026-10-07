package com.casms.ship.repository;

import com.casms.ship.entity.CruiseRegulation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.io.Serializable;

@Repository
public interface CruiseRegulationRepository extends JpaRepository<CruiseRegulation, Serializable> {
}
