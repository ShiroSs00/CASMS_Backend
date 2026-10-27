package com.casms.trip.repository;

import com.casms.trip.entity.TripScheduleVersion;
import com.casms.trip.enums.TripScheduleVersionStatus;
import com.casms.trip.enums.TripStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query("SELECT v FROM TripScheduleVersion v WHERE v.status = :status AND v.trip.status != :excludeTripStatus")
    Page<TripScheduleVersion> findReviewQueue(
            @Param("status") TripScheduleVersionStatus status,
            @Param("excludeTripStatus") TripStatus excludeTripStatus,
            Pageable pageable);

    @Query("SELECT v FROM TripScheduleVersion v " +
            "WHERE v.status = 'PUBLISHED' " +
            "AND v.trip.status != 'ARCHIVED' " +
            "AND (:tripId IS NULL OR v.trip.tripId = :tripId) " +
            "AND (:keyword = '' OR LOWER(v.trip.name) LIKE :keyword OR LOWER(v.trip.tripCode) LIKE :keyword) " +
            "ORDER BY v.trip.tripCode ASC, v.versionNumber DESC")
    Page<TripScheduleVersion> findEligibleSourceVersions(
            @Param("tripId") UUID tripId,
            @Param("keyword") String keyword,
            Pageable pageable);
}

