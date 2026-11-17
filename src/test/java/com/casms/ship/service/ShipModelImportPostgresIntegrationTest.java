package com.casms.ship.service;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.casms.identity.entity.User;
import com.casms.identity.enums.Role;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.repository.UserRepository;
import com.casms.security.jwt.JwtService;
import com.casms.ship.dto.request.ShipCreateRequest;
import com.casms.ship.entity.Cabin;
import com.casms.ship.entity.Deck;
import com.casms.ship.entity.Ship;
import com.casms.ship.entity.ShipModel;
import com.casms.ship.enums.CabinType;
import com.casms.ship.enums.ShipModelStatus;
import com.casms.ship.enums.ShipStatus;
import com.casms.ship.repository.CabinRepository;
import com.casms.ship.repository.DeckRepository;
import com.casms.ship.repository.ModelImportJobRepository;
import com.casms.ship.repository.ShipModelObjectRepository;
import com.casms.ship.repository.ShipModelRepository;
import com.casms.ship.repository.ShipRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Real PostgreSQL + Spring MVC import workflow tests. No importer, storage, security or
 * repositories are mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ShipModelImportPostgresIntegrationTest {
    private static final String ASSET_PREFIX = "model-assets/";

    @Autowired MockMvc mockMvc;
    @Autowired ShipModelAssetService assetService;
    @Autowired ShipService shipService;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired ShipRepository ships;
    @Autowired ShipModelRepository models;
    @Autowired ModelImportJobRepository jobs;
    @Autowired DeckRepository decks;
    @Autowired CabinRepository cabins;
    @Autowired ShipModelObjectRepository objects;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired com.casms.servicepackage.repository.ServiceRepository catalogueServices;
    @Autowired org.flywaydb.core.Flyway flyway;

    private final List<UUID> testShipIds = new ArrayList<>();
    private final List<UUID> testTypeIds = new ArrayList<>();
    private final List<UUID> testUserIds = new ArrayList<>();
    private final Set<String> uploadedBundles = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Map<String, String> storedVersions = new java.util.concurrent.ConcurrentHashMap<>();
    private final List<Path> tempArchives = new ArrayList<>();
    private final List<UUID> testServiceIds = new ArrayList<>();

    @Test
    void importByCodeUpdatesReadyModelAndPreservesAreaAssignments() throws Exception {
        UUID shipId = createShip(1).getShipId();
        String admin = token(Role.ADMIN);
        String key = uniqueKey("by-code");
        Path original = bundleWithPhysicalAreas(key, "v1", "valid");
        UUID modelId = UUID.fromString(importByCode(shipId, key, original, admin, 200)
                .path("data").path("modelId").asText());
        var lounge = objects.findByShipModelShipModelIdAndNodeKey(modelId, "physical-lounge").orElseThrow();
        var gym = objects.findByShipModelShipModelIdAndNodeKey(modelId, "physical-gym").orElseThrow();
        mockMvc.perform(post("/ships/{shipId}/models/{modelId}/activate", shipId, modelId)
                .header("Authorization", "Bearer " + admin)).andExpect(status().isOk());

        var service = new com.casms.servicepackage.entity.Service();
        service.setServiceCode("IT-" + UUID.randomUUID());
        service.setServiceName("Import test service");
        service.setServiceType("DINING");
        service.setStatus(com.casms.servicepackage.enums.ServiceStatus.ACTIVE);
        UUID serviceId = catalogueServices.saveAndFlush(service).getServiceId();
        testServiceIds.add(serviceId);
        UUID assignment = UUID.randomUUID();
        jdbc.update("INSERT INTO \"AreaServiceAssignment\" (assignment_id,area_object_id,service_id) VALUES (?,?,?)",
                assignment, lounge.getShipModelObjectId(), serviceId);
        try {
            Path changed = bundleWithPhysicalAreas(key, "v2", "moved");
            upload(key, "v2", changed, admin);
            mockMvc.perform(post("/ships/{shipId}/models", shipId)
                            .header("Authorization", "Bearer " + admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsBytes(Map.of("modelKey", key))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.id").value(modelId.toString()));
            assertEquals(ShipModelStatus.READY, models.findById(modelId).orElseThrow().getStatus());
            assertEquals(1, models.findByShipShipIdOrderByCreatedAtDesc(shipId).size());
            assertEquals(lounge.getShipModelObjectId(), objects.findByShipModelShipModelIdAndNodeKey(
                    modelId, "physical-lounge").orElseThrow().getShipModelObjectId());
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM \"AreaServiceAssignment\" WHERE assignment_id=? AND area_object_id=?",
                    Integer.class, assignment, lounge.getShipModelObjectId()));
            assertEquals(7.0, objects.findById(gym.getShipModelObjectId()).orElseThrow().getPositionX());
            String version = models.findById(modelId).orElseThrow().getAssetVersion();
            importByCode(shipId, key, changed, admin, 200);
            assertEquals(version, models.findById(modelId).orElseThrow().getAssetVersion());
            importByCode(shipId, key, bundleWithPhysicalAreas(key, "bad", "invalid-size"), admin, 422);
            assertEquals(version, models.findById(modelId).orElseThrow().getAssetVersion());
            assertEquals(7.0, objects.findById(gym.getShipModelObjectId()).orElseThrow().getPositionX());
        } finally {
            jdbc.update("DELETE FROM \"AreaServiceAssignment\" WHERE assignment_id=?", assignment);
        }
    }

    private JsonNode importByCode(UUID shipId, String key, Path bundle, String token, int expected)
            throws Exception {
        uploadedBundles.add(key + "|zip-" + sha256(Files.readAllBytes(bundle)));
        return mapper.readTree(mockMvc.perform(multipart("/ships/{shipId}/models/imports", shipId)
                        .file(new FileBackedMultipartFile("bundle", bundle))
                        .param("modelKey", key).header("Authorization", "Bearer " + token))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    @Test
    void physicalAreasPersistAsFacilitiesAndReadBackWithoutCreatingServices() throws Exception {
        Ship ship = createShip(1);
        String token = token(Role.ADMIN);
        String key = uniqueKey("physical-areas");
        long definitions = catalogueServices.count();
        upload(key, "v1", bundleWithPhysicalAreas(key, "v1", "valid"), token);
        UUID model = register(ship.getShipId(), key, "v1", token);
        JsonNode job = importJob(ship.getShipId(), model, token).path("data");
        assertEquals("SUCCEEDED", job.path("status").asText());
        assertEquals(5, job.path("totalObjects").asInt());
        assertEquals(5, job.path("objectsSucceeded").asInt());
        var all = objects.findByShipModelShipModelId(model);
        var facilities =
                all.stream()
                        .filter(
                                o ->
                                        o.getObjectType()
                                                == com.casms.ship.enums.ShipModelObjectType
                                                        .FACILITY)
                        .toList();
        assertEquals(2, facilities.size());
        assertEquals(1, cabins.countByShipShipId(ship.getShipId()));
        for (var facility : facilities) {
            assertNull(facility.getCabin());
            assertNotNull(facility.getDeck());
            Deck physicalDeck = decks.findById(facility.getDeck().getDeckId()).orElseThrow();
            assertEquals(ship.getShipId(), physicalDeck.getShip().getShipId());
            var metadata = mapper.readTree(facility.getMetadataJson());
            assertEquals(3, metadata.path("size").size());
            assertEquals(16, metadata.path("worldMatrix").size());
            assertEquals(
                    "area", metadata.path("gltfNode").path("extras").path("entityType").asText());
        }
        var gym =
                facilities.stream()
                        .filter(o -> o.getNodeKey().equals("physical-gym"))
                        .findFirst()
                        .orElseThrow();
        assertTrue(decks.findById(gym.getDeck().getDeckId()).orElseThrow().isServiceLevel());
        assertEquals(3.0, gym.getPositionX(), 1e-9);
        assertEquals(Math.PI / 2, gym.getRotationY(), 1e-9);
        assertEquals(2.0, gym.getScaleX(), 1e-9);
        Set<UUID> ids = new HashSet<>();
        facilities.forEach(f -> ids.add(f.getShipModelObjectId()));
        importJob(ship.getShipId(), model, token);
        assertEquals(
                ids,
                objects.findByShipModelShipModelId(model).stream()
                        .filter(
                                o ->
                                        o.getObjectType()
                                                == com.casms.ship.enums.ShipModelObjectType
                                                        .FACILITY)
                        .map(com.casms.ship.entity.ShipModelObject::getShipModelObjectId)
                        .collect(java.util.stream.Collectors.toSet()));
        assertEquals(definitions, catalogueServices.count());
        mockMvc.perform(
                        get("/ships/{shipId}/models/{modelId}/objects", ship.getShipId(), model)
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(5)));
        mockMvc.perform(
                        post("/ships/{shipId}/models/{modelId}/activate", ship.getShipId(), model)
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        JsonNode config =
                mapper.readTree(
                                mockMvc.perform(
                                                get(
                                                                "/ships/{shipId}/3d-configuration",
                                                                ship.getShipId())
                                                        .header("Authorization", "Bearer " + token))
                                        .andExpect(status().isOk())
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsByteArray())
                        .path("data");
        int count = 0;
        for (JsonNode d : config.path("decks"))
            for (JsonNode f : d.path("facilities")) {
                count++;
                assertEquals(d.path("deckId").asText(), f.path("deckId").asText());
                assertTrue(f.path("dimensions").path("x").asDouble() > 0);
            }
        assertEquals(2, count);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {"unknown-deck", "invalid-size", "invalid-transform"})
    void invalidAreaRecordsAnIssueWithoutCreatingFalseCabins(String mode) throws Exception {
        Ship ship = createShip(1);
        String token = token(Role.ADMIN);
        String key = uniqueKey("area-partial");
        upload(key, "v1", bundleWithPhysicalAreas(key, "v1", mode), token);
        UUID model = register(ship.getShipId(), key, "v1", token);
        JsonNode result = importJob(ship.getShipId(), model, token).path("data");
        assertEquals("PARTIAL", result.path("status").asText());
        assertEquals(1, result.path("objectsFailed").asInt());
        assertEquals(4, result.path("objectsSucceeded").asInt());
        assertEquals(1, cabins.countByShipShipId(ship.getShipId()));
        assertEquals(
                1,
                objects.findByShipModelShipModelId(model).stream()
                        .filter(
                                o ->
                                        o.getObjectType()
                                                == com.casms.ship.enums.ShipModelObjectType
                                                        .FACILITY)
                        .count());
        mockMvc.perform(
                        get(
                                        "/ships/{shipId}/imports/{jobId}/issues",
                                        ship.getShipId(),
                                        UUID.fromString(result.path("jobId").asText()))
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].objectType").value("FACILITY"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"duplicate-node", "missing-node"})
    void ambiguousPhysicalStructureFailsBeforeWritingDecks(String mode) throws Exception {
        Ship ship = createShip(1);
        String token = token(Role.ADMIN);
        String key = uniqueKey("area-identity");
        upload(key, "v1", bundleWithPhysicalAreas(key, "v1", mode), token);
        UUID model = register(ship.getShipId(), key, "v1", token);
        mockMvc.perform(
                        post("/ships/{shipId}/models/{modelId}/imports", ship.getShipId(), model)
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        assertEquals(0, decks.countByShipShipId(ship.getShipId()));
        assertEquals(0, objects.findByShipModelShipModelId(model).size());
    }

    @Test
    void databaseFailureDuringAreaPersistenceRollsBackAllImportedStructure() throws Exception {
        Ship ship = createShip(1);
        String token = token(Role.ADMIN);
        String key = uniqueKey("area-rollback");
        upload(key, "v1", bundleWithPhysicalAreas(key, "v1", "valid"), token);
        UUID model = register(ship.getShipId(), key, "v1", token);
        String trigger = "it_area_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute(
                "CREATE FUNCTION "
                        + trigger
                        + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION"
                        + " 'injected physical area persistence failure'; END; $$");
        try {
            jdbc.execute(
                    "CREATE TRIGGER "
                            + trigger
                            + " BEFORE INSERT ON \"ShipModelObject\" FOR EACH ROW WHEN"
                            + " (NEW.ship_model_id='"
                            + model
                            + "'::uuid AND NEW.node_key='physical-lounge') EXECUTE FUNCTION "
                            + trigger
                            + "()");
            mockMvc.perform(
                            post(
                                            "/ships/{shipId}/models/{modelId}/imports",
                                            ship.getShipId(),
                                            model)
                                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().is5xxServerError());
            assertEquals(0, objects.findByShipModelShipModelId(model).size());
            assertEquals(0, decks.countByShipShipId(ship.getShipId()));
            assertEquals(0, cabins.countByShipShipId(ship.getShipId()));
            assertEquals(
                    com.casms.ship.enums.ModelImportStatus.FAILED,
                    jobs.findByShipModelShipModelIdOrderByStartedAtDesc(model)
                            .getFirst()
                            .getStatus());
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS " + trigger + " ON \"ShipModelObject\"");
            jdbc.execute("DROP FUNCTION " + trigger + "()");
        }
    }

    @Test
    void importsEveryDeckIgnoresModelServicesAndSupportsManualAssignments() throws Exception {
        Ship ship = createShip(1);
        String manager = token(Role.ADMIN);
        String serviceManager = token(Role.ACTIVITY_SERVICE_MANAGER);
        String key = uniqueKey("all-decks");
        long serviceCount = catalogueServices.count();
        upload(key, "v1", bundleWithServiceDeck(key, "v1", "M-001"), manager);
        UUID modelId = register(ship.getShipId(), key, "v1", manager);
        JsonNode result = importJob(ship.getShipId(), modelId, manager).path("data");
        assertEquals("SUCCEEDED", result.path("status").asText());
        assertEquals(2, result.path("decksImported").asInt());
        assertEquals(3, result.path("totalObjects").asInt());
        assertEquals(
                serviceCount,
                catalogueServices.count(),
                "model venue names must not create catalogue services");
        Deck serviceDeck = decks.findByShipShipIdAndDeckNumber(ship.getShipId(), 0).orElseThrow();
        assertTrue(serviceDeck.isServiceLevel());
        assertEquals(-2.0, serviceDeck.getModelPositionY());
        assertEquals(
                "mini-d00",
                mapper.readTree(serviceDeck.getModelMetadataJson()).path("id").asText());
        assertEquals(1, decks.countByShipShipIdAndServiceLevelFalse(ship.getShipId()));
        assertEquals(2, decks.countByShipShipId(ship.getShipId()));
        assertEquals(3, objects.findByShipModelShipModelId(modelId).size());
        mockMvc.perform(
                        post("/ships/{shipId}/validate", ship.getShipId())
                                .header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk());
        mockMvc.perform(
                        get("/decks/by-ship/{shipId}", ship.getShipId())
                                .header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
        mockMvc.perform(
                        post("/cabins")
                                .header("Authorization", "Bearer " + manager)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        mapper.writeValueAsBytes(
                                                Map.of(
                                                        "deckId",
                                                        serviceDeck.getDeckId(),
                                                        "cabinNumber",
                                                        "C00",
                                                        "cabinType",
                                                        "INTERIOR",
                                                        "capacity",
                                                        2))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CABIN_PASSENGER_DECK_REQUIRED"));
        mockMvc.perform(
                        get(
                                        "/ships/{shipId}/models/{modelId}/deck-mappings",
                                        ship.getShipId(),
                                        modelId)
                                .header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
        var catalogue = new com.casms.servicepackage.entity.Service();
        catalogue.setServiceCode("IT-SV-" + UUID.randomUUID().toString().substring(0, 12));
        catalogue.setServiceName("Manual restaurant");
        catalogue.setServiceType("DINING");
        catalogue.setStatus(com.casms.servicepackage.enums.ServiceStatus.ACTIVE);
        catalogue = catalogueServices.saveAndFlush(catalogue);
        UUID serviceId = catalogue.getServiceId();
        testServiceIds.add(serviceId);
        MvcResult assigned =
                mockMvc.perform(
                                put(
                                                "/decks/{deckId}/services/{serviceId}",
                                                serviceDeck.getDeckId(),
                                                serviceId)
                                        .header("Authorization", "Bearer " + serviceManager))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.serviceId").value(serviceId.toString()))
                        .andReturn();
        String assignment =
                mapper.readTree(assigned.getResponse().getContentAsByteArray())
                        .path("data")
                        .path("assignmentId")
                        .asText();
        mockMvc.perform(
                        put(
                                        "/decks/{deckId}/services/{serviceId}",
                                        serviceDeck.getDeckId(),
                                        serviceId)
                                .header("Authorization", "Bearer " + serviceManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assignmentId").value(assignment));
        // Reimport does not duplicate decks or erase manual service assignments.
        importJob(ship.getShipId(), modelId, manager);
        assertEquals(
                serviceDeck.getDeckId(),
                decks.findByShipShipIdAndDeckNumber(ship.getShipId(), 0).orElseThrow().getDeckId());
        mockMvc.perform(
                        get("/decks/{deckId}/services", serviceDeck.getDeckId())
                                .header("Authorization", "Bearer " + serviceManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
        upload(key, "v2", bundleWithServiceDeck(key, "v2", "M-001"), manager);
        UUID nextModel = register(ship.getShipId(), key, "v2", manager);
        assertEquals(
                "SUCCEEDED",
                importJob(ship.getShipId(), nextModel, manager)
                        .path("data")
                        .path("status")
                        .asText());
        assertEquals(2, decks.countByShipShipId(ship.getShipId()));
        mockMvc.perform(
                        get("/decks/{deckId}/services", serviceDeck.getDeckId())
                                .header("Authorization", "Bearer " + serviceManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].assignmentId").value(assignment));
        mockMvc.perform(
                        put(
                                        "/decks/{deckId}/services/{serviceId}",
                                        serviceDeck.getDeckId(),
                                        serviceId)
                                .header("Authorization", "Bearer " + manager))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        put("/decks/{deckId}/services/{serviceId}", UUID.randomUUID(), serviceId)
                                .header("Authorization", "Bearer " + serviceManager))
                .andExpect(status().isNotFound());
        mockMvc.perform(
                        put(
                                        "/decks/{deckId}/services/{serviceId}",
                                        serviceDeck.getDeckId(),
                                        UUID.randomUUID())
                                .header("Authorization", "Bearer " + serviceManager))
                .andExpect(status().isNotFound());
        // All physical levels appear once, with usable UUIDs in the configuration.
        mockMvc.perform(
                        post("/ships/{shipId}/models/{modelId}/activate", ship.getShipId(), modelId)
                                .header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk());
        JsonNode config =
                mapper.readTree(
                                mockMvc.perform(
                                                get(
                                                                "/ships/{shipId}/3d-configuration",
                                                                ship.getShipId())
                                                        .header(
                                                                "Authorization",
                                                                "Bearer " + manager))
                                        .andExpect(status().isOk())
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsByteArray())
                        .path("data")
                        .path("decks");
        assertEquals(2, config.size());
        assertTrue(
                java.util.stream.StreamSupport.stream(config.spliterator(), false)
                        .anyMatch(
                                d ->
                                        d.path("serviceLevel").asBoolean()
                                                && serviceDeck
                                                        .getDeckId()
                                                        .toString()
                                                        .equals(d.path("deckId").asText())));
        for (int i = 0; i < 2; i++)
            mockMvc.perform(
                            delete(
                                            "/decks/{deckId}/services/{serviceId}",
                                            serviceDeck.getDeckId(),
                                            serviceId)
                                    .header("Authorization", "Bearer " + serviceManager))
                    .andExpect(status().isNoContent());
        assertTrue(catalogueServices.existsById(serviceId));
        flyway.validate();
    }

    @Test
    void serviceDeckWritesRollbackWhenCabinPersistenceFails() throws Exception {
        Ship ship = createShip(1);
        String manager = token(Role.ADMIN);
        String key = uniqueKey("deck-rollback");
        upload(key, "v1", bundleWithServiceDeck(key, "v1", "CABIN-NUM-TOO-LONG"), manager);
        UUID modelId = register(ship.getShipId(), key, "v1", manager);
        mockMvc.perform(
                        post("/ships/{shipId}/models/{modelId}/imports", ship.getShipId(), modelId)
                                .header("Authorization", "Bearer " + manager))
                .andExpect(status().isConflict());
        assertEquals(0, decks.countByShipShipId(ship.getShipId()));
        assertEquals(0, objects.findByShipModelShipModelId(modelId).size());
        assertEquals(
                com.casms.ship.enums.ModelImportStatus.FAILED,
                jobs.findByShipModelShipModelIdOrderByStartedAtDesc(modelId)
                        .getFirst()
                        .getStatus());
    }

    @Test
    void concurrentManualAssignmentsHaveOneDatabaseIdentity() throws Exception {
        Ship ship = createShip(1);
        String manager = token(Role.ADMIN);
        String serviceManager = token(Role.ACTIVITY_SERVICE_MANAGER);
        String key = uniqueKey("deck-link");
        upload(key, "v1", bundleWithServiceDeck(key, "v1", "M-001"), manager);
        UUID model = register(ship.getShipId(), key, "v1", manager);
        importJob(ship.getShipId(), model, manager);
        UUID deck =
                decks.findByShipShipIdAndDeckNumber(ship.getShipId(), 0).orElseThrow().getDeckId();
        var service = new com.casms.servicepackage.entity.Service();
        service.setServiceCode("IT-SV-" + UUID.randomUUID().toString().substring(0, 12));
        service.setServiceName("Manual gym");
        service.setServiceType("FITNESS");
        service.setStatus(com.casms.servicepackage.enums.ServiceStatus.INACTIVE);
        UUID serviceId = catalogueServices.saveAndFlush(service).getServiceId();
        testServiceIds.add(serviceId);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<String> assign =
                    () ->
                            mapper.readTree(
                                            mockMvc.perform(
                                                            put(
                                                                            "/decks/{deckId}/services/{serviceId}",
                                                                            deck,
                                                                            serviceId)
                                                                    .header(
                                                                            "Authorization",
                                                                            "Bearer "
                                                                                    + serviceManager))
                                                    .andExpect(status().isOk())
                                                    .andReturn()
                                                    .getResponse()
                                                    .getContentAsByteArray())
                                    .path("data")
                                    .path("assignmentId")
                                    .asText();
            var first = executor.submit(assign);
            var second = executor.submit(assign);
            assertEquals(first.get(), second.get());
        } finally {
            executor.shutdownNow();
        }
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM \"DeckServiceAssignment\" WHERE deck_id=? AND"
                            + " service_id=?",
                        Integer.class,
                        deck,
                        serviceId));
    }

    @Test
    void shipDeckCabinReadAndReadinessRoutesRequireTheirPermissions() throws Exception {
        Ship ship = createShip(2);
        String shipManager = token(Role.SHIP_MANAGER);
        String staffWithoutShipRead = token(Role.FINANCE_OFFICER);
        mockMvc.perform(get("/ships/{shipId}", ship.getShipId())
                        .header("Authorization", "Bearer " + shipManager))
                .andExpect(status().isForbidden());
        ship.setCreatedByShipManagerId(jwt.extractUserId(shipManager));
        ships.saveAndFlush(ship);

        mockMvc.perform(
                        get("/ships/{shipId}", ship.getShipId())
                                .header("Authorization", "Bearer " + shipManager))
                .andExpect(status().isOk());
        mockMvc.perform(
                        get("/ships/{shipId}", ship.getShipId())
                                .header("Authorization", "Bearer " + staffWithoutShipRead))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        get("/decks/by-ship/{shipId}", ship.getShipId())
                                .header("Authorization", "Bearer " + shipManager))
                .andExpect(status().isOk());
        mockMvc.perform(
                        get("/decks/by-ship/{shipId}", ship.getShipId())
                                .header("Authorization", "Bearer " + staffWithoutShipRead))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        get("/cabins/by-ship/{shipId}", ship.getShipId())
                                .header("Authorization", "Bearer " + shipManager))
                .andExpect(status().isOk());
        mockMvc.perform(
                        get("/cabins/by-ship/{shipId}", ship.getShipId())
                                .header("Authorization", "Bearer " + staffWithoutShipRead))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        get("/ships/{shipId}/readiness-assessment", ship.getShipId())
                                .header("Authorization", "Bearer " + shipManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readiness").value("NOT_READY"))
                .andExpect(jsonPath("$.data.deckCount").value(0))
                .andExpect(jsonPath("$.data.cabinCount").value(0));
        mockMvc.perform(
                        get("/ships/{shipId}/readiness-assessment", ship.getShipId())
                                .header("Authorization", "Bearer " + staffWithoutShipRead))
                .andExpect(status().isForbidden());
    }

    @Test
    void modelWritesBelongToAdminAndOperationalStatusToShipManager() throws Exception {
        Ship ship = createShip(1);
        String admin = token(Role.ADMIN),
                manager = token(Role.SHIP_MANAGER),
                key = uniqueKey("rbac-model");
        ship.setCreatedByShipManagerId(jwt.extractUserId(manager));
        ships.saveAndFlush(ship);
        Path zip = smallBundle(key, "v1", "OCEAN_VIEW", "M-RBAC", true);
        assertEquals(403, uploadStatus(key, "v1", zip, manager));
        upload(key, "v1", zip, admin);
        UUID model = register(ship.getShipId(), key, "v1", admin);
        mockMvc.perform(
                        post("/ships/{ship}/models/{model}/imports", ship.getShipId(), model)
                                .header("Authorization", "Bearer " + manager))
                .andExpect(status().isForbidden());
        for (String kind : List.of("deck-mappings", "cabin-mappings"))
            mockMvc.perform(
                            put("/ships/{ship}/models/{model}/" + kind, ship.getShipId(), model)
                                    .header("Authorization", "Bearer " + manager)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("[]"))
                    .andExpect(status().isForbidden());
        for (String action : List.of("activate", "ready", "archive"))
            mockMvc.perform(
                            post("/ships/{ship}/models/{model}/" + action, ship.getShipId(), model)
                                    .header("Authorization", "Bearer " + manager))
                    .andExpect(status().isForbidden());
        assertEquals(
                "SUCCEEDED",
                importJob(ship.getShipId(), model, admin).path("data").path("status").asText());
        mockMvc.perform(
                        post("/ships/{ship}/models/{model}/activate", ship.getShipId(), model)
                                .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        mockMvc.perform(
                        post("/ships/{ship}/status", ship.getShipId())
                                .param("status", "MAINTENANCE")
                                .header("Authorization", "Bearer " + admin))
                .andExpect(status().isForbidden());
        mockMvc.perform(
                        post("/ships/{ship}/status", ship.getShipId())
                                .param("status", "MAINTENANCE")
                                .header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk());
    }

    @Test
    void committedZaandamBundle_uploadImportReadAndRetry_isIdempotent() throws Exception {
        Ship ship = createShip(10);
        String token = token(Role.ADMIN);
        String version = "it-" + UUID.randomUUID().toString().substring(0, 8);
        Path archive = committedBundle(version);
        try {
            upload("zaandam", version, archive, token);
            UUID modelId = register(ship.getShipId(), "zaandam", version, token);

            Ship synchronizedShip = ships.findById(ship.getShipId()).orElseThrow();
            assertEquals("zaandam", synchronizedShip.getManifestModelId());
            assertEquals(ship.getShipName(), synchronizedShip.getShipName());
            assertEquals(582, synchronizedShip.getTotalCapacity());
            assertEquals(10, synchronizedShip.getTotalDecks());
            assertEquals(237.7, synchronizedShip.getLengthMeters());
            assertEquals(32.25, synchronizedShip.getBeamMeters());

            JsonNode first = importJob(ship.getShipId(), modelId, token);
            assertEquals("SUCCEEDED", first.path("data").path("status").asText());
            UUID firstJobId = UUID.fromString(first.path("data").path("jobId").asText());
            assertEquals(367, first.path("data").path("totalObjects").asInt());
            assertEquals(12, first.path("data").path("decksImported").asInt());
            assertEquals(291, first.path("data").path("cabinsImported").asInt());
            assertEquals(367, first.path("data").path("objectsSucceeded").asInt());
            assertEquals(0, first.path("data").path("objectsFailed").asInt());

            assertEquals(12, decks.countByShipShipId(ship.getShipId()));
            assertEquals(291, cabins.countByShipShipId(ship.getShipId()));
            assertEquals(367, objects.findByShipModelShipModelId(modelId).size());
            assertEquals(
                    64,
                    objects.findByShipModelShipModelId(modelId).stream()
                            .filter(
                                    o ->
                                            o.getObjectType()
                                                            == com.casms.ship.enums
                                                                    .ShipModelObjectType.FACILITY
                                                    && o.getDeck() != null
                                                    && o.getCabin() == null)
                            .count());
            assertEquals(
                    291L,
                    objects.findByShipModelShipModelId(modelId).stream()
                            .filter(o -> o.getCabin() != null)
                            .count());

            mockMvc.perform(
                            get(
                                            "/ships/{shipId}/models/{modelId}/imports",
                                            ship.getShipId(),
                                            modelId)
                                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(1)));
            mockMvc.perform(
                            get(
                                            "/ships/{shipId}/imports/{jobId}/issues",
                                            ship.getShipId(),
                                            firstJobId)
                                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(0)));
            mockMvc.perform(
                            get(
                                            "/ships/{shipId}/models/{modelId}/objects",
                                            ship.getShipId(),
                                            modelId)
                                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(367)));

            Ship otherShip = createShip(10);
            mockMvc.perform(
                            get(
                                            "/ships/{shipId}/imports/{jobId}",
                                            otherShip.getShipId(),
                                            firstJobId)
                                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isNotFound());
            mockMvc.perform(
                            get(
                                            "/ships/{shipId}/imports/{jobId}/issues",
                                            otherShip.getShipId(),
                                            firstJobId)
                                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isNotFound());
            mockMvc.perform(
                            post(
                                            "/ships/{shipId}/models/{modelId}/imports",
                                            otherShip.getShipId(),
                                            modelId)
                                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isNotFound());

            JsonNode retry = importJob(ship.getShipId(), modelId, token);
            assertEquals("SUCCEEDED", retry.path("data").path("status").asText());
            assertEquals(367, retry.path("data").path("objectsSucceeded").asInt());
            assertEquals(12, decks.countByShipShipId(ship.getShipId()));
            assertEquals(291, cabins.countByShipShipId(ship.getShipId()));
            assertEquals(367, objects.findByShipModelShipModelId(modelId).size());
            mockMvc.perform(
                            get(
                                            "/ships/{shipId}/models/{modelId}/imports",
                                            ship.getShipId(),
                                            modelId)
                                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(2)));

            ShipModel model = models.findById(modelId).orElseThrow();
            model.setStatus(ShipModelStatus.READY);
            models.saveAndFlush(model);
            mockMvc.perform(
                            post(
                                            "/ships/{shipId}/models/{modelId}/imports",
                                            ship.getShipId(),
                                            modelId)
                                    .header("Authorization", "Bearer " + token))
                    .andExpect(status().isConflict());
            String passengerToken = token(Role.PASSENGER);
            mockMvc.perform(
                            post(
                                            "/ships/{shipId}/models/{modelId}/imports",
                                            ship.getShipId(),
                                            modelId)
                                    .header("Authorization", "Bearer " + passengerToken))
                    .andExpect(status().isForbidden());
            String noShipReadToken = token(Role.FINANCE_OFFICER);
            mockMvc.perform(
                            get("/ships/{shipId}/imports/{jobId}", ship.getShipId(), firstJobId)
                                    .header("Authorization", "Bearer " + noShipReadToken))
                    .andExpect(status().isForbidden());
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    @Test
    void smallDatabaseBundles_recordObjectIssuesAndRollbackWholeTransaction() throws Exception {
        Ship ship = createShip(10);
        String token = token(Role.ADMIN);

        String modelKey = uniqueKey("small-bundles");
        String partialKey = modelKey;
        String partialVersion = "partial-v1";
        upload(
                partialKey,
                partialVersion,
                smallBundle(partialKey, partialVersion, "INVALID_TYPE", "M-001", true),
                token);
        UUID partialModel = register(ship.getShipId(), partialKey, partialVersion, token);
        JsonNode partial = importJob(ship.getShipId(), partialModel, token);
        assertEquals("PARTIAL", partial.path("data").path("status").asText());
        assertEquals(1, partial.path("data").path("objectsSucceeded").asInt());
        assertEquals(1, partial.path("data").path("objectsFailed").asInt());
        UUID partialJob = UUID.fromString(partial.path("data").path("jobId").asText());
        mockMvc.perform(
                        get("/ships/{shipId}/imports/{jobId}/issues", ship.getShipId(), partialJob)
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].errorCode").value("INVALID_CABIN_TYPE"));
        assertEquals(1, decks.countByShipShipId(ship.getShipId()));
        assertEquals(0, cabins.countByShipShipId(ship.getShipId()));

        String rollbackKey = modelKey;
        String rollbackVersion = "rollback-v1";
        upload(
                rollbackKey,
                rollbackVersion,
                smallBundle(rollbackKey, rollbackVersion, "OCEAN_VIEW", "CABIN-NUM-TOO-LONG", true),
                token);
        UUID rollbackModel = register(ship.getShipId(), rollbackKey, rollbackVersion, token);
        MvcResult rollbackResponse =
                mockMvc.perform(
                                post(
                                                "/ships/{shipId}/models/{modelId}/imports",
                                                ship.getShipId(),
                                                rollbackModel)
                                        .header("Authorization", "Bearer " + token))
                        .andReturn();
        assertEquals(
                409,
                rollbackResponse.getResponse().getStatus(),
                rollbackResponse.getResponse().getContentAsString());
        var failedJobEntity =
                jobs.findByShipModelShipModelIdOrderByStartedAtDesc(rollbackModel).get(0);
        UUID failedJob = failedJobEntity.getModelImportJobId();
        assertEquals("FAILED", failedJobEntity.getStatus().name());
        mockMvc.perform(
                        get("/ships/{shipId}/imports/{jobId}", ship.getShipId(), failedJob)
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"));
        assertEquals(
                1,
                decks.countByShipShipId(ship.getShipId()),
                "deck written before the cabin constraint failure must roll back");
        assertEquals(0, cabins.countByShipShipId(ship.getShipId()));
        assertEquals(1, objects.findByShipModelShipModelId(partialModel).size());
        assertTrue(objects.findByShipModelShipModelId(rollbackModel).isEmpty());

        String mismatchKey = modelKey;
        String mismatchVersion = "mismatch-v1";
        upload(
                mismatchKey,
                mismatchVersion,
                smallBundle(mismatchKey, mismatchVersion, "OCEAN_VIEW", "M-001", false),
                token);
        UUID mismatchModel = register(ship.getShipId(), mismatchKey, mismatchVersion, token);
        MvcResult mismatchResponse =
                mockMvc.perform(
                                post(
                                                "/ships/{shipId}/models/{modelId}/imports",
                                                ship.getShipId(),
                                                mismatchModel)
                                        .header("Authorization", "Bearer " + token))
                        .andReturn();
        assertEquals(
                400,
                mismatchResponse.getResponse().getStatus(),
                mismatchResponse.getResponse().getContentAsString());
        var mismatchJob = jobs.findByShipModelShipModelIdOrderByStartedAtDesc(mismatchModel).get(0);
        assertEquals("FAILED", mismatchJob.getStatus().name());
        assertTrue(objects.findByShipModelShipModelId(mismatchModel).isEmpty());

        mockMvc.perform(
                        post(
                                        "/ships/{shipId}/models/{modelId}/imports",
                                        UUID.randomUUID(),
                                        partialModel)
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void laterModel_updatesModelManagedFieldsAndPreservesBusinessOwnedCabinFields()
            throws Exception {
        Ship ship = createShip(10);
        String token = token(Role.ADMIN);
        String firstKey = uniqueKey("upsert-a");
        upload(firstKey, "v1", smallBundle(firstKey, "v1", "OCEAN_VIEW", "M-101", true), token);
        UUID firstModel = register(ship.getShipId(), firstKey, "v1", token);
        assertEquals(
                "SUCCEEDED",
                importJob(ship.getShipId(), firstModel, token)
                        .path("data")
                        .path("status")
                        .asText());

        Deck existingDeck = decks.findByShipShipIdAndDeckNumber(ship.getShipId(), 1).orElseThrow();
        existingDeck.setPriceMultiplier(new java.math.BigDecimal("1.35"));
        decks.saveAndFlush(existingDeck);
        Cabin existingCabin =
                cabins.findByShipShipIdAndCabinNumber(ship.getShipId(), "M-101").orElseThrow();
        existingCabin.setCapacity(4);
        existingCabin.setIsAccessible(true);
        existingCabin.setStatus(com.casms.ship.enums.CabinStatus.MAINTENANCE);
        existingCabin.setBedCount(2);
        existingCabin.setBedConfiguration("KING_TWIN");
        cabins.saveAndFlush(existingCabin);

        upload(
                firstKey,
                "v2",
                smallBundle(
                        firstKey,
                        "v2",
                        "SUITE",
                        "M-101",
                        true,
                        true,
                        "Updated Mini Deck",
                        1.7,
                        List.of(7.0, 3.0, 8.0),
                        false,
                        1,
                        8,
                        4),
                token);
        UUID secondModel = register(ship.getShipId(), firstKey, "v2", token);
        assertEquals(
                "SUCCEEDED",
                importJob(ship.getShipId(), secondModel, token)
                        .path("data")
                        .path("status")
                        .asText());

        Deck updatedDeck = decks.findByShipShipIdAndDeckNumber(ship.getShipId(), 1).orElseThrow();
        assertEquals(existingDeck.getDeckId(), updatedDeck.getDeckId());
        assertEquals("Updated Mini Deck", updatedDeck.getDeckName());
        assertEquals(1.7, updatedDeck.getModelPositionY());
        assertEquals(new java.math.BigDecimal("1.35"), updatedDeck.getPriceMultiplier());

        Cabin updatedCabin =
                cabins.findByShipShipIdAndCabinNumber(ship.getShipId(), "M-101").orElseThrow();
        assertEquals(existingCabin.getCabinId(), updatedCabin.getCabinId());
        assertEquals(CabinType.SUITE, updatedCabin.getCabinType());
        assertEquals(4, updatedCabin.getCapacity());
        assertTrue(updatedCabin.getIsAccessible());
        assertEquals(com.casms.ship.enums.CabinStatus.MAINTENANCE, updatedCabin.getStatus());
        assertEquals(2, updatedCabin.getBedCount());
        assertEquals("KING_TWIN", updatedCabin.getBedConfiguration());
        assertEquals(7.0, updatedCabin.getModelWidth());
        assertEquals(1, decks.countByShipShipId(ship.getShipId()));
        assertEquals(1, cabins.countByShipShipId(ship.getShipId()));
    }

    @Test
    void duplicateCabinBusinessIdentity_isRejectedBeforeWritingBusinessRows() throws Exception {
        Ship ship = createShip(10);
        String token = token(Role.ADMIN);
        String key = uniqueKey("duplicate-cabin");
        upload(
                key,
                "v1",
                smallBundle(
                        key,
                        "v1",
                        "OCEAN_VIEW",
                        "M-201",
                        true,
                        true,
                        "Mini Deck",
                        1.0,
                        List.of(5.0, 2.8, 6.5),
                        true),
                token);
        UUID modelId = register(ship.getShipId(), key, "v1", token);

        mockMvc.perform(
                        post("/ships/{shipId}/models/{modelId}/imports", ship.getShipId(), modelId)
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        var failedJob = jobs.findByShipModelShipModelIdOrderByStartedAtDesc(modelId).get(0);
        assertEquals("FAILED", failedJob.getStatus().name());
        assertEquals(0, decks.countByShipShipId(ship.getShipId()));
        assertEquals(0, cabins.countByShipShipId(ship.getShipId()));
        assertTrue(objects.findByShipModelShipModelId(modelId).isEmpty());
    }

    @Test
    void manifestRegistration_createsIndependentShipsFromTheSameUploadedTemplate()
            throws Exception {
        String token = token(Role.ADMIN);
        String key = uniqueKey("provision");
        upload(key, "v1", smallBundle(key, "v1", "OCEAN_VIEW", "M-301", true), token);

        JsonNode first = registerManifestUnchecked(key);
        UUID shipId = UUID.fromString(first.path("shipId").asText());
        testShipIds.add(shipId);
        assertTrue(first.path("shipCreated").asBoolean());
        Ship createdShip = ships.findById(shipId).orElseThrow();
        assertEquals(key, createdShip.getManifestModelId());
        assertEquals("Mini Ship", createdShip.getShipName());
        assertEquals(2, createdShip.getTotalCapacity());
        assertEquals(8, createdShip.getTotalDecks());
        assertEquals(1, createdShip.getDeckNumberLimit());
        assertEquals(100.0, createdShip.getLengthMeters());
        assertEquals(20.0, createdShip.getBeamMeters());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        JsonNode second;
        JsonNode third;
        try {
            var v2 =
                    CompletableFuture.supplyAsync(
                            () -> registerManifestUnchecked(key), pool);
            var v3 =
                    CompletableFuture.supplyAsync(
                            () -> registerManifestUnchecked(key), pool);
            second = v2.join();
            third = v3.join();
        } finally {
            pool.shutdownNow();
        }
        assertTrue(second.path("shipCreated").asBoolean());
        assertTrue(third.path("shipCreated").asBoolean());
        UUID secondShip = UUID.fromString(second.path("shipId").asText()),
                thirdShip = UUID.fromString(third.path("shipId").asText());
        testShipIds.add(secondShip);
        testShipIds.add(thirdShip);
        assertEquals(3, Set.of(shipId, secondShip, thirdShip).size());
        assertEquals(
                3,
                ships.findAll().stream().filter(s -> key.equals(s.getManifestModelId())).count());
        assertEquals(1, models.findByShipShipIdOrderByCreatedAtDesc(shipId).size());
        assertEquals(1, models.findByShipShipIdOrderByCreatedAtDesc(secondShip).size());
        assertEquals(1, models.findByShipShipIdOrderByCreatedAtDesc(thirdShip).size());
        assertEquals(
                "SUCCEEDED",
                importJob(secondShip, UUID.fromString(second.path("shipModelId").asText()), token)
                        .path("data")
                        .path("status")
                        .asText());
        assertEquals(
                "SUCCEEDED",
                importJob(thirdShip, UUID.fromString(third.path("shipModelId").asText()), token)
                        .path("data")
                        .path("status")
                        .asText());
        assertNotEquals(
                decks.findByShipShipIdAndDeckNumber(secondShip, 1).orElseThrow().getDeckId(),
                decks.findByShipShipIdAndDeckNumber(thirdShip, 1).orElseThrow().getDeckId());
    }

    @Test
    void manifestDeckCount_isSeparateFromPassengerDeckNumberLimit() throws Exception {
        String token = token(Role.ADMIN);
        String key = uniqueKey("deck-gap");
        upload(
                key,
                "v1",
                smallBundle(
                        key,
                        "v1",
                        "OCEAN_VIEW",
                        "M-401",
                        true,
                        true,
                        "Sky Deck",
                        14.0,
                        List.of(5.0, 2.8, 6.5),
                        false,
                        14,
                        13),
                token);
        JsonNode registered = registerManifestUnchecked(key);
        UUID shipId = UUID.fromString(registered.path("shipId").asText());
        testShipIds.add(shipId);
        UUID modelId = UUID.fromString(registered.path("shipModelId").asText());
        Ship ship = ships.findById(shipId).orElseThrow();
        assertEquals(13, ship.getTotalDecks());
        assertEquals(14, ship.getDeckNumberLimit());

        JsonNode result = importJob(shipId, modelId, token);
        assertEquals("SUCCEEDED", result.path("data").path("status").asText());
        Deck imported = decks.findByShipShipIdAndDeckNumber(shipId, 14).orElseThrow();
        assertEquals("Sky Deck", imported.getDeckName());
        assertEquals(1, decks.countByShipShipId(shipId));
    }

    @Test
    void simultaneousRetries_areSerializedAndKeepOneBusinessMappingPerNaturalKey()
            throws Exception {
        Ship ship = createShip(10);
        String key = uniqueKey("parallel");
        String version = "v1";
        String token = token(Role.ADMIN);
        upload(key, version, smallBundle(key, version, "OCEAN_VIEW", "M-001", true), token);
        UUID modelId = register(ship.getShipId(), key, version, token);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var a =
                    CompletableFuture.supplyAsync(
                            () -> importJobUnchecked(ship.getShipId(), modelId, token), pool);
            var b =
                    CompletableFuture.supplyAsync(
                            () -> importJobUnchecked(ship.getShipId(), modelId, token), pool);
            assertEquals("SUCCEEDED", a.join().path("data").path("status").asText());
            assertEquals("SUCCEEDED", b.join().path("data").path("status").asText());
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, decks.countByShipShipId(ship.getShipId()));
        assertEquals(1, cabins.countByShipShipId(ship.getShipId()));
        assertEquals(2, objects.findByShipModelShipModelId(modelId).size());

        String noCabinKey = key;
        String noCabinVersion = "v2";
        upload(
                noCabinKey,
                noCabinVersion,
                smallBundle(noCabinKey, noCabinVersion, "OCEAN_VIEW", "M-002", true, false),
                token);
        UUID noCabinModel = register(ship.getShipId(), noCabinKey, noCabinVersion, token);
        assertEquals(
                "SUCCEEDED",
                importJob(ship.getShipId(), noCabinModel, token)
                        .path("data")
                        .path("status")
                        .asText());
        assertEquals(
                1,
                cabins.countByShipShipId(ship.getShipId()),
                "objects absent from a later model must be retained");
    }

    @Test
    void legacyShipUpdateValidatesPassengerDeckCount() throws Exception {
        String token = token(Role.ADMIN);
        UUID legacyId = createShip(6).getShipId();
        long before = ships.count();
        for (String count : List.of("7", "25", "8.5", "null")) {
            if (!count.equals("null")) {
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .patch("/ships/{shipId}", legacyId)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"totalDecks\":" + count + "}"))
                        .andExpect(status().isBadRequest());
            }
        }
        assertEquals(before, ships.count());
        mockMvc.perform(get("/ships/{shipId}", legacyId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalDecks").value(6));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/ships/{shipId}", legacyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shipName\":\"Legacy ship renamed\",\"totalDecks\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalDecks").value(6));

        for (int count : List.of(8, 12, 24)) {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .patch("/ships/{shipId}", legacyId)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsBytes(Map.of("totalDecks", count))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalDecks").value(count));
        }
    }

    @Test
    void invalidManifestCountDoesNotProvisionShip() throws Exception {
        String token = token(Role.ADMIN);
        for (int count : List.of(7, 25)) {
            String key = uniqueKey("deck-count");
            Path zip = smallBundle(key, "v1", "OCEAN_VIEW", "M-001", true, true,
                    "Mini Deck", 1.0, List.of(5.0, 2.8, 6.5), false, 1, count);
            upload(key, "v1", zip, token);
            long before = ships.count();
            createUsingModel(key, Map.of("totalDecks", 8), token, 400);
            assertEquals(before, ships.count());
        }
    }

    @Test
    void shipCreateUsesStoredModelAndGeneratesCodesAndDatesWithoutAnotherUpload() throws Exception {
        String token = token(Role.ADMIN), key = uniqueKey("reuse-create");
        upload(key, "v1", smallBundle(key, "v1", "OCEAN_VIEW", "M-REUSE", true), token);
        long blobCount =
                jdbc.queryForObject(
                        "SELECT count(*) FROM \"ShipModelAssetBlob\" WHERE model_key=?",
                        Long.class,
                        key);
        var uploadedAt =
                jdbc.queryForObject(
                        "SELECT min(created_at) FROM \"ShipModelAssetBlob\" WHERE model_key=?",
                        java.sql.Timestamp.class,
                        key);
        JsonNode first =
                createUsingModel(key,
                        Map.of(
                                "shipName",
                                "Explicit vessel name",
                                "totalCapacity",
                                999,
                                "totalDecks",
                                24),
                        token,
                        201);
        JsonNode second =
                createUsingModel(
                        key,
                        Map.of(
                                "commissionDate", "2020-01-02",
                                "manufacturerName", "Example Shipyard",
                                "origin", "Vietnam",
                                "productionYear", 2019,
                                "engineType", "Diesel"),
                        token,
                        201);
        assertNotEquals(first.path("shipId").asText(), second.path("shipId").asText());
        assertNotEquals(first.path("shipCode").asText(), second.path("shipCode").asText());
        assertEquals("Explicit vessel name", first.path("shipName").asText());
        assertEquals(2, first.path("totalCapacity").asInt());
        assertEquals(8, first.path("totalDecks").asInt());
        assertFalse(first.path("createdAt").asText().isBlank());
        assertEquals("2020-01-02", second.path("commissionDate").asText());
        assertEquals(
                blobCount,
                jdbc.queryForObject(
                        "SELECT count(*) FROM \"ShipModelAssetBlob\" WHERE model_key=?",
                        Long.class,
                        key));
        assertEquals(
                uploadedAt,
                jdbc.queryForObject(
                        "SELECT min(created_at) FROM \"ShipModelAssetBlob\" WHERE model_key=?",
                        java.sql.Timestamp.class,
                        key));
        UUID firstId = UUID.fromString(first.path("shipId").asText()),
                secondId = UUID.fromString(second.path("shipId").asText());
        assertEquals("Example Shipyard", second.path("manufacturerName").asText());
        assertEquals("Vietnam", second.path("origin").asText());
        assertEquals(2019, second.path("productionYear").asInt());
        assertEquals("Diesel", second.path("engineType").asText());
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .patch("/ships/{shipId}", secondId)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"productionYear\":0}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .patch("/ships/{shipId}", secondId)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"engineType\":\"Hybrid\"}"))
                .andExpect(status().isOk());
        Ship saved = ships.findById(secondId).orElseThrow();
        assertEquals("Example Shipyard", saved.getManufacturerName());
        assertEquals("Vietnam", saved.getOrigin());
        assertEquals(2019, saved.getProductionYear());
        assertEquals("Hybrid", saved.getEngineType());
        assertEquals(1, models.findByShipShipIdOrderByCreatedAtDesc(firstId).size());
        assertEquals(1, models.findByShipShipIdOrderByCreatedAtDesc(secondId).size());
    }

    @Test
    void missingOptionalMetadataIsSkippedAndOnlyMissingRequiredValuesNeedSupplements()
            throws Exception {
        String token = token(Role.ADMIN), key = uniqueKey("partial-metadata");
        Path original = smallBundle(key, "v1", "OCEAN_VIEW", "M-PARTIAL", true);
        Path partial =
                rewriteManifest(
                        original,
                        key,
                        "v1",
                        Set.of("name", "passengerCapacity", "deckCount", "length", "beam"));
        upload(key, "v1", partial, token);
        long before = ships.count();
        createUsingModel(key, Map.of(), token, 400);
        assertEquals(before, ships.count());
        JsonNode ship =
                createUsingModel(key,
                        Map.of(
                                "shipName",
                                "Supplemented ship",
                                "totalCapacity",
                                20,
                                "totalDecks",
                                8,
                                "lengthMeters",
                                75.0),
                        token,
                        201);
        assertEquals("Supplemented ship", ship.path("shipName").asText());
        assertEquals(20, ship.path("totalCapacity").asInt());
        assertEquals(8, ship.path("totalDecks").asInt());
        assertEquals(75.0, ship.path("lengthMeters").asDouble());
        assertTrue(ship.path("beamMeters").isNull());
        assertTrue(ship.path("commissionDate").isNull());
        ShipCreateRequest supplements = new ShipCreateRequest();
        supplements.setShipName("Second supplemented ship");
        supplements.setTotalCapacity(30);
        supplements.setTotalDecks(8);
        UUID other = assetService.createShipFromManifest(key, supplements).shipId();
        testShipIds.add(other);
        assertNull(ships.findById(other).orElseThrow().getLengthMeters());
        assertNull(ships.findById(other).orElseThrow().getBeamMeters());
    }

    @Test
    void sameUploadedBundleCanBeAttachedToDifferentExistingShips() throws Exception {
        String token = token(Role.ADMIN), key = uniqueKey("reuse-existing");
        upload(key, "v1", smallBundle(key, "v1", "SUITE", "M-EXIST", true), token);
        Ship first = createShip(2), second = createShip(2);
        UUID a = register(first.getShipId(), key, "v1", token),
                b = register(second.getShipId(), key, "v1", token);
        assertNotEquals(a, b);
        assertEquals(key, ships.findById(first.getShipId()).orElseThrow().getManifestModelId());
        assertEquals(key, ships.findById(second.getShipId()).orElseThrow().getManifestModelId());
        mockMvc.perform(
                        post("/ships/{id}/models", first.getShipId())
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        mapper.writeValueAsBytes(
                                                Map.of("modelKey", key))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(a.toString()));
    }

    @Test
    void contentLimitCountsIdenticalGlbAcrossKeysButAllowsDifferentContentAndUnlimitedShipReuse()
            throws Exception {
        String token = token(Role.ADMIN), first = uniqueKey("copy-a"), alias = uniqueKey("copy-b");
        upload(first, "v1", smallBundle(first, "v1", "OCEAN_VIEW", "M-COPY", true), token);
        upload(alias, "v1", smallBundle(alias, "v1", "OCEAN_VIEW", "M-COPY", true), token);
        long objectsBefore =
                jdbc.queryForObject("SELECT count(*) FROM pg_largeobject_metadata", Long.class);
        assertEquals(
                409,
                uploadStatus(
                        first,
                        "v2",
                        smallBundle(first, "v2", "OCEAN_VIEW", "M-COPY", true),
                        token));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT count(*) FROM \"ShipModelAssetBlob\" WHERE model_key=? AND"
                            + " asset_version=?",
                        Integer.class,
                        first, storedVersions.get(first + "|v2")));
        assertEquals(
                objectsBefore,
                jdbc.queryForObject("SELECT count(*) FROM pg_largeobject_metadata", Long.class));
        for (int i = 0; i < 3; i++) createUsingModel(first, Map.of(), token, 201);
        upload(first, "v3", smallBundle(first, "v3", "SUITE", "M-COPY", true), token);
    }

    @Test
    void invalidUploadDoesNotConsumeDuplicateAllowance() throws Exception {
        String token = token(Role.ADMIN), key = uniqueKey("invalid-copy");
        upload(key, "v1", smallBundle(key, "v1", "OCEAN_VIEW", "M-INVALID", true), token);
        Path invalid =
                rewriteChecksums(
                        smallBundle(key, "v2", "OCEAN_VIEW", "M-INVALID", true), key, "v2");
        assertEquals(400, uploadStatus(key, "v2", invalid, token));
        upload(key, "v3", smallBundle(key, "v3", "OCEAN_VIEW", "M-INVALID", true), token);
        assertEquals(
                409,
                uploadStatus(
                        key, "v4", smallBundle(key, "v4", "OCEAN_VIEW", "M-INVALID", true), token));
    }

    @Test
    void concurrentIdenticalUploadsCannotExceedTwoCopies() throws Exception {
        String token = token(Role.ADMIN),
                a = uniqueKey("race-a"),
                b = uniqueKey("race-b"),
                c = uniqueKey("race-c");
        upload(a, "v1", smallBundle(a, "v1", "OCEAN_VIEW", "M-RACE", true), token);
        Path second = smallBundle(b, "v1", "OCEAN_VIEW", "M-RACE", true),
                third = smallBundle(c, "v1", "OCEAN_VIEW", "M-RACE", true);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var f1 =
                    pool.submit(
                            () -> {
                                start.await();
                                return uploadStatus(b, "v1", second, token);
                            });
            var f2 =
                    pool.submit(
                            () -> {
                                start.await();
                                return uploadStatus(c, "v1", third, token);
                            });
            start.countDown();
            var results =
                    new ArrayList<>(
                            List.of(
                                    f1.get(90, java.util.concurrent.TimeUnit.SECONDS),
                                    f2.get(90, java.util.concurrent.TimeUnit.SECONDS)));
            Collections.sort(results);
            assertEquals(List.of(201, 409), results);
            assertEquals(
                    2,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM \"ShipModelAssetBlob\" WHERE"
                                + " asset_path='manifest.json' AND model_key IN (?,?,?)",
                            Integer.class,
                            a,
                            b,
                            c));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void storedDesignUsesThePreviewedVersionAndIsSafeToRetry() throws Exception {
        String admin = token(Role.ADMIN);
        String manager = token(Role.SHIP_MANAGER);
        String key = uniqueKey("stored-type");
        Path first = smallBundle(key, "v1", "OCEAN_VIEW", "STORED-01", true);
        Path next = smallBundle(key, "v2", "SUITE", "STORED-01", true);
        String version = "zip-" + sha256(Files.readAllBytes(first));
        String nextVersion = "zip-" + sha256(Files.readAllBytes(next));
        uploadedBundles.add(key + "|" + version);
        uploadedBundles.add(key + "|" + nextVersion);
        long shipCount = ships.count();
        for (Path archive : List.of(first, next, first)) {
            mockMvc.perform(multipart("/ship-model-assets")
                            .file(new FileBackedMultipartFile("bundle", archive))
                            .param("modelKey", key).header("Authorization", "Bearer " + admin))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.assetVersion").value(archive.equals(first) ? version : nextVersion));
        }
        var preview = mockMvc.perform(get("/ship-model-assets/{key}/preview", key)
                        .param("assetVersion", nextVersion).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Mini Ship"))
                .andExpect(jsonPath("$.data.cabins[0].cabinType").value("SUITE")).andReturn();
        String fullPath = mapper.readTree(preview.getResponse().getContentAsByteArray())
                .path("data").path("assets").path("full").asText();
        String assetUrl = "/ship-model-assets/" + key + "/versions/" + nextVersion + "/assets/" + fullPath;
        var stream = mockMvc.perform(get(assetUrl).header("Authorization", "Bearer " + admin))
                .andExpect(request().asyncStarted()).andReturn();
        mockMvc.perform(asyncDispatch(stream)).andExpect(status().isOk())
                .andExpect(content().contentType("model/gltf-binary"));
        mockMvc.perform(get(assetUrl).header("Authorization", "Bearer " + manager))
                .andExpect(status().isForbidden());

        byte[] design = mapper.writeValueAsBytes(Map.of("modelKey", key, "assetVersion", nextVersion));
        mockMvc.perform(post("/ship-types/from-stored").header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON).content(design))
                .andExpect(status().isForbidden());
        UUID typeId;
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<UUID> createType = () -> {
                var result = mockMvc.perform(post("/ship-types/from-stored")
                                .header("Authorization", "Bearer " + admin)
                                .contentType(MediaType.APPLICATION_JSON).content(design))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.data.name").value("Mini Ship"))
                        .andExpect(jsonPath("$.data.assetVersion").value(nextVersion))
                        .andExpect(jsonPath("$.data.shipCount").value(0)).andReturn();
                return UUID.fromString(mapper.readTree(result.getResponse().getContentAsByteArray())
                        .path("data").path("shipTypeId").asText());
            };
            var firstRequest = executor.submit(createType);
            var secondRequest = executor.submit(createType);
            typeId = firstRequest.get();
            testTypeIds.add(typeId);
            assertEquals(typeId, secondRequest.get());
        }
        mockMvc.perform(post("/ship-types/from-stored").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("modelKey", key, "assetVersion", version))))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/ship-types/{typeId}/ships", typeId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/ship-types").param("q", key.toUpperCase(Locale.ROOT))
                        .header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(patch("/ship-types/{typeId}/status", typeId).param("status", "INACTIVE")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        mockMvc.perform(get("/ship-types").param("q", key).header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/ship-types").param("q", key).param("status", "INACTIVE")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        assertEquals(shipCount, ships.count());
    }

    @Test
    void stagedUploadRejectsReferencedGlbWithInvalidHeaderAndKeepsNoAssets() throws Exception {
        String key = uniqueKey("broken-glb");
        Path source = smallBundle(key, "v1", "SUITE", "BROKEN-01", true);
        Map<String, byte[]> entries = readBundle(source, key, "v1");
        entries.put("ship.glb", new byte[24]);
        Path invalid = writeBundle(entries, key, "v1", true);

        assertEquals(400, uploadStatus(key, "v1", invalid, token(Role.ADMIN)));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT count(*) FROM \"ShipModelAssetBlob\" WHERE model_key=?", Long.class, key));
    }

    @Test
    void shipTypeProvisioningSharesAssetsAndGatesStatusOnScopedProfile() throws Exception {
        String admin = token(Role.ADMIN);
        String manager = token(Role.SHIP_MANAGER);
        String otherManager = token(Role.SHIP_MANAGER);
        String key = uniqueKey("ship-type");
        Path bundle = rewriteManifest(smallBundle(key, "v1", "OCEAN_VIEW", "TYPE-01", true),
                key, "v1", Set.of("manufacturerShipCode", "shipyard", "buildCountry", "buildYear"));
        long shipCount = ships.count();
        upload(key, "v1", bundle, admin);
        String identity = storedVersions.get(key + "|v1");
        // Emulate a type accepted before ZIP manufacturing metadata became mandatory.
        UUID typeId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO "ShipType" (ship_type_id, type_code, type_name, model_key, asset_version,
                    manufacturer_name, engine_type, total_capacity, total_decks, length_meters, beam_meters)
                VALUES (?, ?, 'Mini Ship', ?, ?, 'Example Manufacturer', 'Diesel', 20, 8, 100, 20)
                """, typeId, key, key, identity);
        testTypeIds.add(typeId);
        assertEquals(shipCount, ships.count());
        long assetCount = jdbc.queryForObject(
                "SELECT count(*) FROM \"ShipModelAssetBlob\" WHERE model_key = ?", Long.class, key);

        Map<String, Object> profile = Map.of(
                "classificationSociety", "Example Classification",
                "classificationExpiryDate", "2099-01-01",
                "certificateReferences", List.of("certificate-1"),
                "warrantyReferences", List.of("warranty-1"),
                "maintenanceReferences", List.of("maintenance-1"));
        UUID firstKey = UUID.randomUUID();
        byte[] firstPayload = mapper.writeValueAsBytes(Map.of("shipName", "Aurora 01",
                "profile", Map.of("imoNumber", "1234567")));
        var executor = Executors.newFixedThreadPool(2);
        UUID first;
        try {
            var requests = new ArrayList<CompletableFuture<UUID>>();
            for (int i = 0; i < 2; i++) {
                requests.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return provisionedShipId(typeId, manager, firstKey, firstPayload, "Aurora 01");
                    } catch (Exception exception) {
                        throw new java.util.concurrent.CompletionException(exception);
                    }
                }, executor));
            }
            first = requests.get(0).get();
            testShipIds.add(first);
            assertEquals(first, requests.get(1).get());
        } finally {
            executor.shutdownNow();
        }
        mockMvc.perform(get("/ship-types/{typeId}/ships/provisioning/{key}", typeId, firstKey)
                        .header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.shipId").value(first.toString()))
                .andExpect(jsonPath("$.data.shipTypeName").value("Mini Ship"));
        mockMvc.perform(get("/ship-types/{typeId}/ships/provisioning/{key}", typeId, firstKey)
                        .header("Authorization", "Bearer " + otherManager))
                .andExpect(status().isNotFound());
        UUID second = createTypedShip(typeId, "Aurora 02", manager, profile);
        assertEquals(shipCount + 2, ships.count());
        assertNull(ships.findById(first).orElseThrow().getOperationalProfile().manufacturerShipCode());
        mockMvc.perform(get("/ships/{id}", second).header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.designReference.assetVersion").value(identity))
                .andExpect(jsonPath("$.data.designReference.manufacturingMetadata.manufacturerShipCode").doesNotExist());
        mockMvc.perform(post("/ship-types/{typeId}/ships", typeId)
                        .header("Authorization", "Bearer " + manager)
                        .header("Idempotency-Key", firstKey)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"shipName\":\"Changed request\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        mockMvc.perform(post("/ship-types/{typeId}/ships", typeId)
                        .header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON).content(firstPayload))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/ship-types/{typeId}/ships", typeId)
                        .header("Authorization", "Bearer " + admin)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(firstPayload))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/ship-types/{typeId}/ships", typeId).param("size", "1")
                        .header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content", hasSize(1)));
        mockMvc.perform(get("/ship-types/{typeId}/ships", typeId)
                        .header("Authorization", "Bearer " + otherManager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(patch("/ships/{shipId}/name", first).header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"shipName\":\"Aurora Renewed\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.shipName").value("Aurora Renewed"));
        mockMvc.perform(patch("/ships/{shipId}/name", first).header("Authorization", "Bearer " + otherManager)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"shipName\":\"Unauthorized name\"}"))
                .andExpect(status().isForbidden());
        assertEquals("Aurora 02", ships.findById(second).orElseThrow().getShipName());
        mockMvc.perform(get("/ship-types/{typeId}", typeId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("Mini Ship"))
                .andExpect(jsonPath("$.data.shipCount").value(2));
        assertNotEquals(first, second);
        assertNotEquals(ships.findById(first).orElseThrow().getShipCode(),
                ships.findById(second).orElseThrow().getShipCode());
        assertEquals(assetCount, jdbc.queryForObject(
                "SELECT count(*) FROM \"ShipModelAssetBlob\" WHERE model_key = ?", Long.class, key));
        assertNotEquals(decks.findByShipShipIdAndDeckNumber(first, 1).orElseThrow().getDeckId(),
                decks.findByShipShipIdAndDeckNumber(second, 1).orElseThrow().getDeckId());

        UUID firstModel = models.findByShipShipIdOrderByCreatedAtDesc(first).getFirst().getShipModelId();
        UUID secondModel = models.findByShipShipIdOrderByCreatedAtDesc(second).getFirst().getShipModelId();
        assertNotEquals(firstModel, secondModel);
        for (UUID id : List.of(firstModel, secondModel)) {
            assertEquals(identity, models.findById(id).orElseThrow().getAssetVersion());
        }
        assertNotEquals(objects.findByShipModelShipModelIdAndNodeKey(firstModel, "mini-c1").orElseThrow().getCabin().getCabinId(),
                objects.findByShipModelShipModelIdAndNodeKey(secondModel, "mini-c1").orElseThrow().getCabin().getCabinId());

        Path v2 = smallBundle(key, "v2", "SUITE", "TYPE-02", true);
        upload(key, "v2", v2, admin);
        String latest = storedVersions.get(key + "|v2");
        mockMvc.perform(put("/ship-types/{typeId}/model", typeId).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("modelKey", key, "assetVersion", latest))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.assetVersion").value(latest));
        for (UUID shipId : List.of(first, second)) {
            mockMvc.perform(get("/ships/{shipId}/model", shipId).header("Authorization", "Bearer " + manager))
                    .andExpect(status().isOk());
            assertEquals(identity, models.findByShipShipIdOrderByCreatedAtDesc(shipId).getFirst().getAssetVersion());
        }
        mockMvc.perform(post("/ship-types/{typeId}/ships", typeId).header("Authorization", "Bearer " + manager)
                        .header("Idempotency-Key", firstKey)
                        .contentType(MediaType.APPLICATION_JSON).content(firstPayload))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.shipId").value(first.toString()));
        assertEquals(shipCount + 2, ships.count());

        mockMvc.perform(post("/ships/{shipId}/status", first)
                        .param("status", "READY").header("Authorization", "Bearer " + manager))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/ships/{shipId}/operational-profile", first)
                        .header("Authorization", "Bearer " + otherManager))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/ships/{shipId}/operational-profile", first)
                        .header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(profile)))
                .andExpect(status().isOk());
        for (UUID shipId : List.of(first, second)) {
            mockMvc.perform(post("/ships/{shipId}/status", shipId)
                            .param("status", "READY").header("Authorization", "Bearer " + manager))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/ships/{shipId}/public-model", shipId))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void oneDesignProvisionsThreeIndependentShipsAndKeepsTheirOriginalReference() throws Exception {
        String admin = token(Role.ADMIN);
        String manager = token(Role.SHIP_MANAGER);
        String key = uniqueKey("design-metadata");
        upload(key, "v1", smallBundle(key, "v1", "OCEAN_VIEW", "HULL-01", true), admin);
        String v1 = storedVersions.get(key + "|v1");
        byte[] design = mapper.writeValueAsBytes(Map.of("modelKey", key, "assetVersion", v1));
        var created = mockMvc.perform(post("/ship-types/from-stored")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(design))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.manufacturingMetadata.manufacturerShipCode").value(key + "-v1"))
                .andReturn();
        UUID typeId = UUID.fromString(mapper.readTree(created.getResponse().getContentAsByteArray())
                .path("data").path("shipTypeId").asText());
        testTypeIds.add(typeId);
        var supplied = Map.<String, Object>of("manufacturerShipCode", "CLIENT",
                "shipyard", "Client yard", "buildCountry", "CA", "buildYear", 1990);
        UUID first = createTypedShip(typeId, "Ship One", manager, supplied);
        UUID second = createTypedShip(typeId, "Ship Two", manager, Map.of());
        UUID third = createTypedShip(typeId, "Ship Three", manager, Map.of());
        Set<String> codes = new HashSet<>();
        Set<UUID> deckIds = new HashSet<>(), cabinIds = new HashSet<>(), modelIds = new HashSet<>(), mappingIds = new HashSet<>();
        for (UUID id : List.of(first, second, third)) {
            Ship ship = ships.findById(id).orElseThrow();
            assertTrue(codes.add(ship.getShipCode()));
            assertNull(ship.getOrigin());
            assertNull(ship.getProductionYear());
            assertNull(ship.getOperationalProfile().manufacturerShipCode());
            assertNull(ship.getOperationalProfile().shipyard());
            assertNull(ship.getOperationalProfile().buildCountry());
            assertNull(ship.getOperationalProfile().buildYear());
            assertTrue(deckIds.add(decks.findByShipShipIdAndDeckNumber(id, 1).orElseThrow().getDeckId()));
            ShipModel model = models.findByShipShipIdOrderByCreatedAtDesc(id).getFirst();
            assertTrue(modelIds.add(model.getShipModelId()));
            assertEquals(v1, model.getAssetVersion());
            var mapping = objects.findByShipModelShipModelIdAndNodeKey(model.getShipModelId(), "mini-c1").orElseThrow();
            assertTrue(mappingIds.add(mapping.getShipModelObjectId()));
            assertTrue(cabinIds.add(mapping.getCabin().getCabinId()));
        }
        mockMvc.perform(put("/ships/{shipId}/operational-profile", first)
                        .header("Authorization", "Bearer " + manager).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("manufacturerShipCode", "CHANGED",
                                "shipyard", "Wrong yard", "buildCountry", "FR", "buildYear", 1991, "imoNumber", "7654321"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.manufacturerShipCode").doesNotExist())
                .andExpect(jsonPath("$.data.imoNumber").value("7654321"));

        upload(key, "v2", smallBundle(key, "v2", "OCEAN_VIEW", "HULL-02", true), admin);
        String v2 = storedVersions.get(key + "|v2");
        mockMvc.perform(put("/ship-types/{typeId}/model", typeId).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("modelKey", key, "assetVersion", v2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.manufacturingMetadata.manufacturerShipCode").value(key + "-v2"));
        for (UUID id : List.of(first, second, third)) {
            String reference = "$.data.designReference.";
            mockMvc.perform(get("/ships/{shipId}", id).header("Authorization", "Bearer " + manager))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(reference + "assetVersion").value(v1))
                    .andExpect(jsonPath(reference + "manufacturingMetadata.manufacturerShipCode").value(key + "-v1"))
                    .andExpect(jsonPath(reference + "manufacturingMetadata.shipyard").value("Example Yard"))
                    .andExpect(jsonPath(reference + "manufacturingMetadata.buildCountry").value("NL"))
                    .andExpect(jsonPath(reference + "manufacturingMetadata.buildYear").value(2000));
        }
        UUID fourth = createTypedShip(typeId, "Ship Four", manager, Map.of());
        mockMvc.perform(get("/ships/{shipId}", fourth).header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.designReference.assetVersion").value(v2));
    }

    @Test
    void previewReportsInvalidManufacturingFieldsAndRefusesToCreateType() throws Exception {
        String admin = token(Role.ADMIN);
        String key = uniqueKey("bad-metadata");
        Path source = smallBundle(key, "v1", "OCEAN_VIEW", "HULL-01", true);
        var entries = readBundle(source, key, "v1");
        var manifest = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(entries.get("manifest.json"));
        manifest.remove("manufacturerShipCode");
        manifest.put("shipyard", 1);
        manifest.put("buildCountry", "ZZ");
        manifest.put("buildYear", "2000");
        entries.put("manifest.json", mapper.writeValueAsBytes(manifest));
        upload(key, "v1", writeBundle(entries, key, "v1", true), admin);
        String version = storedVersions.get(key + "|v1");
        mockMvc.perform(get("/ship-model-assets/{key}/preview", key)
                        .param("assetVersion", version).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shipTypeManufacturingIssues",
                        org.hamcrest.Matchers.contains("manufacturerShipCode", "shipyard", "buildCountry", "buildYear")));
        mockMvc.perform(post("/ship-types/from-stored").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("modelKey", key, "assetVersion", version))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void failedProvisioningRollsBackShipStructureAndLeavesTheRequestRetryable() throws Exception {
        String admin = token(Role.ADMIN);
        String manager = token(Role.SHIP_MANAGER);
        String key = uniqueKey("type-rollback");
        Path invalid = smallBundle(key, "bad", "INVALID_TYPE", "ROLLBK-01", true);
        Path valid = smallBundle(key, "good", "SUITE", "ROLLBK-01", true);
        String badVersion = "zip-" + sha256(Files.readAllBytes(invalid));
        uploadedBundles.add(key + "|" + badVersion);
        var type = mockMvc.perform(multipart("/ship-types")
                        .file(new FileBackedMultipartFile("bundle", invalid))
                        .param("modelKey", key).header("Authorization", "Bearer " + admin))
                .andExpect(status().isCreated()).andReturn();
        UUID typeId = UUID.fromString(mapper.readTree(type.getResponse().getContentAsByteArray())
                .path("data").path("shipTypeId").asText());
        testTypeIds.add(typeId);
        UUID legacyId = UUID.fromString("b3400000-0000-4000-8000-000000000201");
        Ship legacy = ships.findById(legacyId).orElseThrow();
        assertEquals("SH-PARADISE-01", legacy.getShipCode());
        assertTrue(jdbc.queryForObject("SELECT ship_type_id IS NULL FROM \"Ship\" WHERE ship_id=?",
                Boolean.class, legacyId));

        UUID requestKey = UUID.randomUUID();
        byte[] payload = mapper.writeValueAsBytes(Map.of("shipName", "Retry after repair"));
        var initialCounts = List.of(ships.count(), models.count(), decks.count(), cabins.count(), objects.count(),
                jobs.count());
        mockMvc.perform(post("/ship-types/{typeId}/ships", typeId)
                        .header("Authorization", "Bearer " + manager).header("Idempotency-Key", requestKey)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SHIP_MODEL_IMPORT_REJECTED"));
        assertEquals(initialCounts, List.of(ships.count(), models.count(), decks.count(), cabins.count(),
                objects.count(), jobs.count()));
        assertEquals(0L, jdbc.queryForObject("""
                SELECT count(*) FROM "IdempotencyRecord"
                WHERE operation='PROVISION_SHIP' AND idempotency_key=?
                """, Long.class, requestKey.toString()));

        upload(key, "good", valid, admin);
        String newVersion = storedVersions.get(key + "|good");
        mockMvc.perform(put("/ship-types/{typeId}/model", typeId).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("modelKey", key, "assetVersion", newVersion))))
                .andExpect(status().isOk());
        UUID shipId = provisionedShipId(typeId, manager, requestKey, payload, "Retry after repair");
        testShipIds.add(shipId);
        assertEquals(newVersion, models.findByShipShipIdOrderByCreatedAtDesc(shipId).getFirst().getAssetVersion());
    }

    private UUID createTypedShip(UUID typeId, String name, String manager, Map<String, Object> profile)
            throws Exception {
        UUID shipId = provisionedShipId(typeId, manager, UUID.randomUUID(),
                mapper.writeValueAsBytes(Map.of("shipName", name, "profile", profile)), name);
        testShipIds.add(shipId);
        return shipId;
    }

    private UUID provisionedShipId(UUID typeId, String manager, UUID requestKey, byte[] payload, String name)
            throws Exception {
        var result = mockMvc.perform(post("/ship-types/{typeId}/ships", typeId)
                        .header("Authorization", "Bearer " + manager)
                        .header("Idempotency-Key", requestKey)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.shipName").value(name))
                .andExpect(jsonPath("$.data.status").value("DRAFT")).andReturn();
        return UUID.fromString(mapper.readTree(result.getResponse().getContentAsByteArray())
                .path("data").path("shipId").asText());
    }

    @Test
    void retiredRoutesCannotCreateShipsOutsideShipTypes() throws Exception {
        String token = token(Role.ADMIN),
                key = uniqueKey("create-access"),
                passenger = token(Role.PASSENGER);
        upload(key, "v1", smallBundle(key, "v1", "OCEAN_VIEW", "M-ACCESS", true), token);
        long before = ships.count();
        mockMvc.perform(post("/ship-model-assets/{key}/register", key)
                        .header("Authorization", "Bearer " + passenger))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/ship-model-assets/{key}/register", key)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isGone());
        mockMvc.perform(post("/ships").header("Authorization", "Bearer " + token))
                .andExpect(status().isGone());
        assertEquals(before, ships.count());
    }

    private JsonNode createUsingModel(
            String key, Map<String, Object> supplements, String token, int expected)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>(supplements);
        body.put("modelKey", key);
        ShipCreateRequest request = mapper.convertValue(body, ShipCreateRequest.class);
        if (expected != 201) {
            var failure = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                    () -> shipService.create(request));
            assertEquals(expected, failure.getStatusCode().value());
            return mapper.nullNode();
        }
        var result = shipService.create(request);
        testShipIds.add(result.getShipId());
        return mapper.valueToTree(result);
    }

    private int uploadStatus(String key, String version, Path archive, String token)
            throws Exception {
        String identity = "zip-" + sha256(Files.readAllBytes(archive));
        uploadedBundles.add(key + "|" + identity);
        storedVersions.put(key + "|" + version, identity);
        return mockMvc.perform(
                        multipart("/ship-model-assets")
                                .file(new FileBackedMultipartFile("bundle", archive))
                                .param("modelKey", key)
                                .header("Authorization", "Bearer " + token))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private Path rewriteManifest(Path original, String key, String version, Set<String> fields)
            throws Exception {
        Map<String, byte[]> entries = readBundle(original, key, version);
        var manifest =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        mapper.readTree(entries.get("manifest.json"));
        fields.forEach(manifest::remove);
        entries.put("manifest.json", mapper.writeValueAsBytes(manifest));
        return writeBundle(entries, key, version, true);
    }

    private Path rewriteChecksums(Path original, String key, String version) throws Exception {
        Map<String, byte[]> entries = readBundle(original, key, version);
        var sums =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        mapper.readTree(entries.get("checksums.json"));
        sums.put("ship.glb", "0".repeat(64));
        entries.put("checksums.json", mapper.writeValueAsBytes(sums));
        return writeBundle(entries, key, version, false);
    }

    private Map<String, byte[]> readBundle(Path original, String key, String version)
            throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        String prefix = ASSET_PREFIX + key + "/" + version + "/";
        try (var zip = new java.util.zip.ZipInputStream(Files.newInputStream(original))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null)
                if (!entry.isDirectory())
                    entries.put(entry.getName().substring(prefix.length()), zip.readAllBytes());
        }
        return entries;
    }

    private Path writeBundle(
            Map<String, byte[]> entries, String key, String version, boolean checksums)
            throws Exception {
        if (checksums) {
            Map<String, String> sums = new LinkedHashMap<>();
            for (var entry : entries.entrySet())
                if (!entry.getKey().equals("checksums.json"))
                    sums.put(entry.getKey(), sha256(entry.getValue()));
            entries.put("checksums.json", mapper.writeValueAsBytes(sums));
        }
        Path archive = Files.createTempFile("casms-reuse-", ".zip");
        tempArchives.add(archive);
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(
                        new ZipEntry(ASSET_PREFIX + key + "/" + version + "/" + entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return archive;
    }

    @AfterEach
    void cleanTestRowsAndLargeObjects() {
        for (UUID shipId : testShipIds) {
            jdbc.update(
                    "DELETE FROM \"DeckServiceAssignment\" WHERE deck_id IN (SELECT deck_id FROM"
                        + " \"Deck\" WHERE ship_id = ?)",
                    shipId);
            jdbc.update(
                    "DELETE FROM \"ShipModelObject\" WHERE ship_model_id IN (SELECT ship_model_id"
                        + " FROM \"ShipModel\" WHERE ship_id = ?)",
                    shipId);
            jdbc.update(
                    "DELETE FROM \"ModelImportJob\" WHERE ship_model_id IN (SELECT ship_model_id"
                        + " FROM \"ShipModel\" WHERE ship_id = ?)",
                    shipId);
            jdbc.update("DELETE FROM \"Cabin\" WHERE ship_id = ?", shipId);
            jdbc.update("DELETE FROM \"Deck\" WHERE ship_id = ?", shipId);
            jdbc.update("DELETE FROM \"ShipModel\" WHERE ship_id = ?", shipId);
            jdbc.update("DELETE FROM \"Ship\" WHERE ship_id = ?", shipId);
        }
        for (UUID typeId : testTypeIds) {
            jdbc.update("DELETE FROM \"ShipType\" WHERE ship_type_id = ?", typeId);
        }
        for (String entry : uploadedBundles) {
            String[] identity = entry.split("\\|", 2);
            jdbc.update(
                    "DELETE FROM \"ShipModelAssetBlob\" WHERE model_key = ? AND asset_version = ?",
                    identity[0],
                    identity[1]);
        }
        for (UUID userId : testUserIds) {
            jdbc.update("""
                    DELETE FROM "IdempotencyRecord" WHERE actor_id=? AND operation='PROVISION_SHIP'
                    """, userId);
            jdbc.update("DELETE FROM \"UserRole\" WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM \"User\" WHERE user_id = ?", userId);
        }
        testServiceIds.forEach(catalogueServices::deleteById);
        for (Path archive : tempArchives) {
            try {
                Files.deleteIfExists(archive);
            } catch (IOException ignored) {
            }
        }
    }

    private Ship createShip(int totalDecks) {
        Ship ship = new Ship();
        ship.setShipCode("IT-" + UUID.randomUUID().toString().substring(0, 12));
        ship.setShipName("Import Integration " + UUID.randomUUID().toString().substring(0, 6));
        ship.setStatus(ShipStatus.READY);
        ship.setTotalCapacity(1000);
        ship.setTotalDecks(totalDecks);
        Ship saved = ships.saveAndFlush(ship);
        testShipIds.add(saved.getShipId());
        return saved;
    }

    private String token(Role role) {
        User user = new User();
        user.setUsername("import-test-" + UUID.randomUUID().toString().substring(0, 12));
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(role));
        User saved = users.saveAndFlush(user);
        testUserIds.add(saved.getUserId());
        return jwt.generateToken(saved.getUsername(), saved.getUserId());
    }

    private UUID register(UUID shipId, String key, String version, String token) {
        // Legacy draft-import tests need a registered model before running the partial importer.
        return assetService.register(shipId, key, storedVersions.get(key + "|" + version));
    }

    private JsonNode importJob(UUID shipId, UUID modelId, String token) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post("/ships/{shipId}/models/{modelId}/imports", shipId, modelId)
                                        .header("Authorization", "Bearer " + token))
                        .andExpect(status().isCreated())
                        .andReturn();
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private JsonNode importJobUnchecked(UUID shipId, UUID modelId, String token) {
        try {
            return importJob(shipId, modelId, token);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private JsonNode registerManifestUnchecked(String key) {
        return mapper.valueToTree(assetService.createShipFromManifest(key, null));
    }

    private void upload(String key, String version, Path zip, String token) throws Exception {
        assertEquals(201, uploadStatus(key, version, zip, token));
    }

    private Path committedBundle(String version) throws IOException {
        Path root = Path.of(System.getProperty("casms.test.zaandamBundle",
                "model-assets/zaandam/v10")).toAbsolutePath();
        Path archive = Files.createTempFile("casms-zaandam-", ".zip");
        try (ZipOutputStream zip =
                new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(archive)))) {
            zip.setLevel(1);
            try (var paths = Files.walk(root)) {
                for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
                    String relative =
                            root.relativize(file).toString().replace(File.separatorChar, '/');
                    zip.putNextEntry(
                            new ZipEntry(ASSET_PREFIX + "zaandam/" + version + "/" + relative));
                    try (InputStream in = Files.newInputStream(file)) {
                        in.transferTo(zip);
                    }
                    zip.closeEntry();
                }
            }
        }
        tempArchives.add(archive);
        return archive;
    }

    private Path smallBundle(
            String key,
            String version,
            String cabinType,
            String cabinNumber,
            boolean matchingNodeIds)
            throws Exception {
        return smallBundle(key, version, cabinType, cabinNumber, matchingNodeIds, true);
    }

    private Path smallBundle(
            String key,
            String version,
            String cabinType,
            String cabinNumber,
            boolean matchingNodeIds,
            boolean includeCabin)
            throws Exception {
        return smallBundle(
                key,
                version,
                cabinType,
                cabinNumber,
                matchingNodeIds,
                includeCabin,
                "Mini Deck",
                1.0,
                List.of(5.0, 2.8, 6.5),
                false);
    }

    private Path smallBundle(
            String key,
            String version,
            String cabinType,
            String cabinNumber,
            boolean matchingNodeIds,
            boolean includeCabin,
            String deckName,
            double elevation,
            List<Double> cabinSize)
            throws Exception {
        return smallBundle(
                key,
                version,
                cabinType,
                cabinNumber,
                matchingNodeIds,
                includeCabin,
                deckName,
                elevation,
                cabinSize,
                false,
                1,
                8);
    }

    private Path smallBundle(
            String key,
            String version,
            String cabinType,
            String cabinNumber,
            boolean matchingNodeIds,
            boolean includeCabin,
            String deckName,
            double elevation,
            List<Double> cabinSize,
            boolean duplicateCabinNumber)
            throws Exception {
        return smallBundle(
                key,
                version,
                cabinType,
                cabinNumber,
                matchingNodeIds,
                includeCabin,
                deckName,
                elevation,
                cabinSize,
                duplicateCabinNumber,
                1,
                8);
    }

    private Path smallBundle(
            String key,
            String version,
            String cabinType,
            String cabinNumber,
            boolean matchingNodeIds,
            boolean includeCabin,
            String deckName,
            double elevation,
            List<Double> cabinSize,
            boolean duplicateCabinNumber,
            int deckNumber,
            int deckCount)
            throws Exception {
        return smallBundle(
                key,
                version,
                cabinType,
                cabinNumber,
                matchingNodeIds,
                includeCabin,
                deckName,
                elevation,
                cabinSize,
                duplicateCabinNumber,
                deckNumber,
                deckCount,
                2);
    }

    private Path smallBundle(
            String key,
            String version,
            String cabinType,
            String cabinNumber,
            boolean matchingNodeIds,
            boolean includeCabin,
            String deckName,
            double elevation,
            List<Double> cabinSize,
            boolean duplicateCabinNumber,
            int deckNumber,
            int deckCount,
            int passengerCapacity)
            throws Exception {
        Map<String, Object> deckExtras =
                Map.of(
                        "entityType",
                        "deck",
                        "modelId",
                        "mini-d01",
                        "deckNumber",
                        deckNumber,
                        "deckName",
                        deckName,
                        "elevation",
                        elevation);
        Map<String, Object> cabinExtras =
                Map.of(
                        "entityType",
                        "cabin",
                        "modelId",
                        "mini-c1",
                        "deckId",
                        "mini-d01",
                        "cabinNumber",
                        cabinNumber,
                        "cabinType",
                        cabinType,
                        "capacity",
                        2);
        List<Object> nodes = new ArrayList<>();
        nodes.add(Map.of("name", "DECK_01", "extras", deckExtras));
        if (matchingNodeIds && includeCabin)
            nodes.add(
                    Map.of(
                            "name",
                            "CABIN_01",
                            "translation",
                            List.of(0.0, 1.0, 0.0),
                            "extras",
                            cabinExtras));
        if (matchingNodeIds && includeCabin && duplicateCabinNumber) {
            nodes.add(
                    Map.of(
                            "name",
                            "CABIN_02",
                            "translation",
                            List.of(1.0, 1.0, 0.0),
                            "extras",
                            Map.of(
                                    "entityType",
                                    "cabin",
                                    "modelId",
                                    "mini-c2",
                                    "deckId",
                                    "mini-d01",
                                    "cabinNumber",
                                    cabinNumber,
                                    "cabinType",
                                    cabinType,
                                    "capacity",
                                    2)));
        }
        byte[] glb =
                glb(
                        mapper.writeValueAsBytes(
                                Map.of("asset", Map.of("version", "2.0"), "nodes", nodes)));
        Map<String, Object> deck =
                Map.of(
                        "id",
                        "mini-d01",
                        "number",
                        deckNumber,
                        "name",
                        deckName,
                        "elevationMetres",
                        elevation,
                        "asset",
                        "decks/deck_01.glb",
                        "cabinIds",
                        includeCabin
                                ? (duplicateCabinNumber
                                        ? List.of("mini-c1", "mini-c2")
                                        : List.of("mini-c1"))
                                : List.of());
        Map<String, Object> cabin =
                Map.of(
                        "id",
                        "mini-c1",
                        "cabinNumber",
                        cabinNumber,
                        "deckId",
                        "mini-d01",
                        "cabinType",
                        cabinType,
                        "capacity",
                        2,
                        "isAccessible",
                        false,
                        "size",
                        cabinSize,
                        "areaSquareMetres",
                        32.5,
                        "balconySquareMetres",
                        0.0);
        Map<String, Object> duplicateCabin =
                Map.of(
                        "id",
                        "mini-c2",
                        "cabinNumber",
                        cabinNumber,
                        "deckId",
                        "mini-d01",
                        "cabinType",
                        cabinType,
                        "capacity",
                        2,
                        "isAccessible",
                        false,
                        "size",
                        cabinSize,
                        "areaSquareMetres",
                        32.5,
                        "balconySquareMetres",
                        0.0);
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schemaVersion", 10);
        manifest.put("revision", "integration-v1");
        manifest.put("modelId", key);
        manifest.put("name", "Mini Ship");
        manifest.put("shipManufacturerName", "Example Manufacturer");
        manifest.put("manufacturerShipCode", key + "-" + version);
        manifest.put("shipyard", "Example Yard");
        manifest.put("buildCountry", "NL");
        manifest.put("buildYear", 2000);
        manifest.put("engineType", "Diesel");
        manifest.put("length", 100.0);
        manifest.put("beam", 20.0);
        manifest.put("deckCount", deckCount);
        manifest.put("passengerCapacity", passengerCapacity);
        manifest.put("units", "metre");
        manifest.put("gltfAxes", Map.of("up", "+Y", "bow", "+X", "port", "-Z"));
        manifest.put("assets", Map.of("full", "ship.glb", "exterior", "exterior.glb"));
        manifest.put("decks", List.of(deck));
        manifest.put("serviceDecks", List.of());
        manifest.put(
                "cabins",
                includeCabin
                        ? (duplicateCabinNumber ? List.of(cabin, duplicateCabin) : List.of(cabin))
                        : List.of());
        byte[] manifestBytes = mapper.writeValueAsBytes(manifest);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("manifest.json", manifestBytes);
        entries.put("ship.glb", glb);
        entries.put("exterior.glb", glb);
        entries.put("decks/deck_01.glb", glb);
        Map<String, String> checksums = new LinkedHashMap<>();
        for (var entry : entries.entrySet())
            checksums.put(entry.getKey(), sha256(entry.getValue()));
        entries.put("checksums.json", mapper.writeValueAsBytes(checksums));
        Path archive = Files.createTempFile("casms-mini-", ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(
                        new ZipEntry(ASSET_PREFIX + key + "/" + version + "/" + entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        tempArchives.add(archive);
        return archive;
    }

    private Path bundleWithServiceDeck(String key, String version, String cabinNumber)
            throws Exception {
        Path original = smallBundle(key, version, "OCEAN_VIEW", cabinNumber, true);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (var zip = new java.util.zip.ZipInputStream(Files.newInputStream(original))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name =
                        entry.getName()
                                .substring((ASSET_PREFIX + key + "/" + version + "/").length());
                entries.put(name, zip.readAllBytes());
            }
        }
        var manifest =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        mapper.readTree(entries.get("manifest.json"));
        manifest.putArray("serviceDecks")
                .add(
                        mapper.valueToTree(
                                Map.of(
                                        "id",
                                        "mini-d00",
                                        "number",
                                        0,
                                        "name",
                                        "Technical Deck",
                                        "elevationMetres",
                                        -2.0,
                                        "asset",
                                        "services/service_00.glb",
                                        "cabinIds",
                                        List.of())));
        // Deliberately insufficient catalogue metadata: this must be ignored, not guessed or
        // created.
        manifest.putArray("areas")
                .add(
                        mapper.valueToTree(
                                Map.of(
                                        "id",
                                        "area-restaurant",
                                        "name",
                                        "Unconfigured restaurant",
                                        "deckId",
                                        "mini-d00")));
        byte[] source = entries.get("ship.glb");
        int jsonLength = ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN).getInt(12);
        var gltf =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        mapper.readTree(Arrays.copyOfRange(source, 20, 20 + jsonLength));
        var nodes = (com.fasterxml.jackson.databind.node.ArrayNode) gltf.path("nodes");
        nodes.add(
                mapper.valueToTree(
                        Map.of(
                                "name",
                                "SERVICE_00",
                                "extras",
                                Map.of(
                                        "entityType",
                                        "serviceDeck",
                                        "modelId",
                                        "mini-d00",
                                        "deckNumber",
                                        0,
                                        "deckName",
                                        "Technical Deck",
                                        "elevation",
                                        -2.0))));
        nodes.add(
                mapper.valueToTree(
                        Map.of(
                                "name",
                                "Unconfigured restaurant",
                                "extras",
                                Map.of("entityType", "service", "modelId", "area-restaurant"))));
        byte[] updatedGlb = glb(mapper.writeValueAsBytes(gltf));
        entries.put("manifest.json", mapper.writeValueAsBytes(manifest));
        entries.put("ship.glb", updatedGlb);
        entries.put("exterior.glb", updatedGlb);
        entries.put("services/service_00.glb", updatedGlb);
        Map<String, String> checksums = new LinkedHashMap<>();
        for (var e : entries.entrySet())
            if (!e.getKey().equals("checksums.json"))
                checksums.put(e.getKey(), sha256(e.getValue()));
        entries.put("checksums.json", mapper.writeValueAsBytes(checksums));
        Path result = Files.createTempFile("casms-service-deck-", ".zip");
        tempArchives.add(result);
        try (var zip = new ZipOutputStream(Files.newOutputStream(result))) {
            for (var e : entries.entrySet()) {
                zip.putNextEntry(
                        new ZipEntry(ASSET_PREFIX + key + "/" + version + "/" + e.getKey()));
                zip.write(e.getValue());
                zip.closeEntry();
            }
        }
        return result;
    }

    private Path bundleWithPhysicalAreas(String key, String version, String mode) throws Exception {
        Path original = bundleWithServiceDeck(key, version, "M-001");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        String prefix = ASSET_PREFIX + key + "/" + version + "/";
        try (var zip = new java.util.zip.ZipInputStream(Files.newInputStream(original))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null)
                entries.put(entry.getName().substring(prefix.length()), zip.readAllBytes());
        }
        var manifest =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        mapper.readTree(entries.get("manifest.json"));
        var areas = (com.fasterxml.jackson.databind.node.ArrayNode) manifest.path("areas");
        var gym =
                mapper.valueToTree(
                        Map.of(
                                "id",
                                "physical-gym",
                                "name",
                                "Gym",
                                "kind",
                                "gym",
                                "deckId",
                                mode.equals("unknown-deck") ? "unknown" : "mini-d00",
                                "deckNumber",
                                0,
                                "size",
                                mode.equals("invalid-size") ? List.of(-4, 3, 5) : List.of(4, 3, 5),
                                "center",
                                List.of(mode.equals("moved") ? 7 : 3, -1, 2),
                                "components",
                                List.of(Map.of("name", "Gym equipment"))));
        areas.add(gym);
        areas.add(
                mapper.valueToTree(
                        Map.of(
                                "id",
                                "physical-lounge",
                                "name",
                                "Lounge",
                                "kind",
                                "lounge",
                                "deckId",
                                "mini-d01",
                                "deckNumber",
                                1,
                                "size",
                                List.of(6, 3, 4))));
        byte[] source = entries.get("ship.glb");
        int jsonLength = ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN).getInt(12);
        var gltf =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        mapper.readTree(Arrays.copyOfRange(source, 20, 20 + jsonLength));
        var nodes = (com.fasterxml.jackson.databind.node.ArrayNode) gltf.path("nodes");
        JsonNode gymNode =
                mapper.valueToTree(
                        Map.of(
                                "name",
                                "Gym",
                                "translation",
                                List.of(mode.equals("moved") ? 7 : 3, -1, 2),
                                "rotation",
                                mode.equals("invalid-transform")
                                        ? List.of(0, 0, 0, 0)
                                        : List.of(0, Math.sqrt(.5), 0, Math.sqrt(.5)),
                                "scale",
                                List.of(2, 1, 1),
                                "extras",
                                Map.of(
                                        "entityType",
                                        "area",
                                        "modelId",
                                        "physical-gym",
                                        "areaName",
                                        "Gym",
                                        "areaKind",
                                        "gym",
                                        "deckNumber",
                                        0)));
        if (!mode.equals("missing-node")) nodes.add(gymNode);
        if (mode.equals("duplicate-node")) nodes.add(gymNode.deepCopy());
        nodes.add(
                mapper.valueToTree(
                        Map.of(
                                "name",
                                "Lounge",
                                "translation",
                                List.of(5, 1, 3),
                                "extras",
                                Map.of(
                                        "entityType",
                                        "area",
                                        "modelId",
                                        "physical-lounge",
                                        "areaName",
                                        "Lounge",
                                        "areaKind",
                                        "lounge",
                                        "deckNumber",
                                        1))));
        byte[] updatedGlb = glb(mapper.writeValueAsBytes(gltf));
        if (mode.equals("moved")) manifest.remove("revision");
        entries.put("manifest.json", mapper.writeValueAsBytes(manifest));
        entries.put("ship.glb", updatedGlb);
        entries.put("exterior.glb", updatedGlb);
        entries.put("services/service_00.glb", updatedGlb);
        Map<String, String> checksums = new LinkedHashMap<>();
        for (var e : entries.entrySet())
            if (!e.getKey().equals("checksums.json"))
                checksums.put(e.getKey(), sha256(e.getValue()));
        entries.put("checksums.json", mapper.writeValueAsBytes(checksums));
        Path result = Files.createTempFile("casms-physical-areas-", ".zip");
        tempArchives.add(result);
        try (var zip = new ZipOutputStream(Files.newOutputStream(result))) {
            for (var e : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry((mode.equals("moved") ? "" : prefix) + e.getKey()));
                zip.write(e.getValue());
                zip.closeEntry();
            }
        }
        return result;
    }

    private static byte[] glb(byte[] json) throws IOException {
        int paddedLength = (json.length + 3) & ~3;
        int totalLength = 20 + paddedLength;
        ByteBuffer buffer = ByteBuffer.allocate(totalLength).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("glTF".getBytes(StandardCharsets.US_ASCII))
                .putInt(2)
                .putInt(totalLength)
                .putInt(paddedLength)
                .putInt(0x4E4F534A);
        buffer.put(json);
        while (buffer.position() < totalLength) buffer.put((byte) 0x20);
        return buffer.array();
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String uniqueKey(String prefix) {
        return "it-" + prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static final class FileBackedMultipartFile extends MockMultipartFile {
        private final Path path;

        private FileBackedMultipartFile(String name, Path path) {
            super(name, path.getFileName().toString(), "application/zip", new byte[0]);
            this.path = path;
        }

        @Override
        public boolean isEmpty() {
            try {
                return Files.size(path) == 0;
            } catch (IOException e) {
                return true;
            }
        }

        @Override
        public long getSize() {
            try {
                return Files.size(path);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public byte[] getBytes() throws IOException {
            return Files.readAllBytes(path);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return Files.newInputStream(path);
        }

        @Override
        public void transferTo(File dest) throws IOException {
            Files.copy(path, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
