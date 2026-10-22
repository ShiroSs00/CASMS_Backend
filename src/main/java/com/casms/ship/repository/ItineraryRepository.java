package com.casms.ship.repository;

import com.casms.ship.entity.Itinerary;
import com.casms.ship.enums.ItineraryStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ItineraryRepository extends JpaRepository<Itinerary, UUID> {
    Optional<Itinerary> findByCruiseCruiseId(UUID cruiseId);
    boolean existsByCruiseCruiseId(UUID cruiseId);

    @EntityGraph(attributePaths = "cruise")
    java.util.List<Itinerary> findByCruiseCruiseIdInAndStatusIn(
            java.util.Collection<UUID> cruiseIds,
            java.util.Collection<ItineraryStatus> statuses);
}
