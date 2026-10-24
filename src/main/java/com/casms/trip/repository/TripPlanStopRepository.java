package com.casms.trip.repository;

import com.casms.trip.entity.TripPlanStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TripPlanStopRepository extends JpaRepository<TripPlanStop, UUID> {

    List<TripPlanStop> findByVersion_VersionIdOrderByStopOrderAsc(UUID versionId);

    Optional<TripPlanStop> findByVersion_VersionIdAndClientKey(UUID versionId, String clientKey);

    void deleteByVersion_VersionId(UUID versionId);
}
