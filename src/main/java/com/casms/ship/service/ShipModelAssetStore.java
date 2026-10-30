package com.casms.ship.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.postgresql.PGConnection;
import org.postgresql.largeobject.LargeObject;
import org.postgresql.largeobject.LargeObjectManager;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class ShipModelAssetStore {
    private static final long MAX_BUNDLE_BYTES = 2L * 1024 * 1024 * 1024;
    private static final int MAX_FILES = 100;
    private static final int MAX_MANIFEST_BYTES = 20 * 1024 * 1024;
    private static final String TABLE = "\"ShipModelAssetBlob\"";

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;

    public ShipModelAssetStore(ObjectMapper objectMapper, JdbcTemplate jdbc) {
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
    }

    public UploadResult storeZip(String modelKey, String assetVersion, InputStream archive) {
        validateBundleKey(modelKey, assetVersion);
        if (bundleExists(modelKey, assetVersion)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This model asset version is already stored");
        }

        String prefix = "model-assets/" + modelKey + "/" + assetVersion + "/";
        Map<String, AssetMetadata> stored = new HashMap<>();
        long totalBytes = 0;
        int fileCount = 0;
        try (ZipInputStream zip = new ZipInputStream(archive)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String path = entry.getName().replace('\\', '/');
                if (entry.isDirectory()) continue;
                if (!path.startsWith(prefix)) throw bad("ZIP entries must be under " + prefix);
                String name = path.substring(prefix.length());
                validateAssetPath(name, true);
                if (stored.containsKey(name)) throw bad("Duplicate asset path in ZIP: " + name);
                if (++fileCount > MAX_FILES) throw bad("ZIP contains too many files");

                StoredAsset item = storeEntry(modelKey, assetVersion, name, zip, MAX_BUNDLE_BYTES - totalBytes);
                totalBytes += item.metadata().size();
                stored.put(name, item.metadata());
                zip.closeEntry();
            }
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot read the uploaded model ZIP", ex);
        }

        if (!stored.containsKey("manifest.json") || !stored.containsKey("checksums.json")) {
            throw bad("ZIP must include manifest.json and checksums.json");
        }
        JsonNode manifest = readJson(modelKey, assetVersion, "manifest.json", MAX_MANIFEST_BYTES);
        validateManifest(modelKey, manifest);
        Set<String> allowed = allowedAssets(manifest);
        for (String name : allowed) {
            if (!stored.containsKey(name)) throw bad("ZIP is missing manifest asset: " + name);
        }

        JsonNode checksums = readJson(modelKey, assetVersion, "checksums.json", MAX_MANIFEST_BYTES);
        if (!checksums.isObject()) throw bad("checksums.json must contain a JSON object");
        Set<String> checksumPaths = new HashSet<>();
        checksums.fields().forEachRemaining(field -> {
            String name = field.getKey();
            validateAssetPath(name, false);
            if (!field.getValue().isTextual() || !stored.containsKey(name) || "checksums.json".equals(name) ||
                    !field.getValue().asText().equalsIgnoreCase(stored.get(name).sha256())) {
                throw bad("Checksum mismatch or missing file: " + name);
            }
            checksumPaths.add(name);
        });
        Set<String> expected = new HashSet<>(stored.keySet());
        expected.remove("checksums.json");
        if (!checksumPaths.equals(expected)) throw bad("checksums.json does not cover every uploaded asset");

        enforceFullModelCopyLimit(modelKey,assetVersion,manifest,stored);

        return new UploadResult(fileCount, totalBytes);
    }

    private void enforceFullModelCopyLimit(String modelKey,String version,JsonNode manifest,Map<String,AssetMetadata> stored) {
        String fingerprint=stored.get(manifest.path("assets").path("full").asText()).sha256();
        // Lock after validation and before counting; a waiter sees uploads committed by the previous holder.
        jdbc.execute((ConnectionCallback<Void>)connection->{
            try(var statement=connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?,0))")) {
                statement.setString(1,"ship-model-full:"+fingerprint);statement.execute();
            }
            return null;
        });
        var candidates=jdbc.query("SELECT a.model_key,a.asset_version,a.asset_path FROM "+TABLE+" a JOIN "+TABLE+" m "
                +"ON m.model_key=a.model_key AND m.asset_version=a.asset_version AND m.asset_path='manifest.json' "
                +"WHERE a.sha256=? AND a.asset_path LIKE '%.glb' AND (a.model_key<>? OR a.asset_version<>?) ORDER BY a.model_key,a.asset_version,a.asset_path",
                (r,n)->new ContentCandidate(r.getString("model_key"),r.getString("asset_version"),r.getString("asset_path")),fingerprint,modelKey,version);
        Map<String,JsonNode> manifests=new HashMap<>();Set<String> counted=new HashSet<>();
        for(var candidate:candidates) {
            String identity=candidate.modelKey()+"|"+candidate.version();
            JsonNode source=manifests.computeIfAbsent(identity,k->readJson(candidate.modelKey(),candidate.version(),"manifest.json",MAX_MANIFEST_BYTES));
            if(candidate.path().equals(source.path("assets").path("full").asText()))counted.add(identity);
            if(counted.size()>=2)throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This GLB content has already been uploaded twice; reuse an uploaded model to create more ships");
        }
    }

    private record ContentCandidate(String modelKey,String version,String path) {}

    public boolean bundleExists(String modelKey, String assetVersion) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM " + TABLE +
                " WHERE model_key = ? AND asset_version = ?", Integer.class, modelKey, assetVersion);
        return count != null && count > 0;
    }

    public JsonNode manifest(String modelKey, String assetVersion) {
        JsonNode manifest = readJson(modelKey, assetVersion, "manifest.json", MAX_MANIFEST_BYTES);
        validateManifest(modelKey, manifest);
        return manifest;
    }

    /** Reads only the GLB JSON chunk; vertex/index buffers remain in PostgreSQL and are never materialized. */
    public JsonNode glbDocument(String modelKey, String assetVersion, String name) {
        AssetMetadata meta = asset(modelKey, assetVersion, name);
        if (meta.size() < 20) throw bad("GLB file is truncated");
        byte[] jsonChunk = jdbc.execute((ConnectionCallback<byte[]>) connection -> {
            long oid = findOid(connection.prepareStatement("SELECT blob_oid FROM " + TABLE +
                    " WHERE model_key = ? AND asset_version = ? AND asset_path = ?"), modelKey, assetVersion, name);
            LargeObject largeObject = connection.unwrap(PGConnection.class).getLargeObjectAPI()
                    .open(oid, LargeObjectManager.READ);
            try (InputStream input = largeObject.getInputStream()) {
                byte[] header = input.readNBytes(20);
                if (header.length != 20 || !"glTF".equals(new String(header, 0, 4, java.nio.charset.StandardCharsets.US_ASCII))) {
                    throw bad("Invalid GLB header");
                }
                ByteBuffer bytes = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
                int version = bytes.getInt(4);
                long declaredLength = Integer.toUnsignedLong(bytes.getInt(8));
                long chunkLength = Integer.toUnsignedLong(bytes.getInt(12));
                int chunkType = bytes.getInt(16);
                if (version != 2 || declaredLength != meta.size() || chunkType != 0x4E4F534A ||
                        chunkLength > 64L * 1024 * 1024 || 20L + chunkLength > declaredLength) {
                    throw bad("Unsupported or invalid GLB JSON chunk");
                }
                byte[] content = input.readNBytes((int) chunkLength);
                if (content.length != chunkLength) throw bad("Truncated GLB JSON chunk");
                return content;
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            } finally {
                largeObject.close();
            }
        });
        try {
            return objectMapper.readTree(jsonChunk);
        } catch (IOException ex) {
            throw bad("Invalid JSON in GLB document");
        }
    }

    public AssetMetadata asset(String modelKey, String assetVersion, String name) {
        JsonNode manifest = manifest(modelKey, assetVersion);
        validateAssetPath(name, false);
        if (!allowedAssets(manifest).contains(name)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown model asset");
        }
        return metadata(modelKey, assetVersion, name);
    }

    public void writeAsset(String modelKey, String assetVersion, String name, OutputStream output) {
        AssetMetadata metadata = asset(modelKey, assetVersion, name);
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            long oid = findOid(connection.prepareStatement("SELECT blob_oid FROM " + TABLE +
                    " WHERE model_key = ? AND asset_version = ? AND asset_path = ?"), modelKey, assetVersion, name);
            LargeObject largeObject = connection.unwrap(PGConnection.class).getLargeObjectAPI()
                    .open(oid, LargeObjectManager.READ);
            try (InputStream input = largeObject.getInputStream()) {
                input.transferTo(output);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            } finally {
                largeObject.close();
            }
            return null;
        });
    }

    public List<JsonNode> decks(JsonNode manifest) {
        List<JsonNode> result = new ArrayList<>();
        manifest.path("decks").forEach(result::add);
        manifest.path("serviceDecks").forEach(result::add);
        return result;
    }

    public int cabinDeckNumber(JsonNode manifest, String modelCabinId) {
        return decks(manifest).stream().filter(deck -> contains(deck.path("cabinIds"), modelCabinId))
                .findFirst().orElseThrow(() -> bad("Unknown model cabin: " + modelCabinId))
                .path("number").asInt();
    }

    public Set<String> cabinIds(JsonNode manifest) {
        Set<String> result = new HashSet<>();
        manifest.path("cabins").forEach(cabin -> result.add(cabin.path("id").asText()));
        return result;
    }

    public Set<String> allowedAssets(JsonNode manifest) {
        Set<String> paths = new HashSet<>();
        paths.add("manifest.json");
        for (String field : List.of("full", "exterior")) {
            String asset = manifest.path("assets").path(field).asText();
            if (asset.isBlank()) throw bad("Manifest is missing its " + field + " GLB asset");
            validateAssetPath(asset, false);
            paths.add(asset);
        }
        for (JsonNode deck : decks(manifest)) {
            String asset = deck.path("asset").asText();
            if (asset.isBlank()) throw bad("Manifest deck is missing its GLB asset");
            validateAssetPath(asset, false);
            paths.add(asset);
        }
        String network = manifest.path("commonInteriors").path("networkAsset").asText();
        if (!network.isBlank()) {
            validateAssetPath(network, false);
            paths.add(network);
        }
        return paths;
    }

    private StoredAsset storeEntry(String modelKey, String assetVersion, String name, InputStream input, long remainingBytes) {
        return jdbc.execute((ConnectionCallback<StoredAsset>) connection -> {
            PGConnection pg = connection.unwrap(PGConnection.class);
            long oid = pg.getLargeObjectAPI().createLO(LargeObjectManager.READ | LargeObjectManager.WRITE);
            LargeObject largeObject = pg.getLargeObjectAPI().open(oid, LargeObjectManager.WRITE);
            MessageDigest digest = sha256();
            long size = 0;
            byte[] buffer = new byte[1024 * 1024];
            byte[] glbHeader = new byte[12];
            int headerBytes = 0;
            try {
                OutputStream output = largeObject.getOutputStream();
                int read;
                while ((read = input.read(buffer)) != -1) {
                    size += read;
                    if (size > remainingBytes) throw bad("Expanded ZIP exceeds the 2 GiB upload limit");
                    long entryLimit = name.endsWith(".json") ? MAX_MANIFEST_BYTES : 512L * 1024 * 1024;
                    if (size > entryLimit) throw bad("Asset exceeds the per-file size limit: " + name);
                    if (headerBytes < glbHeader.length) {
                        int copyLength = Math.min(read, glbHeader.length - headerBytes);
                        System.arraycopy(buffer, 0, glbHeader, headerBytes, copyLength);
                        headerBytes += copyLength;
                    }
                    digest.update(buffer, 0, read);
                    output.write(buffer, 0, read);
                }
                output.close();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            } finally {
                largeObject.close();
            }

            if (name.endsWith(".glb")) {
                if (headerBytes != glbHeader.length || !"glTF".equals(new String(glbHeader, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)) ||
                        ByteBuffer.wrap(glbHeader).order(ByteOrder.LITTLE_ENDIAN).getInt(4) != 2 ||
                        Integer.toUnsignedLong(ByteBuffer.wrap(glbHeader).order(ByteOrder.LITTLE_ENDIAN).getInt(8)) != size) {
                    throw bad("Invalid GLB header or declared length: " + name);
                }
            }

            String sha = HexFormat.of().formatHex(digest.digest());
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO " + TABLE +
                    " (asset_id, model_key, asset_version, asset_path, blob_oid, byte_size, sha256, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, now())")) {
                statement.setObject(1, UUID.randomUUID());
                statement.setString(2, modelKey);
                statement.setString(3, assetVersion);
                statement.setString(4, name);
                statement.setLong(5, oid);
                statement.setLong(6, size);
                statement.setString(7, sha);
                statement.executeUpdate();
            }
            return new StoredAsset(new AssetMetadata(size, sha));
        });
    }

    private JsonNode readJson(String modelKey, String assetVersion, String name, int maxBytes) {
        AssetMetadata meta = metadata(modelKey, assetVersion, name);
        if (meta.size() > maxBytes) throw bad(name + " exceeds the JSON size limit");
        byte[] bytes = jdbc.execute((ConnectionCallback<byte[]>) connection -> {
            long oid = findOid(connection.prepareStatement("SELECT blob_oid FROM " + TABLE +
                    " WHERE model_key = ? AND asset_version = ? AND asset_path = ?"), modelKey, assetVersion, name);
            LargeObject largeObject = connection.unwrap(PGConnection.class).getLargeObjectAPI()
                    .open(oid, LargeObjectManager.READ);
            try (InputStream input = largeObject.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                input.transferTo(output);
                return output.toByteArray();
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            } finally {
                largeObject.close();
            }
        });
        try {
            return objectMapper.readTree(bytes);
        } catch (IOException ex) {
            throw bad("Invalid JSON asset: " + name);
        }
    }

    private AssetMetadata metadata(String modelKey, String assetVersion, String name) {
        AssetMetadata metadata = jdbc.query("SELECT byte_size, sha256 FROM " + TABLE +
                        " WHERE model_key = ? AND asset_version = ? AND asset_path = ?",
                (ResultSet rs) -> rs.next() ? new AssetMetadata(rs.getLong(1), rs.getString(2)) : null,
                modelKey, assetVersion, name);
        if (metadata == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Model asset not found: " + name);
        return metadata;
    }

    private long findOid(PreparedStatement statement, String modelKey, String assetVersion, String name) throws SQLException {
        try (statement) {
            statement.setString(1, modelKey);
            statement.setString(2, assetVersion);
            statement.setString(3, name);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Model asset not found: " + name);
                return rs.getLong(1);
            }
        }
    }

    private void validateManifest(String modelKey, JsonNode manifest) {
        if (!manifest.isObject() || !modelKey.equals(manifest.path("modelId").asText()) ||
                manifest.path("revision").asText().isBlank() || !"metre".equals(manifest.path("units").asText()) ||
                !"+Y".equals(manifest.path("gltfAxes").path("up").asText())) {
            throw bad("Manifest model ID, revision, units or axes are invalid");
        }
        if (!manifest.path("decks").isArray() || !manifest.path("cabins").isArray()) {
            throw bad("Manifest is missing decks or cabins");
        }
        Set<String> deckIds = new HashSet<>();
        Set<Integer> deckNumbers = new HashSet<>();
        for (JsonNode deck : decks(manifest)) {
            if (deck.path("id").asText().isBlank() || !deckIds.add(deck.path("id").asText()) ||
                    !deck.path("number").isIntegralNumber() || !deckNumbers.add(deck.path("number").asInt())) {
                throw bad("Manifest contains an invalid or duplicate deck");
            }
        }
        Set<String> cabinIds = new HashSet<>();
        for (JsonNode cabin : manifest.path("cabins")) {
            String id = cabin.path("id").asText();
            if (id.isBlank() || !cabinIds.add(id) || decks(manifest).stream()
                    .noneMatch(deck -> contains(deck.path("cabinIds"), id))) {
                throw bad("Manifest contains a duplicate cabin or a cabin without a deck");
            }
        }
    }

    private static void validateBundleKey(String modelKey, String assetVersion) {
        if (modelKey == null || !modelKey.matches("[a-z0-9-]+") || assetVersion == null ||
                !assetVersion.matches("[a-zA-Z0-9._-]+") || assetVersion.equals(".") || assetVersion.equals("..")) {
            throw bad("Invalid model key or asset version");
        }
    }

    private static void validateAssetPath(String name, boolean allowChecksums) {
        if (name == null || name.isBlank() || name.startsWith("/") || name.contains("\\") || name.contains(":") ||
                Arrays.asList(name.split("/", -1)).stream().anyMatch(part -> part.isBlank() || part.equals(".") || part.equals("..")) ||
                !(name.endsWith(".glb") || name.endsWith(".json")) || (!allowChecksums && name.equals("checksums.json"))) {
            throw bad("Invalid model asset path");
        }
    }

    private static boolean contains(JsonNode values, String expected) {
        for (JsonNode value : values) if (expected.equals(value.asText())) return true;
        return false;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record AssetMetadata(long size, String sha256) { }
    public record UploadResult(int files, long bytes) { }
    private record StoredAsset(AssetMetadata metadata) { }
}
