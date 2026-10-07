package com.casms.ship.repository;

import com.casms.ship.entity.ItineraryChangeHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ItineraryChangeHistoryRepository extends JpaRepository<ItineraryChangeHistory, UUID> {
}
