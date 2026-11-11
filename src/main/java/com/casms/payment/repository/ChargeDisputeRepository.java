package com.casms.payment.repository;

import com.casms.payment.entity.ChargeDispute;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChargeDisputeRepository extends JpaRepository<ChargeDispute, UUID> {
    List<ChargeDispute> findByPassenger_PassengerIdOrderByReportedAtDesc(UUID passengerId);

    List<ChargeDispute> findByCharge_ChargeIdOrderByReportedAtDesc(UUID chargeId);

    List<ChargeDispute>
            findByCharge_Transaction_OnboardAccount_OnboardAccountIdOrderByReportedAtDesc(
                    UUID onboardAccountId);
}
