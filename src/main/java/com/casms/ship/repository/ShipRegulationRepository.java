
package com.casms.ship.repository;

import com.casms.ship.entity.ShipRegulation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ShipRegulationRepository
        extends JpaRepository<
        ShipRegulation,
        ShipRegulation.ShipRegulationId> {

    List<ShipRegulation> findByShip_ShipId(UUID shipId);

    boolean existsByShip_ShipIdAndRegulation_RegulationId(
            UUID shipId,
            UUID regulationId
    );

    boolean existsByRegulation_RegulationId(UUID regulationId);
}
