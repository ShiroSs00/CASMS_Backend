package com.casms.ship.service;

import com.casms.activity.entity.OnboardActivity;
import com.casms.activity.entity.ShoreExcursion;
import com.casms.activity.enums.OnboardActivityCategory;
import com.casms.activity.repository.OnboardActivityRepository;
import com.casms.activity.repository.ShoreExcursionRepository;
import com.casms.common.exception.BusinessException;
import com.casms.identity.entity.User;
import com.casms.identity.enums.Role;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.repository.UserRepository;
import com.casms.ship.dto.request.CruiseChangeImpactRequest;
import com.casms.ship.dto.request.CruiseCreateRequest;
import com.casms.ship.dto.request.CruiseStaffRequest;
import com.casms.ship.dto.response.CruiseChangeImpactResponse;
import com.casms.ship.dto.response.CruiseImpactTaskResponse;
import com.casms.ship.dto.response.CruiseReadinessResponse;
import com.casms.ship.dto.response.CruiseResponse;
import com.casms.ship.dto.response.OperationalProgramSyncResponse;
import com.casms.ship.dto.response.ShipChangeImpactResponse;
import com.casms.ship.dto.response.ShipCompatibilityResponse;
import com.casms.ship.entity.*;
import com.casms.ship.enums.*;
import com.casms.ship.repository.*;
import com.casms.trip.entity.*;
import com.casms.trip.enums.TripDurationGroup;
import com.casms.trip.enums.TripPlanEntryKind;
import com.casms.trip.enums.TripScheduleVersionStatus;
import com.casms.trip.enums.TripStatus;
import com.casms.trip.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class CruiseOperationalReadinessPostgresIntegrationTest {

    @Autowired private CruiseService cruiseService;
    @Autowired private OperationalProgramMappingService operationalProgramMappingService;
    @Autowired private CruiseImpactAssessmentService cruiseImpactAssessmentService;
    @Autowired private CruiseStaffService cruiseStaffService;

    @Autowired private CruiseRepository cruiseRepository;
    @Autowired private ShipRepository shipRepository;
    @Autowired private DeckRepository deckRepository;
    @Autowired private CabinRepository cabinRepository;
    @Autowired private PortRepository portRepository;
    @Autowired private ItineraryRepository itineraryRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private TripScheduleVersionRepository versionRepository;
    @Autowired private TripPlanDayRepository planDayRepository;
    @Autowired private TripPlanStopRepository planStopRepository;
    @Autowired private TripPlanEntryRepository planEntryRepository;
    @Autowired private OnboardActivityRepository onboardActivityRepository;
    @Autowired private ShoreExcursionRepository shoreExcursionRepository;
    @Autowired private CruiseStaffRepository cruiseStaffRepository;
    @Autowired private CruiseImpactTaskRepository cruiseImpactTaskRepository;
    @Autowired private UserRepository userRepository;

    private Ship testShip;
    private Ship secondShip;
    private Port portHph;
    private Port portDan;
    private User testStaffUser;

    @BeforeEach
    void setUp() {
        // Setup ports
        portHph = new Port();
        portHph.setPortCode("VN-HP" + UUID.randomUUID().toString().substring(0, 4));
        portHph.setPortName("Hai Phong Port Test");
        portHph.setCountryCode("VN");
        portHph.setTimeZoneId("Asia/Ho_Chi_Minh");
        portHph.setStatus(PortStatus.ACTIVE);
        portHph = portRepository.save(portHph);

        portDan = new Port();
        portDan.setPortCode("VN-DA" + UUID.randomUUID().toString().substring(0, 4));
        portDan.setPortName("Da Nang Port Test");
        portDan.setCountryCode("VN");
        portDan.setTimeZoneId("Asia/Ho_Chi_Minh");
        portDan.setStatus(PortStatus.ACTIVE);
        portDan = portRepository.save(portDan);

        // Setup primary ship with decks and cabins
        String shipCode = "SH-READY-" + UUID.randomUUID().toString().substring(0, 8);
        testShip = new Ship();
        testShip.setShipCode(shipCode);
        testShip.setShipName("Ready Ship Alpha");
        testShip.setStatus(ShipStatus.ACTIVE);
        testShip.setTotalCapacity(500);
        testShip.setTotalDecks(5);
        testShip = shipRepository.save(testShip);

        Deck deck1 = new Deck();
        deck1.setShip(testShip);
        deck1.setDeckNumber(1);
        deck1.setDeckName("Lower Deck");
        deck1 = deckRepository.save(deck1);

        Cabin cabin1 = new Cabin();
        cabin1.setShip(testShip);
        cabin1.setDeck(deck1);
        cabin1.setCabinNumber("101");
        cabin1.setCapacity(2);
        cabin1.setCabinType(CabinType.INTERIOR);
        cabin1.setStatus(CabinStatus.AVAILABLE);
        cabin1.setIsAccessible(false);
        cabinRepository.save(cabin1);

        // Setup second ship
        String shipCode2 = "SH-READY-B-" + UUID.randomUUID().toString().substring(0, 8);
        secondShip = new Ship();
        secondShip.setShipCode(shipCode2);
        secondShip.setShipName("Ready Ship Beta");
        secondShip.setStatus(ShipStatus.ACTIVE);
        secondShip.setTotalCapacity(300);
        secondShip.setTotalDecks(3);
        secondShip = shipRepository.save(secondShip);

        Deck deckB = new Deck();
        deckB.setShip(secondShip);
        deckB.setDeckNumber(1);
        deckB.setDeckName("Deck 1");
        deckB = deckRepository.save(deckB);

        Cabin cabinB = new Cabin();
        cabinB.setShip(secondShip);
        cabinB.setDeck(deckB);
        cabinB.setCabinNumber("B101");
        cabinB.setCapacity(2);
        cabinB.setCabinType(CabinType.INTERIOR);
        cabinB.setStatus(CabinStatus.AVAILABLE);
        cabinB.setIsAccessible(false);
        cabinRepository.save(cabinB);

        // Setup test user with staff role
        String username = "staff_" + UUID.randomUUID().toString().substring(0, 8);
        testStaffUser = new User();
        testStaffUser.setUsername(username);
        testStaffUser.setPasswordHash("hash123");
        testStaffUser.setStatus(UserStatus.ACTIVE);
        testStaffUser.setRoles(Set.of(Role.OPERATIONS_MANAGER));
        testStaffUser = userRepository.save(testStaffUser);
    }

    private CruiseCreateRequest buildCreateRequest(String name, Instant dep, Instant ret, UUID shipId) {
        CruiseCreateRequest req = new CruiseCreateRequest();
        req.setCruiseName(name);
        req.setDepartureDatetime(dep);
        req.setReturnDatetime(ret);
        req.setDeparturePortId(portHph.getPortId());
        req.setReturnPortId(portDan.getPortId());
        req.setShipId(shipId);
        return req;
    }

    @Test
    @DisplayName("BE-04 / E05: Kiểm tra tính tương thích của tàu (Ship Compatibility)")
    void test_E05_ship_compatibility() {
        Instant dep = Instant.now().plus(30, ChronoUnit.DAYS);
        Instant ret = dep.plus(3, ChronoUnit.DAYS);

        CruiseCreateRequest createReq = buildCreateRequest("Cruise Compatibility Test", dep, ret, null);
        CruiseResponse cruise = cruiseService.create(createReq);

        // 1. Check compatibility on active configured ship
        ShipCompatibilityResponse compat = cruiseService.checkShipCompatibility(cruise.getCruiseId(), testShip.getShipId());
        assertTrue(compat.isCompatible());
        assertTrue(compat.isShipActive());
        assertTrue(compat.isScheduleAvailable());
        assertTrue(compat.isCapacitySufficient());
        assertTrue(compat.isShipConfigured());
        assertTrue(compat.getDecksCount() > 0);
        assertTrue(compat.getCabinsCount() > 0);
        assertTrue(compat.getIssues().isEmpty());

        // 2. Assign ship
        CruiseResponse assigned = cruiseService.assignShip(cruise.getCruiseId(), testShip.getShipId());
        assertEquals(testShip.getShipId(), assigned.getShipId());

        // 3. Create second overlapping cruise and verify ship is no longer schedule-available
        CruiseCreateRequest overlapReq = buildCreateRequest("Overlap Cruise", dep.plus(1, ChronoUnit.DAYS), ret.plus(1, ChronoUnit.DAYS), null);
        CruiseResponse overlapCruise = cruiseService.create(overlapReq);

        ShipCompatibilityResponse overlapCompat = cruiseService.checkShipCompatibility(overlapCruise.getCruiseId(), testShip.getShipId());
        assertFalse(overlapCompat.isCompatible());
        assertFalse(overlapCompat.isScheduleAvailable());
        assertFalse(overlapCompat.getIssues().isEmpty());

        // 4. Verify assignShip fails with schedule conflict
        BusinessException ex = assertThrows(BusinessException.class, () ->
                cruiseService.assignShip(overlapCruise.getCruiseId(), testShip.getShipId()));
        assertEquals("SHIP_SCHEDULE_CONFLICT", ex.getCode());
    }

    @Test
    @DisplayName("BE-04 / Concurrency: Hai request gán cùng tàu vào hai Cruise trùng thời gian không thể cùng thành công")
    void test_E05_concurrency_ship_assignment() throws Exception {
        Instant dep = Instant.now().plus(40, ChronoUnit.DAYS);
        Instant ret = dep.plus(4, ChronoUnit.DAYS);

        CruiseCreateRequest req1 = buildCreateRequest("Concurrent Cruise 1", dep, ret, null);
        CruiseResponse cruise1 = cruiseService.create(req1);

        CruiseCreateRequest req2 = buildCreateRequest("Concurrent Cruise 2", dep.plus(1, ChronoUnit.DAYS), ret.minus(1, ChronoUnit.DAYS), null);
        CruiseResponse cruise2 = cruiseService.create(req2);

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        Future<?> f1 = executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                cruiseService.assignShip(cruise1.getCruiseId(), testShip.getShipId());
                successCount.incrementAndGet();
            } catch (BusinessException be) {
                if ("SHIP_SCHEDULE_CONFLICT".equals(be.getCode())) {
                    conflictCount.incrementAndGet();
                }
            } catch (Exception ignored) {}
        });

        Future<?> f2 = executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                cruiseService.assignShip(cruise2.getCruiseId(), testShip.getShipId());
                successCount.incrementAndGet();
            } catch (BusinessException be) {
                if ("SHIP_SCHEDULE_CONFLICT".equals(be.getCode())) {
                    conflictCount.incrementAndGet();
                }
            } catch (Exception ignored) {}
        });

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Exactly one cruise must succeed, and the other must be rejected
        assertEquals(1, successCount.get(), "Exactly one concurrent assignment must succeed");
        assertEquals(1, conflictCount.get(), "The other concurrent assignment must get SHIP_SCHEDULE_CONFLICT");
    }

    @Test
    @DisplayName("BE-05 / E06, E30: Operational Program Mapping từ TripPlanEntry sang OnboardActivity và ShoreExcursion")
    void test_E06_and_E30_operational_program_mapping() {
        // 1. Create Trip and published TripScheduleVersion with Plan Entries
        Trip trip = new Trip();
        trip.setTripCode("TR-PROG-" + UUID.randomUUID().toString().substring(0, 6));
        trip.setName("Hải Trình Văn Hóa OCV");
        trip.setStatus(TripStatus.READY);
        trip.setPlannedScope("DOMESTIC");
        trip.setDurationDays(3);
        trip.setDurationNights(2);
        trip.setDurationGroup(TripDurationGroup.SHORT);
        trip = tripRepository.save(trip);

        TripScheduleVersion version = new TripScheduleVersion();
        version.setTrip(trip);
        version.setVersionNumber(1);
        version.setRevision(1L);
        version.setStatus(TripScheduleVersionStatus.PUBLISHED);
        version.setPublishedAt(Instant.now());
        version = versionRepository.save(version);

        TripPlanDay day1 = new TripPlanDay();
        day1.setVersion(version);
        day1.setDayNumber(1);
        day1.setTitle("Ngày 1: Khởi hành");
        day1 = planDayRepository.save(day1);

        TripPlanStop stop1 = new TripPlanStop();
        stop1.setVersion(version);
        stop1.setPort(portDan);
        stop1.setStopOrder(1);
        stop1.setClientKey("stop-1-dan");
        stop1.setArrivalDayNumber(2);
        stop1.setDepartureDayNumber(2);
        stop1.setPlannedArrivalTime(LocalTime.of(8, 0));
        stop1.setPlannedDepartureTime(LocalTime.of(18, 0));
        stop1 = planStopRepository.save(stop1);

        // Entry 1: Onboard Activity
        TripPlanEntry entryAct = new TripPlanEntry();
        entryAct.setVersion(version);
        entryAct.setDay(day1);
        entryAct.setClientKey("act-cooking-class");
        entryAct.setKind(TripPlanEntryKind.ONBOARD_ACTIVITY);
        entryAct.setTitle("Lớp Học Nấu Ăn OCV");
        entryAct.setCategory(OnboardActivityCategory.ENTERTAINMENT);
        entryAct.setStartDayNumber(1);
        entryAct.setPlannedStartTime(LocalTime.of(14, 0));
        entryAct.setEndDayNumber(1);
        entryAct.setPlannedEndTime(LocalTime.of(16, 0));
        entryAct.setLocationText("Bếp Trưởng Deck 1");
        planEntryRepository.save(entryAct);

        // Entry 2: Shore Excursion
        TripPlanEntry entryExc = new TripPlanEntry();
        entryExc.setVersion(version);
        entryExc.setDay(day1);
        entryExc.setStop(stop1);
        entryExc.setClientKey("exc-danang-tour");
        entryExc.setKind(TripPlanEntryKind.SHORE_EXCURSION);
        entryExc.setTitle("Tour Khám Phá Ngũ Hành Sơn");
        entryExc.setStartDayNumber(2);
        entryExc.setPlannedStartTime(LocalTime.of(9, 0));
        entryExc.setEndDayNumber(2);
        entryExc.setPlannedEndTime(LocalTime.of(16, 0));
        entryExc.setMeetingPoint("Cổng Cảng Tiên Sa");
        planEntryRepository.save(entryExc);

        // 2. Create Cruise from version
        Instant dep = Instant.now().plus(50, ChronoUnit.DAYS);
        Instant ret = dep.plus(3, ChronoUnit.DAYS);

        CruiseCreateRequest createReq = buildCreateRequest("Cruise Operational Program Test", dep, ret, testShip.getShipId());
        createReq.setSourceVersionId(version.getVersionId());
        CruiseResponse cruise = cruiseService.create(createReq);

        // 3. Verify programs are synced
        OperationalProgramSyncResponse syncRes = operationalProgramMappingService.syncProgramsForCruise(cruise.getCruiseId());
        assertEquals(1, syncRes.getOnboardActivitiesCount());
        assertEquals(1, syncRes.getShoreExcursionsCount());

        // Verify OnboardActivity record in database
        List<OnboardActivity> activities = onboardActivityRepository.findByCruise_CruiseId(cruise.getCruiseId());
        assertEquals(1, activities.size());
        OnboardActivity act = activities.get(0);
        assertEquals("Lớp Học Nấu Ăn OCV", act.getActivityName());
        assertEquals(OnboardActivityCategory.ENTERTAINMENT, act.getCategory());
        assertTrue(act.getStartTime().isAfter(Instant.now()));
        assertTrue(act.getEndTime().isAfter(act.getStartTime()));

        // Verify ShoreExcursion record in database
        long excCount = shoreExcursionRepository.countByCruiseId(cruise.getCruiseId());
        assertTrue(excCount >= 1);

        // 4. Retry / Idempotency check: syncing again must not duplicate activities or excursions
        OperationalProgramSyncResponse retrySync = operationalProgramMappingService.syncProgramsForCruise(cruise.getCruiseId());
        assertEquals(1, retrySync.getOnboardActivitiesCount());
        assertEquals(1, retrySync.getShoreExcursionsCount());

        List<OnboardActivity> activitiesAfterRetry = onboardActivityRepository.findByCruise_CruiseId(cruise.getCruiseId());
        assertEquals(1, activitiesAfterRetry.size(), "Activities must not be duplicated on retry");
    }

    @Test
    @DisplayName("BE-06 / E21, E22, E23: Kiểm tra nhân sự và quy định (Staff & Regulation Readiness)")
    void test_E21_E22_E23_staff_and_regulation_readiness() {
        Instant dep = Instant.now().plus(60, ChronoUnit.DAYS);
        Instant ret = dep.plus(3, ChronoUnit.DAYS);

        CruiseCreateRequest createReq = buildCreateRequest("Staff Readiness Test Cruise", dep, ret, testShip.getShipId());
        CruiseResponse cruise = cruiseService.create(createReq);

        // 1. Without Captain: readiness should flag missing captain
        CruiseReadinessResponse unready = cruiseService.validateOperationalReadiness(cruise.getCruiseId());
        assertFalse(unready.isCaptainAssigned());
        assertFalse(unready.isReady());
        assertTrue(unready.getBlockers().contains("MISSING_CAPTAIN"));
        assertTrue(unready.getIssues().stream().anyMatch(i -> i.contains("CAPTAIN")));

        // 2. Assign Captain
        CruiseStaffRequest staffReq = new CruiseStaffRequest();
        staffReq.setUserId(testStaffUser.getUserId());
        staffReq.setRoleInCruise("CAPTAIN");
        staffReq.setNotes("Thuyền trưởng điều hành chính");
        cruiseStaffService.assignStaff(cruise.getCruiseId(), staffReq, testStaffUser.getUserId());

        // 3. Re-evaluate readiness: Captain is now assigned
        CruiseReadinessResponse readyWithStaff = cruiseService.validateOperationalReadiness(cruise.getCruiseId());
        assertTrue(readyWithStaff.isCaptainAssigned());
        assertTrue(readyWithStaff.isStaffAssigned());
        assertFalse(readyWithStaff.getBlockers().contains("MISSING_CAPTAIN"));
    }

    @Test
    @DisplayName("BE-10 / E08, E10, E14, E15, E28, E29: Đánh giá tác động thay đổi, Stale Revision và Recovery Tasks")
    void test_BE10_change_impact_and_recovery_tasks() {
        Instant dep = Instant.now().plus(70, ChronoUnit.DAYS);
        Instant ret = dep.plus(3, ChronoUnit.DAYS);

        CruiseCreateRequest createReq = buildCreateRequest("Change Impact Test Cruise", dep, ret, testShip.getShipId());
        CruiseResponse cruise = cruiseService.create(createReq);

        // 1. Preview Ship Change Impact
        ShipChangeImpactResponse shipImpact = cruiseService.previewShipChangeImpact(cruise.getCruiseId(), secondShip.getShipId());
        assertNotNull(shipImpact);
        assertEquals(cruise.getCruiseId(), shipImpact.getCruiseId());
        assertEquals(testShip.getShipId(), shipImpact.getCurrentShipId());
        assertEquals(secondShip.getShipId(), shipImpact.getNewShipId());
        assertTrue(shipImpact.isCanProceed());

        // 2. Preview Schedule Shift Impact
        Instant shiftedDep = dep.plus(2, ChronoUnit.DAYS);
        Instant shiftedRet = ret.plus(2, ChronoUnit.DAYS);

        CruiseChangeImpactRequest impactReq = CruiseChangeImpactRequest.builder()
                .newDepartureDatetime(shiftedDep)
                .newReturnDatetime(shiftedRet)
                .newShipId(secondShip.getShipId())
                .expectedRevision(cruise.getRevision())
                .reason("Điều chỉnh theo điều kiện thủy triều")
                .build();

        CruiseChangeImpactResponse changeImpact = cruiseImpactAssessmentService.previewChangeImpact(cruise.getCruiseId(), impactReq);
        assertTrue(changeImpact.isScheduleImpacted());
        assertEquals(2, changeImpact.getDateShiftDays());
        assertTrue(changeImpact.isShipImpacted());
        assertTrue(changeImpact.isCanProceed());

        // 3. Optimistic locking / Stale Revision check (E08)
        CruiseChangeImpactRequest staleReq = CruiseChangeImpactRequest.builder()
                .newDepartureDatetime(shiftedDep)
                .newReturnDatetime(shiftedRet)
                .expectedRevision(cruise.getRevision() + 99L) // Wrong revision
                .build();
        BusinessException staleEx = assertThrows(BusinessException.class, () ->
                cruiseImpactAssessmentService.previewChangeImpact(cruise.getCruiseId(), staleReq));
        assertEquals("STALE_REVISION", staleEx.getCode());

        // 4. Generate and persist impact tasks (C17)
        List<CruiseImpactTaskResponse> tasks = cruiseImpactAssessmentService.generateAndPersistImpactTasks(cruise.getCruiseId(), impactReq);
        assertNotNull(tasks);

        // 5. Query persisted tasks
        List<CruiseImpactTaskResponse> list = cruiseImpactAssessmentService.listTasks(cruise.getCruiseId(), null);
        assertNotNull(list);

        // 6. Test resolving a task if tasks were generated
        if (!list.isEmpty()) {
            CruiseImpactTaskResponse firstTask = list.get(0);
            CruiseImpactTaskResponse resolved = cruiseImpactAssessmentService.resolveTask(
                    firstTask.getTaskId(), "Đã cập nhật hệ thống và xác nhận đối tác", testStaffUser.getUserId());
            assertEquals("RESOLVED", resolved.getStatus());
            assertEquals("Đã cập nhật hệ thống và xác nhận đối tác", resolved.getResolutionNotes());
        }
    }
}
