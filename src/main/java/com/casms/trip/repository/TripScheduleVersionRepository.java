package com.casms.trip.repository;

import com.casms.trip.entity.TripScheduleVersion;
import com.casms.trip.enums.TripScheduleVersionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TripScheduleVersionRepository extends JpaRepository<TripScheduleVersion, UUID> {

    Optional<TripScheduleVersion> findByTrip_TripIdAndVersionNumber(UUID tripId, Integer versionNumber);

    List<TripScheduleVersion> findByTrip_TripIdOrderByVersionNumberAsc(UUID tripId);

    Optional<TripScheduleVersion> findTopByTrip_TripIdOrderByVersionNumberDesc(UUID tripId);

    boolean existsByTrip_TripIdAndStatusIn(UUID tripId, Collection<TripScheduleVersionStatus> statuses);

    boolean existsByTrip_TripIdAndStatus(UUID tripId, TripScheduleVersionStatus status);

    Optional<TripScheduleVersion> findByTrip_TripIdAndStatus(UUID tripId, TripScheduleVersionStatus status);
}
