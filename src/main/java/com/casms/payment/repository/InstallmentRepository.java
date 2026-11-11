package com.casms.payment.repository;

import com.casms.payment.entity.Installment;
import com.casms.payment.enums.InstallmentStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface InstallmentRepository extends JpaRepository<Installment, UUID> {
    List<Installment> findByBookingBookingIdOrderBySequenceNoAsc(UUID bookingId);

    List<Installment> findByStatusAndDueAtBetween(InstallmentStatus status, Instant start, Instant end);

    List<Installment> findByStatusAndDueAtBefore(InstallmentStatus status, Instant cutoff);
}
