package com.casms.payment.repository;

import com.casms.payment.entity.Payment;
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
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.paymentId = :paymentId")
    Optional<Payment> findByIdForUpdate(@Param("paymentId") UUID paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p left join p.booking b left join p.installment i left join i.booking ib where b.bookingId = :bookingId or ib.bookingId = :bookingId")
    List<Payment> findForBooking(@Param("bookingId") UUID bookingId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    boolean existsByProcessorReferenceAndPaymentIdNot(String processorReference, UUID paymentId);

    boolean existsByInternalReceiptReferenceAndPaymentIdNot(String internalReceiptReference, UUID paymentId);

    List<Payment> findByBooking_BookingId(UUID bookingId);
}
