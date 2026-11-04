package com.casms.servicepackage.repository;

import com.casms.servicepackage.entity.ServiceUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

/** Existing ServiceUsage storage; no second fulfillment ledger. */
public interface ServiceUsageRepository extends JpaRepository<ServiceUsage,UUID> {
    Optional<ServiceUsage> findByOperationIdentity(String operationIdentity);
}
