package com.casms.servicepackage.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/** Loads the version-controlled, idempotent sample catalogue only in explicitly selected demo profiles. */
@Component
@Profile({"dev", "demo"})
public class DemoCatalogSeeder implements ApplicationRunner {
    private final DataSource dataSource;

    public DemoCatalogSeeder(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/demo/casms-85-catalog.sql"));
        populator.setContinueOnError(false);
        populator.setIgnoreFailedDrops(false);
        DatabasePopulatorUtils.execute(populator, dataSource);
    }
}
