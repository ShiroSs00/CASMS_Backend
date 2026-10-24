package com.casms.trip.repository;

import com.casms.trip.entity.TripPlanEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TripPlanEntryRepository extends JpaRepository<TripPlanEntry, UUID> {

    List<TripPlanEntry> findByVersion_VersionIdOrderByStartDayNumberAscPlannedStartTimeAsc(UUID versionId);

    List<TripPlanEntry> findByStop_StopId(UUID stopId);

    void deleteByVersion_VersionId(UUID versionId);
}
