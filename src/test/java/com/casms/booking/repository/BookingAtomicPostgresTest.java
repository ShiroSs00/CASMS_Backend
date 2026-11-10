package com.casms.booking.repository;

import com.casms.booking.dto.response.BookingDetailResponse;
import com.casms.common.exception.BusinessException;
import com.casms.trip.service.IdempotencyReservationService;
import com.casms.trip.service.IdempotencyService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Opt-in only: point CASMS_TEST_POSTGRES_URL at a disposable PostgreSQL database. */
@EnabledIfEnvironmentVariable(named = "CASMS_TEST_POSTGRES_URL", matches = ".+")
@SpringBootTest(properties = {
        "spring.datasource.url=${CASMS_TEST_POSTGRES_URL}",
        "spring.datasource.username=postgres", "spring.datasource.password=",
        "casms.booking-email.enabled=false"})
class BookingAtomicPostgresTest {
    @Autowired IdempotencyService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.casms.notification.service.BookingEmailService emailService;
    @Autowired com.casms.identity.repository.UserRepository users;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
    @SpyBean IdempotencyReservationService reservations;

    @BeforeEach void prepare() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS \"BookingAtomicProbe\" (id uuid PRIMARY KEY)");
    }

    private IdempotencyReservationService reservationSpy() {
        return org.springframework.test.util.AopTestUtils.getUltimateTargetObject(reservations);
    }

    @AfterEach void resetFailureInjection() { reset(reservationSpy()); }

    @Test void completionFailureRollsBackBusinessWriteAndReservationThenRetrySucceeds() {
        String key = UUID.randomUUID().toString();
        UUID id = UUID.randomUUID();
        doThrow(new IllegalStateException("injected completion write failure"))
                .when(reservationSpy()).completeKeyInCurrentTransaction(any(), any(), any());
        assertEquals("IDEMPOTENCY_COMPLETION_FAILED", assertThrows(BusinessException.class,
                () -> service.executeBookingCreate(key, null, Map.of("trip", "one"), () -> createProbe(id))).getCode());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"BookingAtomicProbe\" WHERE id = ?", Integer.class, id));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM \"IdempotencyRecord\" WHERE operation = 'CREATE_BOOKING' AND idempotency_key = ?", Integer.class, key));
        reset(reservationSpy());
        assertEquals(id, service.executeBookingCreate(key, null, Map.of("trip", "one"), () -> createProbe(id)).getBookingId());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM \"BookingAtomicProbe\" WHERE id = ?", Integer.class, id));
    }

    @Test void concurrentGuestRetryExecutesBusinessActionOnce() throws Exception {
        String key = UUID.randomUUID().toString();
        UUID id = UUID.randomUUID();
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Callable<BookingDetailResponse> request = () -> {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return service.executeBookingCreate(key, null, Map.of("trip", "two"), () -> {
                    calls.incrementAndGet();
                    return createProbe(id);
                });
            };
            Future<BookingDetailResponse> first = pool.submit(request);
            Future<BookingDetailResponse> second = pool.submit(request);
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            assertEquals(id, first.get(30, TimeUnit.SECONDS).getBookingId());
            assertEquals(id, second.get(30, TimeUnit.SECONDS).getBookingId());
            assertEquals(1, calls.get());
        }
    }

    private BookingDetailResponse createProbe(UUID id) {
        jdbc.update("INSERT INTO \"BookingAtomicProbe\" (id) VALUES (?)", id);
        BookingDetailResponse result = new BookingDetailResponse(); result.setBookingId(id);
        return result;
    }

    @Test void accountSetupIsSingleUseAndOnlyChangesTheProvisionedAccount() throws Exception {
        var user = new com.casms.identity.entity.User();
        user.setUsername("setup-" + UUID.randomUUID());
        user.setEmail(UUID.randomUUID() + "@example.com"); user.setPasswordHash("old-hash");
        user.setStatus(com.casms.identity.enums.UserStatus.ACTIVE);
        user = users.saveAndFlush(user);
        String token = UUID.randomUUID().toString();
        String hash = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        jdbc.update("INSERT INTO \"BookingAccountSetup\" (user_id, token_hash, expires_at) VALUES (?, ?, now() + interval '1 hour')", user.getUserId(), hash);
        emailService.activate(token, "long-verified-password");
        assertTrue(encoder.matches("long-verified-password", users.findById(user.getUserId()).orElseThrow().getPasswordHash()));
        assertEquals("ACCOUNT_SETUP_INVALID", assertThrows(BusinessException.class,
                () -> emailService.activate(token, "different-password-now")).getCode());
    }
}
