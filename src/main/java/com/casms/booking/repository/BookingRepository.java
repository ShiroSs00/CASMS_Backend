package com.casms.booking.repository;

import com.casms.booking.entity.Booking;
import com.casms.booking.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {
    boolean existsByCruiseCruiseId(UUID cruiseId);
    boolean existsByBookingReference(String bookingReference);
    Optional<Booking> findByBookingReference(String bookingReference);
    Page<Booking> findByBookedByUserUserId(UUID userId, Pageable pageable);

    @Query("SELECT b FROM Booking b WHERE (:cruiseId IS NULL OR b.cruise.cruiseId = :cruiseId) " +
           "AND (:status IS NULL OR b.bookingStatus = :status) " +
           "AND (:userId IS NULL OR b.bookedByUser.userId = :userId)")
    Page<Booking> searchBookings(@Param("cruiseId") UUID cruiseId,
                                 @Param("status") BookingStatus status,
                                 @Param("userId") UUID userId,
                                 Pageable pageable);
}

