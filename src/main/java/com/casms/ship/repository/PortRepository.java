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

    @Query("SELECT p FROM Port p WHERE (:status IS NULL OR p.status = :status) " +
           "AND (:countryCodes IS NULL OR UPPER(p.countryCode) IN :countryCodes) " +
           "AND (:search IS NULL OR LOWER(p.portName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.portCode) LIKE LOWER(CONCAT('%', :search, '%')))")
    org.springframework.data.domain.Page<Port> findPortsWithFilters(
            @Param("countryCodes") List<String> countryCodes,
            @Param("status") PortStatus status,
            @Param("search") String search,
            org.springframework.data.domain.Pageable pageable);

    @Query("SELECT p FROM Port p WHERE p.status = 'ACTIVE' " +
           "AND (:countryCodes IS NULL OR UPPER(p.countryCode) IN :countryCodes) " +
           "AND (:search IS NULL OR LOWER(p.portName) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.portCode) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY p.portName ASC")
    List<Port> findActivePortsWithFilters(
            @Param("countryCodes") List<String> countryCodes,
            @Param("search") String search);
}