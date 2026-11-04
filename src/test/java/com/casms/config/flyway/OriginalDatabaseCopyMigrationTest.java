package com.casms.config.flyway;

import static org.junit.jupiter.api.Assertions.*;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Original is read-only; only the explicitly restored copy is migrated. */
@EnabledIfEnvironmentVariable(named = "OCVVIET_ORIGINAL_COPY", matches = "true")
class OriginalDatabaseCopyMigrationTest {
    @Test
    void originalV41CopyUpgradesWithoutRewritingHistoryOrExistingData() throws Exception {
        String url = "jdbc:postgresql://127.0.0.1:5432/casms_signoff_copy_20261029";
        var before = new TreeMap<String, String>();
        var columns = new TreeMap<String, String>();
        try (var c =
                DriverManager.getConnection(
                        "jdbc:postgresql://127.0.0.1:5432/casms_local_20261025",
                        "postgres",
                        "12345")) {
            c.setReadOnly(true);
            try (var s = c.createStatement();
                    var r =
                            s.executeQuery(
                                    "select table_name from information_schema.tables where"
                                        + " table_schema='public' and table_type='BASE TABLE' order"
                                        + " by table_name")) {
                while (r.next()) columns.put(r.getString(1), "");
            }
            for (String table : columns.keySet()) {
                var names = new ArrayList<String>();
                try (var s =
                        c.prepareStatement(
                                "select column_name from information_schema.columns where"
                                    + " table_schema='public' and table_name=? order by"
                                    + " ordinal_position")) {
                    s.setString(1, table);
                    try (var r = s.executeQuery()) {
                        while (r.next()) names.add(quote(r.getString(1)));
                    }
                }
                columns.put(table, String.join(",", names));
                before.put(table, snapshot(c, table, columns.get(table)));
            }
        }
        var flyway =
                Flyway.configure()
                        .dataSource(url, "postgres", "12345")
                        .locations("classpath:db/migration")
                        .cleanDisabled(true)
                        .load();
        flyway.migrate();
        flyway.validate();
        assertEquals("65", flyway.info().current().getVersion().getVersion());
        try (var c = DriverManager.getConnection(url, "postgres", "12345")) {
            for (String table : columns.keySet()) {
                String oldRows = before.get(table);
                if (table.equals("flyway_schema_history")) {
                    try (var s = c.createStatement();
                            var r =
                                    s.executeQuery(
                                            "select coalesce(jsonb_agg(to_jsonb(t) order by"
                                                + " installed_rank),'[]'::jsonb)::text from"
                                                + " flyway_schema_history t where"
                                                + " installed_rank<=40")) {
                        assertTrue(r.next());
                        // Compare the original prefix using the same deterministic column/row
                        // snapshot below.
                    }
                    assertEquals(
                            oldRows,
                            snapshot(c, table, columns.get(table), " WHERE installed_rank<=40"),
                            "Original history changed");
                } else if (table.equals("RolePermissionMapping")) {
                    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    var old = mapper.readTree(oldRows);
                    var current = mapper.readTree(snapshot(c, table, columns.get(table)));
                    var currentRows = new HashSet<String>();
                    current.forEach(row -> currentRows.add(row.toString()));
                    old.forEach(
                            row ->
                                    assertTrue(
                                            currentRows.contains(row.toString()),
                                            "Existing role grant removed"));
                } else {
                    assertEquals(
                            oldRows,
                            snapshot(c, table, columns.get(table)),
                            "Existing values changed: " + table);
                }
            }
        }
        assertEquals(0, flyway.migrate().migrationsExecuted);
        Files.writeString(
                Path.of("docs/mainflow-alignment/final-audit/original-copy-migration-result.json"),
                "{\"database\":\"casms_signoff_copy_20261029\",\"fromVersion\":41,\"toVersion\":65,\"originalHistoryRows\":40,\"tablesPreserved\":"
                        + columns.size()
                        + ",\"historyRewritten\":false,\"secondMigrationCount\":0}");
    }

    private static String quote(String s) {
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private static String snapshot(Connection c, String table, String cols) throws SQLException {
        return snapshot(c, table, cols, "");
    }

    private static String snapshot(Connection c, String table, String cols, String where)
            throws SQLException {
        try (var s = c.createStatement();
                var r =
                        s.executeQuery(
                                "select coalesce(jsonb_agg(to_jsonb(t) order by"
                                    + " to_jsonb(t)::text),'[]'::jsonb)::text from (select "
                                        + cols
                                        + " from "
                                        + quote(table)
                                        + where
                                        + ") t")) {
            assertTrue(r.next());
            return r.getString(1);
        }
    }
}
