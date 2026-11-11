package com.casms.payment.repository;

import com.casms.payment.entity.OnboardTransaction;
import com.casms.payment.enums.TransactionSyncStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OnboardTransactionRepository extends JpaRepository<OnboardTransaction, UUID> {
    List<OnboardTransaction> findTop100BySyncStatusOrderByTransactionTimeAsc(TransactionSyncStatus syncStatus);
}
