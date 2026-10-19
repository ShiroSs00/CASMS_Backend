package com.casms.ship.repository;

import com.casms.ship.entity.ShipModel;
import com.casms.ship.enums.ShipModelStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ShipModelRepository extends JpaRepository<ShipModel, UUID> {
    List<ShipModel> findByShipShipIdOrderByCreatedAtDesc(UUID shipId);
    boolean existsByShipShipIdAndVersion(UUID shipId, String version);
    boolean existsByShipModelIdAndStatus(UUID shipModelId, ShipModelStatus status);
}
