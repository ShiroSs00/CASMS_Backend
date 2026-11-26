package com.casms.pos;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/** Fixture-scoped integration tests against the already migrated, explicitly selected local database. */
@TestConfiguration(proxyBeanMethods = false)
@Profile("mobile-casms-verification")
class LocalCasmsVerificationConfiguration {
    @Bean
    static BeanFactoryPostProcessor isolateIntegrationFixture(Environment environment) {
        return factory -> {
            String url = environment.getRequiredProperty("spring.datasource.url");
            if (!"jdbc:postgresql://127.0.0.1:5432/casms?stringtype=unspecified".equals(url)
                    || environment.getProperty("spring.flyway.enabled", Boolean.class, true)
                    || !"validate".equals(environment.getProperty("spring.jpa.hibernate.ddl-auto"))) {
                throw new IllegalStateException("Mobile verification requires the validated local casms datasource");
            }
            var registry = (BeanDefinitionRegistry) factory;
            for (String bean : new String[] {"rolePermissionInitializer", "permissionConstraintInitializer",
                    "org.springframework.context.annotation.internalScheduledAnnotationProcessor"}) {
                if (registry.containsBeanDefinition(bean)) registry.removeBeanDefinition(bean);
            }
        };
    }
}
