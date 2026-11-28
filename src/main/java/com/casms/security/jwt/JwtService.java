package com.casms.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.SecretKey;

@Service
public class JwtService {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_SESSION_STARTED_AT = "sid_iat";

    @Value("${jwt.secret:}")
    private String secret;

    @Value("${jwt.expiration-ms:86400000}")
    private long expirationMs;

    @Value("${jwt.max-session-ms:604800000}")
    private long maxSessionMs;

    private final Map<String, Instant> revokedTokens = new ConcurrentHashMap<>();

    private SecretKey key() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT secret is not configured");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 UTF-8 bytes");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public String generateToken(String username, UUID userId) {
        return generateToken(username, userId, Instant.now());
    }

    public String generateToken(String username, UUID userId, Instant sessionStartedAt) {
        long nowMs = System.currentTimeMillis();
        long sidIat = sessionStartedAt != null ? sessionStartedAt.toEpochMilli() : nowMs;
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(username)
                .claim(CLAIM_USER_ID, userId.toString())
                .claim(CLAIM_SESSION_STARTED_AT, sidIat)
                .issuedAt(new Date(nowMs))
                .expiration(new Date(nowMs + expirationMs))
                .signWith(key())
                .compact();
    }

    public void revokeToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        purgeExpiredRevocations();
        try {
            Claims claims = parseClaimsUncheckedRevocation(token);
            Date exp = claims.getExpiration();
            Instant expiresAt = exp != null ? exp.toInstant() : Instant.now().plusMillis(expirationMs);
            revokedTokens.put(revocationKey(claims, token), expiresAt);
        } catch (JwtException | IllegalArgumentException e) {
            // Even if token is malformed/expired, record hash briefly to fail closed
            revokedTokens.put(sha256(token), Instant.now().plusMillis(Math.min(expirationMs, 3_600_000L)));
        }
    }

    public boolean isRevoked(String token) {
        if (token == null || token.isBlank()) {
            return true;
        }
        purgeExpiredRevocations();
        if (revokedTokens.containsKey(sha256(token))) {
            return true;
        }
        try {
            Claims claims = parseClaimsUncheckedRevocation(token);
            String key = revocationKey(claims, token);
            Instant exp = revokedTokens.get(key);
            return exp != null && exp.isAfter(Instant.now());
        } catch (JwtException | IllegalArgumentException e) {
            return true;
        }
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(parseClaims(token).get(CLAIM_USER_ID, String.class));
    }

    public Optional<UUID> findValidUserId(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = parseClaims(token);
            String id = claims.get(CLAIM_USER_ID, String.class);
            if (claims.getExpiration() == null || id == null) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(id));
        } catch (JwtException | IllegalArgumentException | IllegalStateException e) {
            return Optional.empty();
        }
    }

    public Optional<Instant> findRefreshableSessionStart(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = parseClaims(token);
            Number sidIatNum = claims.get(CLAIM_SESSION_STARTED_AT, Number.class);
            Instant sessionStart =
                    sidIatNum != null
                            ? Instant.ofEpochMilli(sidIatNum.longValue())
                            : (claims.getIssuedAt() != null ? claims.getIssuedAt().toInstant() : Instant.now());
            if (maxSessionMs > 0 && Instant.now().isAfter(sessionStart.plusMillis(maxSessionMs))) {
                return Optional.empty();
            }
            return Optional.of(sessionStart);
        } catch (JwtException | IllegalArgumentException | IllegalStateException e) {
            return Optional.empty();
        }
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException | IllegalStateException e) {
            return false;
        }
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public Instant expiresAt(String token) {
        return parseClaims(token).getExpiration().toInstant();
    }

    private Claims parseClaims(String token) {
        purgeExpiredRevocations();
        if (revokedTokens.containsKey(sha256(token))) {
            throw new JwtException("Token has been revoked");
        }
        Claims claims = parseClaimsUncheckedRevocation(token);
        String key = revocationKey(claims, token);
        Instant exp = revokedTokens.get(key);
        if (exp != null && exp.isAfter(Instant.now())) {
            throw new JwtException("Token has been revoked");
        }
        return claims;
    }

    private Claims parseClaimsUncheckedRevocation(String token) {
        return parser().parseSignedClaims(token).getPayload();
    }

    private String revocationKey(Claims claims, String token) {
        String jti = claims.getId();
        return (jti != null && !jti.isBlank()) ? "jti:" + jti : sha256(token);
    }

    private void purgeExpiredRevocations() {
        Instant now = Instant.now();
        revokedTokens.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private JwtParser parser() {
        return Jwts.parser().verifyWith(key()).build();
    }
}
