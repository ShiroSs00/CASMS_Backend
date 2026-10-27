package com.casms.ship.repository;

import com.casms.ship.entity.CruiseAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CruiseAuditEventRepository extends JpaRepository<CruiseAuditEvent, UUID> {

    List<CruiseAuditEvent> findByCruise_CruiseIdOrderByTimestampDesc(UUID cruiseId);

    List<CruiseAuditEvent> findByCruise_CruiseIdAndEventTypeOrderByTimestampDesc(UUID cruiseId, String eventType);

    boolean existsByEventIdentity(String eventIdentity);

    long countByCruise_CruiseId(UUID cruiseId);
}
