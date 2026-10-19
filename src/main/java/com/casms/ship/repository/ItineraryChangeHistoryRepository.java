package com.casms.ship.repository;

import com.casms.ship.entity.ItineraryChangeHistory;
import com.casms.ship.enums.ItineraryChangeType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ItineraryChangeHistoryRepository
        extends JpaRepository<ItineraryChangeHistory, UUID>, JpaSpecificationExecutor<ItineraryChangeHistory> {

    default List<ItineraryChangeHistory> search(
            UUID itineraryId,
            UUID cruiseStopId,
            ItineraryChangeType changeType,
            UUID changedByUserId
    ) {
        return findAll(
                ItineraryChangeHistorySpecification.filter(
                        itineraryId,
                        cruiseStopId,
                        changeType,
                        changedByUserId
                ),
                Sort.by(Sort.Direction.DESC, "changedAt")
        );
    }

    List<ItineraryChangeHistory>
    findByItinerary_ItineraryIdOrderByChangedAtDesc(
            UUID itineraryId
    );
}