package com.casms.ship.repository;

import com.casms.ship.entity.ModelImportJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ModelImportJobRepository extends JpaRepository<ModelImportJob, UUID> {
    List<ModelImportJob> findByShipModelShipModelIdOrderByStartedAtDesc(UUID shipModelId);
}
