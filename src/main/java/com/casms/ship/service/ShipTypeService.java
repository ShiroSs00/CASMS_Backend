package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.common.service.BusinessCodeGenerator;
import com.casms.identity.enums.Role;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.ShipDeckCount;
import com.casms.ship.dto.request.ShipFromTypeRequest;
import com.casms.ship.dto.request.ShipOperationalProfile;
import com.casms.ship.dto.response.ShipDesignMetadata;
import com.casms.ship.dto.response.ShipResponse;
import com.casms.ship.dto.response.ShipTypeResponse;
import com.casms.ship.entity.Ship;
import com.casms.ship.entity.ShipType;
import com.casms.ship.enums.ShipStatus;
import com.casms.ship.repository.ShipRepository;
import com.casms.ship.repository.ShipTypeRepository;
import com.casms.trip.repository.IdempotencyRecordRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final ShipClassificationMonitorService classificationMonitor;
    private final IdempotencyRecordRepository idempotencyRecords;
    private final ObjectMapper mapper;

    @Transactional
    public ShipTypeResponse create(String modelKey, MultipartFile bundle) {
        return createFromStored(modelKey, storeBundle(modelKey, bundle));
    }

    @Transactional
    public ShipTypeResponse createFromStored(String modelKey, String version) {
        assets.lockShipTypeDesign(modelKey);
        var existing = types.findByCodeOrModelKey(modelKey, modelKey);
        if (!existing.isEmpty()) {
            var type = existing.getFirst();
            if (existing.size() == 1 && modelKey.equals(type.getModelKey())
                    && version.equals(type.getAssetVersion())) {
                return describe(type, true);
            }
            throw new BusinessException(HttpStatus.CONFLICT, "SHIP_TYPE_CODE_CONFLICT",
                    "This design code already exists. Use model replacement to apply another version.");
        }
        ShipType candidate = new ShipType();
        ShipDesignMetadata metadata = readBundle(candidate, modelKey, version);
        candidate.setCode(modelKey);
        return ShipTypeResponse.from(types.saveAndFlush(candidate), 0, metadata);
    }

    @Transactional
    public ShipTypeResponse replaceModel(UUID id, String modelKey, MultipartFile bundle) {
        return replaceStoredModel(id, modelKey, storeBundle(modelKey, bundle));
    }

    @Transactional
    public ShipTypeResponse replaceStoredModel(UUID id, String modelKey, String version) {
        ShipType type = lockType(id);
        if (!Objects.equals(type.getModelKey(), modelKey)) {
            throw new BusinessException(HttpStatus.CONFLICT, "SHIP_TYPE_DESIGN_MISMATCH",
                    "The replacement must belong to the same model design");
        }
        if (!Objects.equals(type.getAssetVersion(), version)) {
            readBundle(type, modelKey, version);
        }
        return describe(type, true);
    }

    @Transactional(readOnly = true)
    public Page<ShipTypeResponse> list(Pageable pageable, boolean admin, Boolean active, String search) {
        if (!admin && Boolean.FALSE.equals(active)) return Page.empty(pageable);
        String term = search == null ? "" : search.strip().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        Boolean activeFilter = admin ? active : Boolean.TRUE;
        Page<ShipType> page = types.findCatalog(activeFilter, "%" + term + "%", pageable);

        Map<UUID, Long> counts = counts(page.map(ShipType::getShipTypeId).getContent(), admin);
        return page.map(type -> ShipTypeResponse.from(type, counts.getOrDefault(type.getShipTypeId(), 0L), null));
    }

    @Transactional(readOnly = true)
    public ShipTypeResponse detail(UUID id, boolean admin) {
        return describe(visibleType(id, admin), admin);
    }

    @Transactional(readOnly = true)
    public Page<ShipResponse> listShips(UUID id, Pageable pageable, boolean admin) {
        visibleType(id, admin);
        return (admin ? ships.findByShipTypeShipTypeIdAndArchivedAtIsNull(id, pageable)
                : ships.findManagedShipsByType(id, CruisePlanOperationsAccess.currentShipManager(), pageable))
                .map(shipService::mapToResponse);
    }

    private ShipType visibleType(UUID id, boolean admin) {
        return types.findById(id).filter(type -> admin || type.isActive())
                .orElseThrow(() -> new ResourceNotFoundException("ShipType", id));
    }

    private ShipTypeResponse describe(ShipType type, boolean admin) {
        UUID id = type.getShipTypeId();
        var metadata = ShipManufacturingMetadata.read(assets.manifest(type.getModelKey(), type.getAssetVersion()));
        return ShipTypeResponse.from(type, counts(List.of(id), admin).getOrDefault(id, 0L), metadata.response());
    }

    private Map<UUID, Long> counts(List<UUID> ids, boolean admin) {
        if (ids.isEmpty()) return Map.of();
        var counts = admin ? ships.countByTypeIds(ids)
                : ships.countManagedByTypeIds(ids, CruisePlanOperationsAccess.currentShipManager());
        return counts.stream().collect(Collectors.toMap(
                ShipRepository.TypeShipCount::getTypeId, ShipRepository.TypeShipCount::getShipCount));
    }

    @Transactional
    public ShipTypeResponse setActive(UUID id, boolean active) {
        ShipType type = lockType(id);
        type.setActive(active);
        return describe(type, true);
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
    public ShipResponse createShip(UUID id, ShipFromTypeRequest request, String actor, UUID requestKey) {
        UUID managerId = requireManagerIdentity();
        String shipName = request.shipName().strip();
        String key = requestKey.toString();
        String hash = requestHash(new ProvisionRequest(id, shipName, request.profile()));
        int inserted = idempotencyRecords.reserveShipProvisioning(UUID.randomUUID(), managerId, key, hash);
        var record = idempotencyRecords.findByActorIdAndOperationAndIdempotencyKey(managerId, "PROVISION_SHIP", key)
                .orElseThrow(() -> new IllegalStateException("Ship provisioning request reservation is missing"));
        if (!hash.equals(record.getRequestHash())) {
            throw new BusinessException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                    "The Idempotency-Key was already used for another ship provisioning request");
        }
        if (inserted == 0) {
            UUID shipId = record.getResultingId();
            if (shipId == null) {
                throw new BusinessException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                        "The provisioning request does not have a completed result");
            }
            return shipService.detail(shipId);
        }

        ShipResponse ship = provision(id, shipName, request.profile(), actor, managerId);
        record.setResultingId(ship.getShipId());
        return ship;
    }

    @Transactional(readOnly = true)
    public ShipResponse provisioningResult(UUID typeId, UUID requestKey) {
        UUID managerId = requireManagerIdentity();
        var shipId = idempotencyRecords.findByActorIdAndOperationAndIdempotencyKey(
                        managerId, "PROVISION_SHIP", requestKey.toString())
                .map(record -> record.getResultingId())
                .orElseThrow(() -> new ResourceNotFoundException("Ship provisioning result", requestKey));
        ShipResponse ship = shipService.detail(shipId);
        if (!typeId.equals(ship.getShipTypeId())) {
            throw new ResourceNotFoundException("Ship provisioning result", requestKey);
        }
        return ship;
    }

    private static UUID requireManagerIdentity() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CasmsPrincipal actor
                && actor.hasRole(Role.SHIP_MANAGER) && actor.getUserId() != null) {
            return actor.getUserId();
        }
        throw new AccessDeniedException("Ship Manager identity is required");
    }

    private record ProvisionRequest(UUID typeId, String shipName, ShipOperationalProfile profile) {}

    private String requestHash(ProvisionRequest request) {
        try {
            byte[] payload = mapper.writeValueAsBytes(request);
            if (request.profile() != null && request.profile().classificationDate() != null) {
                // Keep fingerprints for provisioning requests recorded before the API field rename.
                payload = new String(payload, StandardCharsets.UTF_8)
                        .replace("\"classificationDate\":", "\"lastClassificationDate\":")
                        .getBytes(StandardCharsets.UTF_8);
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload));
        } catch (NoSuchAlgorithmException | JsonProcessingException exception) {
            throw new IllegalStateException("Cannot fingerprint the ship provisioning request", exception);
        }
    }

    private ShipResponse provision(UUID id, String shipName, ShipOperationalProfile profile,
            String actor, UUID managerId) {
        ShipType type = lockType(id);
        if (!type.isActive()) {
            throw new BusinessException(HttpStatus.CONFLICT, "SHIP_TYPE_INACTIVE",
                    "Only an active ship type can be used to create a ship");
        }
        String modelKey = type.getModelKey();
        String assetVersion = type.getAssetVersion();
        JsonNode manifest = assets.manifest(modelKey, assetVersion);
        Ship ship = new Ship();
        ship.setShipType(type);
        ship.setOperationalProfile(profile == null ? null : profile.withLegacyManufacturing(null));
        ShipService.requireOperationalProfile(ship);
        ship.setDesignModelKey(modelKey);
        ship.setDesignAssetVersion(assetVersion);
        ship.setCreatedByShipManagerId(managerId);
        ship.setShipName(shipName);
        ship.setShipCode(codes.next("SH"));
        boolean expiredOnCreate = profile != null && profile.classificationExpiryDate() != null
                && profile.classificationExpiryDate().isBefore(java.time.LocalDate.now());
        ship.setStatus(expiredOnCreate ? ShipStatus.MAINTENANCE : ShipStatus.READY);
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
        UUID shipId = ships.saveAndFlush(ship).getShipId();
        UUID modelId = modelAssets.register(shipId, modelKey, assetVersion);
        importer.importForProvisioning(shipId, modelId, actor);
        modelAssets.activate(shipId, modelId);
        ship.setStatus(ShipStatus.READY);
        return shipService.detail(shipId);
    }

    @Transactional
    public ShipOperationalProfile replaceProfile(UUID shipId, ShipOperationalProfile profile) {
        Ship ship = ships.findByIdForUpdate(shipId)
                .orElseThrow(() -> new ResourceNotFoundException("Ship", shipId));
        ShipArchiveService.requireUnarchived(ship);
        requireTypedShip(ship);
        ShipOperationalProfile previous = ship.getOperationalProfile();
        ShipOperationalProfile updated = profile.withLegacyManufacturing(previous);
        requireCompleteProfileForReplacement(updated);
        if (ship.getCommissionDate() != null && profile.deliveryDate() != null
                && profile.deliveryDate().isBefore(ship.getCommissionDate())) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_DELIVERY_DATE",
                    "Ngày bàn giao không hợp lệ (không được trước ngày đưa tàu vào sử dụng).");
        }
        if (previous != null && previous.deliveryDate() != null
                && !Objects.equals(previous.deliveryDate(), profile.deliveryDate())
                && ships.hasAnyCruise(shipId)) {
            throw new BusinessException(HttpStatus.CONFLICT, "DELIVERY_DATE_LOCKED",
                    "Không thể thay đổi ngày bàn giao khi tàu đã phát sinh hoạt động.");
        }
        if (ShipStatus.isOperational(ship.getStatus()) && previous != null && previous.imoNumber() != null
                && !Objects.equals(previous.imoNumber(), profile.imoNumber())) {
            throw new BusinessException(HttpStatus.CONFLICT, "IMO_LOCKED",
                    "Không thể thay đổi số IMO khi tàu đã được xác minh đưa vào khai thác.");
        }
        ship.setOperationalProfile(updated);
        ships.save(ship);
        classificationMonitor.transitionToMaintenanceIfExpired(ship, java.time.LocalDate.now());
        return updated;
    }

    private static void requireCompleteProfileForReplacement(ShipOperationalProfile profile) {
        List<String> missingFields = new ArrayList<>();
        if (profile.deliveryDate() == null) missingFields.add("deliveryDate");
        if (profile.imoNumber() == null) missingFields.add("imoNumber");
        if (profile.registrationCountry() == null) missingFields.add("registrationCountry");
        if (profile.registrationNumber() == null) missingFields.add("registrationNumber");
        if (profile.classificationSociety() == null) missingFields.add("classificationSociety");
        if (profile.classificationDate() == null) missingFields.add("classificationDate");
        if (profile.classificationExpiryDate() == null) missingFields.add("classificationExpiryDate");
        if (!missingFields.isEmpty()) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "SHIP_OPERATIONAL_PROFILE_INCOMPLETE", "Hồ sơ vận hành tàu chưa đủ thông tin bắt buộc.",
                    Map.of("missingFields", missingFields));
        }
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

    private ShipType lockType(UUID id) {
        return types.findForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("ShipType", id));
    }

    private String storeBundle(String modelKey, MultipartFile bundle) {
        if (modelKey == null || modelKey.length() > 80 || !modelKey.matches("[a-z0-9-]+")) {
            throw invalid("modelKey");
        }
        if (bundle == null || bundle.isEmpty() || bundle.getSize() > 2L * 1024 * 1024 * 1024) {
            throw invalid("bundle");
        }
        try {
            return assets.storeImportZip(modelKey, bundle);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot read model ZIP", ex);
        }
    }

    private ShipDesignMetadata readBundle(ShipType type, String modelKey, String version) {
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
        var metadata = ShipManufacturingMetadata.read(manifest);
        List<String> invalidFields = metadata.invalidFields();
        if (!invalidFields.isEmpty()) throw invalid(String.join(", ", invalidFields));
        ShipModelImportService.validateProvisioningDesign(manifest,
                assets.validateStoredGlbs(modelKey, version, manifest));
        return metadata.response();
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
