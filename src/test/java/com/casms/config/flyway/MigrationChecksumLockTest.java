package com.casms.config.flyway;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.TreeMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BE-FIX-00: committed migrations are immutable. Guards against a repeat of the V16/V35 edits.
 */
class MigrationChecksumLockTest {

    private static final String[] LOCATIONS = {"migration", "prod-migration"};

    @Test
    void committedMigrationsMatchTheChecksumLock() throws IOException {
        Properties lock = loadLock();
        Map<String, Integer> actual = classpathChecksums();

        List<String> problems = new ArrayList<>();
        for (String key : lock.stringPropertyNames()) {
            Integer checksum = actual.get(key);
            int expected = Integer.parseInt(lock.getProperty(key).trim());
            if (checksum == null) {
                problems.add(key + " is locked but missing (applied migrations must not be deleted or renamed)");
            } else if (checksum != expected) {
                problems.add(key + " changed: locked " + expected + ", now " + checksum
                        + " (restore it and put the change in a new migration)");
            }
        }
        actual.forEach((key, checksum) -> {
            if (!lock.containsKey(key)) {
                problems.add(key + " is not in flyway/migration-checksums.properties; append: " + key + "=" + checksum);
            }
        });

        assertTrue(problems.isEmpty(), String.join(System.lineSeparator(), problems));
    }

    @Test
    void acceptedDriftsPointAtTheCommittedChecksum() throws IOException {
        Map<String, Integer> actual = classpathChecksums();
        for (LegacyChecksumRealigner.AcceptedDrift drift : LegacyChecksumRealigner.ACCEPTED_DRIFTS) {
            Integer checksum = actual.get("migration/" + drift.script());
            assertNotNull(checksum, drift.script() + " not found on classpath");
            assertEquals(drift.currentChecksum(), checksum, drift.script());
            assertTrue(drift.script().startsWith("V" + drift.version() + "__"), drift.script());
            assertTrue(drift.legacyChecksums().stream().noneMatch(c -> c == drift.currentChecksum()), drift.script());
        }
    }

    private static Properties loadLock() throws IOException {
        Properties lock = new Properties();
        try (InputStream in = MigrationChecksumLockTest.class.getResourceAsStream("/flyway/migration-checksums.properties")) {
            assertNotNull(in, "flyway/migration-checksums.properties missing");
            lock.load(in);
        }
        return lock;
    }

    private static Map<String, Integer> classpathChecksums() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Map<String, Integer> checksums = new TreeMap<>();
        for (String location : LOCATIONS) {
            for (Resource resource : resolver.getResources("classpath*:db/" + location + "/*.sql")) {
                try (InputStream in = resource.getInputStream()) {
                    checksums.put(location + "/" + resource.getFilename(), FlywaySqlChecksum.of(in));
                }
            }
        }
        return checksums;
    }
}
