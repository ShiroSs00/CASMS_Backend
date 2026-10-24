package com.casms.ship.repository;

import com.casms.ship.entity.Port;
import com.casms.ship.enums.PortStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PortRepository extends JpaRepository<Port, UUID> {

    Optional<Port> findByPortCode(String portCode);

    boolean existsByPortCode(String portCode);

    List<Port> findByStatus(PortStatus status);

    @Query("SELECT p FROM Port p WHERE p.status = 'ACTIVE'")
    List<Port> findAllActivePorts();

    List<Port> findByDestinationDestinationIdOrderByPortNameAsc(UUID destinationId);

    List<Port> findByDestinationDestinationIdAndStatusOrderByPortNameAsc(UUID destinationId, PortStatus status);
}