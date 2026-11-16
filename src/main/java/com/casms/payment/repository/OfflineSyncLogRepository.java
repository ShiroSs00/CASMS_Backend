package com.casms.payment.repository;

import com.casms.payment.entity.OfflineSyncLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OfflineSyncLogRepository extends JpaRepository<OfflineSyncLog, UUID> {
    List<OfflineSyncLog> findByBatchIdOrderBySyncedAtAsc(UUID batchId);

    List<OfflineSyncLog> findByOnboardAccount_OnboardAccountIdOrderBySyncedAtDesc(
            UUID onboardAccountId);

    List<OfflineSyncLog> findTop100ByOrderBySyncedAtDesc();
}

