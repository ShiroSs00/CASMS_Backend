package com.casms.ship.repository;

import com.casms.ship.entity.Itinerary;
import com.casms.ship.enums.ItineraryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ItineraryRepository extends JpaRepository<Itinerary, UUID> {
    Optional<Itinerary> findByCruiseCruiseId(UUID cruiseId);
    boolean existsByCruiseCruiseId(UUID cruiseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Itinerary i where i.itineraryId = :itineraryId")
    Optional<Itinerary> findByIdForUpdate(@Param("itineraryId") UUID itineraryId);

    @EntityGraph(attributePaths = "cruise")
    List<Itinerary> findByCruiseCruiseIdInAndStatusIn(
            Collection<UUID> cruiseIds,
            Collection<ItineraryStatus> statuses);

    Page<Itinerary> findByStatusIn(
            Collection<ItineraryStatus> statuses,
            Pageable pageable);
}
