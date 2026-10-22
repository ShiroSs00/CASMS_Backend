package com.casms.booking.repository;

import com.casms.booking.entity.BookingPassenger;
import com.casms.booking.enums.BookingPassengerParticipationStatus;
import com.casms.booking.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BookingPassengerRepository extends JpaRepository<BookingPassenger, UUID> {

    @Query("""
        SELECT bp FROM BookingPassenger bp
        JOIN FETCH bp.booking b
        JOIN FETCH b.cruise c
        WHERE bp.passenger.user.userId = :userId
          AND b.bookingStatus IN :bookingStatuses
        ORDER BY c.departureDatetime ASC
        """)
    java.util.List<BookingPassenger> findPassengerTrips(
            @Param("userId") UUID userId,
            @Param("bookingStatuses") java.util.Collection<BookingStatus> bookingStatuses);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select bp from BookingPassenger bp where bp.bookingPassengerId = :id")
    java.util.Optional<BookingPassenger> lockById(@org.springframework.data.repository.query.Param("id") UUID id);

    @Query("SELECT COUNT(bp) FROM BookingPassenger bp WHERE bp.booking.cruise.cruiseId = :cruiseId " +
           "AND bp.participationStatus = 'Active' AND bp.booking.bookingStatus != 'Cancelled'")
    long countActivePassengersByCruiseId(@Param("cruiseId") UUID cruiseId);

    @Query("SELECT bp FROM BookingPassenger bp " +
           "JOIN FETCH bp.passenger p " +
           "JOIN FETCH bp.booking b " +
           "WHERE b.cruise.cruiseId = :cruiseId " +
           "AND bp.participationStatus = 'Active' " +
           "AND b.bookingStatus != 'Cancelled'")
    java.util.List<BookingPassenger> findActivePassengersByCruiseId(@Param("cruiseId") UUID cruiseId);

    java.util.List<BookingPassenger> findByBookingBookingId(UUID bookingId);
    java.util.List<BookingPassenger> findByPassenger_PassengerId(UUID passengerId);
    java.util.List<BookingPassenger> findByPassenger_User_UserId(UUID userId);
}
