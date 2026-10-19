package com.casms.servicepackage.repository;

import com.casms.servicepackage.entity.BookingPassengerPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BookingPassengerPackageRepository extends JpaRepository<BookingPassengerPackage, UUID> {
    boolean existsByTourPackage_TourPackageId(UUID packageId);
    java.util.List<BookingPassengerPackage> findByBookingPassenger_Booking_BookingId(UUID bookingId);
    java.util.List<BookingPassengerPackage> findByBookingPassenger_Passenger_User_UserId(UUID userId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from BookingPassengerPackage p where p.bookingPassenger.bookingPassengerId = :id")
    java.util.Optional<BookingPassengerPackage> lockByBookingPassengerId(@org.springframework.data.repository.query.Param("id") UUID id);
}
