package com.casms.trip.repository;

import com.casms.trip.entity.TripPlanDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TripPlanDayRepository extends JpaRepository<TripPlanDay, UUID> {

    List<TripPlanDay> findByVersion_VersionIdOrderByDayNumberAsc(UUID versionId);

    Optional<TripPlanDay> findByVersion_VersionIdAndDayNumber(UUID versionId, Integer dayNumber);
}
