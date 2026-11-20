package com.casms.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.Configuration;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

class DatabaseConfigTest {
    private final DatabaseConfig config = new DatabaseConfig();

    @Test
    void defaultStartupOnlyMigrates() {
        Flyway flyway = mock(Flyway.class);

        config.flywayMigrationStrategy("").migrate(flyway);

        verify(flyway).migrate();
        verifyNoMoreInteractions(flyway);
    }

    @Test
    void explicitLegacyUpgradeRejectsDifferentDatasourceBeforeAnyWrites() throws Exception {
        Flyway flyway = mock(Flyway.class);
        Configuration configuration = mock(Configuration.class);
        DataSource datasource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(flyway.getConfiguration()).thenReturn(configuration);
        when(configuration.getDataSource()).thenReturn(datasource);
        when(datasource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getURL()).thenReturn("jdbc:postgresql://127.0.0.1:15439/actual");

        assertThrows(IllegalStateException.class, () -> config
                .flywayMigrationStrategy("jdbc:postgresql://127.0.0.1:15439/different")
                .migrate(flyway));

        verify(flyway).getConfiguration();
        verifyNoMoreInteractions(flyway);
        verify(connection).getMetaData();
        verify(connection).close();
        verifyNoMoreInteractions(connection);
    }
}
