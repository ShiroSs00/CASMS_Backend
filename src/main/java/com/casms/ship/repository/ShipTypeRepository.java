package com.casms.ship.repository;

import com.casms.ship.entity.ShipType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ShipTypeRepository extends JpaRepository<ShipType, UUID> {
    Page<ShipType> findByActiveTrue(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ShipType t where t.shipTypeId = :id")
    Optional<ShipType> findForUpdate(@Param("id") UUID id);
}
