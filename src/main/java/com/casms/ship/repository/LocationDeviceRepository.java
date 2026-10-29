package com.casms.ship.repository;

import com.casms.ship.entity.LocationDevice;
import com.casms.ship.enums.LocationDeviceStatus;
import com.casms.ship.enums.LocationDeviceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LocationDeviceRepository extends JpaRepository<LocationDevice, UUID> {
    Optional<LocationDevice> findByDeviceCode(String deviceCode);
    boolean existsByDeviceCode(String deviceCode);
    List<LocationDevice> findByShipShipIdAndDeviceTypeOrderByDeviceCode(UUID shipId, LocationDeviceType deviceType);
    List<LocationDevice> findByShipShipIdAndDeckDeckId(UUID shipId, UUID deckId);
    boolean existsByShipShipIdAndBleIdentifier(UUID shipId, String bleIdentifier);
    Optional<LocationDevice> findByLocationDeviceIdAndShipShipId(UUID deviceId, UUID shipId);
    boolean existsByShipShipIdAndStatus(UUID shipId, LocationDeviceStatus status);
    boolean existsByDeckDeckId(UUID deckId);
    List<LocationDevice> findByShipShipId(UUID shipId);
}
