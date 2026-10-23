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

    private final List<UUID> testShipIds = new ArrayList<>();
    private final List<UUID> testUserIds = new ArrayList<>();
    private final Map<String, String> uploadedBundles = new LinkedHashMap<>();
    private final List<Path> tempArchives = new ArrayList<>();

    @Test
    void committedZaandamBundle_uploadImportReadAndRetry_isIdempotent() throws Exception {
        Ship ship = createShip(10);
        String token = token(Role.SHIP_MANAGER);
        String version = "it-" + UUID.randomUUID().toString().substring(0, 8);
        Path archive = committedBundle(version);
        try {
            upload("zaandam", version, archive, token);
            UUID modelId = register(ship.getShipId(), "zaandam", version, token);

            JsonNode first = importJob(ship.getShipId(), modelId, token);
            assertEquals("SUCCEEDED", first.path("data").path("status").asText());
            UUID firstJobId = UUID.fromString(first.path("data").path("jobId").asText());
            assertEquals(303, first.path("data").path("totalObjects").asInt());
            assertEquals(10, first.path("data").path("decksImported").asInt());
            assertEquals(291, first.path("data").path("cabinsImported").asInt());
            assertEquals(303, first.path("data").path("objectsSucceeded").asInt());
            assertEquals(0, first.path("data").path("objectsFailed").asInt());

            assertEquals(10, decks.countByShipShipId(ship.getShipId()));
            assertEquals(291, cabins.countByShipShipId(ship.getShipId()));
            assertEquals(303, objects.findByShipModelShipModelId(modelId).size());
            assertEquals(291L, objects.findByShipModelShipModelId(modelId).stream().filter(o -> o.getCabin() != null).count());

            mockMvc.perform(get("/ships/{shipId}/models/{modelId}/imports", ship.getShipId(), modelId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(1)));
            mockMvc.perform(get("/ships/{shipId}/imports/{jobId}/issues", ship.getShipId(), firstJobId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(0)));
            mockMvc.perform(get("/ships/{shipId}/models/{modelId}/objects", ship.getShipId(), modelId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(303)));

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
            assertEquals(303, retry.path("data").path("objectsSucceeded").asInt());
            assertEquals(10, decks.countByShipShipId(ship.getShipId()));
            assertEquals(291, cabins.countByShipShipId(ship.getShipId()));
            assertEquals(303, objects.findByShipModelShipModelId(modelId).size());
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
            String noShipReadToken = token(Role.ADMIN);
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

        String partialKey = uniqueKey("partial");
        String partialVersion = "v1";
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

        String rollbackKey = uniqueKey("rollback");
        String rollbackVersion = "v1";
        upload(rollbackKey, rollbackVersion, smallBundle(rollbackKey, rollbackVersion, "OCEAN_VIEW", "CABIN-NUM-TOO-LONG", true), token);
        UUID rollbackModel = register(ship.getShipId(), rollbackKey, rollbackVersion, token);
        MvcResult rollbackResponse = mockMvc.perform(post("/ships/{shipId}/models/{modelId}/imports", ship.getShipId(), rollbackModel)
                        .header("Authorization", "Bearer " + token)).andReturn();
        assertEquals(500, rollbackResponse.getResponse().getStatus(), rollbackResponse.getResponse().getContentAsString());
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

        String mismatchKey = uniqueKey("mismatch");
        String mismatchVersion = "v1";
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

        String noCabinKey = uniqueKey("no-cabins");
        String noCabinVersion = "v1";
        upload(noCabinKey, noCabinVersion, smallBundle(noCabinKey, noCabinVersion, "OCEAN_VIEW", "M-002", true, false), token);
        UUID noCabinModel = register(ship.getShipId(), noCabinKey, noCabinVersion, token);
        assertEquals("SUCCEEDED", importJob(ship.getShipId(), noCabinModel, token).path("data").path("status").asText());
        assertEquals(1, cabins.countByShipShipId(ship.getShipId()), "objects absent from a later model must be retained");
    }

    @AfterEach
    void cleanTestRowsAndLargeObjects() {
        for (var entry : uploadedBundles.entrySet()) {
            jdbc.update("DELETE FROM \"ShipModelAssetBlob\" WHERE model_key = ? AND asset_version = ?", entry.getKey(), entry.getValue());
        }
        for (UUID shipId : testShipIds) {
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

    private void upload(String key, String version, Path zip, String token) throws Exception {
        uploadedBundles.put(key, version);
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
        Map<String, Object> deckExtras = Map.of("entityType", "deck", "modelId", "mini-d01", "deckNumber", 1,
                "deckName", "Mini Deck", "elevation", 1.0);
        Map<String, Object> cabinExtras = Map.of("entityType", "cabin", "modelId", "mini-c1", "deckId", "mini-d01",
                "cabinNumber", cabinNumber, "cabinType", cabinType, "capacity", 2);
        List<Object> nodes = new ArrayList<>();
        nodes.add(Map.of("name", "DECK_01", "extras", deckExtras));
        if (matchingNodeIds && includeCabin) nodes.add(Map.of("name", "CABIN_01", "translation", List.of(0.0, 1.0, 0.0), "extras", cabinExtras));
        byte[] glb = glb(mapper.writeValueAsBytes(Map.of("asset", Map.of("version", "2.0"), "nodes", nodes)));
        Map<String, Object> deck = Map.of("id", "mini-d01", "number", 1, "name", "Mini Deck", "elevationMetres", 1.0,
                "asset", "decks/deck_01.glb", "cabinIds", includeCabin ? List.of("mini-c1") : List.of());
        Map<String, Object> cabin = Map.of("id", "mini-c1", "cabinNumber", cabinNumber, "deckId", "mini-d01",
                "cabinType", cabinType, "capacity", 2, "isAccessible", false, "size", List.of(5.0, 2.8, 6.5),
                "areaSquareMetres", 32.5, "balconySquareMetres", 0.0);
        Map<String, Object> manifest = Map.of("schemaVersion", 10, "revision", "integration-v1", "modelId", key,
                "units", "metre", "gltfAxes", Map.of("up", "+Y", "bow", "+X", "port", "-Z"),
                "assets", Map.of("full", "ship.glb", "exterior", "exterior.glb"),
                "decks", List.of(deck), "serviceDecks", List.of(), "cabins", includeCabin ? List.of(cabin) : List.of());
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
