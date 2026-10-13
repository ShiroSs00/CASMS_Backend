package com.casms.ship.repository;

import com.casms.ship.entity.ItineraryChangeHistory;
import com.casms.ship.enums.ItineraryChangeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ItineraryChangeHistoryRepository
        extends JpaRepository<ItineraryChangeHistory, UUID> {

    @Query("""
        SELECT h
        FROM ItineraryChangeHistory h
        WHERE (:itineraryId IS NULL
               OR h.itinerary.itineraryId = :itineraryId)
          AND (:cruiseStopId IS NULL
               OR h.cruiseStop.cruiseStopId = :cruiseStopId)
          AND (:changeType IS NULL
               OR h.changeType = :changeType)
          AND (:changedByUserId IS NULL
               OR h.changedByUser.userId = :changedByUserId)
        ORDER BY h.changedAt DESC
        """)
    List<ItineraryChangeHistory> search(
            @Param("itineraryId") UUID itineraryId,
            @Param("cruiseStopId") UUID cruiseStopId,
            @Param("changeType") ItineraryChangeType changeType,
            @Param("changedByUserId") UUID changedByUserId
    );

    List<ItineraryChangeHistory>
    findByItinerary_ItineraryIdOrderByChangedAtDesc(
            UUID itineraryId
    );
}