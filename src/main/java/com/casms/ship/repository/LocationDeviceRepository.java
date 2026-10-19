package com.casms.ship.repository;

import com.casms.ship.entity.LocationDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LocationDeviceRepository extends JpaRepository<LocationDevice, UUID> {
    Optional<LocationDevice> findByDeviceCode(String deviceCode);
    List<LocationDevice> findByShipShipId(UUID shipId);
    boolean existsByDeviceCode(String deviceCode);
}
