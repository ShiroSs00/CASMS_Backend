package com.casms.ship.repository;

import com.casms.ship.entity.ShipModel;
import com.casms.ship.enums.ShipModelStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;
import jakarta.persistence.LockModeType;

public interface ShipModelRepository extends JpaRepository<ShipModel, UUID> {
    List<ShipModel> findByShipShipIdOrderByCreatedAtDesc(UUID shipId);
    List<ShipModel> findByShipShipIdAndStatusOrderByCreatedAtDesc(UUID shipId, ShipModelStatus status);
    boolean existsByShipShipIdAndVersion(UUID shipId, String version);
    boolean existsByShipModelIdAndStatus(UUID shipModelId, ShipModelStatus status);
    boolean existsByShipShipIdAndModelKeyAndAssetVersion(UUID shipId, String modelKey, String assetVersion);
    List<ShipModel> findByShipShipIdAndModelKeyIsNotNullAndStatusOrderByCreatedAtDesc(UUID shipId, ShipModelStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Ship s where s.shipId = :shipId")
    java.util.Optional<com.casms.ship.entity.Ship> lockShip(@Param("shipId") UUID shipId);

    @Query("select m from ShipModel m where m.ship.shipId = :shipId and m.status = :status and m.modelKey is null order by m.createdAt desc")
    List<ShipModel> findLegacyByShipAndStatus(@Param("shipId") UUID shipId, @Param("status") ShipModelStatus status);
}
