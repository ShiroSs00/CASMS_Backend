package com.casms.ship.service;

import com.casms.activity.entity.OnboardActivity;
import com.casms.activity.entity.Registration;
import com.casms.activity.entity.ShoreExcursion;
import com.casms.activity.entity.Supplier;
import com.casms.activity.enums.*;
import com.casms.activity.repository.OnboardActivityRepository;
import com.casms.activity.repository.RegistrationRepository;
import com.casms.activity.repository.ShoreExcursionRepository;
import com.casms.activity.repository.SupplierRepository;
import com.casms.booking.entity.Booking;
import com.casms.booking.entity.BookingPassenger;
import com.casms.booking.enums.BookingPassengerParticipationStatus;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.booking.repository.BookingRepository;
import com.casms.identity.entity.Passenger;
import com.casms.identity.entity.User;
import com.casms.identity.enums.Role;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.repository.PassengerRepository;
import com.casms.identity.repository.UserRepository;
import com.casms.ship.dto.response.OperationalProgramMappingItemResponse;
import com.casms.ship.dto.response.OperationalProgramSyncResponse;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class OperationalProgramMappingPostgresIntegrationTest {

    @Autowired private OperationalProgramMappingService operationalProgramMappingService;

    @Autowired private CruiseRepository cruiseRepository;
    @Autowired private ShipRepository shipRepository;
    @Autowired private DeckRepository deckRepository;
    @Autowired private CabinRepository cabinRepository;
    @Autowired private PortRepository portRepository;
    @Autowired private ItineraryRepository itineraryRepository;
    @Autowired private ItineraryDayRepository itineraryDayRepository;
    @Autowired private CruiseStopRepository cruiseStopRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private TripScheduleVersionRepository versionRepository;
    @Autowired private TripPlanDayRepository planDayRepository;
    @Autowired private TripPlanStopRepository planStopRepository;
    @Autowired private TripPlanEntryRepository planEntryRepository;
    @Autowired private OnboardActivityRepository onboardActivityRepository;
    @Autowired private ShoreExcursionRepository shoreExcursionRepository;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private RegistrationRepository registrationRepository;
    @Autowired private PassengerRepository passengerRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingPassengerRepository bookingPassengerRepository;
    @Autowired private UserRepository userRepository;

    private Ship testShipA;
    private Ship testShipB;
    private Deck testDeckA;
    private Deck testDeckB;
    private Cabin testCabinA;
    private Cabin testCabinB;
    private Port portHph;
    private Port portDan;
    private User testUser;
    private Supplier testSupplier;

    @BeforeEach
    void setUp() {
        // Ports
        portHph = new Port();
        portHph.setPortCode("VN-HP-" + UUID.randomUUID().toString().substring(0, 4));
        portHph.setPortName("Hai Phong Port Test");
        portHph.setCountryCode("VN");
        portHph.setTimeZoneId("Asia/Ho_Chi_Minh");
        portHph.setStatus(PortStatus.ACTIVE);
        portHph = portRepository.save(portHph);

        portDan = new Port();
        portDan.setPortCode("VN-DA-" + UUID.randomUUID().toString().substring(0, 4));
        portDan.setPortName("Da Nang Port Test");
        portDan.setCountryCode("VN");
        portDan.setTimeZoneId("Asia/Ho_Chi_Minh");
        portDan.setStatus(PortStatus.ACTIVE);
        portDan = portRepository.save(portDan);

        // Ship A
        testShipA = new Ship();
        testShipA.setShipCode("SH-A-" + UUID.randomUUID().toString().substring(0, 6));
        testShipA.setShipName("Hạ Long Princess A");
        testShipA.setStatus(ShipStatus.ACTIVE);
        testShipA.setTotalCapacity(500);
        testShipA.setTotalDecks(5);
        testShipA = shipRepository.save(testShipA);

        testDeckA = new Deck();
        testDeckA.setShip(testShipA);
        testDeckA.setDeckNumber(3);
        testDeckA.setDeckName("Sundeck A");
        testDeckA = deckRepository.save(testDeckA);

        testCabinA = new Cabin();
        testCabinA.setShip(testShipA);
        testCabinA.setDeck(testDeckA);
        testCabinA.setCabinNumber("A301");
        testCabinA.setCabinType(CabinType.BALCONY);
        testCabinA.setCapacity(2);
        testCabinA.setStatus(CabinStatus.AVAILABLE);
        testCabinA.setIsAccessible(false);
        testCabinA = cabinRepository.save(testCabinA);

        // Ship B
        testShipB = new Ship();
        testShipB.setShipCode("SH-B-" + UUID.randomUUID().toString().substring(0, 6));
        testShipB.setShipName("Hạ Long Princess B");
        testShipB.setStatus(ShipStatus.ACTIVE);
        testShipB.setTotalCapacity(400);
        testShipB.setTotalDecks(4);
        testShipB = shipRepository.save(testShipB);

        testDeckB = new Deck();
        testDeckB.setShip(testShipB);
        testDeckB.setDeckNumber(4);
        testDeckB.setDeckName("Sundeck B");
        testDeckB = deckRepository.save(testDeckB);

        testCabinB = new Cabin();
        testCabinB.setShip(testShipB);
        testCabinB.setDeck(testDeckB);
        testCabinB.setCabinNumber("B401");
        testCabinB.setCabinType(CabinType.SUITE);
        testCabinB.setCapacity(4);
        testCabinB.setStatus(CabinStatus.AVAILABLE);
        testCabinB.setIsAccessible(false);
        testCabinB = cabinRepository.save(testCabinB);

        // User
        testUser = new User();
        testUser.setUsername("user_r04_" + UUID.randomUUID().toString().substring(0, 8));
        testUser.setEmail("r04_" + UUID.randomUUID().toString().substring(0, 8) + "@casms.vn");
        testUser.setPasswordHash("HASH123");
        testUser.setRoles(EnumSet.of(Role.ADMIN));
        testUser.setStatus(UserStatus.ACTIVE);
        testUser = userRepository.save(testUser);

        testSupplier = new Supplier();
        testSupplier.setSupplierName("Nhà Cung Cấp Tour Miền Trung");
        testSupplier.setContactName("Lê Điều Hành");
        testSupplier.setStatus(SupplierStatus.ACTIVE);
        testSupplier.setVerificationStatus(SupplierVerificationStatus.VERIFIED);
        testSupplier = supplierRepository.save(testSupplier);
    }

    private Trip createTestTrip() {
        Trip trip = new Trip();
        trip.setTripCode("TR-R04-" + UUID.randomUUID().toString().substring(0, 6));
        trip.setName("Hải trình Di sản Vịnh Bắc Bộ");
        trip.setStatus(TripStatus.READY);
        trip.setPlannedScope("DOMESTIC");
        trip.setDurationGroup(TripDurationGroup.SHORT);
        trip.setDurationDays(3);
        trip.setDurationNights(2);
        return tripRepository.save(trip);
    }

    private TripScheduleVersion createTestVersion(Trip trip) {
        TripScheduleVersion version = new TripScheduleVersion();
        version.setTrip(trip);
        version.setVersionNumber(1);
        version.setRevision(1L);
        version.setStatus(TripScheduleVersionStatus.PUBLISHED);
        version = versionRepository.save(version);

        for (int i = 1; i <= 3; i++) {
            TripPlanDay day = new TripPlanDay();
            day.setVersion(version);
            day.setDayNumber(i);
            day.setTitle("Ngày " + i);
            day.setDayType(i == 2 ? ItineraryDayType.SEA_DAY : ItineraryDayType.PORT_DAY);
            planDayRepository.save(day);
        }

        return version;
    }

    private TripPlanDay getDay(TripScheduleVersion version, int dayNumber) {
        return planDayRepository.findByVersion_VersionIdAndDayNumber(version.getVersionId(), dayNumber).orElseThrow();
    }

    private Cruise createTestCruise(String cruiseCode, Instant dep, Instant ret, Ship ship, TripScheduleVersion version) {
        Cruise cruise = new Cruise();
        cruise.setCruiseCode(cruiseCode);
        cruise.setCruiseName("Chuyến đi " + cruiseCode);
        cruise.setStatus(CruiseStatus.DRAFT);
        cruise.setCruiseType(CruiseCruiseType.MULTI_DAYS);
        cruise.setDepartureDatetime(dep);
        cruise.setReturnDatetime(ret);
        cruise.setDeparturePort(portHph);
        cruise.setReturnPort(portDan);
        cruise.setShip(ship);
        cruise.setSourceTrip(version != null ? version.getTrip() : null);
        cruise.setSourceVersion(version);
        cruise.setRevision(1L);
        return cruiseRepository.save(cruise);
    }

    private Itinerary createTestItinerary(Cruise cruise) {
        Itinerary itinerary = new Itinerary();
        itinerary.setCruise(cruise);
        itinerary.setItineraryName("Lịch trình cho " + cruise.getCruiseCode());
        itinerary.setStartDate(cruise.getDepartureDatetime().atZone(ZoneOffset.UTC).toLocalDate());
        itinerary.setEndDate(cruise.getReturnDatetime().atZone(ZoneOffset.UTC).toLocalDate());
        itinerary.setItineraryType(ItineraryType.ROUND_TRIP);
        itinerary.setStatus(ItineraryStatus.DRAFT);
        return itineraryRepository.save(itinerary);
    }

    private ItineraryDay createTestItineraryDay(Itinerary itinerary, int dayNumber, LocalDate date) {
        ItineraryDay day = new ItineraryDay();
        day.setItinerary(itinerary);
        day.setDayNumber(dayNumber);
        day.setDate(date);
        day.setDayType(ItineraryDayType.PORT_DAY);
        return itineraryDayRepository.save(day);
    }

    private ShoreExcursion createTestTemplateExcursion(String name, Supplier supplier) {
        ShoreExcursion template = new ShoreExcursion();
        template.setExcursionCode("TPL-" + UUID.randomUUID().toString().substring(0, 8));
        template.setExcursionName(name);
        template.setSupplier(supplier);
        template.setStartTime(Instant.now());
        template.setPlannedReturnTime(Instant.now().plus(4, ChronoUnit.HOURS));
        template.setMeetingPoint("Terminal Gate");
        template.setMaximumCapacity(50);
        template.setPrice(BigDecimal.valueOf(500000));
        template.setCurrencyCode("VND");
        template.setStatus(ShoreExcursionStatus.PLANNED);
        return shoreExcursionRepository.save(template);
    }

    @Test
    @DisplayName("BE-R04 - Test 1: Ghé cùng cảng hai lần (đúng occurrence, không fallback về stops.get(0))")
    void test_01_port_visited_twice_matches_exact_occurrence_without_fallback() {
        Trip trip = createTestTrip();
        TripScheduleVersion version = createTestVersion(trip);

        // Stops at same port portDan
        TripPlanStop stop1 = new TripPlanStop();
        stop1.setVersion(version);
        stop1.setClientKey("stop-dan-1-" + UUID.randomUUID());
        stop1.setPort(portDan);
        stop1.setStopOrder(1);
        stop1.setArrivalDayNumber(1);
        stop1.setPlannedArrivalTime(LocalTime.of(8, 0));
        stop1.setDepartureDayNumber(1);
        stop1.setPlannedDepartureTime(LocalTime.of(14, 0));
        stop1.setCallType(CruiseStopCallType.NORMAL);
        stop1.setArrivalMethod(CruiseStopArrivalMethod.BERTH);
        stop1.setIsEmbarkation(true);
        stop1 = planStopRepository.save(stop1);

        TripPlanStop stop2 = new TripPlanStop();
        stop2.setVersion(version);
        stop2.setClientKey("stop-dan-2-" + UUID.randomUUID());
        stop2.setPort(portDan);
        stop2.setStopOrder(2);
        stop2.setArrivalDayNumber(3);
        stop2.setPlannedArrivalTime(LocalTime.of(10, 0));
        stop2.setDepartureDayNumber(3);
        stop2.setPlannedDepartureTime(LocalTime.of(18, 0));
        stop2.setCallType(CruiseStopCallType.NORMAL);
        stop2.setArrivalMethod(CruiseStopArrivalMethod.BERTH);
        stop2.setIsEmbarkation(false);
        stop2 = planStopRepository.save(stop2);

        // Excursions for each stop with valid template
        ShoreExcursion tpl1 = createTestTemplateExcursion("Template Tour 1", testSupplier);
        ShoreExcursion tpl2 = createTestTemplateExcursion("Template Tour 2", testSupplier);

        TripPlanEntry excEntry1 = new TripPlanEntry();
        excEntry1.setVersion(version);
        excEntry1.setDay(getDay(version, 1));
        excEntry1.setClientKey("EXC-DAN-1");
        excEntry1.setKind(TripPlanEntryKind.SHORE_EXCURSION);
        excEntry1.setTitle("Tour Bà Nà Hills Sáng (Lần 1)");
        excEntry1.setStartDayNumber(1);
        excEntry1.setEndDayNumber(1);
        excEntry1.setPlannedStartTime(LocalTime.of(9, 0));
        excEntry1.setPlannedEndTime(LocalTime.of(13, 0));
        excEntry1.setStop(stop1);
        excEntry1.setSourceExcursionId(tpl1.getShoreExcursionId());
        planEntryRepository.save(excEntry1);

        TripPlanEntry excEntry2 = new TripPlanEntry();
        excEntry2.setVersion(version);
        excEntry2.setDay(getDay(version, 3));
        excEntry2.setClientKey("EXC-DAN-2");
        excEntry2.setKind(TripPlanEntryKind.SHORE_EXCURSION);
        excEntry2.setTitle("Tour Ngũ Hành Sơn Chiều (Lần 2)");
        excEntry2.setStartDayNumber(3);
        excEntry2.setEndDayNumber(3);
        excEntry2.setPlannedStartTime(LocalTime.of(11, 0));
        excEntry2.setPlannedEndTime(LocalTime.of(16, 0));
        excEntry2.setStop(stop2);
        excEntry2.setSourceExcursionId(tpl2.getShoreExcursionId());
        planEntryRepository.save(excEntry2);

        // Create Cruise and materialized CruiseStops
        Instant dep = Instant.now().plus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Instant ret = dep.plus(3, ChronoUnit.DAYS);
        Cruise cruise = createTestCruise("CR-TWICE-" + UUID.randomUUID().toString().substring(0, 4), dep, ret, testShipA, version);
        Itinerary itinerary = createTestItinerary(cruise);

        LocalDate baseDate = dep.atZone(ZoneOffset.UTC).toLocalDate();
        ItineraryDay day1 = createTestItineraryDay(itinerary, 1, baseDate);
        ItineraryDay day3 = createTestItineraryDay(itinerary, 3, baseDate.plusDays(2));

        CruiseStop cs1 = new CruiseStop();
        cs1.setItinerary(itinerary);
        cs1.setArrivalItineraryDay(day1);
        cs1.setDepartureItineraryDay(day1);
        cs1.setPort(portDan);
        cs1.setStopOrder(1);
        cs1.setCallType(CruiseStopCallType.NORMAL);
        cs1.setArrivalMethod(CruiseStopArrivalMethod.BERTH);
        cs1.setStatus(CruiseStopStatus.PLANNED);
        cs1.setPlannedArrival(baseDate.atTime(8, 0).atZone(ZoneOffset.UTC).toInstant());
        cs1.setPlannedDeparture(baseDate.atTime(14, 0).atZone(ZoneOffset.UTC).toInstant());
        cs1.setNotes("sourceStopId:" + stop1.getStopId());
        cs1 = cruiseStopRepository.save(cs1);

        CruiseStop cs2 = new CruiseStop();
        cs2.setItinerary(itinerary);
        cs2.setArrivalItineraryDay(day3);
        cs2.setDepartureItineraryDay(day3);
        cs2.setPort(portDan);
        cs2.setStopOrder(2);
        cs2.setCallType(CruiseStopCallType.NORMAL);
        cs2.setArrivalMethod(CruiseStopArrivalMethod.BERTH);
        cs2.setStatus(CruiseStopStatus.PLANNED);
        cs2.setPlannedArrival(baseDate.plusDays(2).atTime(10, 0).atZone(ZoneOffset.UTC).toInstant());
        cs2.setPlannedDeparture(baseDate.plusDays(2).atTime(18, 0).atZone(ZoneOffset.UTC).toInstant());
        cs2.setNotes("sourceStopId:" + stop2.getStopId());
        cs2 = cruiseStopRepository.save(cs2);

        // Execute mapping
        OperationalProgramSyncResponse response = operationalProgramMappingService.mapOperationalPrograms(cruise, version);

        assertEquals(2, response.getShoreExcursionsCount());
        assertEquals(2, response.getItems().size());

        OperationalProgramMappingItemResponse item1 = response.getItems().stream()
                .filter(i -> "EXC-DAN-1".equals(i.getClientKey())).findFirst().orElseThrow();
        OperationalProgramMappingItemResponse item2 = response.getItems().stream()
                .filter(i -> "EXC-DAN-2".equals(i.getClientKey())).findFirst().orElseThrow();

        assertEquals("CREATED", item1.getStatus());
        assertEquals("CREATED", item2.getStatus());

        ShoreExcursion se1 = shoreExcursionRepository.findById(item1.getTargetId()).orElseThrow();
        ShoreExcursion se2 = shoreExcursionRepository.findById(item2.getTargetId()).orElseThrow();

        // Verify exact occurrence matching
        assertEquals(cs1.getCruiseStopId(), se1.getCruiseStop().getCruiseStopId(),
                "Tour 1 phải liên kết chính xác với lần ghé cảng 1 (cs1)");
        assertEquals(cs2.getCruiseStopId(), se2.getCruiseStop().getCruiseStopId(),
                "Tour 2 phải liên kết chính xác với lần ghé cảng 2 (cs2), KHÔNG được fallback về cs1");
    }

    @Test
    @DisplayName("BE-R04 - Test 2: Nguồn tạo hai Cruise độc lập (mã bền vững, không đè lấn)")
    void test_02_two_independent_cruises_from_same_source_version() {
        Trip trip = createTestTrip();
        TripScheduleVersion version = createTestVersion(trip);

        TripPlanEntry actEntry = new TripPlanEntry();
        actEntry.setVersion(version);
        actEntry.setDay(getDay(version, 1));
        actEntry.setClientKey("ACT-COOKING");
        actEntry.setKind(TripPlanEntryKind.ONBOARD_ACTIVITY);
        actEntry.setTitle("Lớp Học Nấu Ăn Bếp Trưởng");
        actEntry.setStartDayNumber(1);
        actEntry.setEndDayNumber(1);
        actEntry.setPlannedStartTime(LocalTime.of(14, 0));
        actEntry.setPlannedEndTime(LocalTime.of(16, 0));
        planEntryRepository.save(actEntry);

        Instant dep1 = Instant.now().plus(15, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Cruise cruise1 = createTestCruise("CR-A1-" + UUID.randomUUID().toString().substring(0, 4), dep1, dep1.plus(3, ChronoUnit.DAYS), testShipA, version);
        createTestItinerary(cruise1);

        Instant dep2 = Instant.now().plus(25, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Cruise cruise2 = createTestCruise("CR-A2-" + UUID.randomUUID().toString().substring(0, 4), dep2, dep2.plus(3, ChronoUnit.DAYS), testShipA, version);
        createTestItinerary(cruise2);

        // Sync cruise 1
        OperationalProgramSyncResponse res1 = operationalProgramMappingService.mapOperationalPrograms(cruise1, version);
        assertEquals(1, res1.getOnboardActivitiesCount());
        UUID actId1 = res1.getItems().get(0).getTargetId();
        String code1 = res1.getItems().get(0).getTargetCode();

        // Sync cruise 2
        OperationalProgramSyncResponse res2 = operationalProgramMappingService.mapOperationalPrograms(cruise2, version);
        assertEquals(1, res2.getOnboardActivitiesCount());
        UUID actId2 = res2.getItems().get(0).getTargetId();
        String code2 = res2.getItems().get(0).getTargetCode();

        // Assert distinct persistent codes and IDs
        assertNotEquals(actId1, actId2, "Hai cruise phải sinh hai activity độc lập");
        assertNotEquals(code1, code2, "Mã activity phải phân biệt rõ theo cruiseCode");
        assertTrue(code1.contains(cruise1.getCruiseCode()));
        assertTrue(code2.contains(cruise2.getCruiseCode()));

        assertEquals(1, onboardActivityRepository.findByCruise_CruiseId(cruise1.getCruiseId()).size());
        assertEquals(1, onboardActivityRepository.findByCruise_CruiseId(cruise2.getCruiseId()).size());
    }

    @Test
    @DisplayName("BE-R04 - Test 3: Thiếu supplier (không tự chọn active đầu tiên, không tạo VERIFIED giả)")
    void test_03_missing_supplier_does_not_invent_verified_supplier() {
        // Đảm bảo có ít nhất 1 supplier ACTIVE và VERIFIED trong DB để kiểm tra hệ thống KHÔNG tự ý vơ bừa
        Supplier existingSupplier = new Supplier();
        existingSupplier.setSupplierName("Nhà Cung Cấp Uy Tín OCV Có Sẵn");
        existingSupplier.setContactName("Nguyễn Quản Lý");
        existingSupplier.setStatus(SupplierStatus.ACTIVE);
        existingSupplier.setVerificationStatus(SupplierVerificationStatus.VERIFIED);
        supplierRepository.save(existingSupplier);

        Trip trip = createTestTrip();
        TripScheduleVersion version = createTestVersion(trip);

        TripPlanStop stop = new TripPlanStop();
        stop.setVersion(version);
        stop.setClientKey("stop-hph-" + UUID.randomUUID());
        stop.setPort(portHph);
        stop.setStopOrder(1);
        stop.setArrivalDayNumber(1);
        stop.setDepartureDayNumber(1);
        stop.setCallType(CruiseStopCallType.NORMAL);
        stop.setArrivalMethod(CruiseStopArrivalMethod.BERTH);
        stop.setIsEmbarkation(true);
        stop = planStopRepository.save(stop);

        TripPlanEntry excEntry = new TripPlanEntry();
        excEntry.setVersion(version);
        excEntry.setDay(getDay(version, 1));
        excEntry.setClientKey("EXC-NO-SUP");
        excEntry.setKind(TripPlanEntryKind.SHORE_EXCURSION);
        excEntry.setTitle("Tour Khám Phá Quần Đảo Cát Bà");
        excEntry.setStartDayNumber(1);
        excEntry.setEndDayNumber(1);
        excEntry.setPlannedStartTime(LocalTime.of(8, 0));
        excEntry.setPlannedEndTime(LocalTime.of(12, 0));
        excEntry.setStop(stop);
        // sourceExcursionId = null (không có template và không có supplier)
        planEntryRepository.save(excEntry);

        Instant dep = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Cruise cruise = createTestCruise("CR-SUP-" + UUID.randomUUID().toString().substring(0, 4), dep, dep.plus(2, ChronoUnit.DAYS), testShipA, version);
        Itinerary itinerary = createTestItinerary(cruise);
        ItineraryDay day = createTestItineraryDay(itinerary, 1, dep.atZone(ZoneOffset.UTC).toLocalDate());

        CruiseStop cs = new CruiseStop();
        cs.setItinerary(itinerary);
        cs.setArrivalItineraryDay(day);
        cs.setDepartureItineraryDay(day);
        cs.setPort(portHph);
        cs.setStopOrder(1);
        cs.setCallType(CruiseStopCallType.NORMAL);
        cs.setArrivalMethod(CruiseStopArrivalMethod.BERTH);
        cs.setStatus(CruiseStopStatus.PLANNED);
        cs.setPlannedArrival(dep.plus(8, ChronoUnit.HOURS));
        cs.setPlannedDeparture(dep.plus(14, ChronoUnit.HOURS));
        cs.setNotes("sourceStopId:" + stop.getStopId());
        cruiseStopRepository.save(cs);

        // Run mapping
        OperationalProgramSyncResponse response = operationalProgramMappingService.mapOperationalPrograms(cruise, version);
        assertEquals(0, response.getShoreExcursionsCount(), "Không tạo bản ghi tour bờ khi thiếu supplier");

        OperationalProgramMappingItemResponse item = response.getItems().get(0);
        assertEquals("FAILED", item.getStatus());
        assertNull(item.getTargetId(), "Không được tự chọn supplier active đầu tiên hoặc tạo dummy supplier");

        // Warnings và requiredData phải cảnh báo rõ
        assertTrue(item.getWarnings().stream().anyMatch(w -> w.contains("Chưa có nhà cung cấp")));
        assertNotNull(item.getRequiredData());
        assertTrue(item.getRequiredData().contains("Cần phân công Nhà cung cấp (Supplier)"));
    }

    @Test
    @DisplayName("BE-R04 - Test 4: Venue sai tàu (deck/cabin không thuộc tàu được gán -> cảnh báo rõ)")
    void test_04_venue_wrong_ship_emits_warning_without_falsifying_readiness() {
        Trip trip = createTestTrip();
        TripScheduleVersion version = createTestVersion(trip);

        TripPlanEntry actEntry = new TripPlanEntry();
        actEntry.setVersion(version);
        actEntry.setDay(getDay(version, 1));
        actEntry.setClientKey("ACT-WRONG-VENUE");
        actEntry.setKind(TripPlanEntryKind.ONBOARD_ACTIVITY);
        actEntry.setTitle("Gala Dạ Tiệc Sundeck");
        actEntry.setStartDayNumber(1);
        actEntry.setEndDayNumber(1);
        actEntry.setPlannedStartTime(LocalTime.of(19, 0));
        actEntry.setPlannedEndTime(LocalTime.of(21, 0));
        // Gán Deck B và Cabin B thuộc Ship B!
        actEntry.setDeck(testDeckB);
        actEntry.setCabin(testCabinB);
        planEntryRepository.save(actEntry);

        // Cruise được gán cho Ship A
        Instant dep = Instant.now().plus(35, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Cruise cruise = createTestCruise("CR-VENUE-" + UUID.randomUUID().toString().substring(0, 4), dep, dep.plus(2, ChronoUnit.DAYS), testShipA, version);
        createTestItinerary(cruise);

        // Run mapping
        OperationalProgramSyncResponse response = operationalProgramMappingService.mapOperationalPrograms(cruise, version);
        assertEquals(1, response.getOnboardActivitiesCount());

        OperationalProgramMappingItemResponse item = response.getItems().get(0);
        OnboardActivity created = onboardActivityRepository.findById(item.getTargetId()).orElseThrow();

        // Assert: deck và cabin sai tàu không được liên kết bừa
        assertNull(created.getDeck(), "Deck sai tàu không được gán cho hoạt động của tàu A");
        assertNull(created.getCabin(), "Cabin sai tàu không được gán cho hoạt động của tàu A");

        // Assert warnings
        assertTrue(item.getWarnings().stream().anyMatch(w -> w.contains("không thuộc con tàu được gán")));
        assertNotNull(item.getRequiredData());
        assertTrue(item.getRequiredData().contains("Cần điều chỉnh"));
    }

    @Test
    @DisplayName("BE-R04 - Test 5: Khung phục vụ không reservation (SERVING_WINDOW tách biệt, không tạo OnboardActivity)")
    void test_05_serving_window_is_not_reservable_activity() {
        Trip trip = createTestTrip();
        TripScheduleVersion version = createTestVersion(trip);

        UUID serviceId = UUID.randomUUID();
        TripPlanEntry swEntry = new TripPlanEntry();
        swEntry.setVersion(version);
        swEntry.setDay(getDay(version, 1));
        swEntry.setClientKey("SW-BREAKFAST");
        swEntry.setKind(TripPlanEntryKind.SERVING_WINDOW);
        swEntry.setTitle("Buffet Sáng Nhà Hàng Horizon");
        swEntry.setStartDayNumber(1);
        swEntry.setEndDayNumber(1);
        swEntry.setPlannedStartTime(LocalTime.of(6, 30));
        swEntry.setPlannedEndTime(LocalTime.of(9, 30));
        swEntry.setServiceId(serviceId);
        planEntryRepository.save(swEntry);

        Instant dep = Instant.now().plus(40, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Cruise cruise = createTestCruise("CR-SW-" + UUID.randomUUID().toString().substring(0, 4), dep, dep.plus(2, ChronoUnit.DAYS), testShipA, version);
        createTestItinerary(cruise);

        // Run mapping
        OperationalProgramSyncResponse response = operationalProgramMappingService.mapOperationalPrograms(cruise, version);

        assertEquals(0, response.getOnboardActivitiesCount(), "SERVING_WINDOW không được tính vào OnboardActivities");
        assertEquals(1, response.getServingWindowsCount(), "SERVING_WINDOW phải được ghi nhận riêng");
        assertEquals(1, response.getItems().size());

        OperationalProgramMappingItemResponse item = response.getItems().get(0);
        assertEquals(TripPlanEntryKind.SERVING_WINDOW, item.getKind());
        assertFalse(item.isReservationRequired(), "Khung phục vụ KHÔNG yêu cầu đặt chỗ (reservationRequired = false)");
        assertEquals("SERVING_WINDOW", item.getTargetType());
        assertEquals("LINKED", item.getStatus());
        assertEquals(serviceId, item.getTargetId());

        // Kiểm tra trong DB: không có OnboardActivity nào được tạo
        assertTrue(onboardActivityRepository.findByCruise_CruiseId(cruise.getCruiseId()).isEmpty(),
                "Tuyệt đối không tạo bản ghi OnboardActivity giả cho SERVING_WINDOW");
    }

    @Test
    @DisplayName("BE-R04 - Test 6: Sync retry / Idempotent (chạy lại không nhân đôi bản ghi, trả LINKED)")
    void test_06_sync_retry_is_idempotent_without_duplicates() {
        Trip trip = createTestTrip();
        TripScheduleVersion version = createTestVersion(trip);

        TripPlanEntry actEntry = new TripPlanEntry();
        actEntry.setVersion(version);
        actEntry.setDay(getDay(version, 1));
        actEntry.setClientKey("ACT-YOGA");
        actEntry.setKind(TripPlanEntryKind.ONBOARD_ACTIVITY);
        actEntry.setTitle("Lớp Yoga Bình Minh");
        actEntry.setStartDayNumber(1);
        actEntry.setEndDayNumber(1);
        actEntry.setPlannedStartTime(LocalTime.of(6, 0));
        actEntry.setPlannedEndTime(LocalTime.of(7, 0));
        planEntryRepository.save(actEntry);

        Instant dep = Instant.now().plus(45, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Cruise cruise = createTestCruise("CR-RETRY-" + UUID.randomUUID().toString().substring(0, 4), dep, dep.plus(2, ChronoUnit.DAYS), testShipA, version);
        createTestItinerary(cruise);

        // Sync 1: CREATED
        OperationalProgramSyncResponse sync1 = operationalProgramMappingService.mapOperationalPrograms(cruise, version);
        assertEquals(1, sync1.getOnboardActivitiesCount());
        assertEquals("CREATED", sync1.getItems().get(0).getStatus());

        // Sync 2: LINKED (Idempotent update)
        OperationalProgramSyncResponse sync2 = operationalProgramMappingService.mapOperationalPrograms(cruise, version);
        assertEquals(1, sync2.getOnboardActivitiesCount());
        assertEquals("LINKED", sync2.getItems().get(0).getStatus());
        assertEquals("Đã cập nhật theo kế hoạch nguồn (Idempotent update)", sync2.getItems().get(0).getReason());

        // Kiểm tra database: vẫn chỉ có duy nhất 1 bản ghi
        List<OnboardActivity> acts = onboardActivityRepository.findByCruise_CruiseId(cruise.getCruiseId());
        assertEquals(1, acts.size(), "Không được nhân đôi bản ghi khi retry sync");
    }

    @Test
    @DisplayName("BE-R04 - Test 7: Chương trình đã có đăng ký của khách (bảo toàn cấu hình, không ghi đè)")
    void test_07_preserve_program_with_existing_passenger_registration() {
        Trip trip = createTestTrip();
        TripScheduleVersion version = createTestVersion(trip);

        TripPlanEntry actEntry = new TripPlanEntry();
        actEntry.setVersion(version);
        actEntry.setDay(getDay(version, 1));
        actEntry.setClientKey("ACT-SPA");
        actEntry.setKind(TripPlanEntryKind.ONBOARD_ACTIVITY);
        actEntry.setTitle("Dịch Vụ Spa Thư Giãn");
        actEntry.setStartDayNumber(1);
        actEntry.setEndDayNumber(1);
        actEntry.setPlannedStartTime(LocalTime.of(10, 0));
        actEntry.setPlannedEndTime(LocalTime.of(12, 0));
        actEntry = planEntryRepository.save(actEntry);

        Instant dep = Instant.now().plus(50, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Cruise cruise = createTestCruise("CR-REG-" + UUID.randomUUID().toString().substring(0, 4), dep, dep.plus(2, ChronoUnit.DAYS), testShipA, version);
        createTestItinerary(cruise);

        // Sync lần đầu để tạo OnboardActivity
        OperationalProgramSyncResponse sync1 = operationalProgramMappingService.mapOperationalPrograms(cruise, version);
        UUID activityId = sync1.getItems().get(0).getTargetId();
        OnboardActivity activity = onboardActivityRepository.findById(activityId).orElseThrow();

        // Tạo hành khách và Registration đặt chỗ cho hoạt động này
        Passenger passenger = new Passenger();
        passenger.setFullName("Nguyễn Văn Đã Đặt Chỗ");
        passenger.setEmail("khach_spa@casms.vn");
        passenger = passengerRepository.save(passenger);

        Booking booking = new Booking();
        booking.setCruise(cruise);
        booking.setBookedByUser(testUser);
        booking.setBookingReference("BK-R04-" + UUID.randomUUID().toString().substring(0, 6));
        booking.setBookingStatus(BookingStatus.Confirmed);
        booking.setAgreedFareAmount(BigDecimal.valueOf(5000000));
        booking.setCurrency("VND");
        booking.setRequiredDepositAmount(BigDecimal.valueOf(1000000));
        booking = bookingRepository.save(booking);

        BookingPassenger bp = new BookingPassenger();
        bp.setBooking(booking);
        bp.setPassenger(passenger);
        bp.setParticipationStatus(BookingPassengerParticipationStatus.Active);
        bp = bookingPassengerRepository.save(bp);

        Registration registration = new Registration();
        registration.setPassenger(passenger);
        registration.setBookingPassenger(bp);
        registration.setOnboardActivity(activity);
        registration.setStatus(RegistrationStatus.CONFIRMED);
        registration.setQuantity(1);
        registration.setRegistrationTime(Instant.now());
        registration.setCreatedAt(Instant.now());
        registration.setUpdatedAt(Instant.now());
        registrationRepository.save(registration);

        // Thay đổi tiêu đề trên source entry để thử xem có bị ghi đè không
        actEntry.setTitle("Dịch Vụ Spa Thư Giãn - Tên Mới Bị Thay Đổi");
        planEntryRepository.save(actEntry);

        // Chạy lại sync
        OperationalProgramSyncResponse sync2 = operationalProgramMappingService.mapOperationalPrograms(cruise, version);
        OperationalProgramMappingItemResponse item = sync2.getItems().get(0);

        assertEquals("LINKED", item.getStatus());
        assertTrue(item.getReason().contains("bảo toàn cấu hình"));
        assertTrue(item.getWarnings().stream().anyMatch(w -> w.contains("đã có khách đăng ký")));

        // Kiểm tra bản ghi trong DB không bị ghi đè tiêu đề
        OnboardActivity afterSync = onboardActivityRepository.findById(activityId).orElseThrow();
        assertEquals("Dịch Vụ Spa Thư Giãn", afterSync.getActivityName(),
                "Tên hoạt động trong database phải được bảo toàn, không bị ghi đè khi đã có khách đăng ký");
    }

    @Test
    @DisplayName("BE-R04 - Test 8: Tour ngoài cửa sổ cảng (Port Call Window -> cảnh báo rõ)")
    void test_08_shore_excursion_outside_port_window_emits_warning() {
        Trip trip = createTestTrip();
        TripScheduleVersion version = createTestVersion(trip);

        TripPlanStop stop = new TripPlanStop();
        stop.setVersion(version);
        stop.setClientKey("stop-dan-win-" + UUID.randomUUID());
        stop.setPort(portDan);
        stop.setStopOrder(1);
        stop.setArrivalDayNumber(1);
        stop.setDepartureDayNumber(1);
        stop.setCallType(CruiseStopCallType.NORMAL);
        stop.setArrivalMethod(CruiseStopArrivalMethod.BERTH);
        stop.setIsEmbarkation(true);
        stop = planStopRepository.save(stop);

        // Tour bắt đầu từ 07:00 đến 16:00
        ShoreExcursion tplWin = createTestTemplateExcursion("Template Tour Hội An", testSupplier);

        TripPlanEntry excEntry = new TripPlanEntry();
        excEntry.setVersion(version);
        excEntry.setDay(getDay(version, 1));
        excEntry.setClientKey("EXC-WINDOW-VIOLATION");
        excEntry.setKind(TripPlanEntryKind.SHORE_EXCURSION);
        excEntry.setTitle("Tour Hội An Cả Ngày");
        excEntry.setStartDayNumber(1);
        excEntry.setEndDayNumber(1);
        excEntry.setPlannedStartTime(LocalTime.of(7, 0));
        excEntry.setPlannedEndTime(LocalTime.of(16, 0));
        excEntry.setStop(stop);
        excEntry.setSourceExcursionId(tplWin.getShoreExcursionId());
        planEntryRepository.save(excEntry);

        Instant dep = Instant.now().plus(60, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Cruise cruise = createTestCruise("CR-WIN-" + UUID.randomUUID().toString().substring(0, 4), dep, dep.plus(2, ChronoUnit.DAYS), testShipA, version);
        Itinerary itinerary = createTestItinerary(cruise);

        LocalDate baseDate = dep.atZone(ZoneOffset.UTC).toLocalDate();
        ItineraryDay day = createTestItineraryDay(itinerary, 1, baseDate);

        // Tàu chỉ cập cảng từ 08:30 đến 14:00 (Tour 07:00 - 16:00 nằm ngoài cửa sổ cảng!)
        CruiseStop cs = new CruiseStop();
        cs.setItinerary(itinerary);
        cs.setArrivalItineraryDay(day);
        cs.setDepartureItineraryDay(day);
        cs.setPort(portDan);
        cs.setStopOrder(1);
        cs.setCallType(CruiseStopCallType.NORMAL);
        cs.setArrivalMethod(CruiseStopArrivalMethod.BERTH);
        cs.setStatus(CruiseStopStatus.PLANNED);
        cs.setPlannedArrival(baseDate.atTime(8, 30).atZone(ZoneOffset.UTC).toInstant());
        cs.setPlannedDeparture(baseDate.atTime(14, 0).atZone(ZoneOffset.UTC).toInstant());
        cs.setNotes("sourceStopId:" + stop.getStopId());
        cruiseStopRepository.save(cs);

        // Run mapping
        OperationalProgramSyncResponse response = operationalProgramMappingService.mapOperationalPrograms(cruise, version);
        assertEquals(1, response.getShoreExcursionsCount());

        OperationalProgramMappingItemResponse item = response.getItems().get(0);
        assertTrue(item.getWarnings().stream().anyMatch(w -> w.contains("nằm ngoài khung giờ tàu cập cảng")));
        assertNotNull(item.getRequiredData());
        assertTrue(item.getRequiredData().contains("Thời gian tour ngoài khung giờ cảng"));
    }

    @Test
    @DisplayName("BE-R04 - Test 9: Batch lỗi giữa chừng & không tìm thấy stop (Partial resilience, FAILED từng mục)")
    void test_09_partial_batch_failure_resilience_and_unmatched_stop() {
        Trip trip = createTestTrip();
        TripScheduleVersion version = createTestVersion(trip);

        // Entry 1: Hoạt động hợp lệ
        TripPlanEntry entry1 = new TripPlanEntry();
        entry1.setVersion(version);
        entry1.setDay(getDay(version, 1));
        entry1.setClientKey("VALID-ACT");
        entry1.setKind(TripPlanEntryKind.ONBOARD_ACTIVITY);
        entry1.setTitle("Lớp Nhạc Jazz Đêm");
        entry1.setStartDayNumber(1);
        entry1.setEndDayNumber(1);
        entry1.setPlannedStartTime(LocalTime.of(20, 0));
        entry1.setPlannedEndTime(LocalTime.of(22, 0));
        planEntryRepository.save(entry1);

        // Entry 2: Tour bờ nhưng Stop không có trong CruiseStop (Unmatched stop)
        TripPlanStop orphanStop = new TripPlanStop();
        orphanStop.setVersion(version);
        orphanStop.setClientKey("orphan-stop-" + UUID.randomUUID());
        orphanStop.setPort(portDan);
        orphanStop.setStopOrder(99); // Không tồn tại trong CruiseStop
        orphanStop.setArrivalDayNumber(1);
        orphanStop.setDepartureDayNumber(1);
        orphanStop.setCallType(CruiseStopCallType.NORMAL);
        orphanStop.setArrivalMethod(CruiseStopArrivalMethod.BERTH);
        orphanStop.setIsEmbarkation(false);
        orphanStop = planStopRepository.save(orphanStop);

        TripPlanEntry entry2 = new TripPlanEntry();
        entry2.setVersion(version);
        entry2.setDay(getDay(version, 1));
        entry2.setClientKey("ORPHAN-TOUR");
        entry2.setKind(TripPlanEntryKind.SHORE_EXCURSION);
        entry2.setTitle("Tour Không Khớp Cảng");
        entry2.setStartDayNumber(1);
        entry2.setEndDayNumber(1);
        entry2.setPlannedStartTime(LocalTime.of(9, 0));
        entry2.setPlannedEndTime(LocalTime.of(12, 0));
        entry2.setStop(orphanStop);
        planEntryRepository.save(entry2);

        // Entry 3: Khung phục vụ hợp lệ
        TripPlanEntry entry3 = new TripPlanEntry();
        entry3.setVersion(version);
        entry3.setDay(getDay(version, 1));
        entry3.setClientKey("VALID-SW");
        entry3.setKind(TripPlanEntryKind.SERVING_WINDOW);
        entry3.setTitle("Trà Chiều Hoàng Hôn");
        entry3.setStartDayNumber(1);
        entry3.setEndDayNumber(1);
        entry3.setPlannedStartTime(LocalTime.of(15, 0));
        entry3.setPlannedEndTime(LocalTime.of(17, 0));
        planEntryRepository.save(entry3);

        Instant dep = Instant.now().plus(70, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Cruise cruise = createTestCruise("CR-BATCH-" + UUID.randomUUID().toString().substring(0, 4), dep, dep.plus(2, ChronoUnit.DAYS), testShipA, version);
        createTestItinerary(cruise);

        // Run mapping
        OperationalProgramSyncResponse response = operationalProgramMappingService.mapOperationalPrograms(cruise, version);

        assertEquals(1, response.getOnboardActivitiesCount());
        assertEquals(0, response.getShoreExcursionsCount());
        assertEquals(1, response.getServingWindowsCount());
        assertEquals(3, response.getItems().size());

        OperationalProgramMappingItemResponse item1 = response.getItems().stream()
                .filter(i -> "VALID-ACT".equals(i.getClientKey())).findFirst().orElseThrow();
        OperationalProgramMappingItemResponse item2 = response.getItems().stream()
                .filter(i -> "ORPHAN-TOUR".equals(i.getClientKey())).findFirst().orElseThrow();
        OperationalProgramMappingItemResponse item3 = response.getItems().stream()
                .filter(i -> "VALID-SW".equals(i.getClientKey())).findFirst().orElseThrow();

        assertEquals("CREATED", item1.getStatus());
        assertEquals("FAILED", item2.getStatus(), "Entry không khớp cảng phải trả về FAILED");
        assertNull(item2.getTargetId());
        assertTrue(item2.getReason().contains("Không tìm thấy lần ghé cảng"));
        assertEquals("LINKED", item3.getStatus());

        // Batch không bị đổ vỡ, các mục hợp lệ đều được ghi nhận đầy đủ
    }
}
