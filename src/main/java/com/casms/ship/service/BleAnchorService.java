package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.ship.dto.request.BleAnchorCreateRequest;
import com.casms.ship.dto.request.BleAnchorUpdateRequest;
import com.casms.ship.dto.response.BleAnchorResponse;
import com.casms.ship.entity.Deck;
import com.casms.ship.entity.LocationDevice;
import com.casms.ship.entity.Ship;
import com.casms.ship.enums.LocationDeviceStatus;
import com.casms.ship.enums.LocationDeviceType;
import com.casms.ship.repository.DeckRepository;
import com.casms.ship.repository.LocationDeviceRepository;
import com.casms.ship.repository.LocationRecordRepository;
import com.casms.ship.repository.ShipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BleAnchorService {
    private final ShipRepository ships;
    private final DeckRepository decks;
    private final LocationDeviceRepository devices;
    private final LocationRecordRepository records;

    @Transactional
    public BleAnchorResponse create(UUID shipId, BleAnchorCreateRequest request) {
        Ship ship = ships.findById(shipId).orElseThrow(() -> new ResourceNotFoundException("Ship", shipId));
        Deck deck = requireDeck(ship, request.getDeckId());
        if (request.getDeviceType() != LocationDeviceType.BLE_BEACON && request.getDeviceType() != LocationDeviceType.BLE_RECEIVER) {
            throw new BusinessException("INVALID_BLE_DEVICE_TYPE", "A BLE anchor must be a BLE_BEACON or BLE_RECEIVER");
        }
        validateTransform(request.getX(), request.getY(), request.getZ(), request.getRotationX(), request.getRotationY(), request.getRotationZ());
        validateRadio(request.getTxPowerDbm(), request.getPathLossExponent(), request.getModelUnitsPerMeter());
        String code = request.getDeviceCode().trim();
        if (devices.existsByDeviceCode(code)) throw new BusinessException("DUPLICATE_BLE_DEVICE_CODE", "Device code already exists");
        String identifier = normalizeBleIdentifier(request.getBleIdentifier());
        if (identifier != null && devices.existsByShipShipIdAndBleIdentifier(shipId, identifier)) {
            throw new BusinessException("DUPLICATE_BLE_IDENTIFIER", "BLE identifier already exists on this ship");
        }
        LocationDevice device = new LocationDevice();
        device.setShip(ship);
        device.setDeck(deck);
        device.setDeviceCode(code);
        device.setDeviceName(normalize(request.getDeviceName()));
        device.setBleIdentifier(identifier);
        device.setModelNodeKey(normalize(request.getModelNodeKey()));
        device.setDeviceType(request.getDeviceType());
        device.setStatus(LocationDeviceStatus.INACTIVE);
        applyTransform(device, request.getX(), request.getY(), request.getZ(), request.getRotationX(), request.getRotationY(), request.getRotationZ());
        device.setTxPowerDbm(request.getTxPowerDbm());
        device.setPathLossExponent(request.getPathLossExponent());
        device.setModelUnitsPerMeter(request.getModelUnitsPerMeter());
        return response(devices.save(device));
    }

    @Transactional(readOnly = true)
    public List<BleAnchorResponse> list(UUID shipId, UUID deckId) {
        if (!ships.existsById(shipId)) throw new ResourceNotFoundException("Ship", shipId);
        List<LocationDevice> found = deckId == null
                ? devices.findByShipShipId(shipId)
                : devices.findByShipShipIdAndDeckDeckId(shipId, deckId);
        if (deckId != null && decks.findById(deckId).map(d -> !d.getShip().getShipId().equals(shipId)).orElse(true)) {
            throw new ResourceNotFoundException("Deck", deckId);
        }
        return found.stream().filter(d -> d.getDeviceType() == LocationDeviceType.BLE_RECEIVER || d.getDeviceType() == LocationDeviceType.BLE_BEACON).map(BleAnchorService::response).toList();
    }

    @Transactional(readOnly = true)
    public BleAnchorResponse detail(UUID shipId, UUID anchorId) {
        return response(get(shipId, anchorId));
    }

    @Transactional
    public BleAnchorResponse update(UUID shipId, UUID anchorId, BleAnchorUpdateRequest request) {
        LocationDevice device = get(shipId, anchorId);
        if (device.getDeviceType() != LocationDeviceType.BLE_RECEIVER && device.getDeviceType() != LocationDeviceType.BLE_BEACON) {
            throw new ResourceNotFoundException("BLE anchor", anchorId);
        }
        if (request.getDeckId() != null) device.setDeck(requireDeck(device.getShip(), request.getDeckId()));
        if (request.getBleIdentifier() != null) {
            String identifier = normalizeBleIdentifier(request.getBleIdentifier());
            if (identifier != null && !identifier.equals(device.getBleIdentifier())
                    && devices.existsByShipShipIdAndBleIdentifier(shipId, identifier)) {
                throw new BusinessException("DUPLICATE_BLE_IDENTIFIER", "BLE identifier already exists on this ship");
            }
            device.setBleIdentifier(identifier);
        }
        Double x = request.getX() == null ? device.getModelPositionX() : request.getX();
        Double y = request.getY() == null ? device.getModelPositionY() : request.getY();
        Double z = request.getZ() == null ? device.getModelPositionZ() : request.getZ();
        Double rx = request.getRotationX() == null ? device.getModelRotationX() : request.getRotationX();
        Double ry = request.getRotationY() == null ? device.getModelRotationY() : request.getRotationY();
        Double rz = request.getRotationZ() == null ? device.getModelRotationZ() : request.getRotationZ();
        validateTransform(x, y, z, rx, ry, rz);
        applyTransform(device, x, y, z, rx, ry, rz);
        if (request.getDeviceName() != null) device.setDeviceName(normalize(request.getDeviceName()));
        if (request.getModelNodeKey() != null) device.setModelNodeKey(normalize(request.getModelNodeKey()));
        if (request.getTxPowerDbm() != null) {
            if (!request.getTxPowerDbm().equals(device.getTxPowerDbm())) device.setCalibrated(false);
            device.setTxPowerDbm(request.getTxPowerDbm());
        }
        if (request.getPathLossExponent() != null) device.setPathLossExponent(request.getPathLossExponent());
        if (request.getModelUnitsPerMeter() != null) device.setModelUnitsPerMeter(request.getModelUnitsPerMeter());
        validateRadio(device.getTxPowerDbm(), device.getPathLossExponent(), device.getModelUnitsPerMeter());
        if (request.getStatus() != null) {
            if (request.getStatus() == LocationDeviceStatus.RETIRED) throw new BusinessException("USE_RETIRE_WORKFLOW", "Use the dedicated retirement operation");
            device.setStatus(request.getStatus());
        }
        return response(devices.save(device));
    }

    @Transactional
    public BleAnchorResponse setStatus(UUID shipId, UUID anchorId, LocationDeviceStatus status) {
        LocationDevice device = get(shipId, anchorId);
        if (status == null) throw new BusinessException("INVALID_DEVICE_STATUS", "Device status is required");
        if (status == LocationDeviceStatus.RETIRED) {
            throw new BusinessException("USE_RETIRE_WORKFLOW", "Retirement is permanent; use the dedicated retirement endpoint");
        }
        device.setStatus(status);
        return response(devices.save(device));
    }

    @Transactional
    public void deleteUnused(UUID shipId, UUID anchorId) {
        LocationDevice device = get(shipId, anchorId);
        if (device.getStatus() != LocationDeviceStatus.INACTIVE || device.getInstalledAt() != null || records.existsByDeviceLocationDeviceId(anchorId)) {
            throw new BusinessException("BLE_ANCHOR_IN_USE", "Only an inactive, uninstalled anchor with no telemetry can be deleted");
        }
        devices.delete(device);
    }

    @Transactional
    public BleAnchorResponse confirmInstallation(UUID shipId, UUID anchorId) {
        LocationDevice device = get(shipId, anchorId);
        if (device.getInstalledAt() == null) {
            device.setInstalledAt(Instant.now());
            Authentication actor = SecurityContextHolder.getContext().getAuthentication();
            device.setInstalledBy(actor == null ? "system" : actor.getName());
        }
        return response(devices.save(device));
    }

    @Transactional
    public BleAnchorResponse retire(UUID shipId, UUID anchorId) {
        LocationDevice device = get(shipId, anchorId);
        if (device.getStatus() == LocationDeviceStatus.RETIRED) return response(device);
        device.setStatus(LocationDeviceStatus.RETIRED);
        return response(devices.save(device));
    }

    @Transactional(readOnly = true)
    public void validate(UUID shipId, UUID anchorId) {
        LocationDevice d = get(shipId, anchorId);
        if (d.getDeck() == null || !d.getDeck().getShip().getShipId().equals(shipId)) {
            throw new BusinessException("BLE_ANCHOR_DECK_MISMATCH", "Anchor deck must belong to the same ship");
        }
        validateTransform(d.getModelPositionX(), d.getModelPositionY(), d.getModelPositionZ(), d.getModelRotationX(), d.getModelRotationY(), d.getModelRotationZ());
        validateRadio(d.getTxPowerDbm(), d.getPathLossExponent(), d.getModelUnitsPerMeter());
    }

    private LocationDevice get(UUID shipId, UUID anchorId) {
        LocationDevice device = devices.findByLocationDeviceIdAndShipShipId(anchorId, shipId)
                .orElseThrow(() -> new ResourceNotFoundException("BLE anchor", anchorId));
        if (device.getDeviceType() != LocationDeviceType.BLE_BEACON && device.getDeviceType() != LocationDeviceType.BLE_RECEIVER) {
            throw new ResourceNotFoundException("BLE anchor", anchorId);
        }
        return device;
    }

    private Deck requireDeck(Ship ship, UUID deckId) {
        Deck deck = decks.findById(deckId).orElseThrow(() -> new ResourceNotFoundException("Deck", deckId));
        if (!deck.getShip().getShipId().equals(ship.getShipId())) throw new BusinessException("DECK_SHIP_MISMATCH", "Deck does not belong to this ship");
        return deck;
    }

    private static void validateTransform(Double x, Double y, Double z, Double rx, Double ry, Double rz) {
        for (Double v : new Double[]{x, y, z, rx == null ? 0.0 : rx, ry == null ? 0.0 : ry, rz == null ? 0.0 : rz})
            if (v == null || !Double.isFinite(v) || Math.abs(v) > 1000) throw new BusinessException("INVALID_MODEL_TRANSFORM", "BLE anchor transform must be finite and within ±1000 model units");
    }

    private static void validateRadio(Double tx, Double exponent, Double scale) {
        if (tx == null || !Double.isFinite(tx) || tx < -100 || tx > -20
                || exponent == null || !Double.isFinite(exponent) || exponent < 1 || exponent > 5
                || scale == null || !Double.isFinite(scale) || scale < .1 || scale > 10) {
            throw new BusinessException("INVALID_BLE_CALIBRATION", "Tx power must be −100..−20 dBm, path-loss exponent 1..5, and model scale 0.1..10");
        }
    }

    private static void applyTransform(LocationDevice d, Double x, Double y, Double z, Double rx, Double ry, Double rz) {
        d.setModelPositionX(x); d.setModelPositionY(y); d.setModelPositionZ(z);
        d.setModelRotationX(rx == null ? 0 : rx); d.setModelRotationY(ry == null ? 0 : ry); d.setModelRotationZ(rz == null ? 0 : rz);
    }

    private static String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String normalizeBleIdentifier(String value) { return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT); }

    public static BleAnchorResponse response(LocationDevice d) {
        return new BleAnchorResponse(d.getLocationDeviceId(), d.getShip().getShipId(), d.getDeck() == null ? null : d.getDeck().getDeckId(),
                d.getDeviceCode(), d.getDeviceName(), d.getBleIdentifier(), d.getDeviceType(), d.getStatus(),
                new BleAnchorResponse.Vector3(d.getModelPositionX(), d.getModelPositionY(), d.getModelPositionZ()),
                new BleAnchorResponse.Vector3(d.getModelRotationX(), d.getModelRotationY(), d.getModelRotationZ()),
                d.getTxPowerDbm(), d.getPathLossExponent(), d.getModelUnitsPerMeter(), Boolean.TRUE.equals(d.getCalibrated()), d.getModelNodeKey(),
                d.getInstalledAt(), d.getInstalledBy(), d.getLastSeenAt(), d.getUpdatedAt());
    }
}
