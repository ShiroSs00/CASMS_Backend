package com.casms.catalog;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfEnvironmentVariable(named = "CASMS_CATALOG_TEST_PG_PORT", matches = "[0-9]{1,5}")
class CatalogBranch75MigrationPostgresTest {
    @Test
    void freshAndV95UpgradeAcceptBothSeedsTwice() throws Exception {
        String port = System.getenv("CASMS_CATALOG_TEST_PG_PORT");
        String base = "jdbc:postgresql://127.0.0.1:" + port + "/";
        try (var admin = DriverManager.getConnection(base + "postgres", "postgres", "");
                var statement = admin.createStatement()) {
            for (String target : List.of("fresh", "95")) {
                String database = "casms_catalog_" + UUID.randomUUID().toString().replace("-", "");
                statement.execute("CREATE DATABASE " + database);
                try {
                    String url = base + database;
                    if ("95".equals(target)) {
                        Flyway.configure().dataSource(url, "postgres", "").target("95").load().migrate();
                    }
                    Flyway flyway = Flyway.configure().dataSource(url, "postgres", "").load();
                    flyway.migrate();
                    flyway.validate();
                    assertEquals("112", flyway.info().current().getVersion().getVersion());
                    CatalogBranch75PostgresFixtures.runSeeds(url);
                } finally {
                    statement.execute("DROP DATABASE " + database + " WITH (FORCE)");
                }
            }
        }
    }
}
