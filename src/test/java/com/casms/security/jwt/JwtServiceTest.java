package com.casms.security.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

class JwtServiceTest {

    private static final String TEST_SECRET = "0123456789abcdef0123456789abcdef";
    private final JwtService jwt = new JwtService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwt, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwt, "expirationMs", 60_000L);
    }

    @Test
    void extractsUserIdFromValidAccessToken() {
        var userId = UUID.randomUUID();

        assertEquals(Optional.of(userId), jwt.findValidUserId(jwt.generateToken("username", userId)));
    }

    @Test
    void rejectsExpiredToken() {
        var token = signedToken(UUID.randomUUID().toString(), Instant.now().minusSeconds(60), TEST_SECRET);

        assertTrue(jwt.findValidUserId(token).isEmpty());
    }

    @Test
    void rejectsTokenSignedWithDifferentKey() {
        var token = signedToken(
                UUID.randomUUID().toString(), Instant.now().plusSeconds(60),
                "different-key-0123456789abcdef012345");

        assertTrue(jwt.findValidUserId(token).isEmpty());
    }

    @Test
    void rejectsTokensWithoutUsableIdentityOrExpiration() {
        var expires = Instant.now().plusSeconds(60);

        assertTrue(jwt.findValidUserId(signedToken(null, expires, TEST_SECRET)).isEmpty());
        assertTrue(jwt.findValidUserId(signedToken("not-a-uuid", expires, TEST_SECRET)).isEmpty());
        assertTrue(jwt.findValidUserId(signedToken(UUID.randomUUID().toString(), null, TEST_SECRET)).isEmpty());
        assertTrue(jwt.findValidUserId("malformed").isEmpty());
    }

    private static String signedToken(String userId, Instant expires, String secret) {
        var builder = Jwts.builder().subject("username");
        if (userId != null) {
            builder.claim("userId", userId);
        }
        if (expires != null) {
            builder.expiration(Date.from(expires));
        }
        return builder.signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }
}
