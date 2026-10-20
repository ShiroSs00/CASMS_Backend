package com.casms.ship.repository;

import com.casms.ship.entity.LocationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface LocationRecordRepository extends JpaRepository<LocationRecord, UUID> {
    List<LocationRecord> findTop100ByDeviceLocationDeviceIdOrderByRecordedAtDesc(UUID deviceId);
    List<LocationRecord> findByShipShipIdAndRecordedAtBetweenOrderByRecordedAtAsc(UUID shipId, Instant from, Instant to);
    Optional<LocationRecord> findFirstByDeviceLocationDeviceIdAndBleIdentifierOrderByRecordedAtDesc(UUID deviceId, String bleIdentifier);
    boolean existsByDeviceLocationDeviceId(UUID deviceId);
    List<LocationRecord> findTop7ByDeviceLocationDeviceIdAndBleIdentifierOrderByRecordedAtDesc(UUID deviceId, String bleIdentifier);
}
