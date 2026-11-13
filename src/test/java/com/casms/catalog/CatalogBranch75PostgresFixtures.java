package com.casms.catalog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;

final class CatalogBranch75PostgresFixtures {
    private CatalogBranch75PostgresFixtures() {}

    static void runSeeds(String url) throws IOException, SQLException {
        Path seeds = Path.of("src/main/resources/db/production-ready");
        String catalog = Files.readString(seeds.resolve("catalog.sql"));
        String quotas = Files.readString(seeds.resolve("package-quotas-draft.sql"));
        String assertions = Files.readString(Path.of("src/test/resources/db/branch75_catalog_assertions.sql"));
        try (var connection = DriverManager.getConnection(url, "postgres", "");
                var statement = connection.createStatement()) {
            for (int pass = 0; pass < 2; pass++) {
                statement.execute(catalog);
                statement.execute(quotas);
                statement.execute(assertions);
            }
        }
    }
}
