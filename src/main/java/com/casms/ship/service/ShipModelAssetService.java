package com.casms.ship.service;

import com.casms.common.exception.ResourceNotFoundException;
import com.casms.common.service.BusinessCodeGenerator;
import com.casms.ship.dto.request.ShipModelAssetRegisterRequest;
import com.casms.ship.dto.request.ShipModelCabinMappingRequest;
import com.casms.ship.dto.request.ShipModelDeckMappingRequest;
import com.casms.ship.dto.response.ShipModelAssetResponse;
import com.casms.ship.dto.response.ShipModelAssetUploadResponse;
import com.casms.ship.dto.response.ShipModelManifestRegistrationResponse;
import com.casms.ship.entity.Cabin;
import com.casms.ship.entity.Deck;
import com.casms.ship.entity.Ship;
import com.casms.ship.entity.ShipModel;
import com.casms.ship.entity.ShipModelObject;
import com.casms.ship.enums.ShipStatus;
import com.casms.ship.enums.ShipModelObjectType;
import com.casms.ship.enums.ShipModelStatus;
import com.casms.ship.repository.CabinRepository;
import com.casms.ship.repository.DeckRepository;
import com.casms.ship.repository.ShipModelObjectRepository;
import com.casms.ship.repository.ShipModelRepository;
import com.casms.ship.repository.ShipRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ShipModelAssetService {
    private final ShipRepository ships;
    private final ShipModelRepository models;
    private final ShipModelObjectRepository objects;
    private final DeckRepository decks;
    private final CabinRepository cabins;
    private final ShipModelAssetStore assets;
    private final BusinessCodeGenerator businessCodes;
    private final JdbcTemplate jdbc;

    @Transactional
    public ShipModelAssetUploadResponse uploadBundle(String modelKey, String assetVersion, MultipartFile bundle) {
        if (bundle == null || bundle.isEmpty() || bundle.getSize() > 2L * 1024 * 1024 * 1024) {
            throw bad("A non-empty ZIP up to 2 GiB is required");
        }
        try (InputStream input = bundle.getInputStream()) {
            ShipModelAssetStore.UploadResult result = assets.storeZip(modelKey, assetVersion, input);
            return new ShipModelAssetUploadResponse(modelKey, assetVersion, result.files(), result.bytes());
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This model asset version is already stored", ex);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot read the uploaded model ZIP", ex);
        }
    }

    @Transactional
    public UUID register(UUID shipId, ShipModelAssetRegisterRequest request) {
        if (!assets.bundleExists(request.modelKey(), request.assetVersion())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Model asset bundle is not uploaded to the database");
        }
        JsonNode manifest = assets.manifest(request.modelKey(), request.assetVersion());
        lockManifestIdentity(manifest.path("modelId").asText(""));
        lockShip(shipId);
        if (models.existsByShipShipIdAndModelKeyAndAssetVersion(shipId, request.modelKey(), request.assetVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Model asset version is already registered on this ship");
        }

        Ship ship = ships.findById(shipId).orElseThrow(() -> new ResourceNotFoundException("Ship", shipId));
        syncShipFromManifest(ship, manifest, false);
        ShipModel model = new ShipModel();
        model.setShip(ship);
        model.setModelKey(request.modelKey());
        model.setAssetVersion(request.assetVersion());
        model.setShipManufacturerName(optionalManifestText(manifest, "shipManufacturerName"));
        model.setModelProvider(optionalManifestText(manifest, "modelProvider"));
        model.setAssetRevision(manifest.path("revision").asText());
        model.setVersion(request.modelKey() + "@" + request.assetVersion());
        model.setFileUri("database://model-assets/" + request.modelKey() + "/" + request.assetVersion() + "/manifest.json");
        model.setFileFormat("glb");
        model.setCoordinateSystem("GLTF");
        model.setCoordinateUnit("metre");
        model.setModelUnitsPerMeter(1.0);
        model.setStatus(ShipModelStatus.DRAFT);
        try {
            return models.save(model).getShipModelId();
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Model asset version is already registered on this ship", ex);
        }
    }

    /** Resolve an existing manifest identity or create its CASMS Ship, then register the asset version. */
    @Transactional
    public ShipModelManifestRegistrationResponse registerFromManifest(String modelKey, String assetVersion,
                                                                       Authentication authentication) {
        if (!assets.bundleExists(modelKey, assetVersion)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Model asset bundle is not uploaded to the database");
        }
        JsonNode manifest = assets.manifest(modelKey, assetVersion);
        String manifestModelId = requiredManifestText(manifest, "modelId");
        lockManifestIdentity(manifestModelId);
        Ship ship = ships.lockByManifestModelId(manifestModelId).orElse(null);
        boolean created = ship == null;
        if (created) {
            requireAuthority(authentication, "SHIP_CREATE");
            ship = new Ship();
            ship.setShipCode(businessCodes.next("SH"));
            ship.setStatus(ShipStatus.ACTIVE);
        } else {
            requireAuthority(authentication, "SHIP_UPDATE");
        }
        syncShipFromManifest(ship, manifest, true);
        try {
            ship = ships.saveAndFlush(ship);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A Ship with this manifest identity was registered concurrently; retry registration", ex);
        }
        UUID shipModelId = register(ship.getShipId(), new ShipModelAssetRegisterRequest(modelKey, assetVersion));
        return new ShipModelManifestRegistrationResponse(ship.getShipId(), shipModelId, created);
    }

    private static void requireAuthority(Authentication authentication, String authority) {
        if (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(granted -> authority.equals(granted.getAuthority()))) {
            throw new AccessDeniedException("Missing required authority: " + authority);
        }
    }

    private void syncShipFromManifest(Ship ship, JsonNode manifest, boolean requireCompleteMetadata) {
        String manifestModelId = manifest.path("modelId").asText("").trim();
        if (manifestModelId.isEmpty()) {
            if (requireCompleteMetadata) throw bad("Manifest modelId is required to provision a Ship");
            return;
        }
        lockManifestIdentity(manifestModelId);
        Ship identityOwner = ships.findByManifestModelId(manifestModelId).orElse(null);
        if (identityOwner != null && ship.getShipId() != null && !identityOwner.getShipId().equals(ship.getShipId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Manifest identity is already linked to another Ship");
        }
        if (ship.getManifestModelId() != null && !ship.getManifestModelId().equals(manifestModelId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ship is already linked to a different manifest identity");
        }

        String name = manifest.path("name").asText("").trim();
        Integer deckCount = manifestInt(manifest, "deckCount");
        Integer passengerCapacity = manifestInt(manifest, "passengerCapacity");
        Double length = manifestDouble(manifest, "length");
        Double beam = manifestDouble(manifest, "beam");
        int maximumDeckNumber = manifest.path("decks").isArray()
                ? java.util.stream.StreamSupport.stream(manifest.path("decks").spliterator(), false)
                    .mapToInt(deck -> deck.path("number").asInt(0)).max().orElse(0)
                : 0;
        boolean complete = !name.isEmpty() && name.length() <= 100 && deckCount != null && deckCount > 0 &&
                maximumDeckNumber > 0 &&
                passengerCapacity != null && passengerCapacity > 0 && length != null && length > 0 &&
                beam != null && beam > 0;
        if (requireCompleteMetadata && !complete) {
            throw bad("Manifest must provide a valid name, deckCount, passengerCapacity, length, beam, and passenger deck numbering to provision a Ship");
        }
        ship.setManifestModelId(manifestModelId);
        if (!complete) return; // Keep older manifest bundles compatible with explicit ship registration.

        if (ship.getShipId() != null) {
            Long configuredCabinCapacity = cabins.sumCapacityByShipId(ship.getShipId());
            if (configuredCabinCapacity != null && passengerCapacity < configuredCabinCapacity) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Manifest passengerCapacity is lower than the configured cabin capacity");
            }
            long configuredDeckCount = decks.countByShipShipIdAndServiceLevelFalse(ship.getShipId());
            if (deckCount < configuredDeckCount) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Manifest deckCount is lower than the configured business deck count");
            }
            maximumDeckNumber = Math.max(maximumDeckNumber,
                    ship.getDeckNumberLimit() == null ? ship.getTotalDecks() : ship.getDeckNumberLimit());
        }
        ship.setShipName(name);
        ship.setTotalCapacity(passengerCapacity);
        ship.setTotalDecks(deckCount); // serviceDecks are model-only, not passenger business decks.
        ship.setDeckNumberLimit(maximumDeckNumber);
        ship.setLengthMeters(length);
        ship.setBeamMeters(beam);
    }

    private void lockManifestIdentity(String manifestModelId) {
        if (manifestModelId == null || manifestModelId.isBlank()) return;
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, manifestModelId);
                statement.execute();
            }
            return null;
        });
    }

    private static String requiredManifestText(JsonNode manifest, String field) {
        String value = manifest.path(field).asText("").trim();
        if (value.isEmpty()) throw bad("Manifest " + field + " is required");
        return value;
    }

    private static String optionalManifestText(JsonNode manifest, String field) {
        String value = manifest.path(field).asText("").trim();
        return value.isEmpty() ? null : value;
    }

    private static Integer manifestInt(JsonNode manifest, String field) {
        JsonNode value = manifest.path(field);
        return value.isIntegralNumber() && value.canConvertToInt() ? value.asInt() : null;
    }

    private static Double manifestDouble(JsonNode manifest, String field) {
        JsonNode value = manifest.path(field);
        if (!value.isNumber()) return null;
        double number = value.asDouble();
        return Double.isFinite(number) ? number : null;
    }

    @Transactional(readOnly = true)
    public ShipModelAssetResponse detail(UUID shipId, UUID modelId, String contextPath) {
        ShipModel model = model(shipId, modelId);
        JsonNode manifest = manifest(model);
        String base = contextPath + "/ships/" + shipId + "/models/" + modelId + "/assets/";
        List<ShipModelObject> mappings = objects.findByShipModelShipModelId(modelId);
        int mappedCabins = (int) mappings.stream().filter(o -> o.getObjectType() == ShipModelObjectType.CABIN).count();
        int mappedDecks = (int) mappings.stream().filter(o -> o.getObjectType() == ShipModelObjectType.DECK).count();
        List<Integer> deckNumbers = assets.decks(manifest).stream().map(d -> d.path("number").asInt()).toList();
        return new ShipModelAssetResponse(modelId, model.getModelKey(), model.getAssetRevision(), model.getAssetVersion(),
                apiStatus(model), base, base + "manifest.json", manifest.path("units").asText(),
                manifest.path("gltfAxes").path("up").asText(), manifest.path("cabins").size(), mappedCabins,
                deckNumbers.size(), mappedDecks, deckNumbers, model.getShipManufacturerName(), model.getModelProvider());
    }

    @Transactional(readOnly = true)
    public ShipModelAssetResponse active(UUID shipId, String contextPath) {
        ShipModel model = models.findByShipShipIdAndModelKeyIsNotNullAndStatusOrderByCreatedAtDesc(shipId, ShipModelStatus.READY)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No active model on this ship"));
        return detail(shipId, model.getShipModelId(), contextPath);
    }

    @Transactional(readOnly = true)
    public com.casms.ship.dto.response.PublicShipModelResponse publicModel(UUID shipId) {
        ShipModel current = models.findByShipShipIdAndModelKeyIsNotNullAndStatusOrderByCreatedAtDesc(shipId, ShipModelStatus.READY)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No published model on this ship"));
        ShipModel model = publicModelVersion(shipId, current.getShipModelId());
        return new com.casms.ship.dto.response.PublicShipModelResponse(shipId, model.getShipModelId(),
                manifest(model), cabinMappings(shipId, model.getShipModelId()));
    }

    private ShipModel publicModelVersion(UUID shipId, UUID modelId) {
        ShipModel model = models.findById(modelId)
                .filter(value -> value.getShip().getShipId().equals(shipId)
                        && value.getShip().getStatus() == ShipStatus.ACTIVE
                        && value.getStatus() == ShipModelStatus.READY && value.getModelKey() != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Published model not found"));
        manifest(model);
        return model;
    }

    private ShipModel publicAssetModel(UUID shipId, UUID modelId, String name) {
        ShipModel model = publicModelVersion(shipId, modelId);
        JsonNode manifest = manifest(model);
        Set<String> allowed = new HashSet<>();
        manifest.path("assets").elements().forEachRemaining(value -> { if (value.isTextual()) allowed.add(value.asText()); });
        assets.decks(manifest).forEach(deck -> allowed.add(deck.path("asset").asText()));
        if (!name.endsWith(".glb") || !allowed.contains(name))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Published model asset not found");
        return model;
    }

    @Transactional(readOnly = true)
    public ShipModelAssetStore.AssetMetadata publicAsset(UUID shipId, UUID modelId, String name) {
        ShipModel model = publicAssetModel(shipId, modelId, name);
        return assets.asset(model.getModelKey(), model.getAssetVersion(), name);
    }

    @Transactional(readOnly = true)
    public void writePublicAsset(UUID shipId, UUID modelId, String name, OutputStream output) {
        ShipModel model = publicAssetModel(shipId, modelId, name);
        assets.writeAsset(model.getModelKey(), model.getAssetVersion(), name, output);
    }

    @Transactional(readOnly = true)
    public List<ShipModelDeckMappingRequest> deckMappings(UUID shipId, UUID modelId) {
        ShipModel model = model(shipId, modelId);
        return objects.findByShipModelShipModelId(modelId).stream()
                .filter(o -> o.getObjectType() == ShipModelObjectType.DECK && o.getDeck() != null)
                .map(o -> new ShipModelDeckMappingRequest(o.getNodeKey(), o.getDeck().getDeckId()))
                .sorted((a, b) -> a.modelDeckId().compareTo(b.modelDeckId())).toList();
    }

    @Transactional(readOnly = true)
    public List<ShipModelCabinMappingRequest> cabinMappings(UUID shipId, UUID modelId) {
        model(shipId, modelId);
        return objects.findByShipModelShipModelId(modelId).stream()
                .filter(o -> o.getObjectType() == ShipModelObjectType.CABIN && o.getCabin() != null)
                .map(o -> new ShipModelCabinMappingRequest(o.getNodeKey(), o.getCabin().getCabinId()))
                .sorted((a, b) -> a.modelId().compareTo(b.modelId())).toList();
    }

    @Transactional
    public void replaceDeckMappings(UUID shipId, UUID modelId, List<ShipModelDeckMappingRequest> requests) {
        lockShip(shipId);
        ShipModel model = editableModel(shipId, modelId);
        JsonNode manifest = manifest(model);
        Map<String, Integer> modelDeckNumbers = new HashMap<>();
        for (JsonNode deck : assets.decks(manifest)) modelDeckNumbers.put(deck.path("id").asText(), deck.path("number").asInt());

        Set<String> seenModelDecks = new HashSet<>();
        Set<UUID> seenDatabaseDecks = new HashSet<>();
        Map<String, Deck> resolved = new HashMap<>();
        for (ShipModelDeckMappingRequest request : requests) {
            Integer expectedNumber = modelDeckNumbers.get(request.modelDeckId());
            if (expectedNumber == null || !seenModelDecks.add(request.modelDeckId()) || !seenDatabaseDecks.add(request.deckId())) {
                throw bad("Unknown or duplicate model/deck mapping");
            }
            Deck deck = decks.findById(request.deckId()).orElseThrow(() -> bad("Deck does not exist"));
            if (!deck.getShip().getShipId().equals(shipId) || !Objects.equals(deck.getDeckNumber(), expectedNumber)) {
                throw bad("Deck must belong to this ship and match the model deck number");
            }
            resolved.put(request.modelDeckId(), deck);
        }

        Map<UUID, Integer> proposedNumberByDatabaseDeck = new HashMap<>();
        resolved.values().forEach(deck -> proposedNumberByDatabaseDeck.put(deck.getDeckId(), deck.getDeckNumber()));
        for (ShipModelObject cabinMapping : cabinMappingObjects(modelId)) {
            int expected = assets.cabinDeckNumber(manifest, cabinMapping.getNodeKey());
            Deck mapped = resolved.values().stream().filter(d -> d.getDeckId().equals(cabinMapping.getCabin().getDeck().getDeckId()))
                    .findFirst().orElse(null);
            if (mapped == null || !Objects.equals(proposedNumberByDatabaseDeck.get(mapped.getDeckId()), expected)) {
                throw bad("Existing cabin mappings would be invalidated; replace or clear them first");
            }
        }

        List<ShipModelObject> old = deckMappingObjects(modelId);
        objects.deleteAll(old);
        objects.flush();
        List<ShipModelObject> saved = new ArrayList<>();
        for (var entry : resolved.entrySet()) saved.add(mapping(model, entry.getKey(), ShipModelObjectType.DECK, entry.getValue(), null));
        objects.saveAll(saved);
    }

    @Transactional
    public void replaceCabinMappings(UUID shipId, UUID modelId, List<ShipModelCabinMappingRequest> requests) {
        lockShip(shipId);
        ShipModel model = editableModel(shipId, modelId);
        JsonNode manifest = manifest(model);
        Set<String> modelCabins = assets.cabinIds(manifest);
        Set<String> seenModelCabins = new HashSet<>();
        Set<UUID> seenDatabaseCabins = new HashSet<>();
        Map<Integer, UUID> mappedDecks = new HashMap<>();
        for (ShipModelObject deckMapping : deckMappingObjects(modelId)) {
            mappedDecks.put(deckMapping.getDeck().getDeckNumber(), deckMapping.getDeck().getDeckId());
        }

        Map<String, Cabin> resolved = new HashMap<>();
        for (ShipModelCabinMappingRequest request : requests) {
            if (!modelCabins.contains(request.modelId()) || !seenModelCabins.add(request.modelId()) ||
                    !seenDatabaseCabins.add(request.cabinId())) throw bad("Unknown or duplicate model/cabin mapping");
            Cabin cabin = cabins.findById(request.cabinId()).orElseThrow(() -> bad("Cabin does not exist"));
            int expectedDeck = assets.cabinDeckNumber(manifest, request.modelId());
            UUID mappedDeckId = mappedDecks.get(expectedDeck);
            if (!cabin.getShip().getShipId().equals(shipId) || !Objects.equals(cabin.getDeck().getDeckNumber(), expectedDeck) ||
                    !Objects.equals(cabin.getDeck().getDeckId(), mappedDeckId)) {
                throw bad("Cabin must belong to this ship and the mapped model deck");
            }
            resolved.put(request.modelId(), cabin);
        }

        objects.deleteAll(cabinMappingObjects(modelId));
        objects.flush();
        List<ShipModelObject> saved = new ArrayList<>();
        for (var entry : resolved.entrySet()) {
            saved.add(mapping(model, entry.getKey(), ShipModelObjectType.CABIN, entry.getValue().getDeck(), entry.getValue()));
        }
        objects.saveAll(saved);
    }

    @Transactional
    public void activate(UUID shipId, UUID modelId) {
        lockShip(shipId);
        ShipModel model = model(shipId, modelId);
        JsonNode manifest = manifest(model);
        validatePersistedMappings(shipId, model, manifest);

        for (ShipModel other : models.findByShipShipIdAndModelKeyIsNotNullAndStatusOrderByCreatedAtDesc(shipId, ShipModelStatus.READY)) {
            if (!other.getShipModelId().equals(modelId)) {
                other.setStatus(ShipModelStatus.ARCHIVED);
                models.save(other);
            }
        }
        model.setStatus(ShipModelStatus.READY);
        models.save(model);
    }

    @Transactional(readOnly = true)
    public ShipModelAssetStore.AssetMetadata asset(UUID shipId, UUID modelId, String assetName) {
        ShipModel model = model(shipId, modelId);
        manifest(model);
        return assets.asset(model.getModelKey(), model.getAssetVersion(), assetName);
    }

    @Transactional(readOnly = true)
    public void writeAsset(UUID shipId, UUID modelId, String assetName, OutputStream output) {
        ShipModel model = model(shipId, modelId);
        assets.writeAsset(model.getModelKey(), model.getAssetVersion(), assetName, output);
    }

    private void validatePersistedMappings(UUID shipId, ShipModel model, JsonNode manifest) {
        for (ShipModelObject deckMapping : deckMappingObjects(model.getShipModelId())) {
            JsonNode modelDeck = assets.decks(manifest).stream()
                    .filter(deck -> deck.path("id").asText().equals(deckMapping.getNodeKey())).findFirst()
                    .orElseThrow(() -> bad("Unknown model deck mapping"));
            Deck actual = deckMapping.getDeck();
            if (!actual.getShip().getShipId().equals(shipId) || actual.getDeckNumber() != modelDeck.path("number").asInt()) {
                throw bad("A deck mapping no longer matches the model");
            }
        }
        Map<Integer, UUID> mappedDecks = new HashMap<>();
        for (ShipModelObject deckMapping : deckMappingObjects(model.getShipModelId())) {
            mappedDecks.put(deckMapping.getDeck().getDeckNumber(), deckMapping.getDeck().getDeckId());
        }
        for (ShipModelObject cabinMapping : cabinMappingObjects(model.getShipModelId())) {
            Cabin cabin = cabinMapping.getCabin();
            int expected = assets.cabinDeckNumber(manifest, cabinMapping.getNodeKey());
            if (!cabin.getShip().getShipId().equals(shipId) || cabin.getDeck().getDeckNumber() != expected ||
                    !Objects.equals(mappedDecks.get(expected), cabin.getDeck().getDeckId())) {
                throw bad("A cabin mapping no longer matches its ship or deck");
            }
        }
    }

    private ShipModelObject mapping(ShipModel model, String nodeId, ShipModelObjectType type, Deck deck, Cabin cabin) {
        ShipModelObject object = new ShipModelObject();
        object.setShipModel(model);
        object.setNodeKey(nodeId);
        object.setDisplayName(nodeId);
        object.setObjectType(type);
        object.setDeck(deck);
        object.setCabin(cabin);
        object.setScaleX(1.0);
        object.setScaleY(1.0);
        object.setScaleZ(1.0);
        return object;
    }

    private List<ShipModelObject> deckMappingObjects(UUID modelId) {
        return objects.findByShipModelShipModelId(modelId).stream()
                .filter(o -> o.getObjectType() == ShipModelObjectType.DECK && o.getDeck() != null).toList();
    }

    private List<ShipModelObject> cabinMappingObjects(UUID modelId) {
        return objects.findByShipModelShipModelId(modelId).stream()
                .filter(o -> o.getObjectType() == ShipModelObjectType.CABIN && o.getCabin() != null).toList();
    }

    private ShipModel editableModel(UUID shipId, UUID modelId) {
        ShipModel model = model(shipId, modelId);
        if (model.getStatus() == ShipModelStatus.READY || model.getStatus() == ShipModelStatus.ARCHIVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mappings can only be edited on a DRAFT model");
        }
        return model;
    }

    private ShipModel model(UUID shipId, UUID modelId) {
        ShipModel model = models.findById(modelId)
                .filter(m -> m.getShip().getShipId().equals(shipId) && m.getModelKey() != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Model not found on this ship"));
        manifest(model);
        return model;
    }

    private JsonNode manifest(ShipModel model) {
        JsonNode manifest = assets.manifest(model.getModelKey(), model.getAssetVersion());
        if (!Objects.equals(model.getAssetRevision(), manifest.path("revision").asText())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Manifest revision changed; register a new asset version");
        }
        return manifest;
    }

    private void lockShip(UUID shipId) {
        ships.findById(shipId).orElseThrow(() -> new ResourceNotFoundException("Ship", shipId));
        models.lockShip(shipId).orElseThrow(() -> new ResourceNotFoundException("Ship", shipId));
    }

    private static String apiStatus(ShipModel model) {
        return model.getStatus() == ShipModelStatus.READY ? "ACTIVE" : model.getStatus().name();
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
