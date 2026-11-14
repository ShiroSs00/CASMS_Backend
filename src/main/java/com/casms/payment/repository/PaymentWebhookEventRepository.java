package com.casms.payment.repository;

import com.casms.payment.entity.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, UUID> {
    Optional<PaymentWebhookEvent> findByExternalEventId(String externalEventId);

    boolean existsByExternalEventId(String externalEventId);

    List<PaymentWebhookEvent> findByPayment_PaymentIdOrderByReceivedAtDesc(UUID paymentId);
}

