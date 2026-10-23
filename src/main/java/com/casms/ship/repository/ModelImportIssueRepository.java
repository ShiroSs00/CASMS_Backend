package com.casms.ship.repository;

import com.casms.ship.entity.ModelImportIssue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ModelImportIssueRepository extends JpaRepository<ModelImportIssue, UUID> {
    List<ModelImportIssue> findByJobModelImportJobIdOrderByCreatedAt(UUID jobId);
}
