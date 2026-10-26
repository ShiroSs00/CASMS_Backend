package com.casms.config.flyway;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.configuration.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

/**
 * Reconciles {@code flyway_schema_history} checksums for migrations that were edited after some databases
 * had already applied them (BE-FIX-00).
 *
 * <p>Only exact, known (version, legacy checksum) pairs listed in {@link #ACCEPTED_DRIFTS} are rewritten,
 * and only while the script on the classpath still has the expected checksum. Any other mismatch is left
 * untouched, so Flyway validation still rejects it. Schema and data are never modified here; the
 * forward-only migration V40 carries the intended follow-up changes.</p>
 */
@Slf4j
public class LegacyChecksumRealigner {

    /**
     * A historical edit of an applied migration that has been reviewed and accepted.
     *
     * @param version         Flyway version
     * @param script          file name under the classpath migration location
     * @param currentChecksum checksum of the file as committed now
     * @param legacyChecksums checksums of earlier committed revisions of the same file
     * @param reason          audit note: which commits produced the legacy checksums
     */
    public record AcceptedDrift(String version, String script, int currentChecksum,
                                Set<Integer> legacyChecksums, String reason) {
    }

    /** Checksums computed from every git revision of each edited migration. */
    public static final List<AcceptedDrift> ACCEPTED_DRIFTS = List.of(
            new AcceptedDrift("1", "V1__initial_schema.sql", 1055026664,
                    Set.of(289348567), "52495d6 edited by 9e34bf3"),
            new AcceptedDrift("2", "V2__add_operational_issue_verification.sql", -424430432,
                    Set.of(-324391712), "12ba5bc edited by c29f9d5"),
            new AcceptedDrift("3", "V3__insert_default_users.sql", -147999042,
                    Set.of(1244942886, 1295536681, 1659504838, -945123753),
                    "f9580ab, 0eb6951, 9e34bf3, d9611f5 edited by c29f9d5"),
            new AcceptedDrift("4", "V4__add_activity_supplier_tour_permissions.sql", 357969419,
                    Set.of(499334649), "acda5a8 edited by c29f9d5"),
            new AcceptedDrift("11", "V11__generate_business_codes.sql", -1724783452,
                    Set.of(-596347025), "early branch applied supplier verification as V11 before rename to V15"),
            new AcceptedDrift("16", "V16__add_activity_and_shore_manager_roles.sql", -1471729173,
                    Set.of(260098715), "original 195be84 restored; edit 45afd10 failed on fresh databases"),
            new AcceptedDrift("21", "V21__align_port_entity_columns.sql", -1709011459,
                    Set.of(-960173765), "d0d449c edited by 5756081"),
            new AcceptedDrift("22", "V22__create_audit_log_table.sql", -532601896,
                    Set.of(1529099746), "d0d449c edited by 5756081"),
            new AcceptedDrift("35", "V35__add_activity_category_and_location_refinement.sql", 267207917,
                    Set.of(-1792007694), "original 4f4f5e0 restored; edit 9d894af carried forward by V40")
    );

    private final List<AcceptedDrift> drifts;

    public LegacyChecksumRealigner() {
        this(ACCEPTED_DRIFTS);
    }

    public LegacyChecksumRealigner(List<AcceptedDrift> drifts) {
        this.drifts = List.copyOf(drifts);
    }

    /**
     * @return number of history rows whose checksum was realigned
     */
    public int realign(Flyway flyway) {
        Configuration configuration = flyway.getConfiguration();
        try (Connection connection = configuration.getDataSource().getConnection()) {
            String schema = resolveSchema(configuration, connection);
            String table = configuration.getTable();
            if (!historyTableExists(connection, schema, table)) {
                return 0;
            }
            return realign(connection, configuration, quote(schema) + "." + quote(table));
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to realign Flyway checksums", ex);
        }
    }

    private int realign(Connection connection, Configuration configuration, String historyTable) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            int realigned = 0;
            String sql = "UPDATE " + historyTable
                    + " SET checksum = ?, script = ? WHERE version = ? AND success AND checksum = ANY (?)";
            for (AcceptedDrift drift : drifts) {
                Integer resolved = classpathChecksum(configuration, drift.script());
                if (resolved == null || resolved != drift.currentChecksum()) {
                    log.warn("Flyway V{} ({}) no longer matches its accepted checksum (found {}); "
                            + "skipping realignment so validation can report it", drift.version(), drift.script(), resolved);
                    continue;
                }
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setInt(1, drift.currentChecksum());
                    statement.setString(2, drift.script());
                    statement.setString(3, drift.version());
                    statement.setArray(4, connection.createArrayOf("integer", drift.legacyChecksums().toArray()));
                    int updated = statement.executeUpdate();
                    if (updated > 0) {
                        log.warn("Realigned Flyway checksum of V{} to {} (accepted legacy edit: {})",
                                drift.version(), drift.currentChecksum(), drift.reason());
                    }
                    realigned += updated;
                }
            }
            connection.commit();
            return realigned;
        } catch (SQLException | RuntimeException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private static Integer classpathChecksum(Configuration configuration, String script) {
        ClassLoader classLoader = configuration.getClassLoader() != null
                ? configuration.getClassLoader()
                : Thread.currentThread().getContextClassLoader();
        for (Location location : configuration.getLocations()) {
            if (!location.isClassPath()) {
                continue;
            }
            String path = location.getRootPath().isEmpty() ? script : location.getRootPath() + "/" + script;
            try (InputStream stream = classLoader.getResourceAsStream(path)) {
                if (stream != null) {
                    return FlywaySqlChecksum.of(stream);
                }
            } catch (IOException ex) {
                throw new IllegalStateException("Unable to read migration " + path, ex);
            }
        }
        return null;
    }

    private static String resolveSchema(Configuration configuration, Connection connection) throws SQLException {
        if (configuration.getDefaultSchema() != null) {
            return configuration.getDefaultSchema();
        }
        if (configuration.getSchemas().length > 0) {
            return configuration.getSchemas()[0];
        }
        return connection.getSchema();
    }

    private static boolean historyTableExists(Connection connection, String schema, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT to_regclass(format('%I.%I', CAST(? AS text), CAST(? AS text))) IS NOT NULL")) {
            statement.setString(1, schema);
            statement.setString(2, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean(1);
            }
        }
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
