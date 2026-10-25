package com.casms.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findByEntityIdOrderByOccurredAtDesc(UUID entityId);
    Page<AuditLog> findByEntityIdOrderByOccurredAtDesc(UUID entityId, Pageable pageable);
    Page<AuditLog> findByEntityIdInOrderByOccurredAtDesc(Collection<UUID> entityIds, Pageable pageable);
}

