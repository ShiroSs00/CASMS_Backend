package com.casms.payment.repository;

import com.casms.payment.entity.Charge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChargeRepository extends JpaRepository<Charge, UUID> {
    List<Charge> findByTransaction_OnboardAccount_OnboardAccountIdOrderByChargedAtAsc(
            UUID onboardAccountId);

    List<Charge> findByPassenger_PassengerIdOrderByChargedAtDesc(UUID passengerId);
}
