package com.casms.pos;

import com.casms.booking.entity.BookingPassenger;
import com.casms.booking.enums.BookingPassengerParticipationStatus;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.checkin.enums.CruiseCheckinStatus;
import com.casms.checkin.repository.CruiseCheckinRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.security.principal.CasmsPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PassengerCardService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration CLAIM_TTL = Duration.ofMinutes(10);

    private final PosService sessions;
    private final BookingPassengerRepository passengers;
    private final CruiseCheckinRepository checkins;
    private final JdbcTemplate jdbc;

    public record CardClaim(String code, Instant expiresAt) {}
    public record PassengerPreview(UUID bookingPassengerId, String passengerName) {}
    public record CardAssignment(UUID bookingPassengerId, String passengerName, Instant confirmedAt) {}

    public enum AssignmentStatus { NOT_ASSIGNED, ASSIGNED, UNAVAILABLE }
    public record CardStatus(AssignmentStatus status, Instant confirmedAt) {}

    @Transactional(readOnly = true)
    public CardStatus status(UUID bookingPassengerId, UUID userId) {
        var passenger = passengers.findById(bookingPassengerId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking passenger", bookingPassengerId));
        requireOwner(passenger, userId);
        if (passenger.getBooking().getBookingStatus() != BookingStatus.Confirmed
                || passenger.getParticipationStatus() != BookingPassengerParticipationStatus.Active) {
            return new CardStatus(AssignmentStatus.UNAVAILABLE, null);
        }
        return jdbc.query("""
                SELECT confirmed_at FROM "PassengerNfcCard"
                WHERE booking_passenger_id = ? AND cruise_id = ?
                """, (rs, row) -> new CardStatus(AssignmentStatus.ASSIGNED, rs.getTimestamp(1).toInstant()),
                bookingPassengerId, passenger.getBooking().getCruise().getCruiseId())
                .stream().findFirst().orElse(new CardStatus(AssignmentStatus.NOT_ASSIGNED, null));
    }

    @Transactional
    public CardClaim issue(UUID bookingPassengerId, UUID userId) {
        var passenger = passengers.lockById(bookingPassengerId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking passenger", bookingPassengerId));
        requireOwner(passenger, userId);
        requireEligible(passenger);
        if (Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM "PassengerNfcCard" WHERE booking_passenger_id = ?)
                """, Boolean.class, bookingPassengerId))) {
            throw conflict("PASSENGER_CARD_ALREADY_ASSIGNED", "Passenger already has a card");
        }

        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        Instant expiresAt = Instant.now().plus(CLAIM_TTL);
        jdbc.update("""
                INSERT INTO "PassengerCardClaim" (booking_passenger_id, code_hash, expires_at)
                VALUES (?, ?, ?)
                ON CONFLICT (booking_passenger_id) DO UPDATE
                SET code_hash = EXCLUDED.code_hash, expires_at = EXCLUDED.expires_at, consumed_at = NULL
                """, bookingPassengerId, hash(code), Timestamp.from(expiresAt));
        return new CardClaim(code, expiresAt);
    }

    @Transactional
    public PassengerPreview preview(String token, CasmsPrincipal employee, String code) {
        UUID cruiseId = sessions.access(token, employee).assignment().cruiseId();
        var passenger = lockClaimPassenger(hash(code), cruiseId);
        return new PassengerPreview(passenger.getBookingPassengerId(),
                passenger.getPassenger().getFullName());
    }

    @Transactional
    public CardAssignment confirm(
            String token, CasmsPrincipal employee, String code, String cardIdentifier) {
        UUID cruiseId = sessions.access(token, employee).assignment().cruiseId();
        String digest = hash(code);
        String card = normalizeCard(cardIdentifier);
        var passenger = lockClaimPassenger(digest, cruiseId);
        UUID passengerId = passenger.getBookingPassengerId();
        Instant now = Instant.now();
        int consumed = jdbc.update("""
                UPDATE "PassengerCardClaim" SET consumed_at = ?
                WHERE booking_passenger_id = ? AND code_hash = ?
                  AND consumed_at IS NULL AND expires_at > ?
                """, Timestamp.from(now), passengerId, digest, Timestamp.from(now));
        if (consumed != 1) {
            throw invalidClaim();
        }
        int inserted = jdbc.update("""
                INSERT INTO "PassengerNfcCard"
                (booking_passenger_id, cruise_id, card_identifier, confirmed_by, confirmed_at)
                VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                """, passengerId, cruiseId, card, employee.getUserId(), Timestamp.from(now));
        if (inserted != 1) {
            throw conflict("PASSENGER_CARD_ALREADY_ASSIGNED",
                    "The passenger or card already has an assignment on this cruise");
        }
        return new CardAssignment(passengerId, passenger.getPassenger().getFullName(), now);
    }

    @Transactional
    public CardAssignment lookup(String token, CasmsPrincipal employee, String cardIdentifier) {
        UUID cruiseId = sessions.access(token, employee).assignment().cruiseId();
        String card = normalizeCard(cardIdentifier);
        return jdbc.query("""
                SELECT c.booking_passenger_id, p.full_name, c.confirmed_at
                FROM "PassengerNfcCard" c
                JOIN "BookingPassenger" bp ON bp.booking_passenger_id = c.booking_passenger_id
                JOIN "Booking" b ON b.booking_id = bp.booking_id AND b.cruise_id = c.cruise_id
                JOIN "Passenger" p ON p.passenger_id = bp.passenger_id
                WHERE c.cruise_id = ? AND c.card_identifier = ?
                  AND bp.participation_status = 'Active' AND b.booking_status = 'Confirmed'
                """, (rs, row) -> new CardAssignment(rs.getObject(1, UUID.class),
                        rs.getString(2), rs.getTimestamp(3).toInstant()), cruiseId, card)
                .stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Passenger card not found"));
    }

    private BookingPassenger lockClaimPassenger(String digest, UUID cruiseId) {
        // Acquire locks in the same order as issuance, then re-read the claim in case it was rotated.
        UUID id = jdbc.query("""
                SELECT c.booking_passenger_id
                FROM "PassengerCardClaim" c
                JOIN "BookingPassenger" bp ON bp.booking_passenger_id = c.booking_passenger_id
                JOIN "Booking" b ON b.booking_id = bp.booking_id
                WHERE c.code_hash = ? AND b.cruise_id = ?
                """, (rs, row) -> rs.getObject(1, UUID.class), digest, cruiseId)
                .stream().findFirst().orElseThrow(PassengerCardService::invalidClaim);
        var passenger = passengers.lockById(id).orElseThrow(PassengerCardService::invalidClaim);
        if (!cruiseId.equals(passenger.getBooking().getCruise().getCruiseId())
                || !Boolean.TRUE.equals(jdbc.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1 FROM "PassengerCardClaim" WHERE booking_passenger_id = ?
                            AND code_hash = ? AND consumed_at IS NULL AND expires_at > ?
                        )
                        """, Boolean.class, id, digest, Timestamp.from(Instant.now())))) {
            throw invalidClaim();
        }
        requireEligible(passenger);
        return passenger;
    }

    private void requireOwner(BookingPassenger passenger, UUID userId) {
        var owner = passenger.getPassenger().getUser();
        if (owner == null || !owner.getUserId().equals(userId)) {
            throw new AccessDeniedException("You can only access your own card claim");
        }
    }

    private void requireEligible(BookingPassenger passenger) {
        if (passenger.getBooking().getBookingStatus() != BookingStatus.Confirmed
                || passenger.getParticipationStatus() != BookingPassengerParticipationStatus.Active
                || checkins.findByBookingPassenger_BookingPassengerId(passenger.getBookingPassengerId())
                        .filter(c -> c.getCheckinStatus() == CruiseCheckinStatus.BoardingReady)
                        .isEmpty()) {
            throw conflict("PASSENGER_CARD_CHECKIN_REQUIRED",
                    "An active passenger on a confirmed booking must complete check-in");
        }
    }

    private static String normalizeCard(String card) {
        if (card == null || !card.matches("(?:[0-9a-fA-F]{2}){4,32}")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_NFC_CARD_IDENTIFIER",
                    "Use a hexadecimal card identifier of four to 32 bytes");
        }
        return card.toUpperCase(Locale.ROOT);
    }

    private static String hash(String code) {
        if (code == null || !code.matches("[A-Za-z0-9_-]{43}")) {
            throw invalidClaim();
        }
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static BusinessException invalidClaim() {
        return new BusinessException(HttpStatus.NOT_FOUND, "PASSENGER_CARD_CLAIM_INVALID",
                "Card claim is invalid, expired or already used");
    }

    private static BusinessException conflict(String code, String message) {
        return new BusinessException(HttpStatus.CONFLICT, code, message);
    }
}
