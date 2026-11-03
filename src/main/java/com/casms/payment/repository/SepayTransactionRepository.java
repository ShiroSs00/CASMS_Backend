package com.casms.payment.repository;

import com.casms.payment.entity.SepayTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SepayTransactionRepository extends JpaRepository<SepayTransaction, UUID> {

    Optional<SepayTransaction> findBySepayId(Long sepayId);

    Optional<SepayTransaction> findByReferenceCode(String referenceCode);

    boolean existsBySepayId(Long sepayId);

    List<SepayTransaction> findByBookingBookingId(UUID bookingId);

    List<SepayTransaction> findByPaymentPaymentId(UUID paymentId);
}

