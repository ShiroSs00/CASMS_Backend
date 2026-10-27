package com.casms.ship.repository;

import com.casms.ship.entity.ItineraryDay;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ItineraryDayRepository extends JpaRepository<ItineraryDay, UUID> {

    List<ItineraryDay> findByItineraryItineraryIdOrderByDayNumberAsc(UUID itineraryId);

    @EntityGraph(attributePaths = "itinerary")
    List<ItineraryDay> findByItineraryItineraryIdInOrderByItineraryItineraryIdAscDayNumberAsc(
            Collection<UUID> itineraryIds);

    @Query("SELECT id FROM ItineraryDay id WHERE id.itinerary.itineraryId = :itineraryId ORDER BY id.dayNumber ASC")
    List<ItineraryDay> findByItineraryOrderByDayNumber(@Param("itineraryId") UUID itineraryId);

    boolean existsByItineraryItineraryIdAndDayNumber(UUID itineraryId, Integer dayNumber);

    boolean existsByItineraryItineraryIdAndDate(UUID itineraryId, LocalDate date);

    Optional<ItineraryDay> findByItineraryItineraryIdAndDayNumber(UUID itineraryId, Integer dayNumber);

    Optional<ItineraryDay> findByItineraryItineraryIdAndDate(UUID itineraryId, LocalDate date);

    @Query("SELECT MAX(id.dayNumber) FROM ItineraryDay id WHERE id.itinerary.itineraryId = :itineraryId")
    Integer findMaxDayNumberByItineraryId(@Param("itineraryId") UUID itineraryId);
}
