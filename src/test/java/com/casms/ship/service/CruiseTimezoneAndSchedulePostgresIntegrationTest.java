package com.casms.ship.service;

import com.casms.activity.entity.OnboardActivity;
import com.casms.activity.entity.ShoreExcursion;
import com.casms.activity.entity.Supplier;
import com.casms.activity.enums.OnboardActivityCategory;
import com.casms.activity.enums.SupplierStatus;
import com.casms.activity.enums.SupplierVerificationStatus;
import com.casms.activity.repository.OnboardActivityRepository;
import com.casms.activity.repository.ShoreExcursionRepository;
import com.casms.activity.repository.SupplierRepository;
import com.casms.common.exception.BusinessException;
import com.casms.ship.dto.request.CruiseCreateRequest;
import com.casms.ship.dto.response.CruiseResponse;
import com.casms.ship.dto.response.CruiseSchedulePreviewResponse;
import com.casms.ship.entity.*;
import com.casms.ship.enums.*;
import com.casms.ship.repository.*;
import com.casms.ship.util.PortTimeZoneHelper;
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
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class CruiseTimezoneAndSchedulePostgresIntegrationTest {

    @Autowired private ScheduleMaterializationService scheduleMaterializationService;
    @Autowired private OperationalProgramMappingService operationalProgramMappingService;
    @Autowired private CruiseService cruiseService;

    @Autowired private CruiseRepository cruiseRepository;
    @Autowired private ShipRepository shipRepository;
    @Autowired private DeckRepository deckRepository;
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

    private Ship testShip;
    private Deck testDeck;
    private Port portHph;
    private Port portDan;
    private Port portTokyo;
    private Port portParis;
    private Supplier testSupplier;

    @BeforeEach
    void setUp() {
        // Ports
        portHph = new Port();
        portHph.setPortCode("VN-HP-TZ-" + UUID.randomUUID().toString().substring(0, 4));
        portHph.setPortName("Hai Phong Port");
        portHph.setCountryCode("VN");
        portHph.setTimeZoneId("Asia/Ho_Chi_Minh");
        portHph.setStatus(PortStatus.ACTIVE);
        portHph = portRepository.save(portHph);

        portDan = new Port();
        portDan.setPortCode("VN-DA-TZ-" + UUID.randomUUID().toString().substring(0, 4));
        portDan.setPortName("Da Nang Port");
        portDan.setCountryCode("VN");
        portDan.setTimeZoneId("Asia/Ho_Chi_Minh");
        portDan.setStatus(PortStatus.ACTIVE);
        portDan = portRepository.save(portDan);

        portTokyo = new Port();
        portTokyo.setPortCode("JP-TYO-" + UUID.randomUUID().toString().substring(0, 4));
        portTokyo.setPortName("Tokyo Port");
        portTokyo.setCountryCode("JP");
        portTokyo.setTimeZoneId("Asia/Tokyo");
        portTokyo.setStatus(PortStatus.ACTIVE);
        portTokyo = portRepository.save(portTokyo);

        portParis = new Port();
        portParis.setPortCode("FR-PAR-" + UUID.randomUUID().toString().substring(0, 4));
        portParis.setPortName("Le Havre Port");
        portParis.setCountryCode("FR");
        portParis.setTimeZoneId("Europe/Paris");
        portParis.setStatus(PortStatus.ACTIVE);
        portParis = portRepository.save(portParis);

        // Ship
        testShip = new Ship();
        testShip.setShipCode("SH-TZ-" + UUID.randomUUID().toString().substring(0, 6));
        testShip.setShipName("Ocean Star TZ");
        testShip.setStatus(ShipStatus.READY);
        testShip.setTotalCapacity(600);
        testShip.setTotalDecks(6);
        testShip = shipRepository.save(testShip);

        testDeck = new Deck();
        testDeck.setShip(testShip);
        testDeck.setDeckNumber(3);
        testDeck.setDeckName("Lido Deck");
        testDeck = deckRepository.save(testDeck);

        testSupplier = new Supplier();
        testSupplier.setSupplierName("Global Shore Excursions Ltd");
        testSupplier.setContactName("John Doe");
        testSupplier.setContactEmail("contact@globalexcursions.com");
        testSupplier.setContactPhone("+84988776655");
        testSupplier.setStatus(SupplierStatus.ACTIVE);
        testSupplier.setVerificationStatus(SupplierVerificationStatus.VERIFIED);
        testSupplier = supplierRepository.save(testSupplier);
    }

    private TripScheduleVersion createTestVersionWithPrograms(String tripCode, boolean overnightActivity) {
        Trip trip = new Trip();
        trip.setTripCode(tripCode + "-" + UUID.randomUUID().toString().substring(0, 6));
        trip.setName("Hải Trình Test " + tripCode);
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
        version.setRevision(1L);
        version.setStatus(TripScheduleVersionStatus.PUBLISHED);
        version = versionRepository.save(version);

        List<TripPlanDay> days = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            TripPlanDay d = new TripPlanDay();
            d.setVersion(version);
            d.setDayNumber(i);
            d.setTitle("Ngày " + i);
            d.setDayType(i == 2 ? ItineraryDayType.PORT_DAY : ItineraryDayType.SEA_DAY);
            days.add(planDayRepository.save(d));
        }
        version.setDays(days);

        // Stops: Day 1 HPH, Day 2 DAN, Day 3 HPH
        TripPlanStop s1 = new TripPlanStop();
        s1.setVersion(version);
        s1.setClientKey("STOP-1-" + UUID.randomUUID());
        s1.setPort(portHph);
        s1.setStopOrder(1);
        s1.setArrivalDayNumber(1);
        s1.setPlannedArrivalTime(LocalTime.of(8, 0));
        s1.setDepartureDayNumber(1);
        s1.setPlannedDepartureTime(LocalTime.of(18, 0));
        s1.setCallType(CruiseStopCallType.NORMAL);
        s1.setArrivalMethod(CruiseStopArrivalMethod.BERTH);
        s1 = planStopRepository.save(s1);

        TripPlanStop s2 = new TripPlanStop();
        s2.setVersion(version);
        s2.setClientKey("STOP-2-" + UUID.randomUUID());
        s2.setPort(portDan);
        s2.setStopOrder(2);
        s2.setArrivalDayNumber(2);
        s2.setPlannedArrivalTime(LocalTime.of(8, 0));
        s2.setDepartureDayNumber(2);
        s2.setPlannedDepartureTime(LocalTime.of(18, 0));
        s2.setCallType(CruiseStopCallType.NORMAL);
        s2.setArrivalMethod(CruiseStopArrivalMethod.BERTH);
        s2 = planStopRepository.save(s2);

        version.setStops(List.of(s1, s2));

        // Entries
        List<TripPlanEntry> entries = new ArrayList<>();

        // Entry 1: Onboard Activity (Normal or Overnight)
        TripPlanEntry e1 = new TripPlanEntry();
        e1.setVersion(version);
        e1.setDay(days.get(0));
        e1.setClientKey("ACT-ONBOARD");
        e1.setKind(TripPlanEntryKind.ONBOARD_ACTIVITY);
        e1.setCategory(OnboardActivityCategory.ENTERTAINMENT);
        e1.setTitle("Welcome Party Onboard");
        e1.setStartDayNumber(1);
        if (overnightActivity) {
            e1.setEndDayNumber(2);
            e1.setPlannedStartTime(LocalTime.of(23, 0));
            e1.setPlannedEndTime(LocalTime.of(2, 0)); // D 23:00 -> D+1 02:00
        } else {
            e1.setEndDayNumber(1);
            e1.setPlannedStartTime(LocalTime.of(14, 0));
            e1.setPlannedEndTime(LocalTime.of(16, 0));
        }
        e1.setDeck(testDeck);
        entries.add(planEntryRepository.save(e1));

        // Template excursion for supplier
        ShoreExcursion tpl = new ShoreExcursion();
        tpl.setExcursionCode("TPL-" + UUID.randomUUID().toString().substring(0, 8));
        tpl.setExcursionName("Tour Bà Nà Hills Template");
        tpl.setSupplier(testSupplier);
        tpl.setStartTime(Instant.now());
        tpl.setPlannedReturnTime(Instant.now().plus(4, java.time.temporal.ChronoUnit.HOURS));
        tpl.setMaximumCapacity(50);
        tpl.setMeetingPoint("Terminal Gate 1");
        tpl.setPrice(BigDecimal.valueOf(500000));
        tpl.setCurrencyCode("VND");
        tpl.setStatus(com.casms.activity.enums.ShoreExcursionStatus.PLANNED);
        tpl = shoreExcursionRepository.save(tpl);

        // Entry 2: Shore Excursion at Stop 2
        TripPlanEntry e2 = new TripPlanEntry();
        e2.setVersion(version);
        e2.setDay(days.get(1));
        e2.setClientKey("EXC-DAN");
        e2.setKind(TripPlanEntryKind.SHORE_EXCURSION);
        e2.setTitle("Tour Bà Nà Hills");
        e2.setStop(s2);
        e2.setStartDayNumber(2);
        e2.setEndDayNumber(2);
        e2.setPlannedStartTime(LocalTime.of(9, 0));
        e2.setPlannedEndTime(LocalTime.of(16, 0));
        e2.setSourceExcursionId(tpl.getShoreExcursionId());
        entries.add(planEntryRepository.save(e2));

        version.setEntries(entries);
        return versionRepository.save(version);
    }

    @Test
    @DisplayName("BE-R05a / Case 1: Preview và Persisted mapping cho cùng input/revision có cùng Instant chính xác")
    void test01_sameInputAndRevision_previewAndMaterializedHaveIdenticalInstants() {
        TripScheduleVersion version = createTestVersionWithPrograms("TR-TZ-01", false);
        Instant departure = Instant.parse("2026-11-01T01:00:00Z"); // 08:00 UTC+7 (Asia/Ho_Chi_Minh)

        // 1. Preview
        CruiseSchedulePreviewResponse preview = scheduleMaterializationService.previewSchedule(version.getVersionId(), departure);
        assertNotNull(preview);
        assertFalse(preview.getMaterialized());
        assertNull(preview.getItineraryId());

        // 2. Create Cruise & Materialize
        CruiseCreateRequest req = new CruiseCreateRequest();
        req.setCruiseName("Hành Trình Múi Giờ Test 01");
        req.setDepartureDatetime(departure);
        req.setReturnDatetime(departure.plusSeconds(86400 * 3));
        req.setDeparturePortId(portHph.getPortId());
        req.setReturnPortId(portHph.getPortId());
        req.setShipId(testShip.getShipId());
        req.setSourceVersionId(version.getVersionId());
        req.setExpectedSourceVersionRevision(version.getRevision());

        CruiseResponse created = cruiseService.create(req);
        assertNotNull(created);

        // 3. Load DB entities
        Itinerary itinerary = itineraryRepository.findByCruiseCruiseId(created.getCruiseId()).orElseThrow();
        List<CruiseStop> dbStops = cruiseStopRepository.findByItineraryItineraryIdOrderByStopOrderAsc(itinerary.getItineraryId());
        List<OnboardActivity> dbActs = onboardActivityRepository.findByCruise_CruiseId(created.getCruiseId());
        List<ShoreExcursion> dbExcs = shoreExcursionRepository.findByCruiseStop_Itinerary_Cruise_CruiseId(created.getCruiseId());

        assertEquals(2, dbStops.size());
        assertEquals(1, dbActs.size());
        assertEquals(1, dbExcs.size());

        // Stop 1 Instant comparison
        CruiseSchedulePreviewResponse.ProjectedStopDto prevStop1 = preview.getDays().get(0).getStops().get(0);
        CruiseStop dbStop1 = dbStops.get(0);
        assertEquals(prevStop1.getPlannedArrival(), dbStop1.getPlannedArrival(), "Stop 1 plannedArrival must match");
        assertEquals(prevStop1.getPlannedDeparture(), dbStop1.getPlannedDeparture(), "Stop 1 plannedDeparture must match");

        // Stop 2 Instant comparison
        CruiseSchedulePreviewResponse.ProjectedStopDto prevStop2 = preview.getDays().get(1).getStops().get(0);
        CruiseStop dbStop2 = dbStops.get(1);
        assertEquals(prevStop2.getPlannedArrival(), dbStop2.getPlannedArrival(), "Stop 2 plannedArrival must match");
        assertEquals(prevStop2.getPlannedDeparture(), dbStop2.getPlannedDeparture(), "Stop 2 plannedDeparture must match");

        // Onboard Activity Instant comparison
        CruiseSchedulePreviewResponse.ProjectedEntryDto prevAct = preview.getDays().get(0).getEntries().get(0);
        OnboardActivity dbAct = dbActs.get(0);
        assertEquals(prevAct.getPlannedStartTime(), dbAct.getStartTime(), "Onboard Activity startTime must match preview exactly");
        assertEquals(prevAct.getPlannedEndTime(), dbAct.getEndTime(), "Onboard Activity endTime must match preview exactly");

        // Shore Excursion Instant comparison
        CruiseSchedulePreviewResponse.ProjectedEntryDto prevExc = preview.getDays().get(1).getEntries().get(0);
        ShoreExcursion dbExc = dbExcs.get(0);
        assertEquals(prevExc.getPlannedStartTime(), dbExc.getStartTime(), "Shore Excursion startTime must match preview exactly");
        assertEquals(prevExc.getPlannedEndTime(), dbExc.getPlannedReturnTime(), "Shore Excursion endTime must match preview exactly");
    }

    @Test
    @DisplayName("BE-R05a / Case 2: D 23:00 -> D+1 02:00 tính toán qua ranh giới ngày an toàn, duration dương")
    void test02_overnightSpan_calculatesAcrossDayBoundariesWithoutNegativeDuration() {
        TripScheduleVersion version = createTestVersionWithPrograms("TR-TZ-02", true);
        Instant departure = Instant.parse("2026-11-05T01:00:00Z");

        // Preview
        CruiseSchedulePreviewResponse preview = scheduleMaterializationService.previewSchedule(version.getVersionId(), departure);
        CruiseSchedulePreviewResponse.ProjectedEntryDto overnightEntry = preview.getDays().get(0).getEntries().get(0);

        assertNotNull(overnightEntry.getPlannedStartTime());
        assertNotNull(overnightEntry.getPlannedEndTime());
        assertTrue(overnightEntry.getPlannedEndTime().isAfter(overnightEntry.getPlannedStartTime()),
                "End time must be after start time across midnight boundary");

        long durationHours = Duration.between(overnightEntry.getPlannedStartTime(), overnightEntry.getPlannedEndTime()).toHours();
        assertEquals(3, durationHours, "23:00 to 02:00 next day must be exactly 3 hours");

        // Materialize and check DB
        CruiseCreateRequest req = new CruiseCreateRequest();
        req.setCruiseName("Hành Trình Qua Đêm Test 02");
        req.setDepartureDatetime(departure);
        req.setReturnDatetime(departure.plusSeconds(86400 * 3));
        req.setDeparturePortId(portHph.getPortId());
        req.setReturnPortId(portHph.getPortId());
        req.setShipId(testShip.getShipId());
        req.setSourceVersionId(version.getVersionId());

        CruiseResponse created = cruiseService.create(req);
        List<OnboardActivity> acts = onboardActivityRepository.findByCruise_CruiseId(created.getCruiseId());
        assertEquals(1, acts.size());

        OnboardActivity act = acts.get(0);
        assertEquals(overnightEntry.getPlannedStartTime(), act.getStartTime());
        assertEquals(overnightEntry.getPlannedEndTime(), act.getEndTime());
        assertEquals(3, Duration.between(act.getStartTime(), act.getEndTime()).toHours());
    }

    @Test
    @DisplayName("BE-R05a / Case 3: Lặp cảng trong cùng hải trình nhận occurrence ID riêng biệt và không lỗi overlap")
    void test03_repeatedPortCalls_differentOccurrenceIdsAndNoOverlap() {
        Trip trip = new Trip();
        trip.setTripCode("TR-TZ-03-" + UUID.randomUUID().toString().substring(0, 4));
        trip.setName("Vòng Lặp Cảng Hải Phòng");
        trip.setStatus(TripStatus.READY);
        trip.setPlannedScope("DOMESTIC");
        trip.setDurationDays(3);
        trip.setDurationNights(2);
        trip = tripRepository.save(trip);

        TripScheduleVersion version = new TripScheduleVersion();
        version.setTrip(trip);
        version.setVersionNumber(1);
        version.setRevision(1L);
        version.setStatus(TripScheduleVersionStatus.PUBLISHED);
        version = versionRepository.save(version);

        // Ghé HPH ngày 1, ghé DAN ngày 2, quay lại HPH ngày 3
        TripPlanStop s1 = new TripPlanStop();
        s1.setVersion(version);
        s1.setClientKey("STOP-REP-1-" + UUID.randomUUID());
        s1.setPort(portHph);
        s1.setStopOrder(1);
        s1.setArrivalDayNumber(1);
        s1.setPlannedArrivalTime(LocalTime.of(8, 0));
        s1.setDepartureDayNumber(1);
        s1.setPlannedDepartureTime(LocalTime.of(18, 0));
        s1.setCallType(CruiseStopCallType.NORMAL);
        s1 = planStopRepository.save(s1);

        TripPlanStop s2 = new TripPlanStop();
        s2.setVersion(version);
        s2.setClientKey("STOP-REP-2-" + UUID.randomUUID());
        s2.setPort(portDan);
        s2.setStopOrder(2);
        s2.setArrivalDayNumber(2);
        s2.setPlannedArrivalTime(LocalTime.of(8, 0));
        s2.setDepartureDayNumber(2);
        s2.setPlannedDepartureTime(LocalTime.of(18, 0));
        s2.setCallType(CruiseStopCallType.NORMAL);
        s2 = planStopRepository.save(s2);

        TripPlanStop s3 = new TripPlanStop();
        s3.setVersion(version);
        s3.setClientKey("STOP-REP-3-" + UUID.randomUUID());
        s3.setPort(portHph); // Lặp cảng HPH
        s3.setStopOrder(3);
        s3.setArrivalDayNumber(3);
        s3.setPlannedArrivalTime(LocalTime.of(8, 0));
        s3.setDepartureDayNumber(3);
        s3.setPlannedDepartureTime(LocalTime.of(18, 0));
        s3.setCallType(CruiseStopCallType.NORMAL);
        s3 = planStopRepository.save(s3);

        version.setStops(List.of(s1, s2, s3));
        version.setEntries(Collections.emptyList());
        version = versionRepository.save(version);

        Instant departure = Instant.parse("2026-11-10T01:00:00Z");
        CruiseSchedulePreviewResponse preview = scheduleMaterializationService.previewSchedule(version.getVersionId(), departure);
        assertNotNull(preview);

        CruiseCreateRequest req = new CruiseCreateRequest();
        req.setCruiseName("Chuyến đi lặp cảng HPH");
        req.setDepartureDatetime(departure);
        req.setReturnDatetime(departure.plusSeconds(86400 * 3));
        req.setDeparturePortId(portHph.getPortId());
        req.setReturnPortId(portHph.getPortId());
        req.setShipId(testShip.getShipId());
        req.setSourceVersionId(version.getVersionId());

        CruiseResponse created = cruiseService.create(req);
        CruiseSchedulePreviewResponse schedule = scheduleMaterializationService.getCruiseSchedule(created.getCruiseId());

        assertTrue(schedule.getMaterialized());
        assertEquals(3, schedule.getDays().size());

        UUID occurrence1 = schedule.getDays().get(0).getStops().get(0).getCruiseStopId();
        UUID occurrence3 = schedule.getDays().get(2).getStops().get(0).getCruiseStopId();

        assertNotNull(occurrence1);
        assertNotNull(occurrence3);
        assertNotEquals(occurrence1, occurrence3, "Cùng cảng HPH tại hai lần ghé phải có hai occurrence ID riêng biệt");
    }

    @Test
    @DisplayName("BE-R05a / Case 4: Các cảng khác múi giờ tính đúng thời gian địa phương và instant tuyệt đối")
    void test04_crossTimezonePorts_correctLocalAndInstantConversions() {
        Trip trip = new Trip();
        trip.setTripCode("TR-TZ-04-" + UUID.randomUUID().toString().substring(0, 6));
        trip.setName("Hải Trình Việt Nam - Nhật Bản");
        trip.setStatus(TripStatus.READY);
        trip.setPlannedScope("INTERNATIONAL");
        trip.setCountryCodes(List.of("VN", "JP"));
        trip.setDurationDays(3);
        trip.setDurationNights(2);
        trip = tripRepository.save(trip);

        TripScheduleVersion version = new TripScheduleVersion();
        version.setTrip(trip);
        version.setVersionNumber(1);
        version.setRevision(1L);
        version.setStatus(TripScheduleVersionStatus.PUBLISHED);
        version = versionRepository.save(version);

        // Stop 1: HPH (UTC+7), Stop 2: Tokyo (UTC+9)
        TripPlanStop s1 = new TripPlanStop();
        s1.setVersion(version);
        s1.setClientKey("STOP-TZ-1-" + UUID.randomUUID());
        s1.setPort(portHph);
        s1.setStopOrder(1);
        s1.setArrivalDayNumber(1);
        s1.setPlannedArrivalTime(LocalTime.of(8, 0));
        s1.setDepartureDayNumber(1);
        s1.setPlannedDepartureTime(LocalTime.of(18, 0));
        s1 = planStopRepository.save(s1);

        TripPlanStop s2 = new TripPlanStop();
        s2.setVersion(version);
        s2.setClientKey("STOP-TZ-2-" + UUID.randomUUID());
        s2.setPort(portTokyo);
        s2.setStopOrder(2);
        s2.setArrivalDayNumber(2);
        s2.setPlannedArrivalTime(LocalTime.of(12, 0));
        s2.setDepartureDayNumber(2);
        s2.setPlannedDepartureTime(LocalTime.of(20, 0));
        s2 = planStopRepository.save(s2);

        version.setStops(List.of(s1, s2));
        version.setEntries(Collections.emptyList());
        version = versionRepository.save(version);

        Instant departure = Instant.parse("2026-11-15T01:00:00Z"); // 08:00 UTC+7
        CruiseSchedulePreviewResponse preview = scheduleMaterializationService.previewSchedule(version.getVersionId(), departure);

        CruiseSchedulePreviewResponse.ProjectedStopDto stop1 = preview.getDays().get(0).getStops().get(0);
        CruiseSchedulePreviewResponse.ProjectedStopDto stop2 = preview.getDays().get(1).getStops().get(0);

        assertEquals("Asia/Ho_Chi_Minh", stop1.getPortTimeZoneId());
        assertEquals("Asia/Tokyo", stop2.getPortTimeZoneId());

        // Stop 1 departure: 2026-11-15 18:00 UTC+7 -> 11:00:00Z
        assertEquals(Instant.parse("2026-11-15T11:00:00Z"), stop1.getPlannedDeparture());

        // Stop 2 arrival: Day 2 (2026-11-16) 12:00 UTC+9 -> 03:00:00Z
        assertEquals(Instant.parse("2026-11-16T03:00:00Z"), stop2.getPlannedArrival());

        // Transit duration: 11:00 UTC Day 1 -> 03:00 UTC Day 2 = 16 hours
        long transitHours = Duration.between(stop1.getPlannedDeparture(), stop2.getPlannedArrival()).toHours();
        assertEquals(16, transitHours, "Transit time between Vietnam and Japan must be 16 hours positive");
    }

    @Test
    @DisplayName("BE-R05a / Case 5: Cảng thiếu hoặc sai múi giờ ném lỗi explicit BusinessException, không fallback ngầm")
    void test05_invalidOrMissingPortTimezone_throwsExplicitBusinessExceptionWithoutSilentFallback() {
        Port invalidPort = new Port();
        invalidPort.setPortCode("VN-BAD-TZ-" + UUID.randomUUID().toString().substring(0, 4));
        invalidPort.setPortName("Bad Timezone Port");
        invalidPort.setCountryCode("VN");
        invalidPort.setTimeZoneId("Invalid/Mui_Gio_Khong_Hop_Le");
        invalidPort.setStatus(PortStatus.ACTIVE);
        invalidPort = portRepository.save(invalidPort);

        Trip trip = new Trip();
        trip.setTripCode("TR-TZ-05-" + UUID.randomUUID().toString().substring(0, 4));
        trip.setName("Test Invalid TZ Trip");
        trip.setStatus(TripStatus.READY);
        trip.setPlannedScope("DOMESTIC");
        trip.setDurationDays(2);
        trip.setDurationNights(1);
        trip = tripRepository.save(trip);

        TripScheduleVersion version = new TripScheduleVersion();
        version.setTrip(trip);
        version.setVersionNumber(1);
        version.setStatus(TripScheduleVersionStatus.PUBLISHED);
        version = versionRepository.save(version);

        TripPlanStop s1 = new TripPlanStop();
        s1.setVersion(version);
        s1.setClientKey("STOP-BAD-1-" + UUID.randomUUID());
        s1.setPort(invalidPort);
        s1.setStopOrder(1);
        s1.setArrivalDayNumber(1);
        s1.setPlannedArrivalTime(LocalTime.of(8, 0));
        s1.setDepartureDayNumber(1);
        s1.setPlannedDepartureTime(LocalTime.of(18, 0));
        s1 = planStopRepository.save(s1);

        version.setStops(List.of(s1));
        version.setEntries(Collections.emptyList());
        TripScheduleVersion savedVersion = versionRepository.save(version);
        UUID targetVersionId = savedVersion.getVersionId();

        Instant departure = Instant.parse("2026-11-20T01:00:00Z");

        BusinessException ex = assertThrows(BusinessException.class, () ->
                scheduleMaterializationService.previewSchedule(targetVersionId, departure));
        assertEquals("INVALID_PORT_TIME_ZONE", ex.getCode(), "Must throw INVALID_PORT_TIME_ZONE and never silently fallback");
    }

    @Test
    @DisplayName("BE-R05a / Case 6: getCruiseSchedule phản ánh rõ lịch đã commit vs chưa commit; không fake projection")
    void test06_getCruiseSchedule_materializedVsUnmaterializedReflectedClearly() {
        TripScheduleVersion version = createTestVersionWithPrograms("TR-TZ-06", false);
        Instant departure = Instant.parse("2026-11-25T01:00:00Z");

        // Tạo cruise DRAFT nhưng KHÔNG gọi materialize (chưa có Itinerary)
        Cruise unmaterializedCruise = new Cruise();
        unmaterializedCruise.setCruiseCode("CR-TZ-UNMAT-" + UUID.randomUUID().toString().substring(0, 4));
        unmaterializedCruise.setCruiseName("Chưa có lịch trình");
        unmaterializedCruise.setCruiseType(CruiseCruiseType.MULTI_DAYS);
        unmaterializedCruise.setStatus(CruiseStatus.DRAFT);
        unmaterializedCruise.setDepartureDatetime(departure);
        unmaterializedCruise.setReturnDatetime(departure.plusSeconds(86400 * 3));
        unmaterializedCruise.setDeparturePort(portHph);
        unmaterializedCruise.setReturnPort(portHph);
        unmaterializedCruise.setSourceTrip(version.getTrip());
        unmaterializedCruise.setSourceVersion(version);
        unmaterializedCruise = cruiseRepository.save(unmaterializedCruise);

        // Gọi getCruiseSchedule khi chưa commit Itinerary
        CruiseSchedulePreviewResponse resUnmaterialized = scheduleMaterializationService.getCruiseSchedule(unmaterializedCruise.getCruiseId());
        assertNotNull(resUnmaterialized);
        assertFalse(resUnmaterialized.getMaterialized(), "materialized must be false");
        assertNull(resUnmaterialized.getItineraryId(), "itineraryId must be null");
        assertTrue(resUnmaterialized.getDays().isEmpty(), "days must be empty, not fake projection");
        assertNotNull(resUnmaterialized.getWarnings());
        assertTrue(resUnmaterialized.getWarnings().get(0).contains("chưa được khởi tạo"));

        // Bây giờ materialize qua cruiseService.create
        CruiseCreateRequest req = new CruiseCreateRequest();
        req.setCruiseName("Đã có lịch trình");
        req.setDepartureDatetime(departure);
        req.setReturnDatetime(departure.plusSeconds(86400 * 3));
        req.setDeparturePortId(portHph.getPortId());
        req.setReturnPortId(portHph.getPortId());
        req.setShipId(testShip.getShipId());
        req.setSourceVersionId(version.getVersionId());

        CruiseResponse materializedCruise = cruiseService.create(req);
        CruiseSchedulePreviewResponse resMaterialized = scheduleMaterializationService.getCruiseSchedule(materializedCruise.getCruiseId());

        assertTrue(resMaterialized.getMaterialized(), "materialized must be true");
        assertNotNull(resMaterialized.getItineraryId(), "itineraryId must be present");
        assertFalse(resMaterialized.getDays().isEmpty(), "days must be present");
        assertNotNull(resMaterialized.getDays().get(0).getItineraryDayId());
        assertNotNull(resMaterialized.getDays().get(0).getStops().get(0).getCruiseStopId());
    }

    @Test
    @DisplayName("BE-R05a / Case 7: Nguồn đổi revision giữa preview và create bị chặn bằng lỗi STALE_PREVIEW")
    void test07_stalePreview_versionRevisionChangedBetweenPreviewAndCreate_rejected() {
        TripScheduleVersion version = createTestVersionWithPrograms("TR-TZ-07", false);
        Instant departure = Instant.parse("2026-12-01T01:00:00Z");

        // Preview thu được revision hiện tại của version
        CruiseSchedulePreviewResponse preview = scheduleMaterializationService.previewSchedule(version.getVersionId(), departure);
        Long initialRevision = preview.getSourceVersionRevision();
        assertNotNull(initialRevision);

        // Cập nhật phiên bản nguồn trên DB để JPA tự tăng revision
        version.setTimePolicyVersion("v1.1-modified");
        version = versionRepository.saveAndFlush(version);
        assertTrue(version.getRevision() > initialRevision, "JPA should bump revision");

        // Client gửi create với preview revision cũ (initialRevision)
        CruiseCreateRequest req = new CruiseCreateRequest();
        req.setCruiseName("Tạo với preview cũ");
        req.setDepartureDatetime(departure);
        req.setReturnDatetime(departure.plusSeconds(86400 * 3));
        req.setDeparturePortId(portHph.getPortId());
        req.setReturnPortId(portHph.getPortId());
        req.setShipId(testShip.getShipId());
        req.setSourceVersionId(version.getVersionId());
        req.setExpectedSourceVersionRevision(initialRevision); // Stale revision!

        BusinessException ex = assertThrows(BusinessException.class, () -> cruiseService.create(req));
        assertEquals("STALE_PREVIEW", ex.getCode(), "Must reject with STALE_PREVIEW when source revision changed");
    }

    @Test
    @DisplayName("BE-R05a / Case 8: Planned time không bao giờ bị ghi đè vào Actual time khi materialize")
    void test08_plannedTimesNotWrittenToActual() {
        TripScheduleVersion version = createTestVersionWithPrograms("TR-TZ-08", false);
        Instant departure = Instant.parse("2026-12-05T01:00:00Z");

        CruiseCreateRequest req = new CruiseCreateRequest();
        req.setCruiseName("Kiểm tra planned vs actual");
        req.setDepartureDatetime(departure);
        req.setReturnDatetime(departure.plusSeconds(86400 * 3));
        req.setDeparturePortId(portHph.getPortId());
        req.setReturnPortId(portHph.getPortId());
        req.setShipId(testShip.getShipId());
        req.setSourceVersionId(version.getVersionId());

        CruiseResponse created = cruiseService.create(req);
        Itinerary itinerary = itineraryRepository.findByCruiseCruiseId(created.getCruiseId()).orElseThrow();
        List<CruiseStop> stops = cruiseStopRepository.findByItineraryItineraryIdOrderByStopOrderAsc(itinerary.getItineraryId());

        assertFalse(stops.isEmpty());
        for (CruiseStop s : stops) {
            assertNotNull(s.getPlannedArrival(), "plannedArrival must be set");
            assertNotNull(s.getPlannedDeparture(), "plannedDeparture must be set");
            assertNull(s.getActualArrival(), "actualArrival must remain NULL at materialization time");
            assertNull(s.getActualDeparture(), "actualDeparture must remain NULL at materialization time");
        }
    }

    @Test
    @DisplayName("BE-R05a / Case 9: DST (Daylight Saving Time) tính toán chuẩn xác cho các cảng Châu Âu theo chuẩn IANA")
    void test09_dstTimezoneHandling() {
        // Paris mùa hè (July 15, UTC+2 CEST): 10:00 local -> 08:00 UTC
        LocalDate summerDate = LocalDate.of(2026, 7, 15);
        ZoneId parisZone = PortTimeZoneHelper.resolveZone("Europe/Paris");
        Instant summerInstant = LocalDateTime.of(summerDate, LocalTime.of(10, 0)).atZone(parisZone).toInstant();
        assertEquals(Instant.parse("2026-07-15T08:00:00Z"), summerInstant);

        // Paris mùa đông (January 15, UTC+1 CET): 10:00 local -> 09:00 UTC
        LocalDate winterDate = LocalDate.of(2026, 1, 15);
        Instant winterInstant = LocalDateTime.of(winterDate, LocalTime.of(10, 0)).atZone(parisZone).toInstant();
        assertEquals(Instant.parse("2026-01-15T09:00:00Z"), winterInstant);
    }
}
