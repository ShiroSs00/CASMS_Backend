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
     * Realigns only reviewed legacy checksums of historically edited migrations, then migrates with
     * Flyway validation enabled so any new edit of an applied migration fails fast.
     */
    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        LegacyChecksumRealigner realigner = new LegacyChecksumRealigner();
        return flyway -> {
            realigner.realign(flyway);
            flyway.migrate();
        };
    }
}

