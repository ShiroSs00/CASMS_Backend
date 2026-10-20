package com.casms.booking.repository;

import com.casms.booking.entity.BookingPassenger;
import com.casms.booking.enums.BookingPassengerParticipationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BookingPassengerRepository extends JpaRepository<BookingPassenger, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select bp from BookingPassenger bp where bp.bookingPassengerId = :id")
    java.util.Optional<BookingPassenger> lockById(@org.springframework.data.repository.query.Param("id") UUID id);

    @Query("SELECT COUNT(bp) FROM BookingPassenger bp WHERE bp.booking.cruise.cruiseId = :cruiseId " +
           "AND bp.participationStatus = 'Active' AND bp.booking.bookingStatus != 'Cancelled'")
    long countActivePassengersByCruiseId(@Param("cruiseId") UUID cruiseId);

    java.util.List<BookingPassenger> findByBookingBookingId(UUID bookingId);
}
