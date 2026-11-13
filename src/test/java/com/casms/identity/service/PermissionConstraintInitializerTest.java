package com.casms.identity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.casms.identity.enums.Permission;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

class PermissionConstraintInitializerTest {

    @Test
    void initializerUpdatesTheConstraintWithEveryPermissionEnumValue() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        new PermissionConstraintInitializer(jdbc).run();

        var statement = ArgumentCaptor.forClass(String.class);
        verify(jdbc).execute(contains("DROP CONSTRAINT IF EXISTS"));
        verify(jdbc, org.mockito.Mockito.times(2)).execute(statement.capture());
        String definition = statement.getAllValues().getLast();

        Set<String> expected =
                Arrays.stream(Permission.values()).map(Enum::name).collect(Collectors.toSet());
        assertEquals(expected, PermissionConstraintInitializer.permissionValues(definition));
    }
}
