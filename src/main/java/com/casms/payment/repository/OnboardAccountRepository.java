package com.casms.payment.repository;

import com.casms.payment.entity.OnboardAccount;

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
public interface OnboardAccountRepository extends JpaRepository<OnboardAccount, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from OnboardAccount a where a.onboardAccountId = :id")
    Optional<OnboardAccount> findByIdForUpdate(@Param("id") UUID id);

    Optional<OnboardAccount> findByCruise_CruiseIdAndPrimaryPassenger_PassengerId(
            UUID cruiseId, UUID passengerId);

    List<OnboardAccount> findByCruise_CruiseId(UUID cruiseId);
}
