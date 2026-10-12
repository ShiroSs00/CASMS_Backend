
package com.casms.ship.repository;

import com.casms.ship.entity.OperationalRegulation;
import com.casms.ship.enums.RegulationScope;
import com.casms.ship.enums.RegulationStatus;
import com.casms.ship.enums.RegulationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OperationalRegulationRepository
        extends JpaRepository<OperationalRegulation, UUID> {

    boolean existsByRegulationCode(String regulationCode);

    boolean existsByRegulationCodeAndRegulationIdNot(
            String regulationCode,
            UUID regulationId
    );

    List<OperationalRegulation> findByScope(RegulationScope scope);

    List<OperationalRegulation> findByStatus(RegulationStatus status);

    List<OperationalRegulation> findByScopeAndStatus(
            RegulationScope scope,
            RegulationStatus status
    );

    List<OperationalRegulation> findByRegulationTypeAndScope(
            RegulationType regulationType,
            RegulationScope scope
    );

    List<OperationalRegulation>
    findByStatusAndEffectiveFromLessThanEqualAndEffectiveToIsNull(
            RegulationStatus status,
            Instant time
    );

    List<OperationalRegulation>
    findByStatusAndEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqual(
            RegulationStatus status,
            Instant effectiveFrom,
            Instant effectiveTo
    );
}
