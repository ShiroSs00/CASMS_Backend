package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.common.service.BusinessCodeGenerator;
import com.casms.ship.ShipDeckCount;
import com.casms.ship.dto.request.ShipFromTypeRequest;
import com.casms.ship.dto.request.ShipOperationalProfile;
import com.casms.ship.dto.response.ShipResponse;
import com.casms.ship.dto.response.ShipTypeResponse;
import com.casms.ship.entity.Ship;
import com.casms.ship.entity.ShipType;
import com.casms.ship.enums.ShipStatus;
import com.casms.ship.repository.ShipRepository;
import com.casms.ship.repository.ShipTypeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShipTypeService {
    private final ShipTypeRepository types;
    private final ShipRepository ships;
    private final ShipModelAssetStore assets;
    private final ShipModelAssetService modelAssets;
    private final ShipModelImportService importer;
    private final BusinessCodeGenerator codes;
    private final ShipService shipService;

    @Transactional
    public ShipTypeResponse create(String modelKey, MultipartFile bundle) {
        ShipType type = new ShipType();
        readBundle(type, modelKey, bundle);
        type.setCode(type.getModelKey());
        return ShipTypeResponse.from(types.saveAndFlush(type));
    }

    @Transactional
    public ShipTypeResponse replaceModel(UUID id, String modelKey, MultipartFile bundle) {
        ShipType type = lockType(id);
        readBundle(type, modelKey, bundle);
        return ShipTypeResponse.from(type);
    }

    @Transactional(readOnly = true)
    public Page<ShipTypeResponse> list(Pageable pageable, boolean includeInactive) {
        return (includeInactive ? types.findAll(pageable) : types.findByActiveTrue(pageable))
                .map(ShipTypeResponse::from);
    }

    @Transactional(readOnly = true)
    public ShipTypeResponse detail(UUID id, boolean includeInactive) {
        return types.findById(id).filter(type -> includeInactive || type.isActive())
                .map(ShipTypeResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("ShipType", id));
    }

    @Transactional
    public ShipTypeResponse setActive(UUID id, boolean active) {
        ShipType type = lockType(id);
        type.setActive(active);
        return ShipTypeResponse.from(type);
    }

    @Transactional
    public void delete(UUID id) {
        ShipType type = lockType(id);
        if (ships.existsByShipTypeShipTypeId(id)) {
            throw new BusinessException(HttpStatus.CONFLICT, "SHIP_TYPE_IN_USE",
                    "Ship type is already assigned to a physical ship");
        }
        types.delete(type);
    }

    @Transactional
    public ShipResponse createShip(UUID id, ShipFromTypeRequest request, String actor) {
        ShipType type = lockType(id);
        if (!type.isActive()) {
            throw new BusinessException(HttpStatus.CONFLICT, "SHIP_TYPE_INACTIVE",
                    "Only an active ship type can be used to create a ship");
        }
        JsonNode manifest = assets.manifest(type.getModelKey(), type.getAssetVersion());
        Ship ship = new Ship();
        ship.setShipType(type);
        ship.setCreatedByShipManagerId(CruisePlanOperationsAccess.currentShipManager());
        ship.setShipName(request.shipName().strip());
        ship.setShipCode(codes.next("SH"));
        ship.setStatus(ShipStatus.DRAFT);
        ship.setTotalCapacity(type.getTotalCapacity());
        ship.setTotalDecks(type.getTotalDecks());
        ship.setLengthMeters(type.getLengthMeters());
        ship.setBeamMeters(type.getBeamMeters());
        ship.setManufacturerName(type.getManufacturerName());
        ship.setEngineType(type.getEngineType());
        ship.setManifestModelId(requiredText(manifest, "modelId", 80));
        int deckLimit = type.getTotalDecks();
        for (JsonNode deck : manifest.path("decks")) {
            deckLimit = Math.max(deckLimit, deck.path("number").asInt());
        }
        ship.setDeckNumberLimit(deckLimit);
        applyProfile(ship, request.profile());
        UUID shipId = ships.saveAndFlush(ship).getShipId();
        UUID modelId = modelAssets.register(shipId, type.getModelKey(), type.getAssetVersion());
        importer.importForProvisioning(shipId, modelId, actor);
        modelAssets.activate(shipId, modelId);
        return shipService.detail(shipId);
    }

    @Transactional
    public ShipOperationalProfile replaceProfile(UUID shipId, ShipOperationalProfile profile) {
        Ship ship = ships.findByIdForUpdate(shipId)
                .orElseThrow(() -> new ResourceNotFoundException("Ship", shipId));
        requireTypedShip(ship);
        applyProfile(ship, profile);
        return profile;
    }

    @Transactional(readOnly = true)
    public ShipOperationalProfile profile(UUID shipId) {
        Ship ship = ships.findById(shipId)
                .orElseThrow(() -> new ResourceNotFoundException("Ship", shipId));
        requireTypedShip(ship);
        return ship.getOperationalProfile();
    }

    private static void requireTypedShip(Ship ship) {
        if (ship.getShipType() == null) {
            throw new BusinessException(HttpStatus.CONFLICT, "SHIP_TYPE_REQUIRED",
                    "This profile is available for ships created from a ship type");
        }
    }

    private static void applyProfile(Ship ship, ShipOperationalProfile profile) {
        ship.setOperationalProfile(profile);
        if (profile != null) {
            ship.setOrigin(profile.buildCountry());
            ship.setProductionYear(profile.buildYear());
        }
    }

    private ShipType lockType(UUID id) {
        return types.findForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("ShipType", id));
    }

    private void readBundle(ShipType type, String modelKey, MultipartFile bundle) {
        if (modelKey == null || modelKey.length() > 80 || !modelKey.matches("[a-z0-9-]+")) {
            throw invalid("modelKey");
        }
        if (bundle == null || bundle.isEmpty() || bundle.getSize() > 2L * 1024 * 1024 * 1024) {
            throw invalid("bundle");
        }
        String version;
        try {
            version = assets.storeImportZip(modelKey, bundle);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot read model ZIP", ex);
        }
        JsonNode manifest = assets.manifest(modelKey, version);
        requiredText(manifest, "modelId", 80);
        type.setModelKey(modelKey);
        type.setAssetVersion(version);
        type.setName(requiredText(manifest, "name", 100));
        type.setManufacturerName(requiredText(manifest, "shipManufacturerName", 150));
        type.setEngineType(requiredText(manifest, "engineType", 100));
        type.setTotalCapacity(requiredPositiveInt(manifest, "passengerCapacity"));
        int decks = requiredPositiveInt(manifest, "deckCount");
        if (!ShipDeckCount.isValid(decks) || decks < manifest.path("decks").size()) {
            throw invalid("deckCount");
        }
        type.setTotalDecks(decks);
        type.setLengthMeters(requiredDimension(manifest, "length"));
        type.setBeamMeters(requiredDimension(manifest, "beam"));
    }

    private static String requiredText(JsonNode manifest, String field, int max) {
        JsonNode node = manifest.path(field);
        String value = node.asText("").strip();
        if (!node.isTextual() || value.isBlank() || value.length() > max) {
            throw invalid(field);
        }
        return value;
    }

    private static int requiredPositiveInt(JsonNode manifest, String field) {
        JsonNode node = manifest.path(field);
        if (!node.isIntegralNumber() || !node.canConvertToInt() || node.intValue() < 1) {
            throw invalid(field);
        }
        return node.intValue();
    }

    private static double requiredDimension(JsonNode manifest, String field) {
        JsonNode node = manifest.path(field);
        double value = node.doubleValue();
        if (!node.isNumber() || !Double.isFinite(value) || value <= 0) {
            throw invalid(field);
        }
        return value;
    }

    private static ResponseStatusException invalid(String field) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Missing or invalid ship type field: " + field);
    }
}
