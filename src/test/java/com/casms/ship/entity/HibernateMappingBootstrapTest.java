package com.casms.ship.entity;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import jakarta.persistence.Entity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class HibernateMappingBootstrapTest {

    @Test
    void compositeShipMappingsBuildWithoutDatabaseAccess() {
        assertDoesNotThrow(() -> {
            StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                    .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                    .applySetting("hibernate.boot.allow_jdbc_metadata_access", "false")
                    .build();
            try {
                MetadataSources metadataSources = new MetadataSources(registry);
                ClassPathScanningCandidateComponentProvider scanner =
                        new ClassPathScanningCandidateComponentProvider(false);
                scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
                for (var candidate : scanner.findCandidateComponents("com.casms")) {
                    metadataSources.addAnnotatedClass(Class.forName(candidate.getBeanClassName()));
                }
                metadataSources.buildMetadata().buildSessionFactory().close();
            } finally {
                StandardServiceRegistryBuilder.destroy(registry);
            }
        });
    }
}
