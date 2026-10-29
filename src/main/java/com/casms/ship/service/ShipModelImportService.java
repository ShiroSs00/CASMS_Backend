package com.casms.ship.service;

import com.casms.common.exception.ResourceNotFoundException;
import com.casms.ship.dto.response.ModelImportIssueResponse;
import com.casms.ship.dto.response.ModelImportJobResponse;
import com.casms.ship.entity.*;
import com.casms.ship.enums.*;
import com.casms.ship.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ShipModelImportService {
    private final ShipRepository ships;
    private final ShipModelRepository models;
    private final ShipModelAssetStore assets;
    private final DeckRepository decks;
    private final CabinRepository cabins;
    private final ShipModelObjectRepository objects;
    private final ModelImportJobRepository jobs;
    private final ModelImportIssueRepository issues;
    private final ObjectMapper mapper;
    private final ModelImportJobLifecycle jobLifecycle;
    private final PlatformTransactionManager transactionManager;

    /** Imports the metadata manifest atomically for each valid object; absent objects are retained. */
    public ModelImportJobResponse importModel(UUID shipId, UUID modelId, String requestedBy) {
        ModelImportJob job = jobLifecycle.start(shipId, modelId, requestedBy);
        try {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            return transaction.execute(status -> importModelTransaction(shipId, modelId, job.getModelImportJobId()));
        } catch (RuntimeException failure) {
            try {
                jobLifecycle.fail(job.getModelImportJobId(), failure);
            } catch (RuntimeException trackingFailure) {
                failure.addSuppressed(trackingFailure);
            }
            throw failure;
        }
    }

    private ModelImportJobResponse importModelTransaction(UUID shipId, UUID modelId, UUID jobId) {
        models.lockShip(shipId).orElseThrow(() -> new ResourceNotFoundException("Ship", shipId));
        ShipModel model = model(shipId, modelId);
        if (model.getStatus() != ShipModelStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Model structure can only be imported while the model is DRAFT");
        }
        JsonNode manifest = assets.manifest(model.getModelKey(), model.getAssetVersion());
        if (!Objects.equals(model.getAssetRevision(), manifest.path("revision").asText())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Manifest revision changed; register a new asset version");
        }
        String glbPath = manifest.path("assets").path("full").asText();
        JsonNode glb = assets.glbDocument(model.getModelKey(), model.getAssetVersion(), glbPath);
        Map<String, JsonNode> glbNodes = indexAndValidateNodes(manifest, glb);
        validateManifestBusinessIdentities(manifest);

        ModelImportJob job = jobs.findById(jobId).orElseThrow(() -> new IllegalStateException("Import job disappeared"));
        int total = manifest.path("decks").size() + manifest.path("serviceDecks").size() + manifest.path("cabins").size();
        job.setTotalObjects(total);

        Ship ship = model.getShip();
        Set<String> deckIds = new HashSet<>();
        Map<String, Deck> resolvedDecks = new HashMap<>();
        for (JsonNode source : manifest.path("decks")) {
            String id = source.path("id").asText("");
            try {
                if (id.isBlank() || !deckIds.add(id)) throw new ObjectImportException("INVALID_DECK_ID", "Deck identifier is missing or duplicated");
                JsonNode glbNode = glbNodes.get(id);
                JsonNode nodeExtras = glbNode.path("extras");
                int number = requiredInt(nodeExtras, "deckNumber");
                String name = requiredText(nodeExtras, "deckName");
                double elevation = requiredDouble(nodeExtras, "elevation");
                if (!source.path("elevationMetres").isNumber() || number != source.path("number").asInt() ||
                        !name.equals(source.path("name").asText()) ||
                        Math.abs(elevation - source.path("elevationMetres").asDouble()) > 0.01) {
                    throw new ObjectImportException("MANIFEST_GLB_MISMATCH", "Deck metadata differs between the GLB and manifest");
                }
                int deckNumberLimit = ship.getDeckNumberLimit() == null ? ship.getTotalDecks() : ship.getDeckNumberLimit();
                if (number < 1 || number > deckNumberLimit) throw new ObjectImportException("INVALID_DECK_NUMBER", "Deck number is outside this ship's configured deck range");
                Deck deck = decks.findByShipShipIdAndDeckNumber(shipId, number).orElseGet(Deck::new);
                if (deck.getDeckId() == null) {
                    deck.setShip(ship);
                    deck.setDeckNumber(number);
                    deck.setDeckName(name);
                    deck.setPriceMultiplier(BigDecimal.ONE);
                }
                // Deck number is the stable business identity; the display name and
                // model elevation are model-managed and may change between versions.
                deck.setDeckName(name);
                deck.setModelPositionY(elevation);
                deck.setModelMetadataJson(json(source));
                deck = decks.save(deck);
                resolvedDecks.put(id, deck);
                saveMapping(model, id, name, ShipModelObjectType.DECK, deck, null, source,
                        0.0, elevation, 0.0, null, null, null);
                job.setDecksImported(job.getDecksImported() + 1);
                job.setObjectsSucceeded(job.getObjectsSucceeded() + 1);
            } catch (ObjectImportException ex) {
                recordIssue(job, id, "DECK", ex.code, ex.getMessage());
            }
        }

        // Persist technical levels as physical Deck rows too. Model areas/services are deliberately ignored.
        for (JsonNode source : manifest.path("serviceDecks")) {
            String id = source.path("id").asText("");
            try {
                if (id.isBlank() || !deckIds.add(id)) throw new ObjectImportException("INVALID_DECK_ID", "Service deck identifier is missing or duplicated");
                int number = requiredInt(source, "number");
                String name = requiredText(source, "name");
                double elevation = requiredDouble(source, "elevationMetres");
                JsonNode extras = glbNodes.get(id).path("extras");
                if (number > 0) throw new ObjectImportException("INVALID_DECK_NUMBER", "Service levels must have non-positive deck numbers");
                if (number != requiredInt(extras, "deckNumber") || !name.equals(requiredText(extras, "deckName"))
                        || Math.abs(elevation - requiredDouble(extras, "elevation")) > 0.01) {
                    throw new ObjectImportException("MANIFEST_GLB_MISMATCH", "Service deck metadata differs between the GLB and manifest");
                }
                Deck deck = decks.findByShipShipIdAndDeckNumber(shipId, number).orElseGet(Deck::new);
                if (deck.getDeckId() == null) {
                    deck.setShip(ship); deck.setDeckNumber(number); deck.setServiceLevel(true);
                    deck.setPriceMultiplier(BigDecimal.ONE);
                } else if (!deck.isServiceLevel()) {
                    throw new ObjectImportException("DECK_TYPE_CONFLICT", "An existing deck has a different level classification");
                }
                deck.setDeckName(name); deck.setModelPositionY(elevation); deck.setModelMetadataJson(json(source));
                deck = decks.save(deck);
                saveMapping(model, id, name, ShipModelObjectType.DECK, deck, null,
                        source, 0.0, elevation, 0.0, null, null, null);
                job.setDecksImported(job.getDecksImported() + 1);
                job.setObjectsSucceeded(job.getObjectsSucceeded() + 1);
            } catch (ObjectImportException ex) {
                recordIssue(job, id, "DECK", ex.code, ex.getMessage());
            }
        }

        Set<String> cabinIds = new HashSet<>();
        for (JsonNode source : manifest.path("cabins")) {
            String id = source.path("id").asText("");
            try {
                if (id.isBlank() || !cabinIds.add(id)) throw new ObjectImportException("INVALID_CABIN_ID", "Cabin identifier is missing or duplicated");
                String cabinNumber = requiredText(source, "cabinNumber");
                String sourceDeckId = requiredText(source, "deckId");
                JsonNode glbNode = glbNodes.get(id);
                JsonNode nodeExtras = glbNode.path("extras");
                if (!sourceDeckId.equals(nodeExtras.path("deckId").asText()) ||
                        !cabinNumber.equals(nodeExtras.path("cabinNumber").asText()) ||
                        !source.path("cabinType").asText().equals(nodeExtras.path("cabinType").asText()) ||
                        source.path("capacity").asInt(-1) != nodeExtras.path("capacity").asInt(-1)) {
                    throw new ObjectImportException("MANIFEST_GLB_MISMATCH", "Cabin metadata differs between the GLB and manifest");
                }
                Deck deck = resolvedDecks.get(sourceDeckId);
                if (deck == null) throw new ObjectImportException("UNKNOWN_DECK", "Cabin refers to a deck that was not imported");
                CabinType type;
                try {
                    type = CabinType.valueOf(requiredText(source, "cabinType"));
                } catch (IllegalArgumentException ex) {
                    throw new ObjectImportException("INVALID_CABIN_TYPE", "Cabin type is missing or is not supported by CASMS");
                }
                int capacity = requiredInt(source, "capacity");
                if (capacity < 1) throw new ObjectImportException("INVALID_CAPACITY", "Cabin capacity must be greater than zero");
                if (!source.path("isAccessible").isBoolean()) {
                    throw new ObjectImportException("MISSING_ACCESSIBILITY", "Cabin accessibility metadata is missing or invalid");
                }
                double[] position = vector(glbNode.path("translation"), "translation", 3);
                double[] size = vector(source.path("size"), "size", 3);
                Cabin cabin = cabins.findByShipShipIdAndCabinNumber(shipId, cabinNumber).orElseGet(Cabin::new);
                if (cabin.getCabinId() == null) {
                    cabin.setShip(ship);
                    cabin.setDeck(deck);
                    cabin.setCabinNumber(cabinNumber);
                    cabin.setStatus(CabinStatus.AVAILABLE); // Existing CabinService creation default.
                    cabin.setCapacity(capacity);
                    cabin.setIsAccessible(source.path("isAccessible").asBoolean(false));
                } else if (!Objects.equals(cabin.getDeck().getDeckId(), deck.getDeckId())) {
                    throw new ObjectImportException("CABIN_DECK_CONFLICT", "An existing cabin number is assigned to another deck");
                }
                // Cabin type and 3D geometry are model-managed (CabinService rejects
                // edits to those fields). Capacity/accessibility remain business-owned
                // for existing cabins and are initialized from the manifest only on create.
                cabin.setCabinType(type);
                cabin.setModelPositionX(position[0]);
                cabin.setModelPositionY(position[1]);
                cabin.setModelPositionZ(position[2]);
                cabin.setModelWidth(size[0]);
                cabin.setModelHeight(size[1]);
                cabin.setModelDepth(size[2]);
                JsonNode area = source.path("areaSquareMetres");
                if (area.isNumber()) cabin.setAreaSquareMeters(area.decimalValue());
                JsonNode balcony = source.path("balconySquareMetres");
                if (balcony.isNumber()) {
                    cabin.setBalconyAreaSquareMeters(balcony.decimalValue());
                    cabin.setHasBalcony(balcony.decimalValue().compareTo(BigDecimal.ZERO) > 0);
                }
                cabin = cabins.save(cabin);
                saveMapping(model, id, cabinNumber, ShipModelObjectType.CABIN, deck, cabin, source,
                        position[0], position[1], position[2], size[0], size[1], size[2]);
                job.setCabinsImported(job.getCabinsImported() + 1);
                job.setObjectsSucceeded(job.getObjectsSucceeded() + 1);
            } catch (ObjectImportException ex) {
                recordIssue(job, id, "CABIN", ex.code, ex.getMessage());
            }
        }

        job.setFinishedAt(Instant.now());
        job.setStatus(job.getObjectsFailed() == 0 ? ModelImportStatus.SUCCEEDED
                : job.getObjectsSucceeded() == 0 ? ModelImportStatus.FAILED : ModelImportStatus.PARTIAL);
        if (job.getObjectsFailed() > 0) job.setErrorMessage("One or more model objects could not be imported; see job issues");
        return response(jobs.save(job));
    }

    @Transactional(readOnly = true)
    public ModelImportJobResponse job(UUID shipId, UUID jobId) {
        ModelImportJob job = jobs.findById(jobId).filter(j -> j.getShipModel().getShip().getShipId().equals(shipId))
                .orElseThrow(() -> new ResourceNotFoundException("Model import job", jobId));
        return response(job);
    }

    @Transactional(readOnly = true)
    public List<ModelImportIssueResponse> issues(UUID shipId, UUID jobId) {
        job(shipId, jobId);
        return issues.findByJobModelImportJobIdOrderByCreatedAt(jobId).stream()
                .map(i -> new ModelImportIssueResponse(i.getModelImportIssueId(), i.getObjectIdentifier(), i.getObjectType(),
                        i.getErrorCode(), i.getMessage(), i.getCreatedAt())).toList();
    }

    @Transactional(readOnly = true)
    public List<ModelImportJobResponse> history(UUID shipId, UUID modelId) {
        model(shipId, modelId);
        return jobs.findByShipModelShipModelIdOrderByStartedAtDesc(modelId).stream().map(ShipModelImportService::response).toList();
    }

    private void saveMapping(ShipModel model, String id, String name, ShipModelObjectType type, Deck deck, Cabin cabin,
                             JsonNode metadata, double x, double y, double z, Double sx, Double sy, Double sz) {
        ShipModelObject object = objects.findByShipModelShipModelIdAndNodeKey(model.getShipModelId(), id)
                .orElseGet(ShipModelObject::new);
        if (object.getShipModelObjectId() == null) {
            object.setShipModel(model);
            object.setNodeKey(id);
            object.setScaleX(1.0);
            object.setScaleY(1.0);
            object.setScaleZ(1.0);
        } else if (object.getObjectType() != type) {
            throw new ObjectImportException("NODE_TYPE_CONFLICT", "Model node identifier is already mapped to another object type");
        }
        object.setDisplayName(name);
        object.setObjectType(type);
        object.setDeck(deck);
        object.setCabin(cabin);
        object.setPositionX(x);
        object.setPositionY(y);
        object.setPositionZ(z);
        // ShipModelObject scale is a transform multiplier, not physical dimensions.
        // The source size remains in metadataJson and Cabin.model_width/height/depth.
        object.setScaleX(1.0);
        object.setScaleY(1.0);
        object.setScaleZ(1.0);
        object.setMetadataJson(json(metadata));
        objects.save(object);
    }

    private void recordIssue(ModelImportJob job, String id, String type, String code, String message) {
        ModelImportIssue issue = new ModelImportIssue();
        issue.setJob(job);
        issue.setObjectIdentifier(id.isBlank() ? null : id);
        issue.setObjectType(type);
        issue.setErrorCode(code);
        issue.setMessage(message);
        issues.save(issue);
        job.setObjectsFailed(job.getObjectsFailed() + 1);
    }

    private ShipModel model(UUID shipId, UUID modelId) {
        return models.findById(modelId).filter(m -> m.getShip().getShipId().equals(shipId) && m.getModelKey() != null)
                .orElseThrow(() -> new ResourceNotFoundException("Ship model", modelId));
    }

    private static Map<String, JsonNode> indexAndValidateNodes(JsonNode manifest, JsonNode glb) {
        if (!glb.path("nodes").isArray()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GLB has no node hierarchy");
        Map<String, JsonNode> indexed = new HashMap<>();
        for (JsonNode node : glb.path("nodes")) {
            JsonNode extras = node.path("extras");
            String kind = extras.path("entityType").asText();
            if (!Set.of("deck", "serviceDeck", "cabin").contains(kind)) continue;
            String id = extras.path("modelId").asText("");
            if (id.isBlank() || indexed.putIfAbsent(id, node) != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GLB contains a missing or duplicate deck/cabin model identifier");
            }
        }
        Map<String, String> expected = new HashMap<>();
        for (JsonNode deck : manifest.path("decks")) expected.put(deck.path("id").asText(), "deck");
        for (JsonNode deck : manifest.path("serviceDecks")) expected.put(deck.path("id").asText(), "serviceDeck");
        for (JsonNode cabin : manifest.path("cabins")) expected.put(cabin.path("id").asText(), "cabin");
        if (!expected.keySet().equals(indexed.keySet())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GLB deck/cabin identifiers do not match the manifest");
        }
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            if (!entry.getValue().equals(indexed.get(entry.getKey()).path("extras").path("entityType").asText())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GLB object type does not match the manifest for " + entry.getKey());
            }
        }
        return indexed;
    }

    /** Reject ambiguous business identities before any Deck/Cabin or mapping is written. */
    private static void validateManifestBusinessIdentities(JsonNode manifest) {
        Set<Integer> deckNumbers = new HashSet<>();
        Set<String> deckIds = new HashSet<>();
        for (JsonNode deck : manifest.path("decks")) {
            int number = deck.path("number").asInt(Integer.MIN_VALUE);
            String id = deck.path("id").asText("");
            if (number == Integer.MIN_VALUE || !deckNumbers.add(number) || id.isBlank() || !deckIds.add(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Manifest contains a missing or duplicate passenger deck identity");
            }
        }
        for (JsonNode deck : manifest.path("serviceDecks")) {
            String id = deck.path("id").asText("");
            if (id.isBlank() || !deckIds.add(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Manifest contains a missing or duplicate service deck identity");
            }
        }
        Set<String> cabinNumbers = new HashSet<>();
        Set<String> cabinIds = new HashSet<>();
        for (JsonNode cabin : manifest.path("cabins")) {
            String number = cabin.path("cabinNumber").asText("").trim();
            String id = cabin.path("id").asText("");
            String deckId = cabin.path("deckId").asText("");
            if (number.isBlank() || !cabinNumbers.add(number) || id.isBlank() || !cabinIds.add(id) ||
                    deckId.isBlank() || !deckIds.contains(deckId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Manifest contains a missing, duplicate or invalid cabin identity/deck reference");
            }
        }
    }

    private static int requiredInt(JsonNode object, String field) {
        JsonNode value = object.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToInt()) throw new ObjectImportException("INVALID_" + field.toUpperCase(Locale.ROOT), "Required integer field is missing or invalid: " + field);
        return value.asInt();
    }

    private static double requiredDouble(JsonNode object, String field) {
        JsonNode value = object.path(field);
        if (!value.isNumber() || !Double.isFinite(value.asDouble())) throw new ObjectImportException("INVALID_" + field.toUpperCase(Locale.ROOT), "Required numeric field is missing or invalid: " + field);
        return value.asDouble();
    }

    private static String requiredText(JsonNode object, String field) {
        String value = object.path(field).asText("").trim();
        if (value.isEmpty()) throw new ObjectImportException("MISSING_" + field.toUpperCase(Locale.ROOT), "Required field is missing: " + field);
        return value;
    }

    private static double[] vector(JsonNode value, String field, int size) {
        if (!value.isArray() || value.size() != size) throw new ObjectImportException("INVALID_" + field.toUpperCase(Locale.ROOT), "Expected " + size + " numeric values for " + field);
        double[] result = new double[size];
        for (int i = 0; i < size; i++) {
            if (!value.get(i).isNumber() || !Double.isFinite(value.get(i).asDouble())) throw new ObjectImportException("INVALID_" + field.toUpperCase(Locale.ROOT), "Invalid numeric value in " + field);
            result[i] = value.get(i).asDouble();
        }
        return result;
    }

    private String json(JsonNode node) {
        try { return mapper.writeValueAsString(node); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("Cannot serialize validated model metadata", ex); }
    }

    private static ModelImportJobResponse response(ModelImportJob job) {
        return new ModelImportJobResponse(job.getModelImportJobId(), job.getShipModel().getShip().getShipId(),
                job.getShipModel().getShipModelId(), job.getStatus(), job.getStartedAt(), job.getFinishedAt(),
                job.getRequestedBy(), job.getTotalObjects(), job.getDecksImported(), job.getCabinsImported(),
                job.getObjectsSucceeded(), job.getObjectsFailed(), job.getObjectsSkipped(), job.getErrorMessage());
    }

    private static final class ObjectImportException extends RuntimeException {
        private final String code;
        private ObjectImportException(String code, String message) { super(message); this.code = code; }
    }
}
