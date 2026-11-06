package com.casms.config;

import com.casms.config.flyway.LegacyChecksumRealigner;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "com.casms")
public class DatabaseConfig {

    /**
     * Realigns legacy checksums, repairs schema history table if checksums drifted across environments,
     * then executes database migrations.
     */
    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        LegacyChecksumRealigner realigner = new LegacyChecksumRealigner();
        return flyway -> {
            realigner.realign(flyway);
            flyway.repair();
            flyway.migrate();
        };
    }
}
