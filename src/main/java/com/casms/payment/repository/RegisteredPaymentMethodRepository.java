package com.casms.payment.repository;

import com.casms.payment.entity.RegisteredPaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RegisteredPaymentMethodRepository extends JpaRepository<RegisteredPaymentMethod, UUID> {
    List<RegisteredPaymentMethod> findByPassenger_PassengerIdAndStatusOrderByCreatedAtDesc(
            UUID passengerId, String status);

    List<RegisteredPaymentMethod> findByPassenger_PassengerIdOrderByCreatedAtDesc(UUID passengerId);

    Optional<RegisteredPaymentMethod> findByPaymentToken(String paymentToken);

    Optional<RegisteredPaymentMethod> findFirstByPassenger_PassengerIdAndStatusAndIsDefaultTrue(
            UUID passengerId, String status);
}

