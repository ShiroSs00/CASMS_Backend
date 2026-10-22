package com.casms.ship.repository;

import com.casms.ship.entity.CruiseStop;
import com.casms.ship.enums.CruiseStopStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CruiseStopRepository extends JpaRepository<CruiseStop, UUID> {

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {
            "itinerary", "arrivalItineraryDay", "departureItineraryDay", "port", "destination"})
    List<CruiseStop> findByItineraryItineraryIdInOrderByItineraryItineraryIdAscStopOrderAsc(
            java.util.Collection<UUID> itineraryIds);

    List<CruiseStop> findByItineraryItineraryIdOrderByStopOrderAsc(UUID itineraryId);

    boolean existsByItineraryItineraryIdAndStopOrder(UUID itineraryId, Integer stopOrder);

    Optional<CruiseStop> findByItineraryItineraryIdAndStopOrder(UUID itineraryId, Integer stopOrder);

    @Query("SELECT cs FROM CruiseStop cs WHERE cs.itinerary.itineraryId = :itineraryId AND cs.port.portId = :portId AND cs.stopOrder != :excludeStopOrder")
    boolean existsByItineraryAndPortExcludingStopOrder(@Param("itineraryId") UUID itineraryId, 
                                                        @Param("portId") UUID portId, 
                                                        @Param("excludeStopOrder") Integer excludeStopOrder);

    List<CruiseStop> findByPortPortIdAndPlannedArrivalBetween(UUID portId, Instant start, Instant end);

    @Query("SELECT cs FROM CruiseStop cs WHERE cs.status IN ('PLANNED', 'DELAYED') AND cs.plannedArrival <= :now AND cs.plannedDeparture >= :now")
    List<CruiseStop> findActiveStopsAtTime(@Param("now") Instant now);

    long countByItineraryItineraryId(UUID itineraryId);
}
