package com.casms.config;

import com.casms.config.flyway.LegacyChecksumRealigner;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Slf4j
@Configuration
@EnableJpaRepositories(basePackages = "com.casms")
public class DatabaseConfig {

    // Recognize the renumbered staff script only when it matches the checksum locked for V101.
    static final int LEGACY_V96_STAFF_CHECKSUM = 1065177894;
    static final int V96_SALE_TERMS_CHECKSUM = -299593305;

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy(
            @Value("${casms.flyway.legacy-upgrade-jdbc-url:}") String legacyUpgradeJdbcUrl) {
        return flyway -> {
            if (!legacyUpgradeJdbcUrl.isBlank()) {
                verifyLegacyUpgradeTarget(flyway, legacyUpgradeJdbcUrl);
                log.warn("Explicit Flyway legacy reconciliation enabled for the verified JDBC target");
                new LegacyChecksumRealigner().realign(flyway);
                reconcilePreMigrationState(flyway);
            }
            flyway.migrate();
        };
    }

    private void verifyLegacyUpgradeTarget(Flyway flyway, String expectedJdbcUrl) {
        var config = flyway.getConfiguration();
        try (Connection connection = config.getDataSource().getConnection()) {
            if (!expectedJdbcUrl.equals(connection.getMetaData().getURL())
                    || !"public".equals(connection.getSchema())
                    || (config.getDefaultSchema() != null && !"public".equals(config.getDefaultSchema()))
                    || (config.getSchemas().length != 0
                            && (config.getSchemas().length != 1 || !"public".equals(config.getSchemas()[0])))
                    || !"flyway_schema_history".equals(config.getTable())) {
                throw new IllegalStateException("Flyway legacy upgrade target or schema does not match");
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to verify the Flyway legacy upgrade target", ex);
        }
    }

    /**
     * Reconciles two legacy states on existing shared databases before Flyway runs:
     * 1. Pre-V100 duplicate ACTIVE CruiseStaff rows per user_id.
     * 2. Pre-V101 renumbered V96__cruise_staff_work_assignments.sql -> V101__cruise_staff_work_assignments.sql
     *    (ensuring V96's CruiseSaleTermsRequest table exists and empty V101 objects are recreated cleanly by V101).
     */
    private void reconcilePreMigrationState(Flyway flyway) {
        try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Statement stmt = connection.createStatement()) {
                if (tableExists(connection, "CruiseStaff") && !migrationApplied(connection, "100")) {
                    int closedCruiseUpdated = stmt.executeUpdate("""
                            UPDATE "CruiseStaff" cs
                            SET status = c.status::text,
                                ended_at = COALESCE(cs.ended_at, CASE WHEN c.status = 'COMPLETED' THEN c.actual_return_datetime END, now())
                            FROM "Cruise" c
                            WHERE c.cruise_id = cs.cruise_id
                              AND cs.status = 'ACTIVE'
                              AND c.status IN ('COMPLETED', 'CANCELLED')
                            """);

                    int duplicateActiveUpdated = stmt.executeUpdate("""
                            WITH ranked AS (
                                SELECT cruise_staff_id,
                                       ROW_NUMBER() OVER (
                                           PARTITION BY user_id
                                           ORDER BY assigned_at DESC NULLS LAST, cruise_staff_id DESC
                                       ) AS rn
                                FROM "CruiseStaff"
                                WHERE status = 'ACTIVE'
                            )
                            UPDATE "CruiseStaff" cs
                            SET status = 'REMOVED',
                                ended_at = COALESCE(cs.ended_at, now())
                            FROM ranked r
                            WHERE cs.cruise_staff_id = r.cruise_staff_id
                              AND r.rn > 1
                            """);

                    if (closedCruiseUpdated > 0 || duplicateActiveUpdated > 0) {
                        log.info(
                                "Reconciled CruiseStaff before V100: closedCruiseRows={}, duplicateActiveRows={}",
                                closedCruiseUpdated,
                                duplicateActiveUpdated);
                    }
                }

                // Reconcile only the known renumbered V96, in the same transaction as the schema change.
                if (hasRenumberedV96(connection)) {
                    stmt.execute("""
                            CREATE TABLE IF NOT EXISTS "CruiseSaleTermsRequest" (
                                cruise_id UUID PRIMARY KEY REFERENCES "Cruise"(cruise_id) ON DELETE CASCADE,
                                requested_by UUID NOT NULL REFERENCES "User"(user_id),
                                requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                                completed_at TIMESTAMPTZ
                            )
                            """);
                    try (PreparedStatement history = connection.prepareStatement("""
                            UPDATE public.flyway_schema_history
                            SET script = 'V96__cruise_sale_terms_requests.sql',
                                description = 'cruise sale terms requests', checksum = ?
                            WHERE version = '96' AND success
                              AND script = 'V96__cruise_staff_work_assignments.sql' AND checksum = ?
                            """)) {
                        history.setInt(1, V96_SALE_TERMS_CHECKSUM);
                        history.setInt(2, LEGACY_V96_STAFF_CHECKSUM);
                        if (history.executeUpdate() != 1) {
                            throw new IllegalStateException("Legacy V96 changed during reconciliation");
                        }
                    }
                    log.info("Reconciled V96 CruiseSaleTermsRequest table from renumbered migration history");
                }

                // If V101 has not been recorded yet but empty StaffActivityQualification/CruiseStaffDuty exist from old V96,
                // drop the empty pre-V101 objects so V101__cruise_staff_work_assignments.sql applies cleanly.
                if (!migrationApplied(connection, "101") && tableExists(connection, "StaffActivityQualification")) {
                    stmt.execute("LOCK TABLE public.\"StaffActivityQualification\" IN ACCESS EXCLUSIVE MODE");
                    boolean hasDuty = tableExists(connection, "CruiseStaffDuty");
                    if (hasDuty) {
                        stmt.execute("LOCK TABLE public.\"CruiseStaffDuty\" IN ACCESS EXCLUSIVE MODE");
                    }
                    if (hasRows(stmt, "StaffActivityQualification") || (hasDuty && hasRows(stmt, "CruiseStaffDuty"))) {
                        throw new IllegalStateException("Legacy V101 objects contain staff data; review before migrating");
                    }
                    stmt.execute("DROP TRIGGER IF EXISTS check_service_staff_work_window ON \"ServiceSchedule\"");
                    stmt.execute("DROP TRIGGER IF EXISTS check_onboard_staff_work_window ON \"OnboardActivity\"");
                    stmt.execute("DROP TRIGGER IF EXISTS check_shore_staff_work_window ON \"ShoreExcursion\"");
                    stmt.execute("DROP FUNCTION IF EXISTS check_staff_work_window() CASCADE");
                    stmt.execute("DROP VIEW IF EXISTS \"CruiseStaffDutyWindow\" CASCADE");
                    stmt.execute("DROP VIEW IF EXISTS \"CruiseStaffWorkWindow\" CASCADE");
                    stmt.execute("DROP TABLE IF EXISTS \"CruiseStaffDuty\" CASCADE");
                    stmt.execute("DROP TABLE IF EXISTS \"StaffActivityQualification\" CASCADE");
                    log.info("Cleaned pre-existing empty V101 objects before applying V101__cruise_staff_work_assignments.sql");
                }

                connection.commit();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to reconcile legacy state before migration", ex);
        }
    }

    private boolean hasRenumberedV96(Connection connection) throws SQLException {
        if (!tableExists(connection, "flyway_schema_history")) {
            return false;
        }
        try (PreparedStatement history = connection.prepareStatement("""
                SELECT 1 FROM public.flyway_schema_history
                WHERE version = '96' AND success
                  AND script = 'V96__cruise_staff_work_assignments.sql' AND checksum = ?
                """)) {
            history.setInt(1, LEGACY_V96_STAFF_CHECKSUM);
            try (ResultSet row = history.executeQuery()) {
                return row.next();
            }
        }
    }

    // Only called with the fixed legacy table identifiers above.
    private boolean hasRows(Statement statement, String legacyTable) throws SQLException {
        try (ResultSet rows = statement.executeQuery("SELECT 1 FROM public.\"" + legacyTable + "\" LIMIT 1")) {
            return rows.next();
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT to_regclass('public.\"' || ? || '\"') IS NOT NULL")) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private boolean migrationApplied(Connection connection, String version) throws SQLException {
        if (!tableExists(connection, "flyway_schema_history")) {
            return false;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM flyway_schema_history WHERE version = ? AND success = true")) {
            ps.setString(1, version);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
