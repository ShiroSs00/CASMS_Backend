package com.casms.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CodeGeneratorTest {

    @Test
    @DisplayName("Should generate ENTITY-XXX code with 3-digit padding")
    void shouldGenerateFormattedCode() {
        String code = CodeGenerator.generate("CRUISE", 1, s -> false);
        assertThat(code).isEqualTo("CRUISE-001");

        String code99 = CodeGenerator.generate("ACTIVITY", 99, s -> false);
        assertThat(code99).isEqualTo("ACTIVITY-099");

        String code100 = CodeGenerator.generate("EXCURSION", 100, s -> false);
        assertThat(code100).isEqualTo("EXCURSION-100");

        String code1234 = CodeGenerator.generate("ISSUE", 1234, s -> false);
        assertThat(code1234).isEqualTo("ISSUE-1234");
    }

    @Test
    @DisplayName("Should handle collision and increment sequence until unique")
    void shouldHandleCollision() {
        Set<String> existingCodes = new HashSet<>();
        existingCodes.add("SHIP-001");
        existingCodes.add("SHIP-002");

        String code = CodeGenerator.generate("SHIP", 1, existingCodes::contains);
        assertThat(code).isEqualTo("SHIP-003");
    }

    @Test
    @DisplayName("Should uppercase entity prefix")
    void shouldUppercaseEntityPrefix() {
        String code = CodeGenerator.generate("service", 5, s -> false);
        assertThat(code).isEqualTo("SERVICE-005");
    }
}
