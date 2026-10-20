package com.casms.ship.service;

import com.casms.common.exception.ResourceNotFoundException;
import com.casms.ship.dto.request.ShipModelAssetRegisterRequest;
import com.casms.ship.dto.request.ShipModelCabinMappingRequest;
import com.casms.ship.dto.request.ShipModelDeckMappingRequest;
import com.casms.ship.dto.response.ShipModelAssetResponse;
import com.casms.ship.dto.response.ShipModelAssetUploadResponse;
import com.casms.ship.entity.Cabin;
import com.casms.ship.entity.Deck;
import com.casms.ship.entity.Ship;
import com.casms.ship.entity.ShipModel;
import com.casms.ship.entity.ShipModelObject;
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
        lockShip(shipId);
        if (!assets.bundleExists(request.modelKey(), request.assetVersion())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Model asset bundle is not uploaded to the database");
        }
        JsonNode manifest = assets.manifest(request.modelKey(), request.assetVersion());
        if (models.existsByShipShipIdAndModelKeyAndAssetVersion(shipId, request.modelKey(), request.assetVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Model asset version is already registered on this ship");
        }

        Ship ship = ships.findById(shipId).orElseThrow(() -> new ResourceNotFoundException("Ship", shipId));
        ShipModel model = new ShipModel();
        model.setShip(ship);
        model.setModelKey(request.modelKey());
        model.setAssetVersion(request.assetVersion());
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
                deckNumbers.size(), mappedDecks, deckNumbers);
    }

    @Transactional(readOnly = true)
    public ShipModelAssetResponse active(UUID shipId, String contextPath) {
        ShipModel model = models.findByShipShipIdAndModelKeyIsNotNullAndStatusOrderByCreatedAtDesc(shipId, ShipModelStatus.READY)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No active model on this ship"));
        return detail(shipId, model.getShipModelId(), contextPath);
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
