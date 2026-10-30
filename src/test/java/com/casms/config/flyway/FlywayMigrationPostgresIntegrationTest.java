package com.casms.config.flyway;

import com.casms.ship.enums.CruiseCruiseType;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BE-FIX-00 against a real PostgreSQL server. Every scenario runs in its own throw-away database
 * and uses the same realign-then-migrate sequence as {@code DatabaseConfig}.
 *
 * <p>Connection: {@code CASMS_PG_ADMIN_URL} (default {@code jdbc:postgresql://localhost:5432/postgres}),
 * {@code DB_USERNAME}/{@code DB_PASSWORD} (defaults match application.yml). The optional clone scenario
 * runs only when {@code -Dcasms.flyway.cloneSource=<existing database>} is set; it copies that database
 * via {@code CREATE DATABASE ... TEMPLATE} and never touches the source.</p>
 */
class FlywayMigrationPostgresIntegrationTest {

    private static final String ADMIN_URL = env("CASMS_PG_ADMIN_URL", "jdbc:postgresql://localhost:5432/postgres");
    private static final String USER = env("DB_USERNAME", "postgres");
    private static final String PASSWORD = env("DB_PASSWORD", "12345");
    private static final String[] APP_LOCATIONS = {"classpath:db/migration"};
    private static final String[] PROD_LOCATIONS = {"classpath:db/migration", "classpath:db/prod-migration"};
    private static final String LATEST = "68";

    private final List<String> createdDatabases = new ArrayList<>();

    @AfterEach
    void dropDatabases() throws SQLException {
        try (Connection admin = admin(); Statement st = admin.createStatement()) {
            for (String db : createdDatabases) {
                st.execute("DROP DATABASE IF EXISTS " + db + " WITH (FORCE)");
            }
        }
    }

    @Test
    void freshDatabaseMigratesToLatestAndValidates() throws SQLException {
        String db = createDatabase();
        Flyway flyway = flyway(db, null, APP_LOCATIONS);

        assertEquals(0, migrateLikeApplication(flyway));

        assertEquals(LATEST, flyway.info().current().getVersion().getVersion());
        assertTrue(flyway.validateWithResult().validationSuccessful);
        try (Connection c = connect(db)) {
            assertEquals(1L, count(c, "SELECT count(*) FROM \"User\" WHERE username = 'operations_manager'"));
            assertEquals(0L, count(c, "SELECT count(*) FROM \"OnboardActivity\" WHERE category = 'WORKSHOPS_CLASSES'"));
            assertEquals(0L, count(c, "SELECT count(*) FROM \"TripPlanEntry\" WHERE category = 'WORKSHOPS_CLASSES'"));
            assertLineageConstraintsValidated(c);
        }
    }

    @Test
    void freshDatabaseMigratesWithProductionLocations() throws SQLException {
        String db = createDatabase();
        Flyway flyway = flyway(db, null, PROD_LOCATIONS);

        migrateLikeApplication(flyway);

        assertEquals(LATEST, flyway.info().current().getVersion().getVersion());
        assertTrue(flyway.validateWithResult().validationSuccessful);
        try (Connection c = connect(db)) {
            assertEquals(1L, count(c, "SELECT count(*) FROM flyway_schema_history WHERE version = '38' AND success"));
        }
    }

    @Test
    void legacyDatabaseWithEditedChecksumsUpgradesWithoutDataLoss() throws SQLException {
        String db = createDatabase();
        flyway(db, "36", APP_LOCATIONS).migrate();

        UUID legacyCruiseId = UUID.randomUUID();
        try (Connection c = connect(db)) {
            insertLegacyCruise(c, legacyCruiseId);
            // The originally applied V35 left the retired category on seed activities.
            assertTrue(count(c, "SELECT count(*) FROM \"OnboardActivity\" WHERE category = 'WORKSHOPS_CLASSES'") > 0);
            // Simulate databases that recorded edited revisions of V3, V16 and V35.
            setChecksum(c, "3", 1244942886);
            setChecksum(c, "16", 260098715);
            setChecksum(c, "35", -1792007694);
        }

        Flyway flyway = flyway(db, null, APP_LOCATIONS);
        assertThrows(FlywayValidateException.class, flyway::migrate, "plain migrate must reject the drift");
        assertEquals(3, migrateLikeApplication(flyway));

        assertEquals(LATEST, flyway.info().current().getVersion().getVersion());
        assertTrue(flyway.validateWithResult().validationSuccessful);
        try (Connection c = connect(db)) {
            assertEquals(-147999042L, checksum(c, "3"));
            assertEquals(-1471729173L, checksum(c, "16"));
            assertEquals(267207917L, checksum(c, "35"));
            assertEquals(0L, count(c, "SELECT count(*) FROM \"OnboardActivity\" WHERE category = 'WORKSHOPS_CLASSES'"));

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT cruise_code, revision, source_trip_id, source_version_id FROM \"Cruise\" WHERE cruise_id = ?")) {
                ps.setObject(1, legacyCruiseId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "legacy cruise must survive the upgrade");
                    assertEquals("LEGACY-IT-1", rs.getString(1));
                    assertEquals(1L, rs.getLong(2));
                    assertNull(rs.getObject(3));
                    assertNull(rs.getObject(4));
                }
            }
            assertLineageConstraintsValidated(c);
            assertLineageIsEnforced(c, legacyCruiseId);
        }
    }

    @Test
    void unknownChecksumDriftStillFailsValidation() throws SQLException {
        String db = createDatabase();
        migrateLikeApplication(flyway(db, null, APP_LOCATIONS));
        try (Connection c = connect(db)) {
            setChecksum(c, "16", 42);
        }

        Flyway flyway = flyway(db, null, APP_LOCATIONS);
        assertThrows(FlywayValidateException.class, () -> migrateLikeApplication(flyway));
        try (Connection c = connect(db)) {
            assertEquals(42L, checksum(c, "16"));
        }
    }

    @Test
    void originalV34ChecksumIsAcceptedWithoutRewritingBusinessData() throws SQLException {
        String db = createDatabase();
        migrateLikeApplication(flyway(db, null, APP_LOCATIONS));
        Map<String, Long> before = new LinkedHashMap<>();
        try (Connection c = connect(db)) {
            for (String table : List.of("Ship", "Deck", "Cabin", "Cruise", "Booking"))
                before.put(table, count(c, "SELECT count(*) FROM \"" + table + "\""));
            setChecksum(c, "34", -1753018760);
        }
        Flyway flyway = flyway(db, null, APP_LOCATIONS);
        assertThrows(FlywayValidateException.class, flyway::migrate);
        assertEquals(1, migrateLikeApplication(flyway));
        assertTrue(flyway.validateWithResult().validationSuccessful);
        try (Connection c = connect(db)) {
            assertEquals(-477179883L, checksum(c, "34"));
            for (var entry : before.entrySet())
                assertEquals(entry.getValue(), count(c, "SELECT count(*) FROM \"" + entry.getKey() + "\""));
            setChecksum(c, "34", 42);
        }
        assertThrows(FlywayValidateException.class, () -> migrateLikeApplication(flyway));
    }

    @Test
    void filesystemLocationsReconcileOnlyTheReviewedV34Checksum(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        String db = createDatabase();
        migrateLikeApplication(flyway(db, null, APP_LOCATIONS));
        try (Connection c = connect(db)) { setChecksum(c, "34", -1753018760); }
        var resources = new org.springframework.core.io.support.PathMatchingResourcePatternResolver()
                .getResources("classpath:db/migration/V*.sql");
        for (var resource : resources) {
            try (var input = resource.getInputStream()) {
                java.nio.file.Files.copy(input, directory.resolve(java.util.Objects.requireNonNull(resource.getFilename())));
            }
        }
        Flyway flyway = flyway(db, null, "filesystem:" + directory.toString().replace('\\', '/'));
        assertEquals(1, migrateLikeApplication(flyway));
        assertTrue(flyway.validateWithResult().validationSuccessful);
        try (Connection c = connect(db)) { setChecksum(c, "34", 42); }
        assertThrows(FlywayValidateException.class, () -> migrateLikeApplication(flyway));
    }

    @Test
    void cloneOfExistingDatabaseUpgradesWithoutDataLoss() throws SQLException {
        String source = System.getProperty("casms.flyway.cloneSource");
        Assumptions.assumeTrue(source != null && source.matches("[A-Za-z0-9_]+"),
                "set -Dcasms.flyway.cloneSource=<database> to run against a copy of an existing database");
        String db = "casms_flyway_clone_" + UUID.randomUUID().toString().substring(0, 8);
        try (Connection admin = admin(); Statement st = admin.createStatement()) {
            st.execute("CREATE DATABASE " + db + " TEMPLATE " + source);
        }
        createdDatabases.add(db);

        String[] tables = {"User", "Trip", "TripScheduleVersion", "Cruise", "CruiseStop", "Booking", "OnboardActivity"};
        Map<String, Long> before = new LinkedHashMap<>();
        String versionBefore;
        try (Connection c = connect(db)) {
            for (String table : tables) {
                before.put(table, count(c, "SELECT count(*) FROM \"" + table + "\""));
            }
            versionBefore = String.valueOf(count(c,
                    "SELECT max(CAST(version AS int)) FROM flyway_schema_history WHERE success"));
        }

        Flyway flyway = flyway(db, null, APP_LOCATIONS);
        int realigned = migrateLikeApplication(flyway);

        assertEquals(LATEST, flyway.info().current().getVersion().getVersion());
        assertTrue(flyway.validateWithResult().validationSuccessful);
        try (Connection c = connect(db)) {
            for (String table : tables) {
                assertEquals(before.get(table), count(c, "SELECT count(*) FROM \"" + table + "\""), table);
            }
            assertEquals(0L, count(c, "SELECT count(*) FROM \"OnboardActivity\" WHERE category = 'WORKSHOPS_CLASSES'"));
        }
        System.out.printf("[BE-FIX-00] clone of %s: schema V%s -> V%s, realigned %d checksum(s), row counts %s preserved%n",
                source, versionBefore, LATEST, realigned, before);
        assertDemoSeederPreservesLegacyInventory(db);
    }

    private static void assertDemoSeederPreservesLegacyInventory(String db) throws SQLException {
        String snapshot = """
            SELECT jsonb_build_object(
              'ship',(SELECT to_jsonb(s) FROM "Ship" s WHERE ship_id='85000000-0000-4000-8000-000000000201'),
              'decks',(SELECT jsonb_agg(to_jsonb(d) ORDER BY deck_id) FROM "Deck" d WHERE ship_id='85000000-0000-4000-8000-000000000201'),
              'cabins',(SELECT jsonb_agg(to_jsonb(c) ORDER BY cabin_id) FROM "Cabin" c WHERE ship_id='85000000-0000-4000-8000-000000000201'))::text
            """;
        try (Connection c = connect(db); Statement st = c.createStatement()) {
            String original;
            try (ResultSet result = st.executeQuery(snapshot)) { result.next(); original = result.getString(1); }
            var dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(url(db), USER, PASSWORD);
            var seeder = new com.casms.servicepackage.config.DemoCatalogSeeder(dataSource);
            seeder.run(null);
            long ships = count(c, "SELECT count(*) FROM \"Ship\"");
            long cabins = count(c, "SELECT count(*) FROM \"Cabin\"");
            seeder.run(null);
            assertEquals(ships, count(c, "SELECT count(*) FROM \"Ship\""));
            assertEquals(cabins, count(c, "SELECT count(*) FROM \"Cabin\""));
            try (ResultSet result = st.executeQuery(snapshot)) { result.next(); assertEquals(original, result.getString(1)); }
            assertEquals(4L, count(c, "SELECT count(*) FROM \"Cabin\" c JOIN \"Ship\" s USING(ship_id) WHERE s.ship_code='DEMO-CATALOG-SHIP'"));
        }
    }

    // ---------------------------------------------------------------------------------------------

    private static int migrateLikeApplication(Flyway flyway) {
        int realigned = new LegacyChecksumRealigner().realign(flyway);
        flyway.migrate();
        return realigned;
    }

    private static void insertLegacyCruise(Connection c, UUID cruiseId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO "Cruise" (cruise_id, cruise_code, cruise_name, cruise_type, departure_datetime,
                                      return_datetime, departure_port_id, return_port_id, status, created_at, updated_at)
                SELECT ?, 'LEGACY-IT-1', 'Legacy cruise before V37', ?, now() + interval '10 days',
                       now() + interval '13 days', p.port_id, p.port_id, 'DRAFT', now(), now()
                FROM "Port" p ORDER BY p.port_code LIMIT 1
                """)) {
            ps.setObject(1, cruiseId);
            ps.setString(2, CruiseCruiseType.values()[0].name());
            assertEquals(1, ps.executeUpdate(), "seeded ports are required");
        }
    }

    private static void assertLineageConstraintsValidated(Connection c) throws SQLException {
        assertEquals(3L, count(c, """
                SELECT count(*) FROM pg_constraint
                WHERE conname IN ('uq_trip_schedule_version_trip_version', 'ck_cruise_source_pair',
                                  'fk_cruise_source_trip_version')
                  AND convalidated
                """));
        assertEquals(1L, count(c, "SELECT count(*) FROM pg_indexes WHERE indexname = 'idx_cruise_source_version_departure'"));
    }

    private static void assertLineageIsEnforced(Connection c, UUID cruiseId) throws SQLException {
        UUID tripA = insertTripWithVersion(c, "IT-TRIP-A");
        UUID tripB = insertTripWithVersion(c, "IT-TRIP-B");
        UUID versionA = versionOf(c, tripA);
        UUID versionB = versionOf(c, tripB);

        SQLException mismatch = assertThrows(SQLException.class, () -> linkCruise(c, cruiseId, tripA, versionB));
        assertEquals("23503", mismatch.getSQLState(), "version of another trip must be rejected");
        SQLException half = assertThrows(SQLException.class, () -> linkCruise(c, cruiseId, tripA, null));
        assertEquals("23514", half.getSQLState(), "trip without version must be rejected");

        linkCruise(c, cruiseId, tripA, versionA);
    }

    private static UUID insertTripWithVersion(Connection c, String code) throws SQLException {
        UUID tripId = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO \"Trip\" (trip_id, trip_code, name, duration_days, duration_nights) VALUES (?, ?, ?, 3, 2)")) {
            ps.setObject(1, tripId);
            ps.setString(2, code);
            ps.setString(3, code);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO \"TripScheduleVersion\" (trip_id, version_number) VALUES (?, 1)")) {
            ps.setObject(1, tripId);
            ps.executeUpdate();
        }
        return tripId;
    }

    private static UUID versionOf(Connection c, UUID tripId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT version_id FROM \"TripScheduleVersion\" WHERE trip_id = ?")) {
            ps.setObject(1, tripId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getObject(1, UUID.class);
            }
        }
    }

    private static void linkCruise(Connection c, UUID cruiseId, UUID tripId, UUID versionId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE \"Cruise\" SET source_trip_id = ?, source_version_id = ? WHERE cruise_id = ?")) {
            ps.setObject(1, tripId);
            ps.setObject(2, versionId);
            ps.setObject(3, cruiseId);
            ps.executeUpdate();
        }
    }

    private static void setChecksum(Connection c, String version, int checksum) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE flyway_schema_history SET checksum = ? WHERE version = ?")) {
            ps.setInt(1, checksum);
            ps.setString(2, version);
            assertEquals(1, ps.executeUpdate());
        }
    }

    private static long checksum(Connection c, String version) throws SQLException {
        return count(c, "SELECT checksum FROM flyway_schema_history WHERE version = '" + version + "'");
    }

    private static long count(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next());
            return rs.getLong(1);
        }
    }

    private String createDatabase() throws SQLException {
        String db = "casms_flyway_it_" + UUID.randomUUID().toString().substring(0, 8);
        try (Connection admin = admin(); Statement st = admin.createStatement()) {
            st.execute("CREATE DATABASE " + db);
        }
        createdDatabases.add(db);
        return db;
    }

    private static Flyway flyway(String db, String target, String... locations) {
        var configuration = Flyway.configure()
                .dataSource(url(db), USER, PASSWORD)
                .locations(locations)
                .baselineOnMigrate(true)
                .validateOnMigrate(true);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private static Connection admin() throws SQLException {
        return DriverManager.getConnection(ADMIN_URL, USER, PASSWORD);
    }

    private static Connection connect(String db) throws SQLException {
        return DriverManager.getConnection(url(db), USER, PASSWORD);
    }

    private static String url(String db) {
        String base = ADMIN_URL.contains("?") ? ADMIN_URL.substring(0, ADMIN_URL.indexOf('?')) : ADMIN_URL;
        return base.substring(0, base.lastIndexOf('/') + 1) + db + "?stringtype=unspecified";
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
