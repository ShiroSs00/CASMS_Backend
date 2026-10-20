package com.casms.identity.service;

import com.casms.identity.enums.Permission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Keeps the persisted permission allowlist aligned with Permission before RBAC rows are initialized. */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class PermissionConstraintInitializer implements CommandLineRunner {

    private static final String TABLE = "\"RolePermissionMapping\"";
    private static final String CONSTRAINT = "RolePermissionMapping_permission_check";
    private static final Pattern SQL_STRING = Pattern.compile("'((?:''|[^'])*)'");

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) {
        Set<String> expected = Arrays.stream(Permission.values())
                .map(Enum::name)
                .collect(Collectors.toUnmodifiableSet());

        String currentDefinition = jdbcTemplate.query(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint " +
                        "WHERE conrelid = to_regclass('" + TABLE + "') AND conname = ?",
                rs -> rs.next() ? rs.getString(1) : null,
                CONSTRAINT);

        if (currentDefinition != null && permissionValues(currentDefinition).equals(expected)) {
            log.debug("Permission check constraint already covers all {} permissions", expected.size());
            return;
        }

        Set<String> existing = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT DISTINCT permission FROM " + TABLE + " WHERE permission IS NOT NULL", String.class));
        existing.removeAll(expected);
        if (!existing.isEmpty()) {
            throw new IllegalStateException("Cannot update permission check constraint; existing values are " +
                    "not defined in Permission enum: " + existing);
        }

        String allowed = expected.stream().sorted()
                .map(value -> "'" + value + "'")
                .collect(Collectors.joining(", "));
        jdbcTemplate.execute("ALTER TABLE " + TABLE + " DROP CONSTRAINT IF EXISTS \"" + CONSTRAINT + "\"");
        jdbcTemplate.execute("ALTER TABLE " + TABLE + " ADD CONSTRAINT \"" + CONSTRAINT + "\" " +
                "CHECK ((permission)::text = ANY (ARRAY[" + allowed + "]::text[]))");
        log.info("Updated {} to accept all {} Permission enum values", CONSTRAINT, expected.size());
    }

    static Set<String> permissionValues(String definition) {
        Set<String> values = new HashSet<>();
        Matcher matcher = SQL_STRING.matcher(definition);
        while (matcher.find()) {
            values.add(matcher.group(1).replace("''", "'"));
        }
        return values;
    }
}
