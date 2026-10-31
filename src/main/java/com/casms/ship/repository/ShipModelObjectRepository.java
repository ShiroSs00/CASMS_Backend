package com.casms.ship.repository;

import com.casms.ship.entity.ShipModelObject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipModelObjectRepository extends JpaRepository<ShipModelObject, UUID> {
    long countByShipModelShipModelIdAndObjectTypeAndDeckIsNotNull(UUID modelId, com.casms.ship.enums.ShipModelObjectType type);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"deck", "deck.ship"})
    List<ShipModelObject> findByShipModelShipModelIdAndObjectTypeAndDeckIsNotNull(UUID modelId, com.casms.ship.enums.ShipModelObjectType type);
    List<ShipModelObject> findByShipModelShipModelId(UUID shipModelId);
    Optional<ShipModelObject> findByShipModelShipModelIdAndNodeKey(UUID shipModelId, String nodeKey);
    boolean existsByShipModelShipModelIdAndNodeKey(UUID shipModelId, String nodeKey);
    boolean existsByShipModelShipModelIdAndNodeKeyAndShipModelObjectIdNot(UUID shipModelId, String nodeKey, UUID objectId);
    boolean existsByShipModelShipModelIdAndDeckDeckId(UUID shipModelId, UUID deckId);
    boolean existsByShipModelShipModelIdAndCabinCabinId(UUID shipModelId, UUID cabinId);
    boolean existsByDeckDeckId(UUID deckId);
    boolean existsByCabinCabinId(UUID cabinId);
}
