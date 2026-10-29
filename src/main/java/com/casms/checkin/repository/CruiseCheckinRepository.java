package com.casms.checkin.repository;

import com.casms.checkin.entity.CruiseCheckin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CruiseCheckinRepository extends JpaRepository<CruiseCheckin, UUID> {
    Optional<CruiseCheckin> findByBookingPassenger_BookingPassengerId(UUID bookingPassengerId);
    boolean existsByBookingPassenger_BookingPassengerId(UUID bookingPassengerId);

    @Query("SELECT c FROM CruiseCheckin c " +
           "WHERE c.bookingPassenger.booking.cruise.cruiseId = :cruiseId")
    List<CruiseCheckin> findByCruiseId(@Param("cruiseId") UUID cruiseId);

    @Query("SELECT COUNT(c) FROM CruiseCheckin c " +
           "WHERE c.bookingPassenger.booking.cruise.cruiseId = :cruiseId " +
           "AND (c.checkinStatus IN (com.casms.checkin.enums.CruiseCheckinStatus.Completed, com.casms.checkin.enums.CruiseCheckinStatus.BoardingReady) " +
           "     OR c.completedAt IS NOT NULL)")
    long countCompletedCheckinsByCruiseId(@Param("cruiseId") UUID cruiseId);

    @Query("SELECT COUNT(c) FROM CruiseCheckin c " +
           "WHERE c.bookingPassenger.booking.cruise.cruiseId = :cruiseId " +
           "AND (c.boardingReadyAt IS NOT NULL OR c.checkinStatus = com.casms.checkin.enums.CruiseCheckinStatus.BoardingReady)")
    long countBoardedPassengersByCruiseId(@Param("cruiseId") UUID cruiseId);
}
