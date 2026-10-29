package com.casms.ship.service;

import com.casms.identity.entity.User;
import com.casms.identity.enums.Role;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.repository.UserRepository;
import com.casms.security.jwt.JwtService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real PostgreSQL + Spring MVC import workflow tests. No importer, storage, security or repositories are mocked. */
@SpringBootTest
@AutoConfigureMockMvc
class ShipModelImportPostgresIntegrationTest {
    private static final String ASSET_PREFIX = "model-assets/";

    @Autowired MockMvc mockMvc;
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
    private final List<UUID> testUserIds = new ArrayList<>();
    private final Set<String> uploadedBundles = new LinkedHashSet<>();
    private final List<Path> tempArchives = new ArrayList<>();
    private final List<UUID> testServiceIds = new ArrayList<>();

    @Test
    void physicalAreasPersistAsFacilitiesAndReadBackWithoutCreatingServices() throws Exception {
        Ship ship=createShip(1);String token=token(Role.SHIP_MANAGER);String key=uniqueKey("physical-areas");
        long definitions=catalogueServices.count();
        upload(key,"v1",bundleWithPhysicalAreas(key,"v1","valid"),token);
        UUID model=register(ship.getShipId(),key,"v1",token);
        JsonNode job=importJob(ship.getShipId(),model,token).path("data");
        assertEquals("SUCCEEDED",job.path("status").asText());assertEquals(5,job.path("totalObjects").asInt());
        assertEquals(5,job.path("objectsSucceeded").asInt());
        var all=objects.findByShipModelShipModelId(model);
        var facilities=all.stream().filter(o->o.getObjectType()==com.casms.ship.enums.ShipModelObjectType.FACILITY).toList();
        assertEquals(2,facilities.size());assertEquals(1,cabins.countByShipShipId(ship.getShipId()));
        for(var facility:facilities) {
            assertNull(facility.getCabin());assertNotNull(facility.getDeck());
            Deck physicalDeck = decks.findById(facility.getDeck().getDeckId()).orElseThrow();
            assertEquals(ship.getShipId(),physicalDeck.getShip().getShipId());
            var metadata=mapper.readTree(facility.getMetadataJson());
            assertEquals(3,metadata.path("size").size());assertEquals(16,metadata.path("worldMatrix").size());
            assertEquals("area",metadata.path("gltfNode").path("extras").path("entityType").asText());
        }
        var gym=facilities.stream().filter(o->o.getNodeKey().equals("physical-gym")).findFirst().orElseThrow();
        assertTrue(decks.findById(gym.getDeck().getDeckId()).orElseThrow().isServiceLevel());assertEquals(3.0,gym.getPositionX(),1e-9);
        assertEquals(Math.PI/2,gym.getRotationY(),1e-9);assertEquals(2.0,gym.getScaleX(),1e-9);
        Set<UUID> ids=new HashSet<>();facilities.forEach(f->ids.add(f.getShipModelObjectId()));
        importJob(ship.getShipId(),model,token);
        assertEquals(ids,objects.findByShipModelShipModelId(model).stream().filter(o->o.getObjectType()==com.casms.ship.enums.ShipModelObjectType.FACILITY).map(com.casms.ship.entity.ShipModelObject::getShipModelObjectId).collect(java.util.stream.Collectors.toSet()));
        assertEquals(definitions,catalogueServices.count());
        mockMvc.perform(get("/ships/{shipId}/models/{modelId}/objects",ship.getShipId(),model).header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data",hasSize(5)));
        mockMvc.perform(post("/ships/{shipId}/models/{modelId}/activate",ship.getShipId(),model).header("Authorization","Bearer "+token)).andExpect(status().isOk());
        JsonNode config=mapper.readTree(mockMvc.perform(get("/ships/{shipId}/3d-configuration",ship.getShipId()).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).path("data");
        int count=0;
        for(JsonNode d:config.path("decks"))for(JsonNode f:d.path("facilities")) {
            count++;assertEquals(d.path("deckId").asText(),f.path("deckId").asText());assertTrue(f.path("dimensions").path("x").asDouble()>0);
        }
        assertEquals(2,count);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"unknown-deck","invalid-size","invalid-transform"})
    void invalidAreaRecordsAnIssueWithoutCreatingFalseCabins(String mode) throws Exception {
        Ship ship=createShip(1);String token=token(Role.SHIP_MANAGER);String key=uniqueKey("area-partial");
        upload(key,"v1",bundleWithPhysicalAreas(key,"v1",mode),token);UUID model=register(ship.getShipId(),key,"v1",token);
        JsonNode result=importJob(ship.getShipId(),model,token).path("data");
        assertEquals("PARTIAL",result.path("status").asText());assertEquals(1,result.path("objectsFailed").asInt());
        assertEquals(4,result.path("objectsSucceeded").asInt());assertEquals(1,cabins.countByShipShipId(ship.getShipId()));
        assertEquals(1,objects.findByShipModelShipModelId(model).stream().filter(o->o.getObjectType()==com.casms.ship.enums.ShipModelObjectType.FACILITY).count());
        mockMvc.perform(get("/ships/{shipId}/imports/{jobId}/issues",ship.getShipId(),UUID.fromString(result.path("jobId").asText())).header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].objectType").value("FACILITY"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"duplicate-node","missing-node"})
    void ambiguousPhysicalStructureFailsBeforeWritingDecks(String mode) throws Exception {
        Ship ship=createShip(1);String token=token(Role.SHIP_MANAGER);String key=uniqueKey("area-identity");
        upload(key,"v1",bundleWithPhysicalAreas(key,"v1",mode),token);UUID model=register(ship.getShipId(),key,"v1",token);
        mockMvc.perform(post("/ships/{shipId}/models/{modelId}/imports",ship.getShipId(),model).header("Authorization","Bearer "+token)).andExpect(status().isBadRequest());
        assertEquals(0,decks.countByShipShipId(ship.getShipId()));assertEquals(0,objects.findByShipModelShipModelId(model).size());
    }

    @Test
    void databaseFailureDuringAreaPersistenceRollsBackAllImportedStructure() throws Exception {
        Ship ship=createShip(1);String token=token(Role.SHIP_MANAGER);String key=uniqueKey("area-rollback");
        upload(key,"v1",bundleWithPhysicalAreas(key,"v1","valid"),token);UUID model=register(ship.getShipId(),key,"v1",token);
        String trigger="it_area_"+UUID.randomUUID().toString().replace("-","");
        jdbc.execute("CREATE FUNCTION "+trigger+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected physical area persistence failure'; END; $$");
        try {
            jdbc.execute("CREATE TRIGGER "+trigger+" BEFORE INSERT ON \"ShipModelObject\" FOR EACH ROW WHEN (NEW.ship_model_id='"+model+"'::uuid AND NEW.node_key='physical-lounge') EXECUTE FUNCTION "+trigger+"()");
            mockMvc.perform(post("/ships/{shipId}/models/{modelId}/imports",ship.getShipId(),model).header("Authorization","Bearer "+token)).andExpect(status().is5xxServerError());
            assertEquals(0,objects.findByShipModelShipModelId(model).size());assertEquals(0,decks.countByShipShipId(ship.getShipId()));assertEquals(0,cabins.countByShipShipId(ship.getShipId()));
            assertEquals(com.casms.ship.enums.ModelImportStatus.FAILED,jobs.findByShipModelShipModelIdOrderByStartedAtDesc(model).getFirst().getStatus());
        } finally {jdbc.execute("DROP TRIGGER IF EXISTS "+trigger+" ON \"ShipModelObject\"");jdbc.execute("DROP FUNCTION "+trigger+"()");}
    }

    @Test
    void importsEveryDeckIgnoresModelServicesAndSupportsManualAssignments() throws Exception {
        Ship ship = createShip(1);
        String manager = token(Role.SHIP_MANAGER);
        String serviceManager = token(Role.ACTIVITY_SERVICE_MANAGER);
        String key = uniqueKey("all-decks");
        long serviceCount = catalogueServices.count();
        upload(key, "v1", bundleWithServiceDeck(key, "v1", "M-001"), manager);
        UUID modelId = register(ship.getShipId(), key, "v1", manager);
        JsonNode result = importJob(ship.getShipId(), modelId, manager).path("data");
        assertEquals("SUCCEEDED", result.path("status").asText());
        assertEquals(2, result.path("decksImported").asInt());
        assertEquals(3, result.path("totalObjects").asInt());
        assertEquals(serviceCount, catalogueServices.count(), "model venue names must not create catalogue services");
        Deck serviceDeck = decks.findByShipShipIdAndDeckNumber(ship.getShipId(), 0).orElseThrow();
        assertTrue(serviceDeck.isServiceLevel());
        assertEquals(-2.0, serviceDeck.getModelPositionY());
        assertEquals("mini-d00", mapper.readTree(serviceDeck.getModelMetadataJson()).path("id").asText());
        assertEquals(1, decks.countByShipShipIdAndServiceLevelFalse(ship.getShipId()));
        assertEquals(2, decks.countByShipShipId(ship.getShipId()));
        assertEquals(3, objects.findByShipModelShipModelId(modelId).size());
        mockMvc.perform(post("/ships/{shipId}/validate", ship.getShipId()).header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk());
        mockMvc.perform(get("/decks/by-ship/{shipId}", ship.getShipId()).header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2));
        mockMvc.perform(post("/cabins").header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of(
                                "deckId", serviceDeck.getDeckId(), "cabinNumber", "C00", "cabinType", "INTERIOR", "capacity", 2))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CABIN_PASSENGER_DECK_REQUIRED"));
        mockMvc.perform(get("/ships/{shipId}/models/{modelId}/deck-mappings", ship.getShipId(), modelId)
                        .header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(2)));
        var catalogue = new com.casms.servicepackage.entity.Service();
        catalogue.setServiceCode("IT-SV-" + UUID.randomUUID().toString().substring(0, 12));
        catalogue.setServiceName("Manual restaurant"); catalogue.setServiceType("DINING");
        catalogue.setStatus(com.casms.servicepackage.enums.ServiceStatus.ACTIVE);
        catalogue = catalogueServices.saveAndFlush(catalogue);
        UUID serviceId = catalogue.getServiceId(); testServiceIds.add(serviceId);
        MvcResult assigned = mockMvc.perform(put("/decks/{deckId}/services/{serviceId}", serviceDeck.getDeckId(), serviceId)
                        .header("Authorization", "Bearer " + serviceManager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.serviceId").value(serviceId.toString())).andReturn();
        String assignment = mapper.readTree(assigned.getResponse().getContentAsByteArray()).path("data").path("assignmentId").asText();
        mockMvc.perform(put("/decks/{deckId}/services/{serviceId}", serviceDeck.getDeckId(), serviceId)
                        .header("Authorization", "Bearer " + serviceManager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.assignmentId").value(assignment));
        // Reimport does not duplicate decks or erase manual service assignments.
        importJob(ship.getShipId(), modelId, manager);
        assertEquals(serviceDeck.getDeckId(), decks.findByShipShipIdAndDeckNumber(ship.getShipId(), 0).orElseThrow().getDeckId());
        mockMvc.perform(get("/decks/{deckId}/services", serviceDeck.getDeckId()).header("Authorization", "Bearer " + serviceManager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(1)));
        upload(key, "v2", bundleWithServiceDeck(key, "v2", "M-001"), manager);
        UUID nextModel = register(ship.getShipId(), key, "v2", manager);
        assertEquals("SUCCEEDED", importJob(ship.getShipId(), nextModel, manager).path("data").path("status").asText());
        assertEquals(2, decks.countByShipShipId(ship.getShipId()));
        mockMvc.perform(get("/decks/{deckId}/services", serviceDeck.getDeckId()).header("Authorization", "Bearer " + serviceManager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].assignmentId").value(assignment));
        mockMvc.perform(put("/decks/{deckId}/services/{serviceId}", serviceDeck.getDeckId(), serviceId)
                        .header("Authorization", "Bearer " + manager)).andExpect(status().isForbidden());
        mockMvc.perform(put("/decks/{deckId}/services/{serviceId}", UUID.randomUUID(), serviceId)
                        .header("Authorization", "Bearer " + serviceManager)).andExpect(status().isNotFound());
        mockMvc.perform(put("/decks/{deckId}/services/{serviceId}", serviceDeck.getDeckId(), UUID.randomUUID())
                        .header("Authorization", "Bearer " + serviceManager)).andExpect(status().isNotFound());
        // All physical levels appear once, with usable UUIDs in the configuration.
        mockMvc.perform(post("/ships/{shipId}/models/{modelId}/activate", ship.getShipId(), modelId)
                        .header("Authorization", "Bearer " + manager)).andExpect(status().isOk());
        JsonNode config = mapper.readTree(mockMvc.perform(get("/ships/{shipId}/3d-configuration", ship.getShipId())
                        .header("Authorization", "Bearer " + manager)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray()).path("data").path("decks");
        assertEquals(2, config.size());
        assertTrue(java.util.stream.StreamSupport.stream(config.spliterator(), false)
                .anyMatch(d -> d.path("serviceLevel").asBoolean() && serviceDeck.getDeckId().toString().equals(d.path("deckId").asText())));
        for (int i=0; i<2; i++) mockMvc.perform(delete("/decks/{deckId}/services/{serviceId}", serviceDeck.getDeckId(), serviceId)
                        .header("Authorization", "Bearer " + serviceManager)).andExpect(status().isNoContent());
        assertTrue(catalogueServices.existsById(serviceId));
        flyway.validate();
    }

    @Test
    void serviceDeckWritesRollbackWhenCabinPersistenceFails() throws Exception {
        Ship ship = createShip(1); String manager = token(Role.SHIP_MANAGER);
        String key = uniqueKey("deck-rollback");
        upload(key, "v1", bundleWithServiceDeck(key, "v1", "CABIN-NUM-TOO-LONG"), manager);
        UUID modelId = register(ship.getShipId(), key, "v1", manager);
        mockMvc.perform(post("/ships/{shipId}/models/{modelId}/imports", ship.getShipId(), modelId)
                        .header("Authorization", "Bearer " + manager)).andExpect(status().isConflict());
        assertEquals(0, decks.countByShipShipId(ship.getShipId()));
        assertEquals(0, objects.findByShipModelShipModelId(modelId).size());
        assertEquals(com.casms.ship.enums.ModelImportStatus.FAILED,
                jobs.findByShipModelShipModelIdOrderByStartedAtDesc(modelId).getFirst().getStatus());
    }

    @Test
    void concurrentManualAssignmentsHaveOneDatabaseIdentity() throws Exception {
        Ship ship = createShip(1); String manager = token(Role.SHIP_MANAGER);
        String serviceManager = token(Role.ACTIVITY_SERVICE_MANAGER); String key = uniqueKey("deck-link");
        upload(key, "v1", bundleWithServiceDeck(key, "v1", "M-001"), manager);
        UUID model = register(ship.getShipId(), key, "v1", manager); importJob(ship.getShipId(), model, manager);
        UUID deck = decks.findByShipShipIdAndDeckNumber(ship.getShipId(), 0).orElseThrow().getDeckId();
        var service = new com.casms.servicepackage.entity.Service();
        service.setServiceCode("IT-SV-" + UUID.randomUUID().toString().substring(0,12));
        service.setServiceName("Manual gym"); service.setServiceType("FITNESS");
        service.setStatus(com.casms.servicepackage.enums.ServiceStatus.INACTIVE);
        UUID serviceId = catalogueServices.saveAndFlush(service).getServiceId(); testServiceIds.add(serviceId);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<String> assign = () -> mapper.readTree(mockMvc.perform(
                    put("/decks/{deckId}/services/{serviceId}", deck, serviceId).header("Authorization", "Bearer " + serviceManager))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray())
                    .path("data").path("assignmentId").asText();
            var first = executor.submit(assign); var second = executor.submit(assign);
            assertEquals(first.get(), second.get());
        } finally { executor.shutdownNow(); }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM \"DeckServiceAssignment\" WHERE deck_id=? AND service_id=?",
                Integer.class, deck, serviceId));
    }

    @Test
    void shipDeckCabinReadAndReadinessRoutesRequireTheirPermissions() throws Exception {
        Ship ship = createShip(2);
        String shipManager = token(Role.SHIP_MANAGER);
        String staffWithoutShipRead = token(Role.FINANCE_OFFICER);

        mockMvc.perform(get("/ships/{shipId}", ship.getShipId()).header("Authorization", "Bearer " + shipManager))
                .andExpect(status().isOk());
        mockMvc.perform(get("/ships/{shipId}", ship.getShipId()).header("Authorization", "Bearer " + staffWithoutShipRead))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/decks/by-ship/{shipId}", ship.getShipId()).header("Authorization", "Bearer " + shipManager))
                .andExpect(status().isOk());
        mockMvc.perform(get("/decks/by-ship/{shipId}", ship.getShipId()).header("Authorization", "Bearer " + staffWithoutShipRead))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/cabins/by-ship/{shipId}", ship.getShipId()).header("Authorization", "Bearer " + shipManager))
                .andExpect(status().isOk());
        mockMvc.perform(get("/cabins/by-ship/{shipId}", ship.getShipId()).header("Authorization", "Bearer " + staffWithoutShipRead))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/ships/{shipId}/readiness-assessment", ship.getShipId())
                        .header("Authorization", "Bearer " + shipManager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readiness").value("NOT_READY"))
                .andExpect(jsonPath("$.data.deckCount").value(0))
                .andExpect(jsonPath("$.data.cabinCount").value(0));
        mockMvc.perform(get("/ships/{shipId}/readiness-assessment", ship.getShipId())
                        .header("Authorization", "Bearer " + staffWithoutShipRead))
                .andExpect(status().isForbidden());
    }

    @Test
    void committedZaandamBundle_uploadImportReadAndRetry_isIdempotent() throws Exception {
        Ship ship = createShip(10);
        String token = token(Role.SHIP_MANAGER);
        String version = "it-" + UUID.randomUUID().toString().substring(0, 8);
        Path archive = committedBundle(version);
        try {
            upload("zaandam", version, archive, token);
            UUID modelId = register(ship.getShipId(), "zaandam", version, token);

            Ship synchronizedShip = ships.findById(ship.getShipId()).orElseThrow();
            assertEquals("zaandam", synchronizedShip.getManifestModelId());
            assertEquals("MS Zaandam", synchronizedShip.getShipName());
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
            assertEquals(64, objects.findByShipModelShipModelId(modelId).stream()
                    .filter(o -> o.getObjectType() == com.casms.ship.enums.ShipModelObjectType.FACILITY && o.getDeck() != null && o.getCabin() == null).count());
            assertEquals(291L, objects.findByShipModelShipModelId(modelId).stream().filter(o -> o.getCabin() != null).count());

            mockMvc.perform(get("/ships/{shipId}/models/{modelId}/imports", ship.getShipId(), modelId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(1)));
            mockMvc.perform(get("/ships/{shipId}/imports/{jobId}/issues", ship.getShipId(), firstJobId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(0)));
            mockMvc.perform(get("/ships/{shipId}/models/{modelId}/objects", ship.getShipId(), modelId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(367)));

            Ship otherShip = createShip(10);
            mockMvc.perform(get("/ships/{shipId}/imports/{jobId}", otherShip.getShipId(), firstJobId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get("/ships/{shipId}/imports/{jobId}/issues", otherShip.getShipId(), firstJobId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isNotFound());
            mockMvc.perform(post("/ships/{shipId}/models/{modelId}/imports", otherShip.getShipId(), modelId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isNotFound());

            JsonNode retry = importJob(ship.getShipId(), modelId, token);
            assertEquals("SUCCEEDED", retry.path("data").path("status").asText());
            assertEquals(367, retry.path("data").path("objectsSucceeded").asInt());
            assertEquals(12, decks.countByShipShipId(ship.getShipId()));
            assertEquals(291, cabins.countByShipShipId(ship.getShipId()));
            assertEquals(367, objects.findByShipModelShipModelId(modelId).size());
            mockMvc.perform(get("/ships/{shipId}/models/{modelId}/imports", ship.getShipId(), modelId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(2)));

            ShipModel model = models.findById(modelId).orElseThrow();
            model.setStatus(ShipModelStatus.READY);
            models.saveAndFlush(model);
            mockMvc.perform(post("/ships/{shipId}/models/{modelId}/imports", ship.getShipId(), modelId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isConflict());
            String passengerToken = token(Role.PASSENGER);
            mockMvc.perform(post("/ships/{shipId}/models/{modelId}/imports", ship.getShipId(), modelId)
                            .header("Authorization", "Bearer " + passengerToken))
                    .andExpect(status().isForbidden());
            String noShipReadToken = token(Role.FINANCE_OFFICER);
            mockMvc.perform(get("/ships/{shipId}/imports/{jobId}", ship.getShipId(), firstJobId)
                            .header("Authorization", "Bearer " + noShipReadToken))
                    .andExpect(status().isForbidden());
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    @Test
    void smallDatabaseBundles_recordObjectIssuesAndRollbackWholeTransaction() throws Exception {
        Ship ship = createShip(10);
        String token = token(Role.SHIP_MANAGER);

        String modelKey = uniqueKey("small-bundles");
        String partialKey = modelKey;
        String partialVersion = "partial-v1";
        upload(partialKey, partialVersion, smallBundle(partialKey, partialVersion, "INVALID_TYPE", "M-001", true), token);
        UUID partialModel = register(ship.getShipId(), partialKey, partialVersion, token);
        JsonNode partial = importJob(ship.getShipId(), partialModel, token);
        assertEquals("PARTIAL", partial.path("data").path("status").asText());
        assertEquals(1, partial.path("data").path("objectsSucceeded").asInt());
        assertEquals(1, partial.path("data").path("objectsFailed").asInt());
        UUID partialJob = UUID.fromString(partial.path("data").path("jobId").asText());
        mockMvc.perform(get("/ships/{shipId}/imports/{jobId}/issues", ship.getShipId(), partialJob)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].errorCode").value("INVALID_CABIN_TYPE"));
        assertEquals(1, decks.countByShipShipId(ship.getShipId()));
        assertEquals(0, cabins.countByShipShipId(ship.getShipId()));

        String rollbackKey = modelKey;
        String rollbackVersion = "rollback-v1";
        upload(rollbackKey, rollbackVersion, smallBundle(rollbackKey, rollbackVersion, "OCEAN_VIEW", "CABIN-NUM-TOO-LONG", true), token);
        UUID rollbackModel = register(ship.getShipId(), rollbackKey, rollbackVersion, token);
        MvcResult rollbackResponse = mockMvc.perform(post("/ships/{shipId}/models/{modelId}/imports", ship.getShipId(), rollbackModel)
                        .header("Authorization", "Bearer " + token)).andReturn();
        assertEquals(409, rollbackResponse.getResponse().getStatus(), rollbackResponse.getResponse().getContentAsString());
        var failedJobEntity = jobs.findByShipModelShipModelIdOrderByStartedAtDesc(rollbackModel).get(0);
        UUID failedJob = failedJobEntity.getModelImportJobId();
        assertEquals("FAILED", failedJobEntity.getStatus().name());
        mockMvc.perform(get("/ships/{shipId}/imports/{jobId}", ship.getShipId(), failedJob)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("FAILED"));
        assertEquals(1, decks.countByShipShipId(ship.getShipId()), "deck written before the cabin constraint failure must roll back");
        assertEquals(0, cabins.countByShipShipId(ship.getShipId()));
        assertEquals(1, objects.findByShipModelShipModelId(partialModel).size());
        assertTrue(objects.findByShipModelShipModelId(rollbackModel).isEmpty());

        String mismatchKey = modelKey;
        String mismatchVersion = "mismatch-v1";
        upload(mismatchKey, mismatchVersion, smallBundle(mismatchKey, mismatchVersion, "OCEAN_VIEW", "M-001", false), token);
        UUID mismatchModel = register(ship.getShipId(), mismatchKey, mismatchVersion, token);
        MvcResult mismatchResponse = mockMvc.perform(post("/ships/{shipId}/models/{modelId}/imports", ship.getShipId(), mismatchModel)
                        .header("Authorization", "Bearer " + token)).andReturn();
        assertEquals(400, mismatchResponse.getResponse().getStatus(), mismatchResponse.getResponse().getContentAsString());
        var mismatchJob = jobs.findByShipModelShipModelIdOrderByStartedAtDesc(mismatchModel).get(0);
        assertEquals("FAILED", mismatchJob.getStatus().name());
        assertTrue(objects.findByShipModelShipModelId(mismatchModel).isEmpty());

        mockMvc.perform(post("/ships/{shipId}/models/{modelId}/imports", UUID.randomUUID(), partialModel)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void laterModel_updatesModelManagedFieldsAndPreservesBusinessOwnedCabinFields() throws Exception {
        Ship ship = createShip(10);
        String token = token(Role.SHIP_MANAGER);
        String firstKey = uniqueKey("upsert-a");
        upload(firstKey, "v1", smallBundle(firstKey, "v1", "OCEAN_VIEW", "M-101", true), token);
        UUID firstModel = register(ship.getShipId(), firstKey, "v1", token);
        assertEquals("SUCCEEDED", importJob(ship.getShipId(), firstModel, token).path("data").path("status").asText());

        Deck existingDeck = decks.findByShipShipIdAndDeckNumber(ship.getShipId(), 1).orElseThrow();
        existingDeck.setPriceMultiplier(new java.math.BigDecimal("1.35"));
        decks.saveAndFlush(existingDeck);
        Cabin existingCabin = cabins.findByShipShipIdAndCabinNumber(ship.getShipId(), "M-101").orElseThrow();
        existingCabin.setCapacity(4);
        existingCabin.setIsAccessible(true);
        existingCabin.setStatus(com.casms.ship.enums.CabinStatus.MAINTENANCE);
        existingCabin.setBedCount(2);
        existingCabin.setBedConfiguration("KING_TWIN");
        cabins.saveAndFlush(existingCabin);

        upload(firstKey, "v2", smallBundle(firstKey, "v2", "SUITE", "M-101", true,
                true, "Updated Mini Deck", 1.7, List.of(7.0, 3.0, 8.0), false, 1, 1, 4), token);
        UUID secondModel = register(ship.getShipId(), firstKey, "v2", token);
        assertEquals("SUCCEEDED", importJob(ship.getShipId(), secondModel, token).path("data").path("status").asText());

        Deck updatedDeck = decks.findByShipShipIdAndDeckNumber(ship.getShipId(), 1).orElseThrow();
        assertEquals(existingDeck.getDeckId(), updatedDeck.getDeckId());
        assertEquals("Updated Mini Deck", updatedDeck.getDeckName());
        assertEquals(1.7, updatedDeck.getModelPositionY());
        assertEquals(new java.math.BigDecimal("1.35"), updatedDeck.getPriceMultiplier());

        Cabin updatedCabin = cabins.findByShipShipIdAndCabinNumber(ship.getShipId(), "M-101").orElseThrow();
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
        String token = token(Role.SHIP_MANAGER);
        String key = uniqueKey("duplicate-cabin");
        upload(key, "v1", smallBundle(key, "v1", "OCEAN_VIEW", "M-201", true,
                true, "Mini Deck", 1.0, List.of(5.0, 2.8, 6.5), true), token);
        UUID modelId = register(ship.getShipId(), key, "v1", token);

        mockMvc.perform(post("/ships/{shipId}/models/{modelId}/imports", ship.getShipId(), modelId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        var failedJob = jobs.findByShipModelShipModelIdOrderByStartedAtDesc(modelId).get(0);
        assertEquals("FAILED", failedJob.getStatus().name());
        assertEquals(0, decks.countByShipShipId(ship.getShipId()));
        assertEquals(0, cabins.countByShipShipId(ship.getShipId()));
        assertTrue(objects.findByShipModelShipModelId(modelId).isEmpty());
    }

    @Test
    void manifestRegistration_createsShipAndReusesStableIdentityAcrossVersions() throws Exception {
        String token = token(Role.SHIP_MANAGER);
        String key = uniqueKey("provision");
        upload(key, "v1", smallBundle(key, "v1", "OCEAN_VIEW", "M-301", true), token);

        MvcResult firstResult = mockMvc.perform(post("/ship-model-assets/{modelKey}/{assetVersion}/register", key, "v1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andReturn();
        JsonNode first = mapper.readTree(firstResult.getResponse().getContentAsByteArray()).path("data");
        UUID shipId = UUID.fromString(first.path("shipId").asText());
        testShipIds.add(shipId);
        assertTrue(first.path("shipCreated").asBoolean());
        Ship createdShip = ships.findById(shipId).orElseThrow();
        assertEquals(key, createdShip.getManifestModelId());
        assertEquals("Mini Ship", createdShip.getShipName());
        assertEquals(2, createdShip.getTotalCapacity());
        assertEquals(1, createdShip.getTotalDecks());
        assertEquals(1, createdShip.getDeckNumberLimit());
        assertEquals(100.0, createdShip.getLengthMeters());
        assertEquals(20.0, createdShip.getBeamMeters());

        upload(key, "v2", smallBundle(key, "v2", "SUITE", "M-301", true), token);
        upload(key, "v3", smallBundle(key, "v3", "BALCONY", "M-301", true), token);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        JsonNode second;
        JsonNode third;
        try {
            var v2 = CompletableFuture.supplyAsync(() -> registerManifestUnchecked(key, "v2", token), pool);
            var v3 = CompletableFuture.supplyAsync(() -> registerManifestUnchecked(key, "v3", token), pool);
            second = v2.join();
            third = v3.join();
        } finally {
            pool.shutdownNow();
        }
        assertFalse(second.path("shipCreated").asBoolean());
        assertFalse(third.path("shipCreated").asBoolean());
        assertEquals(shipId, UUID.fromString(second.path("shipId").asText()));
        assertEquals(shipId, UUID.fromString(third.path("shipId").asText()));
        assertEquals(1, ships.findAll().stream().filter(s -> key.equals(s.getManifestModelId())).count());
        assertEquals(3, models.findByShipShipIdOrderByCreatedAtDesc(shipId).size());
    }

    @Test
    void manifestDeckCount_isSeparateFromPassengerDeckNumberLimit() throws Exception {
        String token = token(Role.SHIP_MANAGER);
        String key = uniqueKey("deck-gap");
        upload(key, "v1", smallBundle(key, "v1", "OCEAN_VIEW", "M-401", true,
                true, "Sky Deck", 14.0, List.of(5.0, 2.8, 6.5), false, 14, 13), token);
        MvcResult registration = mockMvc.perform(post("/ship-model-assets/{modelKey}/{assetVersion}/register", key, "v1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andReturn();
        JsonNode registered = mapper.readTree(registration.getResponse().getContentAsByteArray()).path("data");
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
    void simultaneousRetries_areSerializedAndKeepOneBusinessMappingPerNaturalKey() throws Exception {
        Ship ship = createShip(10);
        String key = uniqueKey("parallel");
        String version = "v1";
        String token = token(Role.SHIP_MANAGER);
        upload(key, version, smallBundle(key, version, "OCEAN_VIEW", "M-001", true), token);
        UUID modelId = register(ship.getShipId(), key, version, token);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var a = CompletableFuture.supplyAsync(() -> importJobUnchecked(ship.getShipId(), modelId, token), pool);
            var b = CompletableFuture.supplyAsync(() -> importJobUnchecked(ship.getShipId(), modelId, token), pool);
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
        upload(noCabinKey, noCabinVersion, smallBundle(noCabinKey, noCabinVersion, "OCEAN_VIEW", "M-002", true, false), token);
        UUID noCabinModel = register(ship.getShipId(), noCabinKey, noCabinVersion, token);
        assertEquals("SUCCEEDED", importJob(ship.getShipId(), noCabinModel, token).path("data").path("status").asText());
        assertEquals(1, cabins.countByShipShipId(ship.getShipId()), "objects absent from a later model must be retained");
    }

    @AfterEach
    void cleanTestRowsAndLargeObjects() {
        for (String entry : uploadedBundles) {
            String[] identity = entry.split("\\|", 2);
            jdbc.update("DELETE FROM \"ShipModelAssetBlob\" WHERE model_key = ? AND asset_version = ?", identity[0], identity[1]);
        }
        for (UUID shipId : testShipIds) {
            jdbc.update("DELETE FROM \"DeckServiceAssignment\" WHERE deck_id IN (SELECT deck_id FROM \"Deck\" WHERE ship_id = ?)", shipId);
            jdbc.update("DELETE FROM \"ShipModelObject\" WHERE ship_model_id IN (SELECT ship_model_id FROM \"ShipModel\" WHERE ship_id = ?)", shipId);
            jdbc.update("DELETE FROM \"ModelImportJob\" WHERE ship_model_id IN (SELECT ship_model_id FROM \"ShipModel\" WHERE ship_id = ?)", shipId);
            jdbc.update("DELETE FROM \"Cabin\" WHERE ship_id = ?", shipId);
            jdbc.update("DELETE FROM \"Deck\" WHERE ship_id = ?", shipId);
            jdbc.update("DELETE FROM \"ShipModel\" WHERE ship_id = ?", shipId);
            jdbc.update("DELETE FROM \"Ship\" WHERE ship_id = ?", shipId);
        }
        for (UUID userId : testUserIds) {
            jdbc.update("DELETE FROM \"UserRole\" WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM \"User\" WHERE user_id = ?", userId);
        }
        testServiceIds.forEach(catalogueServices::deleteById);
        for (Path archive : tempArchives) {
            try { Files.deleteIfExists(archive); } catch (IOException ignored) { }
        }
    }

    private Ship createShip(int totalDecks) {
        Ship ship = new Ship();
        ship.setShipCode("IT-" + UUID.randomUUID().toString().substring(0, 12));
        ship.setShipName("Import Integration " + UUID.randomUUID().toString().substring(0, 6));
        ship.setStatus(ShipStatus.ACTIVE);
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

    private UUID register(UUID shipId, String key, String version, String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/ships/{shipId}/models", shipId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("modelKey", key, "assetVersion", version))))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(mapper.readTree(result.getResponse().getContentAsByteArray()).path("data").path("id").asText());
    }

    private JsonNode importJob(UUID shipId, UUID modelId, String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/ships/{shipId}/models/{modelId}/imports", shipId, modelId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private JsonNode importJobUnchecked(UUID shipId, UUID modelId, String token) {
        try { return importJob(shipId, modelId, token); }
        catch (Exception ex) { throw new RuntimeException(ex); }
    }

    private JsonNode registerManifestUnchecked(String key, String version, String token) {
        try {
            MvcResult result = mockMvc.perform(post("/ship-model-assets/{modelKey}/{assetVersion}/register", key, version)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isCreated()).andReturn();
            return mapper.readTree(result.getResponse().getContentAsByteArray()).path("data");
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private void upload(String key, String version, Path zip, String token) throws Exception {
        uploadedBundles.add(key + "|" + version);
        FileBackedMultipartFile bundle = new FileBackedMultipartFile("bundle", zip);
        MockHttpServletRequestBuilder request = multipart("/ship-model-assets")
                .file(bundle).param("modelKey", key).param("assetVersion", version)
                .header("Authorization", "Bearer " + token);
        mockMvc.perform(request).andExpect(status().isCreated());
    }

    private Path committedBundle(String version) throws IOException {
        Path root = Path.of("model-assets", "zaandam", "v10").toAbsolutePath();
        Path archive = Files.createTempFile("casms-zaandam-", ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(archive)))) {
            zip.setLevel(1);
            try (var paths = Files.walk(root)) {
                for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
                    String relative = root.relativize(file).toString().replace(File.separatorChar, '/');
                    zip.putNextEntry(new ZipEntry(ASSET_PREFIX + "zaandam/" + version + "/" + relative));
                    try (InputStream in = Files.newInputStream(file)) { in.transferTo(zip); }
                    zip.closeEntry();
                }
            }
        }
        tempArchives.add(archive);
        return archive;
    }

    private Path smallBundle(String key, String version, String cabinType, String cabinNumber, boolean matchingNodeIds) throws Exception {
        return smallBundle(key, version, cabinType, cabinNumber, matchingNodeIds, true);
    }

    private Path smallBundle(String key, String version, String cabinType, String cabinNumber,
                             boolean matchingNodeIds, boolean includeCabin) throws Exception {
        return smallBundle(key, version, cabinType, cabinNumber, matchingNodeIds, includeCabin,
                "Mini Deck", 1.0, List.of(5.0, 2.8, 6.5), false);
    }

    private Path smallBundle(String key, String version, String cabinType, String cabinNumber,
                             boolean matchingNodeIds, boolean includeCabin, String deckName,
                             double elevation, List<Double> cabinSize) throws Exception {
        return smallBundle(key, version, cabinType, cabinNumber, matchingNodeIds, includeCabin,
                deckName, elevation, cabinSize, false, 1, 1);
    }

    private Path smallBundle(String key, String version, String cabinType, String cabinNumber,
                             boolean matchingNodeIds, boolean includeCabin, String deckName,
                             double elevation, List<Double> cabinSize, boolean duplicateCabinNumber) throws Exception {
        return smallBundle(key, version, cabinType, cabinNumber, matchingNodeIds, includeCabin, deckName,
                elevation, cabinSize, duplicateCabinNumber, 1, 1);
    }

    private Path smallBundle(String key, String version, String cabinType, String cabinNumber,
                             boolean matchingNodeIds, boolean includeCabin, String deckName,
                             double elevation, List<Double> cabinSize, boolean duplicateCabinNumber,
                             int deckNumber, int deckCount) throws Exception {
        return smallBundle(key, version, cabinType, cabinNumber, matchingNodeIds, includeCabin, deckName,
                elevation, cabinSize, duplicateCabinNumber, deckNumber, deckCount, 2);
    }

    private Path smallBundle(String key, String version, String cabinType, String cabinNumber,
                             boolean matchingNodeIds, boolean includeCabin, String deckName,
                             double elevation, List<Double> cabinSize, boolean duplicateCabinNumber,
                             int deckNumber, int deckCount, int passengerCapacity) throws Exception {
        Map<String, Object> deckExtras = Map.of("entityType", "deck", "modelId", "mini-d01", "deckNumber", deckNumber,
                "deckName", deckName, "elevation", elevation);
        Map<String, Object> cabinExtras = Map.of("entityType", "cabin", "modelId", "mini-c1", "deckId", "mini-d01",
                "cabinNumber", cabinNumber, "cabinType", cabinType, "capacity", 2);
        List<Object> nodes = new ArrayList<>();
        nodes.add(Map.of("name", "DECK_01", "extras", deckExtras));
        if (matchingNodeIds && includeCabin) nodes.add(Map.of("name", "CABIN_01", "translation", List.of(0.0, 1.0, 0.0), "extras", cabinExtras));
        if (matchingNodeIds && includeCabin && duplicateCabinNumber) {
            nodes.add(Map.of("name", "CABIN_02", "translation", List.of(1.0, 1.0, 0.0), "extras",
                    Map.of("entityType", "cabin", "modelId", "mini-c2", "deckId", "mini-d01",
                            "cabinNumber", cabinNumber, "cabinType", cabinType, "capacity", 2)));
        }
        byte[] glb = glb(mapper.writeValueAsBytes(Map.of("asset", Map.of("version", "2.0"), "nodes", nodes)));
        Map<String, Object> deck = Map.of("id", "mini-d01", "number", deckNumber, "name", deckName, "elevationMetres", elevation,
                "asset", "decks/deck_01.glb", "cabinIds", includeCabin
                        ? (duplicateCabinNumber ? List.of("mini-c1", "mini-c2") : List.of("mini-c1")) : List.of());
        Map<String, Object> cabin = Map.of("id", "mini-c1", "cabinNumber", cabinNumber, "deckId", "mini-d01",
                "cabinType", cabinType, "capacity", 2, "isAccessible", false, "size", cabinSize,
                "areaSquareMetres", 32.5, "balconySquareMetres", 0.0);
        Map<String, Object> duplicateCabin = Map.of("id", "mini-c2", "cabinNumber", cabinNumber, "deckId", "mini-d01",
                "cabinType", cabinType, "capacity", 2, "isAccessible", false, "size", cabinSize,
                "areaSquareMetres", 32.5, "balconySquareMetres", 0.0);
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schemaVersion", 10);
        manifest.put("revision", "integration-v1");
        manifest.put("modelId", key);
        manifest.put("name", "Mini Ship");
        manifest.put("length", 100.0);
        manifest.put("beam", 20.0);
        manifest.put("deckCount", deckCount);
        manifest.put("passengerCapacity", passengerCapacity);
        manifest.put("units", "metre");
        manifest.put("gltfAxes", Map.of("up", "+Y", "bow", "+X", "port", "-Z"));
        manifest.put("assets", Map.of("full", "ship.glb", "exterior", "exterior.glb"));
        manifest.put("decks", List.of(deck));
        manifest.put("serviceDecks", List.of());
        manifest.put("cabins", includeCabin
                ? (duplicateCabinNumber ? List.of(cabin, duplicateCabin) : List.of(cabin)) : List.of());
        byte[] manifestBytes = mapper.writeValueAsBytes(manifest);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("manifest.json", manifestBytes);
        entries.put("ship.glb", glb);
        entries.put("exterior.glb", glb);
        entries.put("decks/deck_01.glb", glb);
        Map<String, String> checksums = new LinkedHashMap<>();
        for (var entry : entries.entrySet()) checksums.put(entry.getKey(), sha256(entry.getValue()));
        entries.put("checksums.json", mapper.writeValueAsBytes(checksums));
        Path archive = Files.createTempFile("casms-mini-", ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(ASSET_PREFIX + key + "/" + version + "/" + entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        tempArchives.add(archive);
        return archive;
    }

    private Path bundleWithServiceDeck(String key, String version, String cabinNumber) throws Exception {
        Path original = smallBundle(key, version, "OCEAN_VIEW", cabinNumber, true);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (var zip = new java.util.zip.ZipInputStream(Files.newInputStream(original))) {
            java.util.zip.ZipEntry entry;
            while ((entry=zip.getNextEntry()) != null) {
                String name=entry.getName().substring((ASSET_PREFIX+key+"/"+version+"/").length());
                entries.put(name, zip.readAllBytes());
            }
        }
        var manifest = (com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(entries.get("manifest.json"));
        manifest.putArray("serviceDecks").add(mapper.valueToTree(Map.of("id","mini-d00", "number",0,
                "name","Technical Deck", "elevationMetres",-2.0, "asset","services/service_00.glb", "cabinIds",List.of())));
        // Deliberately insufficient catalogue metadata: this must be ignored, not guessed or created.
        manifest.putArray("areas").add(mapper.valueToTree(Map.of("id","area-restaurant", "name","Unconfigured restaurant", "deckId","mini-d00")));
        byte[] source=entries.get("ship.glb");
        int jsonLength=ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN).getInt(12);
        var gltf=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(Arrays.copyOfRange(source,20,20+jsonLength));
        var nodes=(com.fasterxml.jackson.databind.node.ArrayNode)gltf.path("nodes");
        nodes.add(mapper.valueToTree(Map.of("name","SERVICE_00","extras",Map.of("entityType","serviceDeck",
                "modelId","mini-d00","deckNumber",0,"deckName","Technical Deck","elevation",-2.0))));
        nodes.add(mapper.valueToTree(Map.of("name","Unconfigured restaurant","extras",Map.of("entityType","service","modelId","area-restaurant"))));
        byte[] updatedGlb=glb(mapper.writeValueAsBytes(gltf));
        entries.put("manifest.json",mapper.writeValueAsBytes(manifest));
        entries.put("ship.glb",updatedGlb); entries.put("exterior.glb",updatedGlb);
        entries.put("services/service_00.glb",updatedGlb);
        Map<String,String> checksums=new LinkedHashMap<>();
        for(var e:entries.entrySet()) if(!e.getKey().equals("checksums.json")) checksums.put(e.getKey(),sha256(e.getValue()));
        entries.put("checksums.json",mapper.writeValueAsBytes(checksums));
        Path result=Files.createTempFile("casms-service-deck-", ".zip");tempArchives.add(result);
        try(var zip=new ZipOutputStream(Files.newOutputStream(result))) {
            for(var e:entries.entrySet()) {zip.putNextEntry(new ZipEntry(ASSET_PREFIX+key+"/"+version+"/"+e.getKey()));zip.write(e.getValue());zip.closeEntry();}
        }
        return result;
    }

    private Path bundleWithPhysicalAreas(String key, String version, String mode) throws Exception {
        Path original = bundleWithServiceDeck(key, version, "M-001");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        String prefix = ASSET_PREFIX + key + "/" + version + "/";
        try (var zip = new java.util.zip.ZipInputStream(Files.newInputStream(original))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) entries.put(entry.getName().substring(prefix.length()), zip.readAllBytes());
        }
        var manifest = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(entries.get("manifest.json"));
        var areas = (com.fasterxml.jackson.databind.node.ArrayNode) manifest.path("areas");
        var gym = mapper.valueToTree(Map.of("id", "physical-gym", "name", "Gym", "kind", "gym",
                "deckId", mode.equals("unknown-deck") ? "unknown" : "mini-d00", "deckNumber", 0,
                "size", mode.equals("invalid-size") ? List.of(-4, 3, 5) : List.of(4, 3, 5),
                "center", List.of(3, -1, 2), "components", List.of(Map.of("name", "Gym equipment"))));
        areas.add(gym);
        areas.add(mapper.valueToTree(Map.of("id", "physical-lounge", "name", "Lounge", "kind", "lounge",
                "deckId", "mini-d01", "deckNumber", 1, "size", List.of(6, 3, 4))));
        byte[] source = entries.get("ship.glb");
        int jsonLength = ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN).getInt(12);
        var gltf = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(Arrays.copyOfRange(source, 20, 20 + jsonLength));
        var nodes = (com.fasterxml.jackson.databind.node.ArrayNode) gltf.path("nodes");
        JsonNode gymNode = mapper.valueToTree(Map.of("name", "Gym", "translation", List.of(3, -1, 2),
                "rotation", mode.equals("invalid-transform") ? List.of(0, 0, 0, 0) : List.of(0, Math.sqrt(.5), 0, Math.sqrt(.5)),
                "scale", List.of(2, 1, 1), "extras", Map.of("entityType", "area", "modelId", "physical-gym",
                        "areaName", "Gym", "areaKind", "gym", "deckNumber", 0)));
        if (!mode.equals("missing-node")) nodes.add(gymNode);
        if (mode.equals("duplicate-node")) nodes.add(gymNode.deepCopy());
        nodes.add(mapper.valueToTree(Map.of("name", "Lounge", "translation", List.of(5, 1, 3),
                "extras", Map.of("entityType", "area", "modelId", "physical-lounge", "areaName", "Lounge",
                        "areaKind", "lounge", "deckNumber", 1))));
        byte[] updatedGlb = glb(mapper.writeValueAsBytes(gltf));
        entries.put("manifest.json", mapper.writeValueAsBytes(manifest));
        entries.put("ship.glb", updatedGlb);
        entries.put("exterior.glb", updatedGlb);
        entries.put("services/service_00.glb", updatedGlb);
        Map<String, String> checksums = new LinkedHashMap<>();
        for (var e : entries.entrySet()) if (!e.getKey().equals("checksums.json")) checksums.put(e.getKey(), sha256(e.getValue()));
        entries.put("checksums.json", mapper.writeValueAsBytes(checksums));
        Path result = Files.createTempFile("casms-physical-areas-", ".zip");
        tempArchives.add(result);
        try (var zip = new ZipOutputStream(Files.newOutputStream(result))) {
            for (var e : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(prefix + e.getKey()));
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
        buffer.put("glTF".getBytes(StandardCharsets.US_ASCII)).putInt(2).putInt(totalLength).putInt(paddedLength).putInt(0x4E4F534A);
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
        @Override public boolean isEmpty() { try { return Files.size(path) == 0; } catch (IOException e) { return true; } }
        @Override public long getSize() { try { return Files.size(path); } catch (IOException e) { throw new UncheckedIOException(e); } }
        @Override public byte[] getBytes() throws IOException { return Files.readAllBytes(path); }
        @Override public InputStream getInputStream() throws IOException { return Files.newInputStream(path); }
        @Override public void transferTo(File dest) throws IOException { Files.copy(path, dest.toPath(), StandardCopyOption.REPLACE_EXISTING); }
    }
}
