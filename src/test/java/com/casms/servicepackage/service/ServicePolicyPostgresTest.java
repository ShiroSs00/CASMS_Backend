package com.casms.servicepackage.service;

import static org.junit.jupiter.api.Assertions.*;
import com.casms.activity.entity.OnboardActivity;
import com.casms.activity.repository.OnboardActivityRepository;
import com.casms.activity.service.ActivityService;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.common.exception.BusinessException;
import com.casms.servicepackage.entity.Service;
import com.casms.servicepackage.entity.ServiceRequest;
import com.casms.servicepackage.entity.ServiceUsage;
import com.casms.servicepackage.enums.ServiceRequestStatus;
import com.casms.servicepackage.enums.ServiceStatus;
import com.casms.servicepackage.enums.ServiceUsagePolicy;
import com.casms.servicepackage.repository.*;
import com.casms.ship.enums.CruiseStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;

/** Opt-in tests on an isolated local clone; every scenario rolls back. */
@SpringBootTest
@Transactional
@EnabledIfSystemProperty(named="service.policy.integration", matches="true")
class ServicePolicyPostgresTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry p) {
        String url = System.getProperty("service.policy.jdbcUrl", "");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:5432/casms_service_policy_[0-9]+"))
            throw new IllegalStateException("An isolated casms_service_policy_* database is required");
        p.add("spring.datasource.url", () -> url);
        p.add("spring.datasource.username", () -> "postgres");
        p.add("spring.datasource.password", () -> System.getenv("DB_PASSWORD"));
    }
    @Autowired ServiceUsageQuotaService quotas;
    @Autowired ServiceRequestService requests;
    @Autowired ServiceRepository services;
    @Autowired ServiceRequestRepository requestRepository;
    @Autowired ServiceUsageRepository usages;
    @Autowired BookingPassengerRepository passengers;
    @Autowired ActivityService activities;
    @Autowired OnboardActivityRepository activityRepository;
    @Autowired EntityManager em;

    ServiceRequest request(ServiceUsagePolicy policy, int limit) {
        var user = new com.casms.identity.entity.User(); user.setUsername("policy-" + UUID.randomUUID());
        user.setPasswordHash("not-used"); user.setStatus(com.casms.identity.enums.UserStatus.ACTIVE); em.persist(user);
        var passenger = new com.casms.identity.entity.Passenger(); passenger.setUser(user); passenger.setFullName("Policy fixture"); em.persist(passenger);
        var port = new com.casms.ship.entity.Port(); port.setPortCode("P" + UUID.randomUUID().toString().substring(0,8));
        port.setPortName("Policy port"); port.setCountryCode("VN"); port.setTimeZoneId("Asia/Ho_Chi_Minh");
        port.setStatus(com.casms.ship.enums.PortStatus.ACTIVE); em.persist(port);
        var cruise = new com.casms.ship.entity.Cruise(); cruise.setCruiseCode("P" + UUID.randomUUID().toString().substring(0,12));
        cruise.setCruiseName("Policy cruise"); cruise.setDeparturePort(port); cruise.setReturnPort(port);
        cruise.setCruiseType(com.casms.ship.enums.CruiseCruiseType.ONE_DAY); cruise.setStatus(CruiseStatus.IN_PROGRESS);
        cruise.setDepartureDatetime(Instant.now().minusSeconds(3600)); cruise.setReturnDatetime(Instant.now().plusSeconds(3600)); em.persist(cruise);
        var booking = new com.casms.booking.entity.Booking(); booking.setCruise(cruise); booking.setBookedByUser(user);
        booking.setBookingReference("policy-" + UUID.randomUUID()); booking.setBookingStatus(BookingStatus.Confirmed);
        booking.setAgreedFareAmount(BigDecimal.ZERO); booking.setRequiredDepositAmount(BigDecimal.ZERO); booking.setCurrency("USD"); em.persist(booking);
        var bp = new com.casms.booking.entity.BookingPassenger(); bp.setBooking(booking); bp.setPassenger(passenger);
        bp.setParticipationStatus(com.casms.booking.enums.BookingPassengerParticipationStatus.Active); em.persist(bp);
        var s = new Service(); s.setServiceCode("POLICY-" + UUID.randomUUID()); s.setServiceName("Policy test");
        s.setServiceType("TEST"); s.setStatus(ServiceStatus.ACTIVE); s.setCurrencyCode("USD");
        s.setPrice(BigDecimal.ZERO); s.setPreCruiseUsagePolicy(policy); s.setPreCruiseUsageLimit(limit);
        services.save(s);
        var r = new ServiceRequest(); r.setService(s); r.setPassenger(bp.getPassenger());
        r.setBookingPassenger(bp); r.setBooking(bp.getBooking()); r.setQuantity(1);
        r.setUsageMode("PRE_CRUISE"); r.setUsagePolicySnapshot(policy); r.setUsageLimitSnapshot(limit);
        r.setUsageTimeZoneSnapshot("Asia/Ho_Chi_Minh"); r.setRequestTime(Instant.now());
        r.setStatus(ServiceRequestStatus.IN_PROGRESS); r.setAssignedUser(bp.getPassenger().getUser());
        return requestRepository.saveAndFlush(r);
    }

    @Test void completedUseConsumesQuotaOnceAndBlocksNextReservation() {
        var r = request(ServiceUsagePolicy.PER_CRUISE, 1);
        var cruise = r.getBooking().getCruise();
        cruise.setStatus(CruiseStatus.IN_PROGRESS); cruise.setDepartureDatetime(Instant.now().minusSeconds(3600));
        cruise.setReturnDatetime(Instant.now().plusSeconds(3600)); r.getBooking().setBookingStatus(BookingStatus.Confirmed);
        em.flush();
        var userId = r.getAssignedUser().getUserId();
        requests.transition(r.getServiceRequestId(), userId, "complete"); em.flush();
        requests.transition(r.getServiceRequestId(), userId, "complete"); em.flush();
        assertEquals(1, r.getUsageRecords().size());
        assertEquals(1, r.getUsageRecords().getFirst().getQuantity());
        var next = new ServiceRequest(); next.setService(r.getService()); next.setBookingPassenger(r.getBookingPassenger());
        next.setUsageMode("PRE_CRUISE"); next.setUsagePolicySnapshot(ServiceUsagePolicy.PER_CRUISE);
        next.setUsageLimitSnapshot(1); next.setQuantity(1);
        assertEquals("SERVICE_USAGE_LIMIT_EXCEEDED", assertThrows(BusinessException.class,
                () -> quotas.requireAvailable(next, Instant.now())).getCode());
    }

    @Test void dailyQuotaResetsAtLocalMidnightAndCancelledReservationsReleaseQuota() {
        var r = request(ServiceUsagePolicy.PER_DAY, 1);
        r.setStatus(ServiceRequestStatus.CANCELLED); em.flush();
        Instant previousDay = Instant.parse("2030-01-01T16:59:59Z");
        var usage = new ServiceUsage(); usage.setServiceRequest(r); usage.setQuantity(1); usage.setUsedAt(previousDay);
        usages.saveAndFlush(usage);
        assertThrows(BusinessException.class, () -> quotas.requireAvailable(r, previousDay));
        assertDoesNotThrow(() -> quotas.requireAvailable(r, Instant.parse("2030-01-01T17:00:00Z")));
        var pending = new ServiceRequest(); pending.setService(r.getService()); pending.setBookingPassenger(r.getBookingPassenger());
        pending.setBooking(r.getBooking()); pending.setPassenger(r.getPassenger()); pending.setQuantity(1);
        pending.setUsageMode("PRE_CRUISE"); pending.setStatus(ServiceRequestStatus.REQUESTED);
        pending.setRequestTime(Instant.parse("2030-01-01T17:00:00Z")); requestRepository.saveAndFlush(pending);
        assertThrows(BusinessException.class, () -> quotas.requireAvailable(r, Instant.parse("2030-01-01T17:00:00Z")));
        pending.setStatus(ServiceRequestStatus.CANCELLED); em.flush();
        assertDoesNotThrow(() -> quotas.requireAvailable(r, Instant.parse("2030-01-01T17:00:00Z")));
    }

    @Test void activityCatalogPersistsWithoutScheduleAndCannotBeOpened() {
        var input = new OnboardActivity(); input.setActivityName("Independent catalog"); input.setCapacity(10);
        var catalog = activities.saveCatalog(null, input); em.flush(); em.clear();
        var loaded = activityRepository.findById(catalog.getOnboardActivityId()).orElseThrow();
        assertTrue(loaded.isCatalogEntry()); assertNull(loaded.getCruise());
        assertNull(loaded.getStartTime()); assertNull(loaded.getEndTime());
        assertThrows(IllegalArgumentException.class, () -> activities.lifecycle(loaded.getOnboardActivityId(), "open"));
        activities.deleteActivity(loaded.getOnboardActivityId());
        em.flush();
        assertFalse(activityRepository.existsById(loaded.getOnboardActivityId()));
    }

    @Test void legacyIncludedPackageIsHonoredAndItsTotalQuotaIsEnforced() {
        var r = request(ServiceUsagePolicy.PER_CRUISE, 5);
        r.getService().setPrice(BigDecimal.TEN); r.setUnitPriceSnapshot(BigDecimal.TEN);
        assertEquals("PREPAID_ENTITLEMENT_REQUIRED", assertThrows(BusinessException.class,
                () -> quotas.requireLegacyPrepaid(r)).getCode());
        var p = new com.casms.servicepackage.entity.TourPackage(); p.setPackageCode("P" + UUID.randomUUID());
        p.setPackageName("Legacy fixture"); p.setPackageType(com.casms.servicepackage.enums.TourPackageType.BASIC);
        p.setStatus(com.casms.servicepackage.enums.TourPackageStatus.ACTIVE); p.setPrice(BigDecimal.TEN); p.setCurrencyCode("USD"); em.persist(p);
        var purchase = new com.casms.servicepackage.entity.BookingPassengerPackage();
        purchase.setBookingPassenger(r.getBookingPassenger()); purchase.setTourPackage(p);
        purchase.setAssignedAt(Instant.now()); purchase.setAgreedPrice(BigDecimal.TEN); purchase.setCurrency("USD"); em.persist(purchase);
        var link = new com.casms.servicepackage.entity.TourPackageService(); link.setTourPackage(p); link.setService(r.getService());
        link.setBenefitType(com.casms.servicepackage.enums.BenefitType.INCLUDED); link.setQuantityLimit(1); em.persist(link); em.flush();
        assertDoesNotThrow(() -> quotas.requireLegacyPrepaid(r));
        r.setQuantity(2);
        assertEquals("ENTITLEMENT_EXHAUSTED", assertThrows(BusinessException.class,
                () -> quotas.requireLegacyPrepaid(r)).getCode());
    }
}
