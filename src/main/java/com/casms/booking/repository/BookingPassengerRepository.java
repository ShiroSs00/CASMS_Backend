package com.casms.booking.repository;

import com.casms.booking.entity.BookingPassenger;
import com.casms.booking.enums.BookingPassengerParticipationStatus;
import com.casms.booking.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
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
    List<BookingPassenger> findPassengerTrips(
            @Param("userId") UUID userId,
            @Param("bookingStatuses") Collection<BookingStatus> bookingStatuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select bp from BookingPassenger bp where bp.bookingPassengerId = :id")
    Optional<BookingPassenger> lockById(@Param("id") UUID id);

    @Query("SELECT COUNT(bp) FROM BookingPassenger bp WHERE bp.booking.cruise.cruiseId = :cruiseId " +
           "AND bp.participationStatus = com.casms.booking.enums.BookingPassengerParticipationStatus.Active " +
           "AND bp.booking.bookingStatus != com.casms.booking.enums.BookingStatus.Cancelled")
    long countActivePassengersByCruiseId(@Param("cruiseId") UUID cruiseId);

    @Query("SELECT COUNT(bp) FROM BookingPassenger bp WHERE bp.booking.cruise.cruiseId = :cruiseId " +
           "AND bp.participationStatus = com.casms.booking.enums.BookingPassengerParticipationStatus.Active " +
           "AND bp.booking.bookingStatus = com.casms.booking.enums.BookingStatus.Draft")
    long countHeldPassengersByCruiseId(@Param("cruiseId") UUID cruiseId);

    @Query("SELECT COUNT(bp) FROM BookingPassenger bp WHERE bp.booking.cruise.cruiseId = :cruiseId " +
           "AND bp.participationStatus = com.casms.booking.enums.BookingPassengerParticipationStatus.Active " +
           "AND bp.booking.bookingStatus = com.casms.booking.enums.BookingStatus.Confirmed")
    long countConfirmedPassengersByCruiseId(@Param("cruiseId") UUID cruiseId);

    @Query("SELECT bp FROM BookingPassenger bp " +
           "JOIN FETCH bp.passenger p " +
           "JOIN FETCH bp.booking b " +
           "WHERE b.cruise.cruiseId = :cruiseId " +
           "AND bp.participationStatus = com.casms.booking.enums.BookingPassengerParticipationStatus.Active " +
           "AND b.bookingStatus != com.casms.booking.enums.BookingStatus.Cancelled")
    List<BookingPassenger> findActivePassengersByCruiseId(@Param("cruiseId") UUID cruiseId);

    List<BookingPassenger> findByBookingBookingId(UUID bookingId);
    List<BookingPassenger> findByPassenger_PassengerId(UUID passengerId);
    List<BookingPassenger> findByPassenger_User_UserId(UUID userId);
}
