package com.casms.payment.repository;

import com.casms.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Payment p where p.paymentId = :paymentId")
    java.util.Optional<Payment> findByIdForUpdate(@org.springframework.data.repository.query.Param("paymentId") UUID paymentId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Payment p left join p.booking b left join p.installment i left join i.booking ib where b.bookingId = :bookingId or ib.bookingId = :bookingId")
    java.util.List<Payment> findForBooking(@org.springframework.data.repository.query.Param("bookingId") UUID bookingId);

    java.util.Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    java.util.List<Payment> findByBooking_BookingId(UUID bookingId);
}
