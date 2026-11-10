package com.casms.notification.service;

import com.casms.booking.entity.Booking;
import com.casms.common.exception.BusinessException;
import com.casms.identity.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingEmailService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    @Value("${casms.booking-email.portal-url:http://localhost:3000}")
    private String portalUrl;

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void lockContact(String email) {
        // Serialize this provisioning flow; the database unique index also protects other writers.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtext(?))", email);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void markProvisioned(User user) {
        jdbc.update("INSERT INTO \"BookingAccountSetup\" (user_id) VALUES (?) ON CONFLICT DO NOTHING", user.getUserId());
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void enqueueConfirmation(Booking booking, BigDecimal paid) {
        User user = booking.getBookedByUser();
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            throw new BusinessException(HttpStatus.CONFLICT, "BOOKING_EMAIL_REQUIRED", "A booking contact email is required");
        }
        String event = "BOOKING_CONFIRMED:" + booking.getBookingId();
        if (Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM \"BookingEmailOutbox\" WHERE event_key = ?)", Boolean.class, event))) return;
        String setup = "";
        // Only accounts provisioned by booking can be activated; existing passwords are untouched.
        var pending = jdbc.queryForList("SELECT user_id FROM \"BookingAccountSetup\" WHERE user_id = ? AND activated_at IS NULL AND (token_hash IS NULL OR expires_at <= now()) FOR UPDATE", UUID.class, user.getUserId());
        if (!pending.isEmpty()) {
            byte[] bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            jdbc.update("UPDATE \"BookingAccountSetup\" SET token_hash = ?, expires_at = ? WHERE user_id = ?",
                    hash(token), java.sql.Timestamp.from(Instant.now().plusSeconds(7 * 86400)), user.getUserId());
            setup = "\nSet your password: " + portalUrl + "/activate-booking-account#token=" + token;
        }
        String body = "Booking " + booking.getBookingReference() + " confirmed.\nCruise: "
                + booking.getCruise().getCruiseName() + "\nDeparture: " + booking.getCruise().getDepartureDatetime()
                + "\nReturn: " + booking.getCruise().getReturnDatetime() + "\nPaid: " + paid + " " + booking.getCurrency()
                + "\nOutstanding: " + booking.getAgreedFareAmount().subtract(paid).max(BigDecimal.ZERO)
                + " " + booking.getCurrency() + "\nUsername: " + user.getUsername()
                + "\nView your booking, installments and daily itinerary: " + portalUrl + setup;
        jdbc.update("INSERT INTO \"BookingEmailOutbox\" (event_key, booking_id, recipient, subject, body) VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING",
                event, booking.getBookingId(), user.getEmail(), "CASMS booking confirmation", body);
    }

    @Transactional
    public void activate(String token, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PASSWORD_TOO_LONG", "Password must fit 72 UTF-8 bytes");
        }
        var users = jdbc.queryForList("SELECT user_id FROM \"BookingAccountSetup\" WHERE token_hash = ? AND activated_at IS NULL AND expires_at > now() FOR UPDATE",
                UUID.class, hash(token));
        if (users.isEmpty()) throw new BusinessException(HttpStatus.BAD_REQUEST, "ACCOUNT_SETUP_INVALID", "Setup token is invalid or expired");
        UUID userId = users.get(0);
        int updated = jdbc.update("UPDATE \"User\" SET password_hash = ?, updated_at = now() WHERE user_id = ? AND status = 'ACTIVE'",
                passwordEncoder.encode(password), userId);
        if (updated != 1) throw new BusinessException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "Account is disabled");
        jdbc.update("UPDATE \"BookingAccountSetup\" SET activated_at = now(), token_hash = NULL WHERE user_id = ?", userId);
        // Remove the bearer setup link from retained outbox bodies after successful activation.
        jdbc.update("UPDATE \"BookingEmailOutbox\" SET body = 'Account setup completed' WHERE booking_id IN (SELECT booking_id FROM \"Booking\" WHERE booked_by_user_id = ?) AND body LIKE '%activate-booking-account#token=%'", userId);
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
