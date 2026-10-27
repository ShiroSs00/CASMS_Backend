package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.ship.dto.request.ShipModelCreateRequest;
import com.casms.ship.dto.request.ShipModelObjectCreateRequest;
import com.casms.ship.dto.response.*;
import com.casms.ship.entity.*;
import com.casms.ship.enums.LocationDeviceType;
import com.casms.ship.enums.ShipModelObjectType;
import com.casms.ship.enums.ShipModelStatus;
import com.casms.ship.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ShipModelService {
    private static final Pattern SHA256 = Pattern.compile("(?i)^[0-9a-f]{64}$");
    private final ShipRepository ships;
    private final ShipModelRepository models;
    private final ShipModelObjectRepository objects;
    private final DeckRepository decks;
    private final CabinRepository cabins;
    private final LocationDeviceRepository devices;
    private final ObjectMapper objectMapper;

    @Transactional
    public ShipModelResponse create(UUID shipId, ShipModelCreateRequest request) {
        Ship ship = ships.findById(shipId).orElseThrow(() -> new ResourceNotFoundException("Ship", shipId));
        validateAssetReference(request.getFileUri(), request.getFileFormat(), request.getChecksumSha256());
        validateScale(request.getModelUnitsPerMeter());
        if (models.existsByShipShipIdAndVersion(shipId, request.getVersion().trim())) {
            throw new BusinessException("DUPLICATE_SHIP_MODEL_VERSION", "This ship model version already exists");
        }
        ShipModel model = new ShipModel();
        model.setShip(ship);
        model.setVersion(request.getVersion().trim());
        model.setFileUri(request.getFileUri().trim());
        model.setFileFormat(request.getFileFormat().trim().toLowerCase(Locale.ROOT));
        model.setChecksumSha256(normalizeChecksum(request.getChecksumSha256()));
        model.setCoordinateSystem(request.getCoordinateSystem().trim());
        model.setCoordinateUnit(request.getCoordinateUnit().trim());
        model.setModelUnitsPerMeter(request.getModelUnitsPerMeter());
        model.setShipManufacturerName(normalize(request.getShipManufacturerName()));
        model.setModelProvider(normalize(request.getModelProvider()));
        model.setStatus(ShipModelStatus.DRAFT);
        return response(models.save(model));
    }

    @Transactional(readOnly = true)
    public List<ShipModelResponse> list(UUID shipId) {
        if (!ships.existsById(shipId)) throw new ResourceNotFoundException("Ship", shipId);
        return models.findByShipShipIdOrderByCreatedAtDesc(shipId).stream().map(ShipModelService::response).toList();
    }

    @Transactional(readOnly = true)
    public ShipModelResponse detail(UUID shipId, UUID modelId) { return response(getModel(shipId, modelId)); }

    @Transactional
    public ShipModelResponse update(UUID shipId, UUID modelId, ShipModelCreateRequest request) {
        ShipModel model = getEditableModel(shipId, modelId);
        validateAssetReference(request.getFileUri(), request.getFileFormat(), request.getChecksumSha256());
        validateScale(request.getModelUnitsPerMeter());
        String version = request.getVersion().trim();
        if (!model.getVersion().equals(version) && models.existsByShipShipIdAndVersion(shipId, version)) {
            throw new BusinessException("DUPLICATE_SHIP_MODEL_VERSION", "This ship model version already exists");
        }
        model.setVersion(version);
        model.setFileUri(request.getFileUri().trim());
        model.setFileFormat(request.getFileFormat().trim().toLowerCase(Locale.ROOT));
        model.setChecksumSha256(normalizeChecksum(request.getChecksumSha256()));
        model.setCoordinateSystem(request.getCoordinateSystem().trim());
        model.setCoordinateUnit(request.getCoordinateUnit().trim());
        model.setModelUnitsPerMeter(request.getModelUnitsPerMeter());
        model.setShipManufacturerName(normalize(request.getShipManufacturerName()));
        model.setModelProvider(normalize(request.getModelProvider()));
        return response(models.save(model));
    }

    @Transactional
    public ShipModelResponse setReady(UUID shipId, UUID modelId) {
        ShipModel model = getModel(shipId, modelId);
        if (model.getModelKey() != null) {
            throw new BusinessException("SHIP_MODEL_ASSET_ACTIVATION_REQUIRED", "Registered asset bundles must use the model asset activation API");
        }
        if (model.getStatus() != ShipModelStatus.DRAFT && model.getStatus() != ShipModelStatus.PROCESSING) {
            throw new BusinessException("INVALID_MODEL_TRANSITION", "Only draft or processing models can be marked ready");
        }
        validateAssetReference(model.getFileUri(), model.getFileFormat(), model.getChecksumSha256());
        model.setStatus(ShipModelStatus.READY);
        return response(models.save(model));
    }

    @Transactional
    public ShipModelResponse archive(UUID shipId, UUID modelId) {
        ShipModel model = getModel(shipId, modelId);
        if (model.getStatus() != ShipModelStatus.READY) throw new BusinessException("INVALID_MODEL_TRANSITION", "Only a ready model can be archived");
        model.setStatus(ShipModelStatus.ARCHIVED);
        return response(models.save(model));
    }

    @Transactional
    public void deleteDraft(UUID shipId, UUID modelId) {
        ShipModel model = getModel(shipId, modelId);
        if (model.getStatus() != ShipModelStatus.DRAFT && model.getStatus() != ShipModelStatus.FAILED) {
            throw new BusinessException("SHIP_MODEL_IN_USE", "Only draft or failed models can be deleted");
        }
        if (!objects.findByShipModelShipModelId(modelId).isEmpty()) {
            throw new BusinessException("SHIP_MODEL_HAS_OBJECTS", "Delete model object mappings before deleting the draft model");
        }
        models.delete(model);
    }

    @Transactional
    public ShipModelObjectResponse createObject(UUID shipId, UUID modelId, ShipModelObjectCreateRequest request) {
        ShipModel model = getEditableModel(shipId, modelId);
        String nodeKey = request.getNodeKey().trim();
        if (objects.existsByShipModelShipModelIdAndNodeKey(modelId, nodeKey)) throw new BusinessException("DUPLICATE_MODEL_NODE", "Node key already exists for this model");
        validateObjectLinks(model, request.getObjectType(), request.getDeckId(), request.getCabinId());
        validateTransform(request);
        validateJson(request.getMetadataJson());
        ShipModelObject entity = new ShipModelObject();
        entity.setShipModel(model);
        entity.setNodeKey(nodeKey);
        entity.setDisplayName(normalize(request.getDisplayName()));
        entity.setObjectType(request.getObjectType());
        entity.setDeck(request.getDeckId() == null ? null : decks.findById(request.getDeckId()).orElseThrow());
        entity.setCabin(request.getCabinId() == null ? null : cabins.findById(request.getCabinId()).orElseThrow());
        applyObjectFields(entity, request);
        return objectResponse(objects.save(entity));
    }

    @Transactional(readOnly = true)
    public List<ShipModelObjectResponse> listObjects(UUID shipId, UUID modelId) {
        getModel(shipId, modelId);
        return objects.findByShipModelShipModelId(modelId).stream().map(ShipModelService::objectResponse).toList();
    }

    @Transactional
    public ShipModelObjectResponse updateObject(UUID shipId, UUID modelId, UUID objectId, ShipModelObjectCreateRequest request) {
        getEditableModel(shipId, modelId);
        ShipModelObject entity = objects.findById(objectId).filter(o -> o.getShipModel().getShipModelId().equals(modelId))
                .orElseThrow(() -> new ResourceNotFoundException("Ship model object", objectId));
        String nodeKey = request.getNodeKey().trim();
        if (objects.existsByShipModelShipModelIdAndNodeKeyAndShipModelObjectIdNot(modelId, nodeKey, objectId)) {
            throw new BusinessException("DUPLICATE_MODEL_NODE", "Node key already exists for this model");
        }
        validateObjectLinks(entity.getShipModel(), request.getObjectType(), request.getDeckId(), request.getCabinId());
        validateTransform(request);
        validateJson(request.getMetadataJson());
        entity.setNodeKey(nodeKey);
        entity.setDisplayName(normalize(request.getDisplayName()));
        entity.setObjectType(request.getObjectType());
        entity.setDeck(request.getDeckId() == null ? null : decks.findById(request.getDeckId()).orElseThrow());
        entity.setCabin(request.getCabinId() == null ? null : cabins.findById(request.getCabinId()).orElseThrow());
        applyObjectFields(entity, request);
        return objectResponse(objects.save(entity));
    }

    @Transactional
    public void deleteObject(UUID shipId, UUID modelId, UUID objectId) {
        getEditableModel(shipId, modelId);
        ShipModelObject entity = objects.findById(objectId).filter(o -> o.getShipModel().getShipModelId().equals(modelId))
                .orElseThrow(() -> new ResourceNotFoundException("Ship model object", objectId));
        objects.delete(entity);
    }

    @Transactional(readOnly = true)
    public Ship3dConfigurationResponse configuration(UUID shipId) {
        Ship ship = ships.findById(shipId).orElseThrow(() -> new ResourceNotFoundException("Ship", shipId));
        ShipModel model = models.findByShipShipIdAndModelKeyIsNotNullAndStatusOrderByCreatedAtDesc(shipId, ShipModelStatus.READY)
                .stream().findFirst()
                .or(() -> models.findLegacyByShipAndStatus(shipId, ShipModelStatus.READY).stream().findFirst())
                .orElseThrow(() -> new BusinessException("SHIP_MODEL_NOT_READY", "No ready 3D model has been published for this ship"));
        List<ShipModelObject> modelObjects = objects.findByShipModelShipModelId(model.getShipModelId());
        Map<UUID, ShipModelObject> cabinNodes = new HashMap<>();
        Map<UUID, ShipModelObject> deckNodes = new HashMap<>();
        for (ShipModelObject object : modelObjects) {
            if (object.getCabin() != null) cabinNodes.put(object.getCabin().getCabinId(), object);
            if (object.getDeck() != null) deckNodes.put(object.getDeck().getDeckId(), object);
        }
        List<Deck> shipDecks = decks.findByShipShipIdOrderByDeckNumber(shipId);
        List<Cabin> shipCabins = cabins.findByShipShipIdOrderByDeckDeckNumberAscCabinNumberAsc(shipId);
        List<LocationDevice> shipDevices = devices.findByShipShipId(shipId).stream()
                .filter(d -> d.getDeviceType() == LocationDeviceType.BLE_BEACON || d.getDeviceType() == LocationDeviceType.BLE_RECEIVER).toList();
        Map<UUID, List<Cabin>> cabinsByDeck = new HashMap<>();
        shipCabins.forEach(c -> cabinsByDeck.computeIfAbsent(c.getDeck().getDeckId(), ignored -> new ArrayList<>()).add(c));
        Map<UUID, List<LocationDevice>> anchorsByDeck = new HashMap<>();
        shipDevices.stream().filter(d -> d.getDeck() != null).forEach(d -> anchorsByDeck.computeIfAbsent(d.getDeck().getDeckId(), ignored -> new ArrayList<>()).add(d));
        Instant configurationUpdatedAt = shipDecks.stream().map(Deck::getUpdatedAt)
                .filter(Objects::nonNull).reduce(ship.getUpdatedAt(), (a, b) -> a.isAfter(b) ? a : b);
        configurationUpdatedAt = shipCabins.stream().map(Cabin::getUpdatedAt).filter(Objects::nonNull)
                .reduce(configurationUpdatedAt, (a, b) -> a.isAfter(b) ? a : b);
        configurationUpdatedAt = shipDevices.stream().map(LocationDevice::getUpdatedAt).filter(Objects::nonNull)
                .reduce(configurationUpdatedAt, (a, b) -> a.isAfter(b) ? a : b);
        if (model.getUpdatedAt().isAfter(configurationUpdatedAt)) configurationUpdatedAt = model.getUpdatedAt();
        List<Ship3dConfigurationResponse.Deck> deckData = shipDecks.stream().map(deck -> {
            List<Ship3dConfigurationResponse.Room> rooms = cabinsByDeck.getOrDefault(deck.getDeckId(), List.of()).stream()
                    .map(c -> {
                        ShipModelObject mapping = cabinNodes.get(c.getCabinId());
                        return new Ship3dConfigurationResponse.Room(c.getCabinId(), c.getCabinNumber(), c.getCabinType(),
                                deck.getDeckId(), preferVector(mapping, true, c.getModelPositionX(), c.getModelPositionY(), c.getModelPositionZ()),
                                preferVector(mapping, false, c.getModelRotationX(), c.getModelRotationY(), c.getModelRotationZ()),
                                vector(c.getModelWidth(), c.getModelHeight(), c.getModelDepth()), mapping == null ? null : mapping.getNodeKey());
                    })
                    .toList();
            List<Ship3dConfigurationResponse.Anchor> anchors = anchorsByDeck.getOrDefault(deck.getDeckId(), List.of()).stream()
                    .map(d -> new Ship3dConfigurationResponse.Anchor(d.getLocationDeviceId(), d.getDeviceCode(), d.getDeviceName(),
                            d.getBleIdentifier(), d.getStatus(), vector(d.getModelPositionX(), d.getModelPositionY(), d.getModelPositionZ()),
                            vector(d.getModelRotationX(), d.getModelRotationY(), d.getModelRotationZ()), d.getTxPowerDbm(),
                            d.getPathLossExponent(), Boolean.TRUE.equals(d.getCalibrated()), d.getModelNodeKey(), d.getInstalledAt(), d.getLastSeenAt()))
                    .toList();
            String code = "DECK_%02d".formatted(deck.getDeckNumber());
            ShipModelObject mapping = deckNodes.get(deck.getDeckId());
            Ship3dConfigurationResponse.Vector3 position = preferVector(mapping, true,
                    deck.getModelPositionX(), deck.getModelPositionY(), deck.getModelPositionZ());
            return new Ship3dConfigurationResponse.Deck(deck.getDeckId(), code, deck.getDeckName(), deck.getDeckNumber(),
                    mapping == null ? code : mapping.getNodeKey(),
                    position == null ? null : position.y(), position,
                    preferVector(mapping, false, deck.getModelRotationX(), deck.getModelRotationY(), deck.getModelRotationZ()), rooms, anchors, false);
        }).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        modelObjects.stream()
                .filter(object -> object.getObjectType() == ShipModelObjectType.DECK && object.getDeck() == null)
                .map(this::serviceLevelResponse)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Ship3dConfigurationResponse.Deck::deckNumber))
                .forEach(deckData::add);
        return new Ship3dConfigurationResponse(ship.getShipId(), ship.getShipCode(), ship.getShipName(),
                model.getVersion() + ":" + configurationUpdatedAt.toEpochMilli(), configurationUpdatedAt,
                new Ship3dConfigurationResponse.Model(model.getShipModelId(), model.getFileUri(), model.getFileFormat(),
                        model.getVersion(), model.getCoordinateSystem(), model.getCoordinateUnit(), model.getModelUnitsPerMeter()), deckData);
    }

    @Transactional(readOnly = true)
    public void validateConfiguration(UUID shipId) {
        Ship3dConfigurationResponse config = configuration(shipId);
        if (config.decks().isEmpty()) throw new BusinessException("SHIP_CONFIGURATION_MISSING_DECKS", "Add at least one deck before using the 3D configuration");
        Ship ship = ships.findById(shipId).orElseThrow(() -> new ResourceNotFoundException("Ship", shipId));
        int deckNumberLimit = ship.getDeckNumberLimit() == null ? ship.getTotalDecks() : ship.getDeckNumberLimit();
        for (var deck : config.decks()) {
            if (!deck.serviceLevel() && (deck.deckNumber() < 1 || deck.deckNumber() > deckNumberLimit)) {
                throw new BusinessException("SHIP_CONFIGURATION_INVALID_DECK", "A configured deck is outside the ship deck range");
            }
            validateVector(deck.position(), "deck position", false);
            validateVector(deck.rotation(), "deck rotation", false);
            for (var room : deck.rooms()) {
                if (room.modelNodeKey() == null || room.modelNodeKey().isBlank()) throw new BusinessException("SHIP_CONFIGURATION_MISSING_ROOM_MAPPING", "Each cabin included in the 3D configuration must map to a GLB model node");
                validateVector(room.position(), "room position", false);
                validateVector(room.rotation(), "room rotation", false);
                validatePositiveVector(room.dimensions(), "room dimensions");
            }
            for (var anchor : deck.bleAnchors()) {
                validateVector(anchor.position(), "BLE anchor position", true);
                validateVector(anchor.rotation(), "BLE anchor rotation", false);
            }
        }
    }

    private Ship3dConfigurationResponse.Deck serviceLevelResponse(ShipModelObject object) {
        try {
            var metadata = objectMapper.readTree(object.getMetadataJson());
            int number = metadata.path("number").asInt(Integer.MIN_VALUE);
            if (number == Integer.MIN_VALUE) return null;
            String name = metadata.path("name").asText(object.getDisplayName());
            Ship3dConfigurationResponse.Vector3 position = vector(object.getPositionX(), object.getPositionY(), object.getPositionZ());
            Ship3dConfigurationResponse.Vector3 rotation = vector(object.getRotationX(), object.getRotationY(), object.getRotationZ());
            return new Ship3dConfigurationResponse.Deck(null, "SERVICE_" + number, name, number,
                    object.getNodeKey(), position == null ? metadata.path("elevationMetres").asDouble() : position.y(),
                    position, rotation, List.of(), List.of(), true);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void validateVector(Ship3dConfigurationResponse.Vector3 value, String field, boolean required) {
        if (value == null) {
            if (required) throw new BusinessException("SHIP_CONFIGURATION_INVALID_TRANSFORM", field + " is required");
            return;
        }
        for (Double coordinate : new Double[]{value.x(), value.y(), value.z()}) {
            if (coordinate == null || !Double.isFinite(coordinate) || Math.abs(coordinate) > 1000) throw new BusinessException("SHIP_CONFIGURATION_INVALID_TRANSFORM", field + " must contain finite x/y/z values within ±1000 model units");
        }
    }

    private static void validatePositiveVector(Ship3dConfigurationResponse.Vector3 value, String field) {
        if (value == null) return;
        for (Double dimension : new Double[]{value.x(), value.y(), value.z()}) {
            if (dimension == null || !Double.isFinite(dimension) || dimension <= 0 || dimension > 1000) throw new BusinessException("SHIP_CONFIGURATION_INVALID_DIMENSION", field + " must contain positive finite x/y/z values");
        }
    }

    private ShipModel getModel(UUID shipId, UUID modelId) {
        return models.findById(modelId).filter(m -> m.getShip().getShipId().equals(shipId))
                .orElseThrow(() -> new ResourceNotFoundException("Ship model", modelId));
    }
    private ShipModel getEditableModel(UUID shipId, UUID modelId) {
        ShipModel m = getModel(shipId, modelId);
        if (m.getModelKey() != null) {
            throw new BusinessException("SHIP_MODEL_ASSET_MANAGED", "Registered asset bundles are managed through the model asset API");
        }
        if (m.getStatus() == ShipModelStatus.READY || m.getStatus() == ShipModelStatus.ARCHIVED) {
            throw new BusinessException("SHIP_MODEL_IMMUTABLE", "Ready or archived models are immutable; create a new version to change configuration");
        }
        return m;
    }
    private void validateAssetReference(String uriValue, String formatValue, String checksum) {
        String format = formatValue.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("glb", "gltf").contains(format)) throw new BusinessException("UNSUPPORTED_MODEL_FORMAT", "Only GLB and glTF assets are supported");
        try {
            URI uri = URI.create(uriValue.trim());
            boolean allowed = uri.isAbsolute() ? Set.of("https", "http").contains(uri.getScheme().toLowerCase(Locale.ROOT)) : uriValue.startsWith("/") && !uriValue.contains("..") && !uriValue.contains("\\");
            if (!allowed || uriValue.isBlank()) throw new IllegalArgumentException();
        } catch (RuntimeException ex) {
            throw new BusinessException("INVALID_MODEL_ASSET_URI", "Asset reference must be an HTTP(S) URL or a safe root-relative path");
        }
        if (checksum != null && !checksum.isBlank() && !SHA256.matcher(checksum.trim()).matches()) throw new BusinessException("INVALID_MODEL_CHECKSUM", "Checksum must be a 64-character SHA-256 hex value");
    }
    private static void validateScale(Double scale) {
        if (scale == null || !Double.isFinite(scale) || scale < .1 || scale > 10) throw new BusinessException("INVALID_MODEL_SCALE", "Model units per meter must be between 0.1 and 10");
    }
    private static String normalizeChecksum(String value) { return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT); }
    private void validateObjectLinks(ShipModel model, ShipModelObjectType type, UUID deckId, UUID cabinId) {
        if (type == ShipModelObjectType.DECK && deckId == null) throw new BusinessException("MODEL_OBJECT_DECK_REQUIRED", "A DECK model object requires a deck ID");
        if (type == ShipModelObjectType.CABIN && cabinId == null) throw new BusinessException("MODEL_OBJECT_CABIN_REQUIRED", "A CABIN model object requires a cabin ID");
        if (deckId != null) {
            Deck deck = decks.findById(deckId).orElseThrow(() -> new ResourceNotFoundException("Deck", deckId));
            if (!deck.getShip().getShipId().equals(model.getShip().getShipId())) throw new BusinessException("DECK_SHIP_MISMATCH", "Model deck must belong to the model ship");
        }
        if (cabinId != null) {
            Cabin cabin = cabins.findById(cabinId).orElseThrow(() -> new ResourceNotFoundException("Cabin", cabinId));
            if (!cabin.getShip().getShipId().equals(model.getShip().getShipId()) || (deckId != null && !cabin.getDeck().getDeckId().equals(deckId))) throw new BusinessException("CABIN_SHIP_DECK_MISMATCH", "Mapped cabin and deck must belong to this model ship and each other");
        }
    }
    private void validateTransform(ShipModelObjectCreateRequest r) {
        validateTriplet(r.getPositionX(), r.getPositionY(), r.getPositionZ(), "position");
        validateTriplet(r.getRotationX(), r.getRotationY(), r.getRotationZ(), "rotation");
        for (Double v : new Double[]{r.getPositionX(), r.getPositionY(), r.getPositionZ(), r.getRotationX(), r.getRotationY(), r.getRotationZ()}) if (v != null && (!Double.isFinite(v) || Math.abs(v) > 1000)) throw new BusinessException("INVALID_MODEL_TRANSFORM", "Model transforms must be finite and within ±1000 model units");
        for (Double v : new Double[]{r.getScaleX(), r.getScaleY(), r.getScaleZ()}) if (v == null || !Double.isFinite(v) || v <= 0 || v > 1000) throw new BusinessException("INVALID_MODEL_SCALE", "Model scales must be finite and greater than 0 and at most 1000");
    }
    private static void validateTriplet(Double x, Double y, Double z, String name) {
        if ((x == null || y == null || z == null) && (x != null || y != null || z != null)) throw new BusinessException("INCOMPLETE_MODEL_TRANSFORM", "Model " + name + " requires x, y and z values together");
    }
    private void validateJson(String value) {
        if (value == null || value.isBlank()) return;
        try { objectMapper.readTree(value); } catch (Exception ex) { throw new BusinessException("INVALID_MODEL_METADATA", "Model metadata must be valid JSON"); }
    }
    private static void applyObjectFields(ShipModelObject e, ShipModelObjectCreateRequest r) {
        e.setPositionX(r.getPositionX()); e.setPositionY(r.getPositionY()); e.setPositionZ(r.getPositionZ());
        e.setRotationX(r.getRotationX()); e.setRotationY(r.getRotationY()); e.setRotationZ(r.getRotationZ());
        e.setScaleX(r.getScaleX()); e.setScaleY(r.getScaleY()); e.setScaleZ(r.getScaleZ()); e.setMetadataJson(r.getMetadataJson());
    }
    private static String normalize(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private static Ship3dConfigurationResponse.Vector3 vector(Double x, Double y, Double z) {
        return x == null && y == null && z == null ? null : new Ship3dConfigurationResponse.Vector3(x, y, z);
    }
    private static Ship3dConfigurationResponse.Vector3 preferVector(ShipModelObject object, boolean position, Double x, Double y, Double z) {
        if (object != null) {
            var mapped = vector(position ? object.getPositionX() : object.getRotationX(),
                    position ? object.getPositionY() : object.getRotationY(), position ? object.getPositionZ() : object.getRotationZ());
            if (mapped != null) return mapped;
        }
        return vector(x, y, z);
    }
    private static ShipModelResponse response(ShipModel m) {
        return new ShipModelResponse(m.getShipModelId(), m.getShip().getShipId(), m.getVersion(), m.getFileUri(), m.getFileFormat(),
                m.getChecksumSha256(), m.getCoordinateSystem(), m.getCoordinateUnit(), m.getModelUnitsPerMeter(), m.getStatus(), m.getCreatedAt(), m.getUpdatedAt(),
                m.getShipManufacturerName(), m.getModelProvider());
    }
    private static ShipModelObjectResponse objectResponse(ShipModelObject o) {
        return new ShipModelObjectResponse(o.getShipModelObjectId(), o.getShipModel().getShipModelId(), o.getNodeKey(), o.getDisplayName(),
                o.getObjectType(), o.getDeck() == null ? null : o.getDeck().getDeckId(), o.getCabin() == null ? null : o.getCabin().getCabinId(),
                vector(o.getPositionX(), o.getPositionY(), o.getPositionZ()), vector(o.getRotationX(), o.getRotationY(), o.getRotationZ()),
                vector(o.getScaleX(), o.getScaleY(), o.getScaleZ()), o.getMetadataJson(), o.getCreatedAt());
    }
}
