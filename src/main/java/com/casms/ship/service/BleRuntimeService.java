package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.identity.entity.PassengerBleIdentity;
import com.casms.identity.enums.PassengerBleIdentityStatus;
import com.casms.identity.repository.PassengerBleIdentityRepository;
import com.casms.ship.dto.request.BleReadingRequest;
import com.casms.ship.dto.response.BleAnchorResponse;
import com.casms.ship.dto.response.BleTrackingResponse;
import com.casms.ship.dto.response.PassengerTrackingResponse;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.LocationDevice;
import com.casms.ship.entity.LocationRecord;
import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.enums.LocationDeviceStatus;
import com.casms.ship.enums.LocationDeviceType;
import com.casms.ship.repository.CruiseRepository;
import com.casms.ship.repository.LocationDeviceRepository;
import com.casms.ship.repository.LocationRecordRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BleRuntimeService {
    private static final String ENTITY_CRUISE = "Cruise";
    private static final String RANGE_ONLY_NO_DIRECTION = "RANGE_ONLY_NO_DIRECTION";

    private final CruiseRepository cruises;
    private final LocationDeviceRepository devices;
    private final LocationRecordRepository records;
    private final PassengerBleIdentityRepository identities;

    @Transactional
    public void ingest(UUID cruiseId, UUID receiverId, BleReadingRequest request) {
        Cruise cruise =
                cruises.findById(cruiseId)
                        .orElseThrow(() -> new ResourceNotFoundException(ENTITY_CRUISE, cruiseId));
        if (cruise.getStatus() != CruiseStatus.IN_PROGRESS) {
            throw new BusinessException(
                    "CRUISE_NOT_IN_PROGRESS",
                    "BLE tracking is available only while the cruise is in progress");
        }
        LocationDevice receiver = requireReceiver(cruise, receiverId);
        Instant now = Instant.now();
        if (request.getTimestamp().isAfter(now.plusSeconds(2))
                || request.getTimestamp().isBefore(now.minusSeconds(5))) {
            throw new BusinessException(
                    "STALE_BLE_OBSERVATION",
                    "BLE observation timestamp must be within the last 5 seconds and at most 2 seconds in the future");
        }
        String identifier = request.getDeviceId().trim().toUpperCase(Locale.ROOT);
        if (identities.findByCruiseCruiseIdAndActiveBleIdentifier(cruiseId, identifier).isEmpty()) {
            throw new BusinessException(
                    "BLE_TAG_NOT_ASSIGNED",
                    "BLE identifier is not actively assigned to a passenger on this cruise");
        }
        List<LocationRecord> previous =
                records.findTop7ByDeviceLocationDeviceIdAndBleIdentifierOrderByRecordedAtDesc(
                        receiverId, identifier);
        if (!previous.isEmpty()
                && !request.getTimestamp().isAfter(previous.getFirst().getRecordedAt())) {
            throw new BusinessException(
                    "OUT_OF_ORDER_BLE_OBSERVATION",
                    "BLE observations must arrive in timestamp order for this receiver and tag");
        }
        LocationRecord locRecord = new LocationRecord();
        locRecord.setDevice(receiver);
        locRecord.setShip(cruise.getShip());
        locRecord.setRecordedAt(request.getTimestamp());
        locRecord.setRssiDbm(request.getRssi());
        locRecord.setBleIdentifier(identifier);
        locRecord.setZoneKey(receiver.getDeviceCode());
        locRecord.setAccuracyMeters(null);
        double distanceEstimate =
                Math.pow(
                        10,
                        (receiver.getTxPowerDbm() - request.getRssi())
                                / (10 * receiver.getPathLossExponent()));
        locRecord.setEstimatedDistanceMeters(
                BigDecimal.valueOf(Math.clamp(distanceEstimate, .2, 50))
                        .setScale(2, RoundingMode.HALF_UP));
        records.save(locRecord);
        receiver.setLastSeenAt(now);
        devices.save(receiver);
    }

    @Transactional(readOnly = true)
    public BleTrackingResponse snapshot(UUID cruiseId, UUID receiverId, String rawIdentifier) {
        return doSnapshot(cruiseId, receiverId, rawIdentifier);
    }

    private BleTrackingResponse doSnapshot(UUID cruiseId, UUID receiverId, String rawIdentifier) {
        Cruise cruise =
                cruises.findById(cruiseId)
                        .orElseThrow(() -> new ResourceNotFoundException(ENTITY_CRUISE, cruiseId));
        if (cruise.getStatus() != CruiseStatus.IN_PROGRESS) {
            throw new BusinessException(
                    "CRUISE_NOT_IN_PROGRESS",
                    "BLE tracking is available only while the cruise is in progress");
        }
        LocationDevice receiver = requireReceiver(cruise, receiverId);
        String identifier =
                rawIdentifier == null ? null : rawIdentifier.trim().toUpperCase(Locale.ROOT);
        if (identifier == null
                || identities
                        .findByCruiseCruiseIdAndActiveBleIdentifier(cruiseId, identifier)
                        .isEmpty()) {
            throw new ResourceNotFoundException("Active passenger BLE identity", rawIdentifier);
        }
        List<LocationRecord> latest =
                records.findTop7ByDeviceLocationDeviceIdAndBleIdentifierOrderByRecordedAtDesc(
                        receiverId, identifier);
        return snapshotFrom(cruiseId, receiver, latest, Instant.now());
    }

    private BleTrackingResponse snapshotFrom(
            UUID cruiseId, LocationDevice receiver, List<LocationRecord> latest, Instant now) {
        LocationRecord newest = latest.isEmpty() ? null : latest.getFirst();
        String status = determineObservationStatus(newest, now);
        Double median = median(latest);
        ProximityDistanceResult distanceResult =
                calculateProximityDistance(receiver, latest, median, status, newest);

        BleAnchorResponse mapped = BleAnchorService.response(receiver);
        var zone =
                new BleTrackingResponse.Zone(
                        receiver.getDeviceCode(),
                        receiver.getDeviceName(),
                        receiver.getDeck().getDeckNumber(),
                        receiver.getModelNodeKey(),
                        mapped.position());
        var proximity =
                new BleTrackingResponse.Proximity(
                        newest == null ? null : newest.getRssiDbm(),
                        distanceResult.filteredRssi(),
                        distanceResult.distance(),
                        distanceResult.clamped(),
                        status,
                        newest == null ? null : newest.getRecordedAt(),
                        latest.size(),
                        Boolean.TRUE.equals(receiver.getCalibrated()),
                        receiver.getModelUnitsPerMeter(),
                        RANGE_ONLY_NO_DIRECTION);
        return new BleTrackingResponse(
                cruiseId,
                mapped,
                zone,
                proximity,
                RANGE_ONLY_NO_DIRECTION,
                "CASMS BLE RSSI observations");
    }

    private static String determineObservationStatus(LocationRecord newest, Instant now) {
        if (newest == null) {
            return "WAITING";
        }
        Duration age = Duration.between(newest.getRecordedAt(), now);
        if (age.compareTo(Duration.ofSeconds(15)) > 0) {
            return "OFFLINE";
        }
        if (age.compareTo(Duration.ofSeconds(5)) > 0) {
            return "STALE";
        }
        return "LIVE";
    }

    private record ProximityDistanceResult(Double filteredRssi, Double distance, boolean clamped) {}

    private static ProximityDistanceResult calculateProximityDistance(
            LocationDevice receiver, List<LocationRecord> latest, Double median, String status, LocationRecord newest) {
        Double filteredRssi = median;
        Double distance = null;
        boolean clamped = false;
        if (newest != null && "LIVE".equals(status) && median != null) {
            List<LocationRecord> prior =
                    latest.size() > 1 ? latest.subList(1, latest.size()) : List.of();
            Double priorMedian = median(prior);
            double filtered = priorMedian == null ? median : priorMedian * .75 + median * .25;
            filteredRssi = filtered;
            double calculated =
                    Math.pow(
                            10,
                            (receiver.getTxPowerDbm() - filtered)
                                    / (10 * receiver.getPathLossExponent()));
            clamped = calculated < .2 || calculated > 50;
            distance = Math.clamp(calculated, .2, 50);
        }
        return new ProximityDistanceResult(filteredRssi, distance, clamped);
    }

    @Transactional(readOnly = true)
    public PassengerTrackingResponse passengerOverview(UUID cruiseId) {
        Cruise cruise =
                cruises.findById(cruiseId)
                        .orElseThrow(() -> new ResourceNotFoundException(ENTITY_CRUISE, cruiseId));
        if (cruise.getStatus() != CruiseStatus.IN_PROGRESS) {
            throw new BusinessException(
                    "CRUISE_NOT_IN_PROGRESS", "Passenger tracking requires a cruise in progress");
        }
        if (cruise.getShip() == null) {
            throw new BusinessException("CRUISE_HAS_NO_SHIP", "Cruise has no assigned ship");
        }
        UUID shipId = cruise.getShip().getShipId();
        Instant now = Instant.now();
        List<LocationDevice> receivers = findActiveReceivers(shipId);
        List<PassengerBleIdentity> active =
                identities.findByCruiseCruiseIdAndStatusOrderByAssignedAtDesc(
                        cruiseId, PassengerBleIdentityStatus.ACTIVE);
        Map<String, List<LocationRecord>> grouped =
                groupRecentObservations(shipId, now, !active.isEmpty() && !receivers.isEmpty());

        List<PassengerTrackingResponse.Passenger> passengers = new ArrayList<>();
        for (var identity : active) {
            BleTrackingResponse best =
                    findBestTrackingCandidate(cruiseId, identity, receivers, grouped, cruise, now);
            passengers.add(mapPassengerTracking(identity, best));
        }
        return new PassengerTrackingResponse(
                shipId, cruiseId, now, RANGE_ONLY_NO_DIRECTION, List.copyOf(passengers));
    }

    private List<LocationDevice> findActiveReceivers(UUID shipId) {
        return devices.findByShipShipId(shipId).stream()
                .filter(d -> d.getDeviceType() == LocationDeviceType.BLE_RECEIVER
                        && d.getStatus() == LocationDeviceStatus.ACTIVE
                        && d.getInstalledAt() != null
                        && d.getDeck() != null
                        && d.getModelPositionX() != null
                        && d.getModelPositionY() != null
                        && d.getModelPositionZ() != null)
                .toList();
    }

    private Map<String, List<LocationRecord>> groupRecentObservations(
            UUID shipId, Instant now, boolean hasWork) {
        Map<String, List<LocationRecord>> grouped = new HashMap<>();
        if (!hasWork) {
            return grouped;
        }
        for (var observation :
                records.findByShipShipIdAndRecordedAtBetweenOrderByRecordedAtAsc(
                        shipId, now.minusSeconds(60), now)) {
            if (observation.getBleIdentifier() != null && observation.getRssiDbm() != null) {
                grouped.computeIfAbsent(
                                observation.getDevice().getLocationDeviceId()
                                        + ":"
                                        + observation.getBleIdentifier(),
                                key -> new ArrayList<>())
                        .add(observation);
            }
        }
        return grouped;
    }

    private BleTrackingResponse findBestTrackingCandidate(
            UUID cruiseId,
            PassengerBleIdentity identity,
            List<LocationDevice> receivers,
            Map<String, List<LocationRecord>> grouped,
            Cruise cruise,
            Instant now) {
        BleTrackingResponse best = null;
        for (var receiver : receivers) {
            List<LocationRecord> latest =
                    filterRecentObservations(grouped, receiver, identity, cruise);
            if (latest.isEmpty()) {
                continue;
            }
            BleTrackingResponse candidate = snapshotFrom(cruiseId, receiver, latest, now);
            if (isCandidateBetter(candidate, best)) {
                best = candidate;
            }
        }
        return best;
    }

    private static List<LocationRecord> filterRecentObservations(
            Map<String, List<LocationRecord>> grouped,
            LocationDevice receiver,
            PassengerBleIdentity identity,
            Cruise cruise) {
        String key = receiver.getLocationDeviceId() + ":" + identity.getBleIdentifier();
        return grouped.getOrDefault(key, List.of()).stream()
                .filter(r -> !r.getRecordedAt().isBefore(identity.getAssignedAt())
                        && (cruise.getDepartureDatetime() == null
                                || !r.getRecordedAt().isBefore(cruise.getDepartureDatetime())))
                .sorted(Comparator.comparing(LocationRecord::getRecordedAt).reversed())
                .limit(7)
                .toList();
    }

    private static boolean isCandidateBetter(BleTrackingResponse candidate, BleTrackingResponse best) {
        if (best == null) {
            return true;
        }
        boolean live = "LIVE".equals(candidate.proximity().status());
        boolean bestLive = "LIVE".equals(best.proximity().status());
        if (live && !bestLive) {
            return true;
        }
        if (live && bestLive) {
            return candidate.proximity().distanceMeters() < best.proximity().distanceMeters();
        }
        if (!live && !bestLive) {
            return candidate.proximity().lastSeen().isAfter(best.proximity().lastSeen());
        }
        return false;
    }

    private static PassengerTrackingResponse.Passenger mapPassengerTracking(
            PassengerBleIdentity identity, BleTrackingResponse best) {
        var bp = identity.getBookingPassenger();
        String name = bp.getPassenger() == null ? "Hành khách" : bp.getPassenger().getFullName();
        return new PassengerTrackingResponse.Passenger(
                identity.getPassengerBleIdentityId(),
                bp.getBookingPassengerId(),
                name,
                identity.getBleIdentifier(),
                identity.getDeviceName(),
                best == null ? "NO_RECENT_SIGNAL" : best.proximity().status(),
                best == null ? null : best.receiver(),
                best == null ? null : best.proximity());
    }

    @Transactional
    public BleTrackingResponse calibrateAtOneMeter(
            UUID cruiseId, UUID receiverId, String rawIdentifier) {
        Cruise cruise =
                cruises.findById(cruiseId)
                        .orElseThrow(() -> new ResourceNotFoundException(ENTITY_CRUISE, cruiseId));
        if (cruise.getStatus() != CruiseStatus.IN_PROGRESS) {
            throw new BusinessException(
                    "CRUISE_NOT_IN_PROGRESS",
                    "BLE tracking is available only while the cruise is in progress");
        }
        LocationDevice receiver = requireReceiver(cruise, receiverId);
        String identifier =
                rawIdentifier == null ? null : rawIdentifier.trim().toUpperCase(Locale.ROOT);
        if (identifier == null
                || identities
                        .findByCruiseCruiseIdAndActiveBleIdentifier(cruiseId, identifier)
                        .isEmpty()) {
            throw new ResourceNotFoundException("Active passenger BLE identity", rawIdentifier);
        }
        Instant cutoff = Instant.now().minusSeconds(10);
        List<LocationRecord> fresh =
                records.findTop100ByDeviceLocationDeviceIdOrderByRecordedAtDesc(receiverId).stream()
                        .filter(
                                r ->
                                        identifier.equals(r.getBleIdentifier())
                                                && r.getRssiDbm() != null
                                                && !r.getRecordedAt().isBefore(cutoff))
                        .toList();
        if (fresh.size() < 10
                || fresh.getFirst().getRecordedAt().isBefore(Instant.now().minusSeconds(2))) {
            throw new BusinessException(
                    "INSUFFICIENT_CALIBRATION_SAMPLES",
                    "Hold the assigned tag at 1 meter until at least 10 fresh BLE observations arrive");
        }
        Double tx = median(fresh);
        if (tx == null) {
            throw new BusinessException(
                    "INSUFFICIENT_CALIBRATION_SAMPLES",
                    "No valid BLE observations available for calibration");
        }
        if (tx < -100 || tx > -20) {
            throw new BusinessException(
                    "INVALID_CALIBRATION_RSSI", "Calibration RSSI is outside the supported range");
        }
        receiver.setTxPowerDbm(tx);
        receiver.setCalibrated(true);
        devices.save(receiver);
        return doSnapshot(cruiseId, receiverId, identifier);
    }

    private LocationDevice requireReceiver(Cruise cruise, UUID receiverId) {
        if (cruise.getShip() == null) {
            throw new BusinessException("CRUISE_HAS_NO_SHIP", "This cruise has no assigned ship");
        }
        LocationDevice receiver =
                devices.findByLocationDeviceIdAndShipShipId(
                                receiverId, cruise.getShip().getShipId())
                        .orElseThrow(
                                () -> new ResourceNotFoundException("BLE receiver", receiverId));
        if (receiver.getDeviceType() != LocationDeviceType.BLE_RECEIVER) {
            throw new BusinessException(
                    "NOT_BLE_RECEIVER", "Selected device is not a BLE receiver");
        }
        if (receiver.getStatus() != LocationDeviceStatus.ACTIVE) {
            throw new BusinessException("BLE_RECEIVER_INACTIVE", "BLE receiver is inactive");
        }
        if (receiver.getInstalledAt() == null) {
            throw new BusinessException(
                    "BLE_RECEIVER_NOT_INSTALLED",
                    "Physical installation has not been confirmed for this receiver");
        }
        if (receiver.getDeck() == null
                || receiver.getModelPositionX() == null
                || receiver.getModelPositionY() == null
                || receiver.getModelPositionZ() == null) {
            throw new BusinessException(
                    "BLE_RECEIVER_CONFIGURATION_INCOMPLETE",
                    "Receiver requires a deck and complete 3D coordinates");
        }
        return receiver;
    }

    private static Double median(List<LocationRecord> records) {
        if (records.isEmpty()) {
            return null;
        }
        List<Integer> values =
                new ArrayList<>(records.stream().map(LocationRecord::getRssiDbm).toList());
        Collections.sort(values);
        int middle = values.size() / 2;
        return values.size() % 2 == 1
                ? values.get(middle).doubleValue()
                : (values.get(middle - 1) + values.get(middle)) / 2.0;
    }
}
