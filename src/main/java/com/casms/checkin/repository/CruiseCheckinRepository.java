package com.casms.checkin.repository;

import com.casms.checkin.entity.CruiseCheckin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CruiseCheckinRepository extends JpaRepository<CruiseCheckin, UUID> {
    Optional<CruiseCheckin> findByBookingPassenger_BookingPassengerId(UUID bookingPassengerId);
    boolean existsByBookingPassenger_BookingPassengerId(UUID bookingPassengerId);
}
