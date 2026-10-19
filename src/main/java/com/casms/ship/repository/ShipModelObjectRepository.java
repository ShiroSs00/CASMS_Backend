package com.casms.ship.repository;

import com.casms.ship.entity.ShipModelObject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipModelObjectRepository extends JpaRepository<ShipModelObject, UUID> {
    List<ShipModelObject> findByShipModelShipModelId(UUID shipModelId);
    Optional<ShipModelObject> findByShipModelShipModelIdAndNodeKey(UUID shipModelId, String nodeKey);
    boolean existsByShipModelShipModelIdAndNodeKey(UUID shipModelId, String nodeKey);
}
