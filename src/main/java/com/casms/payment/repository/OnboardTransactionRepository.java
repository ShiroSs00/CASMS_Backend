package com.casms.payment.repository;

import com.casms.payment.entity.OnboardTransaction;
import com.casms.payment.enums.TransactionStatus;
import com.casms.payment.enums.TransactionSyncStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OnboardTransactionRepository extends JpaRepository<OnboardTransaction, UUID> {
    List<OnboardTransaction> findTop100BySyncStatusOrderByTransactionTimeAsc(TransactionSyncStatus syncStatus);

    List<OnboardTransaction> findByOnboardAccount_OnboardAccountIdOrderByTransactionTimeDesc(UUID onboardAccountId);

    Optional<OnboardTransaction> findByIdempotencyKey(String idempotencyKey);

    List<OnboardTransaction> findByOnboardAccount_OnboardAccountIdAndSyncStatusIn(
            UUID onboardAccountId, List<TransactionSyncStatus> statuses);

    long countByOnboardAccount_OnboardAccountIdAndSyncStatusNot(
            UUID onboardAccountId, TransactionSyncStatus syncStatus);

    long countByOnboardAccount_OnboardAccountIdAndStatus(
            UUID onboardAccountId, TransactionStatus status);
}
