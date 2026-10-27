package com.casms.servicepackage.repository;

import com.casms.servicepackage.entity.BookingPassengerPackage;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingPassengerPackageRepository extends JpaRepository<BookingPassengerPackage, UUID> {
    boolean existsByTourPackage_TourPackageId(UUID packageId);
    List<BookingPassengerPackage> findByBookingPassenger_Booking_BookingId(UUID bookingId);
    List<BookingPassengerPackage> findByBookingPassenger_Passenger_User_UserId(UUID userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from BookingPassengerPackage p where p.bookingPassenger.bookingPassengerId = :id")
    Optional<BookingPassengerPackage> lockByBookingPassengerId(@Param("id") UUID id);
    List<BookingPassengerPackage> findByBookingPassengerBookingBookingId(UUID bookingId);
}
