
package com.casms.ship.repository;

import com.casms.ship.entity.CruiseRegulation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CruiseRegulationRepository
        extends JpaRepository<
        CruiseRegulation,
        CruiseRegulation.CruiseRegulationId> {

    List<CruiseRegulation> findByCruise_CruiseId(UUID cruiseId);

    boolean existsByCruise_CruiseIdAndRegulation_RegulationId(
            UUID cruiseId,
            UUID regulationId
    );

    boolean existsByRegulation_RegulationId(UUID regulationId);
}
