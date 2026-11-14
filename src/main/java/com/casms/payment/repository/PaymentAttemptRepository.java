package com.casms.payment.repository;

import com.casms.payment.entity.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {
    List<PaymentAttempt> findByPayment_PaymentIdOrderByAttemptNoAsc(UUID paymentId);

    int countByPayment_PaymentId(UUID paymentId);
}

