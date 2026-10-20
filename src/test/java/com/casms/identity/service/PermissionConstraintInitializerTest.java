package com.casms.identity.service;

import com.casms.identity.enums.Permission;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionConstraintInitializerTest {

    @Test
    void migrationAllowlistMatchesEveryPermissionEnumValue() throws IOException {
        String migration;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V6__sync_role_permission_constraint.sql")) {
            assertTrue(stream != null, "V6 permission constraint migration must be on the classpath");
            migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        Set<String> expected = Arrays.stream(Permission.values())
                .map(Enum::name)
                .collect(Collectors.toSet());
        assertEquals(expected, PermissionConstraintInitializer.permissionValues(migration));
        assertTrue(migration.contains("CHECK ("));
        assertTrue(migration.contains("(permission)::text = ANY (ARRAY["));
    }
}
