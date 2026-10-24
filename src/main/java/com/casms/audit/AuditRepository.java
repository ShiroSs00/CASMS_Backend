package com.casms.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findByEntityIdOrderByOccurredAtDesc(UUID entityId);
}
