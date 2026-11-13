package com.casms.ship.service;

import static org.junit.jupiter.api.Assertions.*;

import com.casms.common.exception.BusinessException;
import com.casms.identity.entity.User;
import com.casms.identity.enums.Role;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.repository.RolePermissionRepository;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.dto.request.CruiseStaffRequest;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.Port;
import com.casms.ship.enums.CruiseCruiseType;
import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.enums.PortStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Only run against the approved disposable PostgreSQL datasource. */
@EnabledIfEnvironmentVariable(named = "CASMS_TEST_POSTGRES_URL", matches = ".+")
@SpringBootTest(properties = {
    "spring.datasource.url=${CASMS_TEST_POSTGRES_URL}",
    "spring.datasource.username=postgres",
    "spring.datasource.password=",
    "casms.booking-email.enabled=false",
    "casms.staff-assignment-email.enabled=false",
    "casms.employee-activation-email.enabled=false"
})
class CruiseStaffLifecyclePostgresIntegrationTest {
    @Autowired private EntityManager em;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CruiseStaffService service;
    @Autowired private RolePermissionRepository roles;
    @Autowired private PlatformTransactionManager transactionManager;
    private UUID firstCruise;
    private UUID secondCruise;
    private UUID employee;
    private CasmsPrincipal hr;
    private CasmsPrincipal otherHr;

    @BeforeEach
    void prepare() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var port = new Port();
            port.setPortCode("VN-" + code());
            port.setPortName("HR assignment test port");
            port.setCountryCode("VN");
            port.setTimeZoneId("Asia/Ho_Chi_Minh");
            port.setStatus(PortStatus.ACTIVE);
            em.persist(port);
            firstCruise = cruise(port, Instant.parse("2032-02-01T00:00:00Z"));
            secondCruise = cruise(port, Instant.parse("2032-03-01T00:00:00Z"));
            employee = user(Role.ONBOARD_STAFF).getUserId();
            hr = new CasmsPrincipal(user(Role.HR_MANAGER), roles);
            otherHr = new CasmsPrincipal(user(Role.HR_MANAGER), roles);
        });
    }

    @Test
    void concurrentAssignmentsToOverlappingCruisesHaveOneWinnerAndDatabaseRejectsBypass() throws Exception {
        jdbc.update("""
                UPDATE "Cruise" target SET departure_datetime = source.departure_datetime,
                    return_datetime = source.return_datetime
                FROM "Cruise" source WHERE target.cruise_id = ? AND source.cruise_id = ?
                """, secondCruise, firstCruise);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> assignConcurrently(start, firstCruise, hr));
            var second = pool.submit(() -> assignConcurrently(start, secondCruise, otherHr));
            start.countDown();
            var outcomes = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter("SUCCESS"::equals).count());
            assertEquals(1, outcomes.stream().filter("SHIP_STAFF_SCHEDULE_CONFLICT"::equals).count());
        }
        assertEquals(1L, jdbc.queryForObject("""
                SELECT count(*) FROM "CruiseStaff" WHERE user_id = ? AND status = 'ACTIVE'
                """, Long.class, employee));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO "CruiseStaff" (cruise_staff_id, cruise_id, user_id, role_in_cruise, status)
                VALUES (gen_random_uuid(), ?, ?, 'ONBOARD_STAFF', 'ACTIVE')
                """, secondCruise, employee));
    }

    @Test
    void runningCruiseBlocksRemovalAndCompletionReleasesEmployee() {
        var first = service.assignStaff(firstCruise, request(), hr).getCruiseStaffId();
        jdbc.update("""
                UPDATE "Cruise" SET status = 'IN_PROGRESS', actual_departure_datetime = now()
                WHERE cruise_id = ?
                """, firstCruise);
        assertEquals("CRUISE_STAFF_REMOVAL_NOT_ALLOWED", assertThrows(BusinessException.class,
                () -> service.removeStaff(firstCruise, employee, first, null, hr)).getCode());
        assertEquals("ACTIVE", service.assignStaff(secondCruise, request(), hr).getStatus());
        assertEquals("ACTIVE", status(first));
        jdbc.update("""
                UPDATE "Cruise" SET status = 'COMPLETED', actual_return_datetime = now()
                WHERE cruise_id = ?
                """, firstCruise);
        assertEquals("COMPLETED", status(first));
        assertEquals(1L, jdbc.queryForObject("""
                SELECT count(*) FROM "CruiseStaff" WHERE user_id = ? AND status = 'ACTIVE'
                """, Long.class, employee));
    }

    @Test
    void removingBeforeDepartureKeepsHistoryAndNotificationIsIdempotent() {
        var first = service.assignStaff(firstCruise, request(), hr).getCruiseStaffId();
        service.removeStaff(firstCruise, employee, first, "Chuyển vị trí", hr);
        service.removeStaff(firstCruise, employee, first, "Chuyển vị trí", hr);

        assertEquals("REMOVED", status(first));
        assertEquals("Chuyển vị trí", jdbc.queryForObject("""
                SELECT removal_reason FROM "CruiseStaff"
                WHERE cruise_staff_id = ? AND ended_by_user_id = ? AND ended_at IS NOT NULL
                """, String.class, first, hr.getUserId()));
        assertEquals(1L, noticeCount());
        assertEquals("ACTIVE", service.assignStaff(secondCruise, request(), hr).getStatus());
        assertEquals("REMOVED", status(first));
    }

    @Test
    void notificationConstraintFailureRollsBackRemovalAndAudit() {
        var first = service.assignStaff(firstCruise, request(), hr).getCruiseStaffId();
        var transaction = new TransactionTemplate(transactionManager);
        assertThrows(DataIntegrityViolationException.class, () -> transaction.executeWithoutResult(status -> {
            jdbc.execute("""
                    ALTER TABLE "Notification" ADD CONSTRAINT qa_hr_as_notification_failure
                        CHECK (notification_type <> 'EMPLOYEE_REPLACEMENT_REQUIRED') NOT VALID
                    """);
            service.removeStaff(firstCruise, employee, first, "Thử rollback", hr);
        }));
        assertEquals("ACTIVE", status(first));
        assertEquals(0L, noticeCount());
        assertEquals(0L, jdbc.queryForObject("""
                SELECT count(*) FROM audit_log WHERE entity_id = ? AND action = 'CRUISE_STAFF_REMOVED'
                """, Long.class, first));
    }

    private String assignConcurrently(CountDownLatch start, UUID cruise, CasmsPrincipal actor) throws Exception {
        assertTrue(start.await(5, TimeUnit.SECONDS));
        try {
            service.assignStaff(cruise, request(), actor);
            return "SUCCESS";
        } catch (BusinessException rejected) {
            return rejected.getCode();
        }
    }

    private long noticeCount() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM "Notification" WHERE recipient_user_id = ?
                    AND notification_type = 'EMPLOYEE_REPLACEMENT_REQUIRED'
                """, Long.class, hr.getUserId());
    }

    private String status(UUID assignment) {
        return jdbc.queryForObject("""
                SELECT status FROM "CruiseStaff" WHERE cruise_staff_id = ?
                """, String.class, assignment);
    }

    private CruiseStaffRequest request() {
        var request = new CruiseStaffRequest();
        request.setUserId(employee);
        request.setRoleInCruise("ONBOARD_STAFF");
        return request;
    }

    private UUID cruise(Port port, Instant departure) {
        var cruise = new Cruise();
        cruise.setCruiseCode("CR-" + code());
        cruise.setCruiseName("HR assignment test cruise");
        cruise.setDeparturePort(port);
        cruise.setReturnPort(port);
        cruise.setDepartureDatetime(departure);
        cruise.setReturnDatetime(departure.plusSeconds(86400));
        cruise.setCruiseType(CruiseCruiseType.MULTI_DAYS);
        cruise.setStatus(CruiseStatus.DRAFT);
        em.persist(cruise);
        return cruise.getCruiseId();
    }

    private User user(Role role) {
        var user = new User();
        user.setUsername("hr-test-" + UUID.randomUUID());
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash("fixture-disabled-password");
        user.setRoles(EnumSet.of(role));
        em.persist(user);
        return user;
    }

    private String code() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
