package com.casms.ship.repository;

import com.casms.ship.entity.ShipType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipTypeRepository extends JpaRepository<ShipType, UUID> {
    @Query("""
            SELECT t FROM ShipType t WHERE (:active IS NULL OR t.active = :active)
              AND (lower(t.code) LIKE :pattern ESCAPE '!' OR lower(t.name) LIKE :pattern ESCAPE '!')
            """)
    Page<ShipType> findCatalog(@Param("active") Boolean active,
            @Param("pattern") String pattern, Pageable pageable);

    List<ShipType> findByCodeOrModelKey(String code, String modelKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ShipType t where t.shipTypeId = :id")
    Optional<ShipType> findForUpdate(@Param("id") UUID id);
}
