package com.casms.trip.repository;

import com.casms.trip.entity.Trip;
import com.casms.trip.enums.TripStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TripRepository extends JpaRepository<Trip, UUID>, JpaSpecificationExecutor<Trip> {

    Optional<Trip> findByTripCode(String tripCode);

    boolean existsByTripCode(String tripCode);

    Page<Trip> findByStatus(TripStatus status, Pageable pageable);

    Page<Trip> findByStatusNot(TripStatus status, Pageable pageable);
}
