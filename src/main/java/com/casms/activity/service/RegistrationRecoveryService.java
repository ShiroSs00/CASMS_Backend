package com.casms.activity.service;

import com.casms.activity.entity.Registration;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.security.principal.CasmsPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegistrationRecoveryService {
    private static final String OPERATION = "MOBILE_ACTIVITY_REGISTRATION";
    private final ActivityService activities;
    private final JdbcTemplate jdbc;

    @Transactional
    public Registration register(CasmsPrincipal actor, Kind kind, UUID passenger, UUID resource,
                                 Integer quantity, String suppliedKey) {
        int count = quantity == null ? 1 : quantity;
        if (suppliedKey == null) return create(actor, kind, passenger, resource, count);

        String key = validateKey(suppliedKey);
        UUID userId = actor.getUserId();
        String hash = fingerprint(kind, passenger, resource, count);
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                OPERATION + ":" + userId + ":" + key);
        Optional<Stored> previous = find(userId, key);
        if (previous.isPresent()) {
            if (!hash.equals(previous.get().hash()))
                throw new BusinessException(HttpStatus.CONFLICT, "REGISTRATION_IDEMPOTENCY_CONFLICT",
                        "Key belongs to a different registration request");
            return activities.getRegistration(previous.get().id(), userId, actor.isStaff());
        }

        Registration registration = create(actor, kind, passenger, resource, count);
        jdbc.update("""
                INSERT INTO "IdempotencyRecord"
                    (actor_id, operation, idempotency_key, request_hash, resulting_id)
                VALUES (?, ?, ?, ?, ?)
                """, userId, OPERATION, key, hash, registration.getRegistrationId());
        return registration;
    }

    @Transactional(readOnly = true)
    public Registration recover(CasmsPrincipal actor, String key) {
        var record = find(actor.getUserId(), validateKey(key))
                .orElseThrow(() -> new ResourceNotFoundException("Registration operation not found"));
        Registration registration = activities.getRegistration(record.id(), actor.getUserId(), actor.isStaff());
        Kind kind = registration.getShoreExcursion() == null ? Kind.ACTIVITY : Kind.EXCURSION;
        if (!actor.hasAuthority(kind.permission))
            throw new org.springframework.security.access.AccessDeniedException("Registration permission is required");
        return registration;
    }

    private Registration create(CasmsPrincipal actor, Kind kind, UUID passenger, UUID resource, int quantity) {
        return kind == Kind.ACTIVITY
                ? activities.register(passenger, resource, quantity, actor.getUserId(), actor.isStaff())
                : activities.registerExcursion(passenger, resource, quantity, actor.getUserId(), actor.isStaff());
    }

    private Optional<Stored> find(UUID actor, String key) {
        return jdbc.query("""
                SELECT request_hash, resulting_id FROM "IdempotencyRecord"
                WHERE actor_id = ? AND operation = ? AND idempotency_key = ?
                """, (r, n) -> new Stored(r.getString(1), r.getObject(2, UUID.class)),
                actor, OPERATION, key).stream().findFirst();
    }

    private static String validateKey(String key) {
        String normalized = key.trim();
        if (normalized.isEmpty() || normalized.length() > 100)
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REGISTRATION_IDEMPOTENCY_KEY_INVALID",
                    "Use a nonblank operation key of up to 100 characters");
        return normalized;
    }

    private static String fingerprint(Kind kind, UUID passenger, UUID resource, int quantity) {
        String input = kind.name() + "|" + passenger + "|" + resource + "|" + quantity;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public enum Kind {
        ACTIVITY("ACTIVITY_REGISTRATION"), EXCURSION("EXCURSION_BOOKING");
        private final String permission;
        Kind(String permission) { this.permission = permission; }
    }

    private record Stored(String hash, UUID id) {}
}
