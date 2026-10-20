package com.casms.booking.repository;

import com.casms.booking.entity.BookingContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingContractRepository extends JpaRepository<BookingContract, UUID> {
    Optional<BookingContract> findByBookingBookingId(UUID bookingId);
    boolean existsByContractReference(String contractReference);
}
