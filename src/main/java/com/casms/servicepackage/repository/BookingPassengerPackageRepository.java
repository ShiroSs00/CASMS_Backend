package com.casms.servicepackage.repository;

import com.casms.servicepackage.entity.BookingPassengerPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BookingPassengerPackageRepository extends JpaRepository<BookingPassengerPackage, UUID> {
    List<BookingPassengerPackage> findByBookingPassengerBookingBookingId(UUID bookingId);
}
