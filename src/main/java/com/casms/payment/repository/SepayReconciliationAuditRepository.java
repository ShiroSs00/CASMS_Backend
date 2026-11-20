package com.casms.payment.repository;

import com.casms.payment.entity.SepayReconciliationAudit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SepayReconciliationAuditRepository
        extends JpaRepository<SepayReconciliationAudit, UUID> {

    List<SepayReconciliationAudit> findBySepayTransactionIdOrderByCreatedAtAsc(
            UUID sepayTransactionId);
}
