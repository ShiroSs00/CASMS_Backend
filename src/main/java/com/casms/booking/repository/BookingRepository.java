package com.casms.booking.repository;

import com.casms.booking.entity.Booking;
import com.casms.booking.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID>, JpaSpecificationExecutor<Booking> {
    boolean existsByCruiseCruiseId(UUID cruiseId);
    boolean existsByBookingReference(String bookingReference);
    Optional<Booking> findByBookingReference(String bookingReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.bookingId = :bookingId")
    Optional<Booking> findByIdForUpdate(@Param("bookingId") UUID bookingId);
    Page<Booking> findByBookedByUserUserId(UUID userId, Pageable pageable);

    default Page<Booking> searchBookings(UUID cruiseId,
                                         BookingStatus status,
                                         UUID userId,
                                         Pageable pageable) {
        return findAll(
                BookingSpecification.filter(cruiseId, status, userId),
                pageable != null ? pageable : Pageable.unpaged()
        );
    }
}

