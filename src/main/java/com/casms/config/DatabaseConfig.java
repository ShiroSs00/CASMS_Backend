package com.casms.config;

import com.casms.config.flyway.LegacyChecksumRealigner;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
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

    /**
     * Realigns legacy checksums, reconciles pre-V100/V101 schema state on upgraded environments,
     * repairs schema history table if checksums drifted across environments,
     * then executes database migrations.
     */
    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        LegacyChecksumRealigner realigner = new LegacyChecksumRealigner();
        return flyway -> {
            realigner.realign(flyway);
            reconcilePreMigrationState(flyway);
            flyway.repair();
            flyway.migrate();
        };
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

                // Ensure V96 CruiseSaleTermsRequest exists if V96 was previously applied as V96__cruise_staff_work_assignments.sql
                if (migrationApplied(connection, "96") && !tableExists(connection, "CruiseSaleTermsRequest")) {
                    stmt.execute("""
                            CREATE TABLE IF NOT EXISTS "CruiseSaleTermsRequest" (
                                cruise_id UUID PRIMARY KEY REFERENCES "Cruise"(cruise_id) ON DELETE CASCADE,
                                requested_by UUID NOT NULL REFERENCES "User"(user_id),
                                requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                                completed_at TIMESTAMPTZ
                            )
                            """);
                    stmt.executeUpdate("""
                            UPDATE flyway_schema_history
                            SET script = 'V96__cruise_sale_terms_requests.sql',
                                description = 'cruise sale terms requests'
                            WHERE version = '96'
                            """);
                    log.info("Reconciled V96 CruiseSaleTermsRequest table from renumbered migration history");
                }

                // If V101 has not been recorded yet but empty StaffActivityQualification/CruiseStaffDuty exist from old V96,
                // drop the empty pre-V101 objects so V101__cruise_staff_work_assignments.sql applies cleanly.
                if (!migrationApplied(connection, "101") && tableExists(connection, "StaffActivityQualification")) {
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
            } catch (SQLException ex) {
                connection.rollback();
                log.warn("Pre-migration state reconciliation skipped: {}", ex.getMessage());
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException ex) {
            log.warn("Unable to inspect database before migration: {}", ex.getMessage());
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
