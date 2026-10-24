package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.identity.repository.PassengerBleIdentityRepository;
import com.casms.ship.dto.request.BleReadingRequest;
import com.casms.ship.dto.response.BleAnchorResponse;
import com.casms.ship.dto.response.BleTrackingResponse;
import com.casms.ship.dto.response.PassengerTrackingResponse;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.LocationDevice;
import com.casms.ship.entity.LocationRecord;
import com.casms.ship.enums.LocationDeviceStatus;
import com.casms.ship.enums.LocationDeviceType;
import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.repository.CruiseRepository;
import com.casms.ship.repository.LocationDeviceRepository;
import com.casms.ship.repository.LocationRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BleRuntimeService {
    private final CruiseRepository cruises;
    private final LocationDeviceRepository devices;
    private final LocationRecordRepository records;
    private final PassengerBleIdentityRepository identities;

    @Transactional
    public void ingest(UUID cruiseId, UUID receiverId, BleReadingRequest request) {
        Cruise cruise = cruises.findById(cruiseId).orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));
        if (cruise.getStatus() != CruiseStatus.IN_PROGRESS) throw new BusinessException("CRUISE_NOT_IN_PROGRESS", "BLE tracking is available only while the cruise is in progress");
        LocationDevice receiver = requireReceiver(cruise, receiverId);
        Instant now = Instant.now();
        if (request.getTimestamp().isAfter(now.plusSeconds(2)) || request.getTimestamp().isBefore(now.minusSeconds(5))) {
            throw new BusinessException("STALE_BLE_OBSERVATION", "BLE observation timestamp must be within the last 5 seconds and at most 2 seconds in the future");
        }
        String identifier = request.getDeviceId().trim().toUpperCase(Locale.ROOT);
        if (identities.findByCruiseCruiseIdAndActiveBleIdentifier(cruiseId, identifier).isEmpty()) {
            throw new BusinessException("BLE_TAG_NOT_ASSIGNED", "BLE identifier is not actively assigned to a passenger on this cruise");
        }
        List<LocationRecord> previous = records.findTop7ByDeviceLocationDeviceIdAndBleIdentifierOrderByRecordedAtDesc(receiverId, identifier);
        if (!previous.isEmpty() && !request.getTimestamp().isAfter(previous.getFirst().getRecordedAt())) {
            throw new BusinessException("OUT_OF_ORDER_BLE_OBSERVATION", "BLE observations must arrive in timestamp order for this receiver and tag");
        }
        LocationRecord record = new LocationRecord();
        record.setDevice(receiver);
        record.setShip(cruise.getShip());
        record.setRecordedAt(request.getTimestamp());
        record.setRssiDbm(request.getRssi());
        record.setBleIdentifier(identifier);
        record.setZoneKey(receiver.getDeviceCode());
        record.setAccuracyMeters(null);
        double distanceEstimate = Math.pow(10, (receiver.getTxPowerDbm() - request.getRssi()) / (10 * receiver.getPathLossExponent()));
        record.setEstimatedDistanceMeters(BigDecimal.valueOf(Math.clamp(distanceEstimate, .2, 50)).setScale(2, RoundingMode.HALF_UP));
        records.save(record);
        receiver.setLastSeenAt(now);
        devices.save(receiver);
    }

    @Transactional(readOnly = true)
    public BleTrackingResponse snapshot(UUID cruiseId, UUID receiverId, String rawIdentifier) {
        Cruise cruise = cruises.findById(cruiseId).orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));
        if (cruise.getStatus() != CruiseStatus.IN_PROGRESS) throw new BusinessException("CRUISE_NOT_IN_PROGRESS", "BLE tracking is available only while the cruise is in progress");
        LocationDevice receiver = requireReceiver(cruise, receiverId);
        String identifier = rawIdentifier == null ? null : rawIdentifier.trim().toUpperCase(Locale.ROOT);
        if (identifier == null || identities.findByCruiseCruiseIdAndActiveBleIdentifier(cruiseId, identifier).isEmpty()) {
            throw new ResourceNotFoundException("Active passenger BLE identity", rawIdentifier);
        }
        List<LocationRecord> latest = records.findTop7ByDeviceLocationDeviceIdAndBleIdentifierOrderByRecordedAtDesc(receiverId, identifier);
        return snapshotFrom(cruiseId, receiver, latest, Instant.now());
    }

    private BleTrackingResponse snapshotFrom(UUID cruiseId, LocationDevice receiver, List<LocationRecord> latest, Instant now) {
        LocationRecord newest = latest.isEmpty() ? null : latest.getFirst();
        String status = newest == null ? "WAITING"
                : Duration.between(newest.getRecordedAt(), now).compareTo(Duration.ofSeconds(15)) > 0 ? "OFFLINE"
                : Duration.between(newest.getRecordedAt(), now).compareTo(Duration.ofSeconds(5)) > 0 ? "STALE" : "LIVE";
        Double median = median(latest);
        Double previousFiltered = median;
        Double distance = null;
        boolean clamped = false;
        if (newest != null && status.equals("LIVE") && median != null) {
            List<LocationRecord> prior = latest.size() > 1 ? latest.subList(1, latest.size()) : List.of();
            Double priorMedian = median(prior);
            double filtered = priorMedian == null ? median : priorMedian * .75 + median * .25;
            previousFiltered = filtered;
            double calculated = Math.pow(10, (receiver.getTxPowerDbm() - filtered) / (10 * receiver.getPathLossExponent()));
            clamped = calculated < .2 || calculated > 50;
            distance = Math.clamp(calculated, .2, 50);
        }
        BleAnchorResponse mapped = BleAnchorService.response(receiver);
        var zone = new BleTrackingResponse.Zone(receiver.getDeviceCode(), receiver.getDeviceName(),
                receiver.getDeck().getDeckNumber(), receiver.getModelNodeKey(), mapped.position());
        var proximity = new BleTrackingResponse.Proximity(newest == null ? null : newest.getRssiDbm(), previousFiltered, distance, clamped, status,
                newest == null ? null : newest.getRecordedAt(), latest.size(), Boolean.TRUE.equals(receiver.getCalibrated()),
                receiver.getModelUnitsPerMeter(), "RANGE_ONLY_NO_DIRECTION");
        return new BleTrackingResponse(cruiseId, mapped, zone, proximity, "RANGE_ONLY_NO_DIRECTION", "CASMS BLE RSSI observations");
    }

    @Transactional(readOnly = true)
    public PassengerTrackingResponse passengerOverview(UUID cruiseId) {
        Cruise cruise = cruises.findById(cruiseId).orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));
        if (cruise.getStatus() != CruiseStatus.IN_PROGRESS) throw new BusinessException("CRUISE_NOT_IN_PROGRESS", "Passenger tracking requires a cruise in progress");
        if (cruise.getShip() == null) throw new BusinessException("CRUISE_HAS_NO_SHIP", "Cruise has no assigned ship");
        UUID shipId = cruise.getShip().getShipId();
        Instant now = Instant.now();
        var receivers = devices.findByShipShipId(shipId).stream().filter(d -> d.getDeviceType() == LocationDeviceType.BLE_RECEIVER
                && d.getStatus() == LocationDeviceStatus.ACTIVE && d.getInstalledAt() != null && d.getDeck() != null
                && d.getModelPositionX() != null && d.getModelPositionY() != null && d.getModelPositionZ() != null).toList();
        var active = identities.findByCruiseCruiseIdAndStatusOrderByAssignedAtDesc(cruiseId, com.casms.identity.enums.PassengerBleIdentityStatus.ACTIVE);
        var grouped = new java.util.HashMap<String, List<LocationRecord>>();
        if (!active.isEmpty() && !receivers.isEmpty()) for (var record : records.findByShipShipIdAndRecordedAtBetweenOrderByRecordedAtAsc(shipId, now.minusSeconds(60), now)) {
            if (record.getBleIdentifier() != null && record.getRssiDbm() != null) grouped.computeIfAbsent(record.getDevice().getLocationDeviceId() + ":" + record.getBleIdentifier(), key -> new ArrayList<>()).add(record);
        }
        var passengers = new ArrayList<PassengerTrackingResponse.Passenger>();
        for (var identity : active) {
            BleTrackingResponse best = null;
            for (var receiver : receivers) {
                var latest = grouped.getOrDefault(receiver.getLocationDeviceId() + ":" + identity.getBleIdentifier(), List.of()).stream()
                        .filter(r -> !r.getRecordedAt().isBefore(identity.getAssignedAt()) && (cruise.getDepartureDatetime() == null || !r.getRecordedAt().isBefore(cruise.getDepartureDatetime())))
                        .sorted(java.util.Comparator.comparing(LocationRecord::getRecordedAt).reversed()).limit(7).toList();
                if (latest.isEmpty()) continue;
                var candidate = snapshotFrom(cruiseId, receiver, latest, now);
                boolean live = candidate.proximity().status().equals("LIVE");
                boolean bestLive = best != null && best.proximity().status().equals("LIVE");
                if (best == null || (live && !bestLive) || (live && bestLive && candidate.proximity().distanceMeters() < best.proximity().distanceMeters())
                        || (!live && !bestLive && candidate.proximity().lastSeen().isAfter(best.proximity().lastSeen()))) best = candidate;
            }
            var bp = identity.getBookingPassenger();
            String name = bp.getPassenger() == null ? "Hành khách" : bp.getPassenger().getFullName();
            passengers.add(new PassengerTrackingResponse.Passenger(identity.getPassengerBleIdentityId(), bp.getBookingPassengerId(), name,
                    identity.getDeviceName(), best == null ? "NO_RECENT_SIGNAL" : best.proximity().status(), best == null ? null : best.receiver(), best == null ? null : best.proximity()));
        }
        return new PassengerTrackingResponse(shipId, cruiseId, now, "RANGE_ONLY_NO_DIRECTION", List.copyOf(passengers));
    }

    @Transactional
    public BleTrackingResponse calibrateAtOneMeter(UUID cruiseId, UUID receiverId, String rawIdentifier) {
        Cruise cruise = cruises.findById(cruiseId).orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));
        if (cruise.getStatus() != CruiseStatus.IN_PROGRESS) throw new BusinessException("CRUISE_NOT_IN_PROGRESS", "BLE tracking is available only while the cruise is in progress");
        LocationDevice receiver = requireReceiver(cruise, receiverId);
        String identifier = rawIdentifier == null ? null : rawIdentifier.trim().toUpperCase(Locale.ROOT);
        if (identifier == null || identities.findByCruiseCruiseIdAndActiveBleIdentifier(cruiseId, identifier).isEmpty()) {
            throw new ResourceNotFoundException("Active passenger BLE identity", rawIdentifier);
        }
        Instant cutoff = Instant.now().minusSeconds(10);
        List<LocationRecord> fresh = records.findTop100ByDeviceLocationDeviceIdOrderByRecordedAtDesc(receiverId).stream()
                .filter(r -> identifier.equals(r.getBleIdentifier()) && r.getRssiDbm() != null && !r.getRecordedAt().isBefore(cutoff))
                .toList();
        if (fresh.size() < 10 || fresh.getFirst().getRecordedAt().isBefore(Instant.now().minusSeconds(2))) throw new BusinessException("INSUFFICIENT_CALIBRATION_SAMPLES", "Hold the assigned tag at 1 meter until at least 10 fresh BLE observations arrive");
        double tx = median(fresh);
        if (tx < -100 || tx > -20) throw new BusinessException("INVALID_CALIBRATION_RSSI", "Calibration RSSI is outside the supported range");
        receiver.setTxPowerDbm(tx);
        receiver.setCalibrated(true);
        devices.save(receiver);
        return snapshot(cruiseId, receiverId, identifier);
    }

    private LocationDevice requireReceiver(Cruise cruise, UUID receiverId) {
        if (cruise.getShip() == null) throw new BusinessException("CRUISE_HAS_NO_SHIP", "This cruise has no assigned ship");
        LocationDevice receiver = devices.findByLocationDeviceIdAndShipShipId(receiverId, cruise.getShip().getShipId())
                .orElseThrow(() -> new ResourceNotFoundException("BLE receiver", receiverId));
        if (receiver.getDeviceType() != LocationDeviceType.BLE_RECEIVER) throw new BusinessException("NOT_BLE_RECEIVER", "Selected device is not a BLE receiver");
        if (receiver.getStatus() != LocationDeviceStatus.ACTIVE) throw new BusinessException("BLE_RECEIVER_INACTIVE", "BLE receiver is inactive");
        if (receiver.getInstalledAt() == null) throw new BusinessException("BLE_RECEIVER_NOT_INSTALLED", "Physical installation has not been confirmed for this receiver");
        if (receiver.getDeck() == null || receiver.getModelPositionX() == null || receiver.getModelPositionY() == null || receiver.getModelPositionZ() == null) {
            throw new BusinessException("BLE_RECEIVER_CONFIGURATION_INCOMPLETE", "Receiver requires a deck and complete 3D coordinates");
        }
        return receiver;
    }

    private static Double median(List<LocationRecord> records) {
        if (records.isEmpty()) return null;
        List<Integer> values = new ArrayList<>(records.stream().map(LocationRecord::getRssiDbm).toList());
        Collections.sort(values);
        int middle = values.size() / 2;
        return values.size() % 2 == 1 ? values.get(middle).doubleValue() : (values.get(middle - 1) + values.get(middle)) / 2.0;
    }
}
