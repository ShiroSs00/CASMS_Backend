package com.casms.ship.service;

import com.casms.activity.entity.OnboardActivity;
import com.casms.activity.entity.ShoreExcursion;
import com.casms.activity.entity.Supplier;
import com.casms.activity.enums.OnboardActivityCategory;
import com.casms.activity.enums.OnboardActivityStatus;
import com.casms.activity.enums.ShoreExcursionStatus;
import com.casms.activity.enums.SupplierStatus;
import com.casms.activity.enums.SupplierVerificationStatus;
import com.casms.activity.repository.OnboardActivityRepository;
import com.casms.activity.repository.ShoreExcursionRepository;
import com.casms.activity.repository.SupplierRepository;
import com.casms.booking.entity.Booking;
import com.casms.booking.entity.BookingPassenger;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.booking.repository.BookingRepository;
import com.casms.common.exception.BusinessException;
import com.casms.identity.entity.Passenger;
import com.casms.identity.entity.User;
import com.casms.identity.repository.PassengerRepository;
import com.casms.identity.repository.UserRepository;
import com.casms.ship.dto.request.*;
import com.casms.ship.dto.response.*;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class CruiseChangeAndRecoveryPostgresIntegrationTest {

    @Autowired private CruiseService cruiseService;
    @Autowired private CruiseImpactAssessmentService cruiseImpactAssessmentService;
    @Autowired private ScheduleMaterializationService scheduleMaterializationService;

    @Autowired private CruiseRepository cruiseRepository;
    @Autowired private ShipRepository shipRepository;
    @Autowired private DeckRepository deckRepository;
    @Autowired private CabinRepository cabinRepository;
    @Autowired private PortRepository portRepository;
    @Autowired private ItineraryRepository itineraryRepository;
    @Autowired private ItineraryDayRepository itineraryDayRepository;
    @Autowired private CruiseStopRepository cruiseStopRepository;
    @Autowired private CruiseTourPackageRepository cruiseTourPackageRepository;
    @Autowired private CruiseImpactTaskRepository cruiseImpactTaskRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingPassengerRepository bookingPassengerRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PassengerRepository passengerRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private TripScheduleVersionRepository versionRepository;
    @Autowired private TripPlanDayRepository planDayRepository;
    @Autowired private TripPlanStopRepository planStopRepository;
    @Autowired private TripPlanEntryRepository planEntryRepository;
    @Autowired private OnboardActivityRepository onboardActivityRepository;
    @Autowired private ShoreExcursionRepository shoreExcursionRepository;
    @Autowired private SupplierRepository supplierRepository;

    private Ship shipA;
    private Ship shipB;
    private Deck deckA;
    private Deck deckB;
    private Port portHph;
    private Port portDan;
    private User testUser;
    private User actorUser;

    @BeforeEach
    void setUp() {
        // Cảng Hải Phòng
        portHph = portRepository.findAll().stream()
                .filter(p -> "Asia/Ho_Chi_Minh".equals(p.getTimeZoneId()) && p.getStatus() == PortStatus.ACTIVE)
                .findFirst()
                .orElseGet(() -> {
                    Port p = new Port();
                    p.setPortCode("HPH-" + UUID.randomUUID().toString().substring(0, 4));
                    p.setPortName("Cảng Hải Phòng Test");
                    p.setCountryCode("VN");
                    p.setTimeZoneId("Asia/Ho_Chi_Minh");
                    p.setStatus(PortStatus.ACTIVE);
                    return portRepository.save(p);
                });

        // Cảng Đà Nẵng
        portDan = portRepository.findAll().stream()
                .filter(p -> !"HPH".equals(p.getPortCode()) && "Asia/Ho_Chi_Minh".equals(p.getTimeZoneId()) && p.getStatus() == PortStatus.ACTIVE)
                .findFirst()
                .orElseGet(() -> {
                    Port p = new Port();
                    p.setPortCode("DAN-" + UUID.randomUUID().toString().substring(0, 4));
                    p.setPortName("Cảng Đà Nẵng Test");
                    p.setCountryCode("VN");
                    p.setTimeZoneId("Asia/Ho_Chi_Minh");
                    p.setStatus(PortStatus.ACTIVE);
                    return portRepository.save(p);
                });

        // Tàu A
        shipA = new Ship();
        shipA.setShipCode("SHA-" + UUID.randomUUID().toString().substring(0, 4));
        shipA.setShipName("Tàu Ngôi Sao Phương Đông A");
        shipA.setStatus(ShipStatus.ACTIVE);
        shipA.setTotalCapacity(100);
        shipA.setTotalDecks(3);
        shipA = shipRepository.save(shipA);

        deckA = new Deck();
        deckA.setShip(shipA);
        deckA.setDeckNumber(1);
        deckA.setDeckName("Boong 1 - Tàu A");
        deckA = deckRepository.save(deckA);

        Cabin cabinA = new Cabin();
        cabinA.setShip(shipA);
        cabinA.setDeck(deckA);
        cabinA.setCabinNumber("A101");
        cabinA.setCabinType(CabinType.BALCONY);
        cabinA.setCapacity(2);
        cabinA.setStatus(CabinStatus.AVAILABLE);
        cabinA.setIsAccessible(false);
        cabinRepository.save(cabinA);

        // Tàu B
        shipB = new Ship();
        shipB.setShipCode("SHB-" + UUID.randomUUID().toString().substring(0, 4));
        shipB.setShipName("Tàu Đại Dương Huyền Thoại B");
        shipB.setStatus(ShipStatus.ACTIVE);
        shipB.setTotalCapacity(150);
        shipB.setTotalDecks(5);
        shipB = shipRepository.save(shipB);

        deckB = new Deck();
        deckB.setShip(shipB);
        deckB.setDeckNumber(1);
        deckB.setDeckName("Boong 1 - Tàu B");
        deckB = deckRepository.save(deckB);

        Cabin cabinB = new Cabin();
        cabinB.setShip(shipB);
        cabinB.setDeck(deckB);
        cabinB.setCabinNumber("B101");
        cabinB.setCabinType(CabinType.SUITE);
        cabinB.setCapacity(4);
        cabinB.setStatus(CabinStatus.AVAILABLE);
        cabinB.setIsAccessible(false);
        cabinRepository.save(cabinB);

        // Users
        testUser = userRepository.findByUsername("test_owner_user").orElseGet(() -> {
            User u = new User();
            u.setUsername("test_owner_user");
            u.setEmail("owner@casms.test");
            u.setPasswordHash("$2a$10$xyz");
            u.setStatus(com.casms.identity.enums.UserStatus.ACTIVE);
            return userRepository.save(u);
        });

        actorUser = userRepository.findByUsername("test_resolver_actor").orElseGet(() -> {
            User u = new User();
            u.setUsername("test_resolver_actor");
            u.setEmail("actor@casms.test");
            u.setPasswordHash("$2a$10$xyz");
            u.setStatus(com.casms.identity.enums.UserStatus.ACTIVE);
            return userRepository.save(u);
        });
    }

    private Cruise createSampleCruise(String suffix, boolean withItinerary, Instant departure) {
        Trip trip = new Trip();
        trip.setTripCode("TR-R05B-" + suffix + "-" + UUID.randomUUID().toString().substring(0, 4));
        trip.setName("Tuyến mẫu R05B " + suffix);
        trip.setStatus(TripStatus.READY);
        trip.setPlannedScope("DOMESTIC");
        trip.setCountryCodes(List.of("VN"));
        trip.setDurationDays(3);
        trip.setDurationNights(2);
        trip.setDurationGroup(TripDurationGroup.SHORT);
        trip = tripRepository.save(trip);

        TripScheduleVersion version = new TripScheduleVersion();
        version.setTrip(trip);
        version.setVersionNumber(1);
        version.setStatus(TripScheduleVersionStatus.PUBLISHED);
        version.setTimePolicyVersion("v1.0");
        version = versionRepository.save(version);

        for (int i = 1; i <= 3; i++) {
            TripPlanDay day = new TripPlanDay();
            day.setVersion(version);
            day.setDayNumber(i);
            day.setTitle("Ngày " + i);
            day.setDayType(i == 2 ? ItineraryDayType.SEA_DAY : ItineraryDayType.PORT_DAY);
            planDayRepository.save(day);
        }

        TripPlanStop stop1 = new TripPlanStop();
        stop1.setVersion(version);
        stop1.setClientKey("stop-1-" + UUID.randomUUID());
        stop1.setPort(portHph);
        stop1.setStopOrder(1);
        stop1.setArrivalDayNumber(1);
        stop1.setPlannedArrivalTime(LocalTime.of(8, 0));
        stop1.setDepartureDayNumber(1);
        stop1.setPlannedDepartureTime(LocalTime.of(18, 0));
        stop1.setCallType(CruiseStopCallType.NORMAL);
        stop1.setArrivalMethod(CruiseStopArrivalMethod.BERTH);
        stop1.setIsEmbarkation(true);
        planStopRepository.save(stop1);

        TripPlanStop stop2 = new TripPlanStop();
        stop2.setVersion(version);
        stop2.setClientKey("stop-2-" + UUID.randomUUID());
        stop2.setPort(portDan);
        stop2.setStopOrder(2);
        stop2.setArrivalDayNumber(3);
        stop2.setPlannedArrivalTime(LocalTime.of(9, 0));
        stop2.setDepartureDayNumber(3);
        stop2.setPlannedDepartureTime(LocalTime.of(17, 0));
        stop2.setCallType(CruiseStopCallType.NORMAL);
        stop2.setArrivalMethod(CruiseStopArrivalMethod.BERTH);
        stop2.setIsEmbarkation(false);
        planStopRepository.save(stop2);

        CruiseCreateRequest req = new CruiseCreateRequest();
        req.setCruiseName("Chuyến đi R05B " + suffix);
        req.setDepartureDatetime(departure);
        req.setReturnDatetime(departure.plusSeconds(86400 * 3));
        req.setDeparturePortId(portHph.getPortId());
        req.setReturnPortId(portDan.getPortId());
        req.setShipId(shipA.getShipId());
        req.setSourceVersionId(version.getVersionId());

        CruiseResponse res = cruiseService.create(req);
        return cruiseRepository.findById(res.getCruiseId()).orElseThrow();
    }

    private Booking createTestBooking(Cruise cruise, String ref) {
        Booking b = new Booking();
        b.setCruise(cruise);
        b.setBookingReference(ref + "-" + UUID.randomUUID().toString().substring(0, 8));
        b.setBookedByUser(testUser);
        b.setBookingStatus(BookingStatus.Confirmed);
        b.setAgreedFareAmount(BigDecimal.valueOf(10000000));
        b.setRequiredDepositAmount(BigDecimal.valueOf(2000000));
        b.setCurrency("VND");
        b = bookingRepository.save(b);

        Passenger p = passengerRepository.findByUser_UserId(testUser.getUserId()).orElseGet(() -> {
            Passenger newP = new Passenger();
            newP.setUser(testUser);
            newP.setFullName("Nguyễn Văn Khách " + ref);
            newP.setNationality("Vietnamese");
            return passengerRepository.save(newP);
        });

        BookingPassenger bp = new BookingPassenger();
        bp.setBooking(b);
        bp.setPassenger(p);
        bp.setParticipationStatus(com.casms.booking.enums.BookingPassengerParticipationStatus.Active);
        bookingPassengerRepository.save(bp);

        return b;
    }

    @Test
    @DisplayName("BE-R05b / Case 1: Đổi ngày khi chưa có booking dời đồng bộ ngày của ItineraryDay, CruiseStop và Programs")
    void test01_updateDatesWithoutBooking_shiftsItineraryDaysStopsAndPrograms() {
        Instant initialDep = Instant.parse("2026-11-01T01:00:00Z");
        Cruise cruise = createSampleCruise("01", true, initialDep);

        // Tạo 1 OnboardActivity và 1 ShoreExcursion mẫu
        OnboardActivity act = new OnboardActivity();
        act.setCruise(cruise);
        act.setActivityCode("ACT-01-" + UUID.randomUUID().toString().substring(0, 4));
        act.setActivityName("Tiệc chào mừng");
        act.setStartTime(initialDep.plus(Duration.ofHours(3)));
        act.setEndTime(initialDep.plus(Duration.ofHours(5)));
        act.setCapacity(50);
        act.setStatus(OnboardActivityStatus.OPEN);
        act.setDeck(deckA);
        onboardActivityRepository.save(act);

        // Dời lịch sang 2 ngày sau (+48h)
        Instant newDep = initialDep.plus(Duration.ofDays(2));
        Instant newRet = newDep.plus(Duration.ofDays(3));

        CruiseUpdateDraftRequest draftReq = new CruiseUpdateDraftRequest();
        draftReq.setDepartureDatetime(newDep);
        draftReq.setReturnDatetime(newRet);
        draftReq.setExpectedRevision(cruise.getRevision());

        CruiseResponse updated = cruiseService.updateDraft(cruise.getCruiseId(), draftReq);
        assertEquals(newDep, updated.getDepartureDatetime());

        // Kiểm tra OnboardActivity được dời đúng 48h
        OnboardActivity shiftedAct = onboardActivityRepository.findById(act.getOnboardActivityId()).orElseThrow();
        assertEquals(initialDep.plus(Duration.ofHours(3)).plus(Duration.ofDays(2)), shiftedAct.getStartTime());
        assertEquals(initialDep.plus(Duration.ofHours(5)).plus(Duration.ofDays(2)), shiftedAct.getEndTime());
    }

    @Test
    @DisplayName("BE-R05b / Case 2: Đổi ngày hoặc cảng trực tiếp qua updateDraft khi đã có booking bị từ chối")
    void test02_updateDatesOrPortsWithBooking_rejectedViaUpdateDraft() {
        Instant initialDep = Instant.parse("2026-11-10T01:00:00Z");
        Cruise cruise = createSampleCruise("02", true, initialDep);

        // Tạo đặt chỗ cho chuyến đi
        createTestBooking(cruise, "BK-R05B-02");

        // Cố tình sửa ngày qua updateDraft
        CruiseUpdateDraftRequest req = new CruiseUpdateDraftRequest();
        req.setDepartureDatetime(initialDep.plus(Duration.ofDays(5)));
        req.setReturnDatetime(initialDep.plus(Duration.ofDays(8)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> cruiseService.updateDraft(cruise.getCruiseId(), req));
        assertEquals("CANNOT_MODIFY_COMMITTED_CRUISE_DIRECTLY", ex.getCode(),
                "Must reject direct schedule update when active bookings exist");
    }

    @Test
    @DisplayName("BE-R05b & BE-R07 / Case 3: Áp dụng thay đổi lịch qua applyChange khi có booking thành công và sinh task C17")
    void test03_applyChangeWithBooking_generatesTasksAndAppliesCleanly() {
        Instant initialDep = Instant.parse("2026-11-15T01:00:00Z");
        Cruise cruise = createSampleCruise("03", true, initialDep);

        // Tạo booking
        createTestBooking(cruise, "BK-R05B-03");

        Instant newDep = initialDep.plus(Duration.ofDays(3));
        Instant newRet = newDep.plus(Duration.ofDays(3));

        cruise = cruiseRepository.findById(cruise.getCruiseId()).orElseThrow();
        CruiseChangeImpactRequest applyReq = CruiseChangeImpactRequest.builder()
                .newDepartureDatetime(newDep)
                .newReturnDatetime(newRet)
                .expectedRevision(cruise.getRevision())
                .reason("Dời lịch do bảo trì đột xuất luồng cảng")
                .eventIdentity("EVT-CHG-DATE-20261118")
                .build();

        CruiseApplyChangeResponse res = cruiseService.applyChange(cruise.getCruiseId(), applyReq, actorUser.getUserId());
        assertNotNull(res);
        assertEquals(newDep, res.getAfterDepartureDatetime());
        assertEquals(1, res.getAffectedBookingsCount());
        assertEquals(1, res.getAffectedPassengersCount());
        assertFalse(res.getGeneratedTasks().isEmpty(), "Must generate C17 notification task for affected passengers");

        CruiseImpactTaskResponse task = res.getGeneratedTasks().get(0);
        assertEquals("PASSENGER_NOTIFICATION", task.getCategory());
        assertEquals("EVT-CHG-DATE-20261118", task.getEventIdentity());
        assertEquals("PENDING", task.getStatus());
    }

    @Test
    @DisplayName("BE-R05b / Case 4: Đổi tàu kiểm tra sức chứa và unassign deck thuộc tàu cũ trên OnboardActivity")
    void test04_changeShip_capacityOverlapAndOldVenuesGuarded() {
        Instant dep = Instant.parse("2026-11-20T01:00:00Z");
        Cruise cruise = createSampleCruise("04", true, dep);

        // Tạo OnboardActivity gắn deckA (thuộc shipA)
        OnboardActivity act = new OnboardActivity();
        act.setCruise(cruise);
        act.setActivityCode("ACT-VENUE-" + UUID.randomUUID().toString().substring(0, 4));
        act.setActivityName("Sự kiện boong tàu A");
        act.setStartTime(dep.plus(Duration.ofHours(2)));
        act.setEndTime(dep.plus(Duration.ofHours(4)));
        act.setCapacity(50);
        act.setStatus(OnboardActivityStatus.OPEN);
        act.setDeck(deckA);
        act = onboardActivityRepository.save(act);

        // Đổi sang shipB
        CruiseChangeImpactRequest req = CruiseChangeImpactRequest.builder()
                .newShipId(shipB.getShipId())
                .expectedRevision(cruise.getRevision())
                .reason("Nâng cấp sang tàu B có sức chứa lớn hơn")
                .eventIdentity("EVT-CHG-SHIP-B")
                .build();

        CruiseApplyChangeResponse res = cruiseService.applyChange(cruise.getCruiseId(), req, actorUser.getUserId());
        assertEquals(shipB.getShipId(), res.getAfterShipId());

        // Kiểm tra OnboardActivity đã được gỡ deck cũ của tàu A để bảo toàn tính nhất quán
        OnboardActivity refreshedAct = onboardActivityRepository.findById(act.getOnboardActivityId()).orElseThrow();
        assertNull(refreshedAct.getDeck(), "Old deck belonging to previous ship must be safely unassigned");
    }

    @Test
    @DisplayName("BE-R05b & BE-R08 / Case 5: Apply change với revision cũ bị từ chối bằng STALE_REVISION")
    void test05_applyChangeStaleRevision_rejected() {
        Instant dep = Instant.parse("2026-11-25T01:00:00Z");
        Cruise cruise = createSampleCruise("05", true, dep);

        CruiseChangeImpactRequest req = CruiseChangeImpactRequest.builder()
                .newDepartureDatetime(dep.plus(Duration.ofDays(1)))
                .expectedRevision(cruise.getRevision() + 999L) // Stale revision
                .reason("Cố tình gửi revision không khớp")
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> cruiseService.applyChange(cruise.getCruiseId(), req, actorUser.getUserId()));
        assertEquals("STALE_REVISION", ex.getCode());
    }

    @Test
    @DisplayName("BE-R06 / Case 6: Dừng mở bán (stopSale) khi không có booking lưu audit fields và không sinh task thừa")
    void test06_stopSale_withoutBooking_updatesAuditFields_noExtraTask() {
        Instant dep = Instant.parse("2026-12-01T01:00:00Z");
        Cruise cruise = createSampleCruise("06", true, dep);

        CruiseStopSaleRequest req = CruiseStopSaleRequest.builder()
                .reason("Tạm dừng kiểm tra kỹ thuật định kỳ")
                .evidence("Biên bản kỹ thuật số 102/BB-KT")
                .scope("CRUISE")
                .eventIdentity("EVT-STOP-06-TECH")
                .expectedRevision(cruise.getRevision())
                .build();

        CruiseResponse res = cruiseService.stopSale(cruise.getCruiseId(), req, actorUser.getUserId());
        assertTrue(res.isSaleStopped(), "Cruise sale must be marked as stopped");
        assertEquals("Tạm dừng kiểm tra kỹ thuật định kỳ", res.getStopReason());
        assertEquals("Biên bản kỹ thuật số 102/BB-KT", res.getStopEvidence());
        assertEquals(actorUser.getUsername(), res.getStoppedByUsername());
        assertNotNull(res.getStoppedAt());

        // Không có booking nên không sinh task
        List<CruiseImpactTaskResponse> tasks = cruiseImpactAssessmentService.listTasks(cruise.getCruiseId(), null);
        assertTrue(tasks.isEmpty(), "Manual stop without bookings should not produce unnecessary impact tasks");
    }

    @Test
    @DisplayName("BE-R06 & BE-R07 / Case 7: Dừng mở bán khi có booking sinh C17 task và retry cùng event không tạo lặp")
    void test07_stopSale_withBooking_createsC17Task_retryDoesNotDuplicate() {
        Instant dep = Instant.parse("2026-12-05T01:00:00Z");
        Cruise cruise = createSampleCruise("07", true, dep);

        // Tạo booking
        createTestBooking(cruise, "BK-R06-07");

        CruiseStopSaleRequest req = CruiseStopSaleRequest.builder()
                .reason("Bão biển đột xuất cấp 9")
                .evidence("Công điện khẩn số 15/CĐ-KTTV")
                .scope("CRUISE")
                .eventIdentity("EVT-STOP-TYPHOON-01")
                .expectedRevision(cruise.getRevision())
                .build();

        // Lần 1: stopSale
        cruiseService.stopSale(cruise.getCruiseId(), req, actorUser.getUserId());
        List<CruiseImpactTaskResponse> tasks1 = cruiseImpactAssessmentService.listTasks(cruise.getCruiseId(), null);
        assertEquals(1, tasks1.size());
        assertEquals("EVT-STOP-TYPHOON-01", tasks1.get(0).getEventIdentity());

        // Lần 2: Retry cùng event
        cruise = cruiseRepository.findById(cruise.getCruiseId()).orElseThrow();
        req.setExpectedRevision(cruise.getRevision());
        cruiseService.stopSale(cruise.getCruiseId(), req, actorUser.getUserId());

        List<CruiseImpactTaskResponse> tasks2 = cruiseImpactAssessmentService.listTasks(cruise.getCruiseId(), null);
        assertEquals(1, tasks2.size(), "Retry with the same eventIdentity must not create duplicate tasks");
    }

    @Test
    @DisplayName("BE-R06 / Case 8: Resume cả chuyến đi kiểm tra sale readiness; từ chối khi chưa đủ điều kiện")
    void test08_resumeCruise_clearsAuditOnSuccess() {
        Instant dep = Instant.parse("2026-12-10T01:00:00Z");
        Cruise cruise = createSampleCruise("08", true, dep);

        // Đặt Itinerary sang OPEN_FOR_SALE trước
        itineraryRepository.findByCruiseCruiseId(cruise.getCruiseId()).ifPresent(it -> {
            it.setStatus(ItineraryStatus.OPEN_FOR_SALE);
            itineraryRepository.save(it);
        });

        // Dừng bán
        CruiseStopSaleRequest stopReq = CruiseStopSaleRequest.builder()
                .reason("Tạm dừng phục vụ bảo trì")
                .scope("CRUISE")
                .expectedRevision(cruise.getRevision())
                .build();
        cruiseService.stopSale(cruise.getCruiseId(), stopReq, actorUser.getUserId());

        // BE-R06: Hồi phục không auto-resume. Resume cả Cruise/từng package đều kiểm điều kiện chung/riêng, quyền và dữ liệu mới nhất
        cruise = cruiseRepository.findById(cruise.getCruiseId()).orElseThrow();
        final UUID targetCruiseId = cruise.getCruiseId();
        CruiseResumeSaleRequest resumeReq = CruiseResumeSaleRequest.builder()
                .expectedRevision(cruise.getRevision())
                .reason("Bảo trì hoàn tất, yêu cầu mở bán lại")
                .build();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> cruiseService.resumeSale(targetCruiseId, resumeReq));
        assertEquals("CRUISE_NOT_SALE_READY", ex.getCode(), "Must reject resume when sale readiness conditions are incomplete");
        assertTrue(ex.getMessage().contains("chưa đủ điều kiện"));
    }

    @Test
    @DisplayName("BE-R07 / Case 9: Resolve task kiểm tra Cruise trong URL và từ chối ghi chú giải quyết không đủ bằng chứng")
    void test09_resolveTask_urlMismatchRejected_insufficientEvidenceRejected() {
        Instant dep = Instant.parse("2026-12-15T01:00:00Z");
        Cruise cruise1 = createSampleCruise("09A", true, dep);
        Cruise cruise2 = createSampleCruise("09B", true, dep.plus(Duration.ofDays(10)));

        CruiseImpactTask task = new CruiseImpactTask();
        task.setCruise(cruise1);
        task.setTaskCode("TASK-CR1-01-" + UUID.randomUUID().toString().substring(0, 6));
        task.setEventIdentity("EVT-09");
        task.setCategory("CABIN_REASSIGNMENT");
        task.setTitle("Xếp lại phòng");
        task.setStatus("PENDING");
        task.setSeverity("BLOCKING");
        task = cruiseImpactTaskRepository.save(task);

        UUID taskId = task.getTaskId();

        // 1. URL mismatch: truyền cruiseId khác cruise của task
        BusinessException exUrl = assertThrows(BusinessException.class, () ->
                cruiseImpactAssessmentService.resolveTask(cruise2.getCruiseId(), taskId, "Đã xếp lại phòng đầy đủ", 1L, actorUser.getUserId()));
        assertEquals("TASK_CRUISE_MISMATCH", exUrl.getCode(), "Must reject task resolution when cruiseId in URL mismatches task cruise");

        // 2. Thiếu bằng chứng: dùng ghi chú mặc định giả tạo
        BusinessException exEv = assertThrows(BusinessException.class, () ->
                cruiseImpactAssessmentService.resolveTask(cruise1.getCruiseId(), taskId, "Resolved by operations staff", 1L, actorUser.getUserId()));
        assertEquals("INSUFFICIENT_RESOLUTION_EVIDENCE", exEv.getCode(), "Must reject placeholder resolution notes");
    }

    @Test
    @DisplayName("BE-R07 / Case 10: Resolve task tách biệt owner (assignedTo) và actor (resolvedBy), không cho resolve lại task đã xong")
    void test10_resolveTask_ownerAndActorSeparated_resolvedNotOverwritten() {
        Instant dep = Instant.parse("2026-12-20T01:00:00Z");
        Cruise cruise = createSampleCruise("10", true, dep);

        CruiseImpactTask task = new CruiseImpactTask();
        task.setCruise(cruise);
        task.setTaskCode("TASK-CR10-01-" + UUID.randomUUID().toString().substring(0, 6));
        task.setEventIdentity("EVT-10");
        task.setCategory("PASSENGER_NOTIFICATION");
        task.setTitle("Thông báo dời ngày");
        task.setStatus("PENDING");
        task.setSeverity("WARNING");
        task.setAssignedTo(testUser); // Owner là testUser
        CruiseImpactTask savedTask = cruiseImpactTaskRepository.save(task);
        final UUID targetTaskId = savedTask.getTaskId();

        // Resolve bởi actorUser với bằng chứng thực tế
        CruiseImpactTaskResponse resolved = cruiseImpactAssessmentService.resolveTask(
                cruise.getCruiseId(),
                targetTaskId,
                "Đã gọi điện và gửi email xác nhận dời lịch tới toàn bộ hành khách",
                1L,
                actorUser.getUserId()
        );

        assertEquals("RESOLVED", resolved.getStatus());
        assertEquals(testUser.getUserId(), resolved.getAssignedToUserId(), "Owner (assignedTo) must NOT be replaced by resolver actor");
        assertEquals(actorUser.getUserId(), resolved.getResolvedByUserId(), "Resolver actor must be recorded in resolvedBy");
        assertNotNull(resolved.getResolvedAt());

        // Cố tình resolve lại task đã xong
        BusinessException ex = assertThrows(BusinessException.class, () ->
                cruiseImpactAssessmentService.resolveTask(
                        cruise.getCruiseId(),
                        targetTaskId,
                        "Cố tình hoàn tất lần 2",
                        resolved.getRevision(),
                        actorUser.getUserId()
                ));
        assertEquals("TASK_ALREADY_RESOLVED", ex.getCode(), "Cannot resolve an already resolved task");
    }

    @Test
    @DisplayName("BE-R07 / Case 11: Hai sự kiện khác nhau tạo task độc lập, không ghi đè task cũ/RESOLVED")
    void test11_twoDifferentEvents_doNotOverwriteEachOther() {
        Instant dep = Instant.parse("2026-12-25T01:00:00Z");
        Cruise cruise = createSampleCruise("11", true, dep);

        // Sự kiện 1
        CruiseChangeImpactRequest req1 = CruiseChangeImpactRequest.builder()
                .newShipId(shipB.getShipId())
                .reason("Đổi tàu lần 1")
                .eventIdentity("EVT-SHIP-CHANGE-V1")
                .build();

        // Tạo booking để có task
        createTestBooking(cruise, "BK-R07-11");

        List<CruiseImpactTaskResponse> tasksEvent1 = cruiseImpactAssessmentService.generateAndPersistImpactTasks(cruise.getCruiseId(), req1);
        assertEquals(1, tasksEvent1.size());
        UUID taskId1 = tasksEvent1.get(0).getTaskId();

        // Resolve task của sự kiện 1
        cruiseImpactAssessmentService.resolveTask(
                cruise.getCruiseId(),
                taskId1,
                "Đã phân bổ lại phòng đợt 1 hoàn tất",
                1L,
                actorUser.getUserId()
        );

        // Sự kiện 2 diễn ra sau đó (eventIdentity khác, ví dụ dời ngày)
        CruiseChangeImpactRequest req2 = CruiseChangeImpactRequest.builder()
                .newDepartureDatetime(dep.plus(Duration.ofDays(2)))
                .reason("Dời ngày khởi hành do luồng cảng")
                .eventIdentity("EVT-DATE-CHANGE-V2")
                .build();

        List<CruiseImpactTaskResponse> tasksEvent2 = cruiseImpactAssessmentService.generateAndPersistImpactTasks(cruise.getCruiseId(), req2);
        assertEquals(1, tasksEvent2.size());
        UUID taskId2 = tasksEvent2.get(0).getTaskId();

        assertNotEquals(taskId1, taskId2, "Two different events must generate distinct task instances");

        // Task 1 vẫn giữ trạng thái RESOLVED, không bị biến thành PENDING
        CruiseImpactTask task1 = cruiseImpactTaskRepository.findById(taskId1).orElseThrow();
        assertEquals("RESOLVED", task1.getStatus(), "Previous event task must remain RESOLVED");

        // Task 2 là PENDING
        CruiseImpactTask task2 = cruiseImpactTaskRepository.findById(taskId2).orElseThrow();
        assertEquals("PENDING", task2.getStatus());
    }
}
