package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.identity.entity.User;
import com.casms.identity.enums.Role;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.repository.UserRepository;
import com.casms.ship.dto.request.CruiseCreateRequest;
import com.casms.ship.dto.request.CruiseUpdateDraftRequest;
import com.casms.ship.dto.response.CruiseReadinessResponse;
import com.casms.ship.dto.response.CruiseResponse;
import com.casms.ship.dto.response.CruiseSchedulePreviewResponse;
import com.casms.ship.entity.*;
import com.casms.ship.enums.*;
import com.casms.ship.repository.*;
import com.casms.trip.dto.request.ScheduleDraftSaveRequest;
import com.casms.trip.dto.request.TripPlanDayDraftDto;
import com.casms.trip.dto.request.TripPlanStopDraftDto;
import com.casms.trip.dto.response.EligibleTripSourceResponse;
import com.casms.trip.entity.*;
import com.casms.trip.enums.TripDurationGroup;
import com.casms.trip.enums.TripScheduleVersionStatus;
import com.casms.trip.enums.TripStatus;
import com.casms.trip.repository.*;
import com.casms.trip.service.IdempotencyService;
import com.casms.trip.service.TripScheduleVersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class CruiseDraftAndMaterializationPostgresIntegrationTest {

    @Autowired private CruiseService cruiseService;
    @Autowired private ScheduleMaterializationService scheduleMaterializationService;
    @Autowired private TripScheduleVersionService versionService;
    @Autowired private IdempotencyService idempotencyService;

    @Autowired private CruiseRepository cruiseRepository;
    @Autowired private ItineraryRepository itineraryRepository;
    @Autowired private ItineraryDayRepository itineraryDayRepository;
    @Autowired private CruiseStopRepository cruiseStopRepository;
    @Autowired private PortRepository portRepository;
    @Autowired private ShipRepository shipRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private TripScheduleVersionRepository versionRepository;
    @Autowired private TripPlanDayRepository planDayRepository;
    @Autowired private TripPlanStopRepository planStopRepository;
    @Autowired private UserRepository userRepository;

    private User testActor;
    private Port portHph;
    private Port portDan;
    private Ship testShip;

    @BeforeEach
    void setUp() {
        testActor = new User();
        testActor.setUsername("tester-" + UUID.randomUUID().toString().substring(0, 8));
        testActor.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        testActor.setStatus(UserStatus.ACTIVE);
        testActor.setRoles(Set.of(Role.ADMIN));
        testActor = userRepository.save(testActor);

        portHph = new Port();
        portHph.setPortCode("VN-HP" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        portHph.setPortName("Cảng Hải Phòng Test");
        portHph.setCountryCode("VN");
        portHph.setTimeZoneId("Asia/Ho_Chi_Minh");
        portHph.setStatus(PortStatus.ACTIVE);
        portHph = portRepository.save(portHph);

        portDan = new Port();
        portDan.setPortCode("VN-DA" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        portDan.setPortName("Cảng Đà Nẵng Test");
        portDan.setCountryCode("VN");
        portDan.setTimeZoneId("Asia/Ho_Chi_Minh");
        portDan.setStatus(PortStatus.ACTIVE);
        portDan = portRepository.save(portDan);

        testShip = new Ship();
        testShip.setShipCode("SH-" + UUID.randomUUID().toString().substring(0, 6));
        testShip.setShipName("Tàu Hoàng Gia Test");
        testShip.setStatus(ShipStatus.READY);
        testShip.setTotalCapacity(500);
        testShip.setTotalDecks(5);
        testShip = shipRepository.save(testShip);
    }

    private TripScheduleVersion createPublishedTripVersion(String tripCode, String plannedScope) {
        Trip trip = new Trip();
        trip.setTripCode(tripCode);
        trip.setName("Chuyến đi mẫu " + tripCode);
        trip.setStatus(TripStatus.READY);
        trip.setPlannedScope(plannedScope);
        trip.setDurationGroup(TripDurationGroup.SHORT);
        trip.setDurationDays(3);
        trip.setDurationNights(2);
        trip = tripRepository.save(trip);

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

        return version;
    }

    @Test
    @DisplayName("E01: getEligibleSources - Chỉ trả về Trip version PUBLISHED của Trip không bị ARCHIVED")
    void test_E01_eligibleSourcesQuery() {
        TripScheduleVersion pubVer = createPublishedTripVersion("TR-E01-PUB-" + UUID.randomUUID().toString().substring(0, 4), "DOMESTIC");

        // Version Draft
        Trip draftTrip = new Trip();
        draftTrip.setTripCode("TR-E01-DR-" + UUID.randomUUID().toString().substring(0, 4));
        draftTrip.setName("Trip Draft");
        draftTrip.setStatus(TripStatus.READY);
        draftTrip.setPlannedScope("DOMESTIC");
        draftTrip.setDurationDays(2);
        draftTrip.setDurationNights(1);
        draftTrip = tripRepository.save(draftTrip);

        TripScheduleVersion draftVer = new TripScheduleVersion();
        draftVer.setTrip(draftTrip);
        draftVer.setVersionNumber(1);
        draftVer.setStatus(TripScheduleVersionStatus.DRAFT);
        versionRepository.save(draftVer);

        // Version Published but Trip Archived
        Trip archTrip = new Trip();
        archTrip.setTripCode("TR-E01-ARC-" + UUID.randomUUID().toString().substring(0, 4));
        archTrip.setName("Trip Archived");
        archTrip.setStatus(TripStatus.ARCHIVED);
        archTrip.setPlannedScope("DOMESTIC");
        archTrip.setDurationDays(2);
        archTrip.setDurationNights(1);
        archTrip = tripRepository.save(archTrip);

        TripScheduleVersion archVer = new TripScheduleVersion();
        archVer.setTrip(archTrip);
        archVer.setVersionNumber(1);
        archVer.setStatus(TripScheduleVersionStatus.PUBLISHED);
        versionRepository.save(archVer);

        Page<EligibleTripSourceResponse> eligiblePage = versionService.getEligibleSources(null, null, PageRequest.of(0, 50));
        List<EligibleTripSourceResponse> eligible = eligiblePage.getContent();
        assertNotNull(eligible);
        assertTrue(eligible.stream().anyMatch(e -> e.getVersionId().equals(pubVer.getVersionId())));
        assertFalse(eligible.stream().anyMatch(e -> e.getVersionId().equals(draftVer.getVersionId())));
        assertFalse(eligible.stream().anyMatch(e -> e.getVersionId().equals(archVer.getVersionId())));
    }

    @Test
    @DisplayName("E02: previewSchedule - Preview tính đúng ngày giờ mà không ghi DB")
    void test_E02_previewSchedule() {
        TripScheduleVersion version = createPublishedTripVersion("TR-E02-" + UUID.randomUUID().toString().substring(0, 4), "DOMESTIC");
        Instant departure = Instant.parse("2026-12-01T01:00:00Z");

        long countItinBefore = itineraryRepository.count();
        long countStopsBefore = cruiseStopRepository.count();

        CruiseSchedulePreviewResponse preview = scheduleMaterializationService.previewSchedule(version.getVersionId(), departure);

        assertNotNull(preview);
        assertEquals(3, preview.getDays().size());
        assertEquals(departure, preview.getDepartureDatetime());
        assertNotNull(preview.getReturnDatetime());
        assertEquals("VN", preview.getDays().get(0).getStops().get(0).getCountryCode());

        // Đảm bảo không ghi database
        assertEquals(countItinBefore, itineraryRepository.count());
        assertEquals(countStopsBefore, cruiseStopRepository.count());
    }

    @Test
    @DisplayName("E03 & E04: Tạo Cruise DRAFT từ sourceVersion và đọc lại lịch thực tế")
    void test_E03_and_E04_createDraftWithMaterializationAndReadBack() {
        TripScheduleVersion version = createPublishedTripVersion("TR-E03-" + UUID.randomUUID().toString().substring(0, 4), "DOMESTIC");
        Instant departure = Instant.parse("2026-12-05T01:00:00Z");
        Instant returned = Instant.parse("2026-12-07T10:00:00Z");

        CruiseCreateRequest req = new CruiseCreateRequest();
        req.setCruiseName("Hành Trình E03");
        req.setDepartureDatetime(departure);
        req.setReturnDatetime(returned);
        req.setDeparturePortId(portHph.getPortId());
        req.setReturnPortId(portDan.getPortId());
        req.setShipId(testShip.getShipId());
        req.setSourceVersionId(version.getVersionId());

        CruiseResponse created = cruiseService.create(req);
        assertNotNull(created);
        assertEquals(CruiseStatus.DRAFT, created.getStatus());
        assertEquals(version.getVersionId(), created.getSourceVersionId());
        assertEquals(version.getTrip().getTripId(), created.getSourceTripId());
        assertNotNull(created.getRevision());

        // Kiểm tra Itinerary và CruiseStop đã được sinh trong DB
        Optional<Itinerary> itinOpt = itineraryRepository.findByCruiseCruiseId(created.getCruiseId());
        assertTrue(itinOpt.isPresent());
        Itinerary itin = itinOpt.get();
        assertEquals(ItineraryStatus.DRAFT, itin.getStatus());

        List<ItineraryDay> days = itineraryDayRepository.findByItineraryItineraryIdOrderByDayNumberAsc(itin.getItineraryId());
        assertEquals(3, days.size());

        List<CruiseStop> stops = cruiseStopRepository.findByItineraryItineraryIdOrderByStopOrderAsc(itin.getItineraryId());
        assertEquals(2, stops.size());
        assertEquals(portHph.getPortId(), stops.get(0).getPort().getPortId());
        assertEquals(portDan.getPortId(), stops.get(1).getPort().getPortId());
        assertTrue(stops.get(0).getNotes().startsWith("sourceStopId:"));

        // E04: Đọc lại toàn bộ lịch thực tế từ ScheduleMaterializationService
        CruiseSchedulePreviewResponse schedule = scheduleMaterializationService.getCruiseSchedule(created.getCruiseId());
        assertNotNull(schedule);
        assertEquals(created.getSourceTripId(), schedule.getSourceTripId());
        assertEquals(3, schedule.getDays().size());
        assertEquals(1, schedule.getDays().get(0).getStops().size());
        assertEquals(1, schedule.getDays().get(2).getStops().size());
    }

    @Test
    @DisplayName("E07 & E19: Idempotency-Key replay thành công, đổi payload ném 409 IDEMPOTENCY_CONFLICT")
    void test_E07_and_E19_idempotencyReplayAndConflict() {
        TripScheduleVersion version = createPublishedTripVersion("TR-E07-" + UUID.randomUUID().toString().substring(0, 4), "DOMESTIC");
        String idemKey = "IDEM-" + UUID.randomUUID();

        CruiseCreateRequest req1 = new CruiseCreateRequest();
        req1.setCruiseName("Idempotent Cruise Original");
        req1.setDepartureDatetime(Instant.parse("2026-12-10T01:00:00Z"));
        req1.setReturnDatetime(Instant.parse("2026-12-12T10:00:00Z"));
        req1.setDeparturePortId(portHph.getPortId());
        req1.setReturnPortId(portDan.getPortId());
        req1.setSourceVersionId(version.getVersionId());

        CruiseResponse first = idempotencyService.execute(
                idemKey, testActor.getUserId(), "CREATE_CRUISE", req1, CruiseResponse.class,
                () -> cruiseService.create(req1));
        assertNotNull(first);

        // Replay cùng key và payload
        CruiseResponse replayed = idempotencyService.execute(
                idemKey, testActor.getUserId(), "CREATE_CRUISE", req1, CruiseResponse.class,
                () -> cruiseService.create(req1));
        assertEquals(first.getCruiseId(), replayed.getCruiseId(), "Replay cùng key phải trả về kết quả đã cache");

        // Gọi cùng key nhưng payload khác -> ném IDEMPOTENCY_CONFLICT
        CruiseCreateRequest req2 = new CruiseCreateRequest();
        req2.setCruiseName("Idempotent Cruise Changed Payload");
        req2.setDepartureDatetime(req1.getDepartureDatetime());
        req2.setReturnDatetime(req1.getReturnDatetime());
        req2.setDeparturePortId(portHph.getPortId());
        req2.setReturnPortId(portDan.getPortId());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                idempotencyService.execute(
                        idemKey, testActor.getUserId(), "CREATE_CRUISE", req2, CruiseResponse.class,
                        () -> cruiseService.create(req2)));
        assertEquals("IDEMPOTENCY_CONFLICT", ex.getCode());
    }

    @Test
    @DisplayName("E08: Optimistic Locking - expectedRevision lệch ném 409 STALE_REVISION, khớp thì tăng revision")
    void test_E08_optimisticConcurrencyCheck() {
        TripScheduleVersion version = createPublishedTripVersion("TR-E08-" + UUID.randomUUID().toString().substring(0, 4), "DOMESTIC");

        CruiseCreateRequest req = new CruiseCreateRequest();
        req.setCruiseName("Cruise Revision Test");
        req.setDepartureDatetime(Instant.parse("2026-12-15T01:00:00Z"));
        req.setReturnDatetime(Instant.parse("2026-12-17T10:00:00Z"));
        req.setDeparturePortId(portHph.getPortId());
        req.setReturnPortId(portDan.getPortId());
        req.setSourceVersionId(version.getVersionId());

        CruiseResponse created = cruiseService.create(req);
        assertEquals(1L, created.getRevision());

        // Update với expectedRevision sai
        CruiseUpdateDraftRequest badPatch = new CruiseUpdateDraftRequest();
        badPatch.setExpectedRevision(999L);
        badPatch.setCruiseName("Renamed with bad revision");

        BusinessException ex = assertThrows(BusinessException.class, () ->
                cruiseService.updateDraft(created.getCruiseId(), badPatch));
        assertEquals("STALE_REVISION", ex.getCode());

        // Update với expectedRevision đúng
        CruiseUpdateDraftRequest goodPatch = new CruiseUpdateDraftRequest();
        goodPatch.setExpectedRevision(1L);
        goodPatch.setCruiseName("Renamed Successfully");

        CruiseResponse updated = cruiseService.updateDraft(created.getCruiseId(), goodPatch);
        assertEquals("Renamed Successfully", updated.getCruiseName());
        assertEquals(2L, updated.getRevision(), "Sau cập nhật thành công, revision phải tăng lên 2L");
    }

    @Test
    @DisplayName("E10: Không cho phép gán cùng tàu vào hai Cruise trùng lịch")
    void test_E10_shipScheduleConflict() {
        Instant dep = Instant.parse("2026-12-20T01:00:00Z");
        Instant ret = Instant.parse("2026-12-22T10:00:00Z");

        CruiseCreateRequest req1 = new CruiseCreateRequest();
        req1.setCruiseName("Cruise Ship Conflict 1");
        req1.setDepartureDatetime(dep);
        req1.setReturnDatetime(ret);
        req1.setDeparturePortId(portHph.getPortId());
        req1.setReturnPortId(portDan.getPortId());
        req1.setShipId(testShip.getShipId());

        CruiseResponse cruise1 = cruiseService.create(req1);
        assertNotNull(cruise1);

        // Tạo cruise thứ 2 trùng khoảng thời gian với cùng tàu
        CruiseCreateRequest req2 = new CruiseCreateRequest();
        req2.setCruiseName("Cruise Ship Conflict 2");
        req2.setDepartureDatetime(dep.plusSeconds(3600)); // trùng giữa chừng
        req2.setReturnDatetime(ret.plusSeconds(3600));
        req2.setDeparturePortId(portHph.getPortId());
        req2.setReturnPortId(portDan.getPortId());
        req2.setShipId(testShip.getShipId());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                cruiseService.create(req2));
        assertEquals("SHIP_SCHEDULE_CONFLICT", ex.getCode());
    }

    @Test
    @DisplayName("E15: Lọc danh sách Cruise theo sourceTripId và sourceVersionId")
    void test_E15_filterBySourceTripAndVersion() {
        TripScheduleVersion verA = createPublishedTripVersion("TR-E15A-" + UUID.randomUUID().toString().substring(0, 4), "DOMESTIC");
        TripScheduleVersion verB = createPublishedTripVersion("TR-E15B-" + UUID.randomUUID().toString().substring(0, 4), "DOMESTIC");

        CruiseCreateRequest reqA = new CruiseCreateRequest();
        reqA.setCruiseName("Cruise A from verA");
        reqA.setDepartureDatetime(Instant.parse("2026-12-25T01:00:00Z"));
        reqA.setReturnDatetime(Instant.parse("2026-12-27T10:00:00Z"));
        reqA.setDeparturePortId(portHph.getPortId());
        reqA.setReturnPortId(portDan.getPortId());
        reqA.setSourceVersionId(verA.getVersionId());
        cruiseService.create(reqA);

        CruiseCreateRequest reqB = new CruiseCreateRequest();
        reqB.setCruiseName("Cruise B from verB");
        reqB.setDepartureDatetime(Instant.parse("2026-12-28T01:00:00Z"));
        reqB.setReturnDatetime(Instant.parse("2026-12-30T10:00:00Z"));
        reqB.setDeparturePortId(portHph.getPortId());
        reqB.setReturnPortId(portDan.getPortId());
        reqB.setSourceVersionId(verB.getVersionId());
        cruiseService.create(reqB);

        Page<CruiseResponse> filterA = cruiseService.list(
                null, null, null, verA.getTrip().getTripId(), verA.getVersionId(), PageRequest.of(0, 10));
        assertEquals(1, filterA.getTotalElements());
        assertEquals("Cruise A from verA", filterA.getContent().get(0).getCruiseName());

        Page<CruiseResponse> filterB = cruiseService.list(
                null, null, null, verB.getTrip().getTripId(), verB.getVersionId(), PageRequest.of(0, 10));
        assertEquals(1, filterB.getTotalElements());
        assertEquals("Cruise B from verB", filterB.getContent().get(0).getCruiseName());
    }

    @Test
    @DisplayName("E17: validateOperationalReadiness là read-only, không tự ý chuyển trạng thái sang mở bán")
    void test_E17_readinessProbeIsReadOnly() {
        TripScheduleVersion version = createPublishedTripVersion("TR-E17-" + UUID.randomUUID().toString().substring(0, 4), "DOMESTIC");

        CruiseCreateRequest req = new CruiseCreateRequest();
        req.setCruiseName("Cruise Readiness Probe Test");
        req.setDepartureDatetime(Instant.parse("2027-01-01T01:00:00Z"));
        req.setReturnDatetime(Instant.parse("2027-01-03T10:00:00Z"));
        req.setDeparturePortId(portHph.getPortId());
        req.setReturnPortId(portDan.getPortId());
        req.setSourceVersionId(version.getVersionId());

        CruiseResponse created = cruiseService.create(req);
        assertEquals(CruiseStatus.DRAFT, created.getStatus());

        // Chạy kiểm tra sẵn sàng vận hành
        CruiseReadinessResponse readiness = cruiseService.validateOperationalReadiness(created.getCruiseId());
        assertNotNull(readiness);
        assertNotNull(readiness.getEvaluatedAt());
        assertNotNull(readiness.getCruiseRevision());

        // Kiểm tra sau khi gọi readiness probe, Cruise và Itinerary vẫn giữ nguyên DRAFT, tuyệt đối không tự publish
        Cruise afterProbe = cruiseRepository.findById(created.getCruiseId()).orElseThrow();
        assertEquals(CruiseStatus.DRAFT, afterProbe.getStatus(), "Cruise status phải duy trì DRAFT sau khi probe readiness");

        Itinerary itin = itineraryRepository.findByCruiseCruiseId(created.getCruiseId()).orElseThrow();
        assertEquals(ItineraryStatus.DRAFT, itin.getStatus(), "Itinerary status phải duy trì DRAFT, không được tự ý sang OPEN_FOR_SALE");
    }

    @Test
    @DisplayName("BE-02 & BE-03 / G04: Tạo Cruise từ sourceVersion tự động suy luận returnDatetime và cảng, khóa thời lượng")
    void test_BE02_and_BE03_createCruise_autoDerivesDurationAndPorts_locksDuration() {
        TripScheduleVersion version = createPublishedTripVersion("TR-BE03-" + UUID.randomUUID().toString().substring(0, 4), "DOMESTIC");
        Instant departure = Instant.parse("2026-12-15T01:00:00Z");

        CruiseCreateRequest req = new CruiseCreateRequest();
        req.setCruiseName("Hành Trình Khóa Thời Lượng Tự Động");
        req.setDepartureDatetime(departure);
        // Không truyền returnDatetime, departurePortId, returnPortId (G04)
        req.setSourceVersionId(version.getVersionId());
        req.setShipId(testShip.getShipId());

        CruiseResponse created = cruiseService.create(req);
        assertNotNull(created);
        assertEquals(CruiseStatus.DRAFT, created.getStatus());
        assertEquals(departure, created.getDepartureDatetime());
        assertNotNull(created.getReturnDatetime(), "Return datetime phải được tự động tính toán từ lịch nguồn");
        assertEquals(portHph.getPortId(), created.getDeparturePortId(), "Cảng đi phải tự động lấy từ điểm dừng 1");
        assertEquals(portDan.getPortId(), created.getReturnPortId(), "Cảng về phải tự động lấy từ điểm dừng cuối");

        // Kiểm tra Itinerary đã được sinh ra khớp với số ngày của nguồn
        Optional<Itinerary> itinOpt = itineraryRepository.findByCruiseCruiseId(created.getCruiseId());
        assertTrue(itinOpt.isPresent());
        List<ItineraryDay> days = itineraryDayRepository.findByItineraryItineraryIdOrderByDayNumberAsc(itinOpt.get().getItineraryId());
        assertEquals(3, days.size(), "Hải trình phải có đúng 3 ngày theo nguồn");
    }

    @Test
    @DisplayName("BE-02 / G01: Tạo Cruise từ phiên bản chưa PUBLISHED ném VERSION_NOT_PUBLISHED")
    void test_BE02_createCruise_rejectsNonPublishedVersion() {
        Trip trip = new Trip();
        trip.setTripCode("TR-UNPUB-" + UUID.randomUUID().toString().substring(0, 4));
        trip.setName("Trip Chưa Duyệt");
        trip.setStatus(TripStatus.READY);
        trip.setPlannedScope("DOMESTIC");
        trip.setDurationDays(2);
        trip.setDurationNights(1);
        trip.setDurationGroup(TripDurationGroup.SHORT);
        trip = tripRepository.save(trip);

        TripScheduleVersion unpubVersion = new TripScheduleVersion();
        unpubVersion.setTrip(trip);
        unpubVersion.setVersionNumber(1);
        unpubVersion.setRevision(1L);
        unpubVersion.setStatus(TripScheduleVersionStatus.DRAFT);
        unpubVersion = versionRepository.save(unpubVersion);

        CruiseCreateRequest req = new CruiseCreateRequest();
        req.setCruiseName("Cruise Invalid Source");
        req.setDepartureDatetime(Instant.parse("2026-12-20T01:00:00Z"));
        req.setSourceVersionId(unpubVersion.getVersionId());

        BusinessException ex = assertThrows(BusinessException.class, () -> cruiseService.create(req));
        assertEquals("VERSION_NOT_PUBLISHED", ex.getCode());
    }

    @Test
    @DisplayName("BE-03 / G04: Client truyền returnDatetime sai lệch sẽ bị khóa theo projection nguồn")
    void test_BE03_createCruise_locksDuration_whenClientPassesTamperedReturnDate() {
        TripScheduleVersion version = createPublishedTripVersion("TR-TAMP-" + UUID.randomUUID().toString().substring(0, 4), "DOMESTIC");
        Instant departure = Instant.parse("2026-12-10T01:00:00Z");
        Instant tamperedReturn = departure.plus(10, ChronoUnit.DAYS); // Client cố tình gửi 10 ngày thay vì 3 ngày nguồn

        CruiseCreateRequest req = new CruiseCreateRequest();
        req.setCruiseName("Cruise Tampered Return Test");
        req.setDepartureDatetime(departure);
        req.setReturnDatetime(tamperedReturn);
        req.setSourceVersionId(version.getVersionId());

        CruiseResponse created = cruiseService.create(req);
        assertNotNull(created);
        assertNotEquals(tamperedReturn, created.getReturnDatetime(), "Return datetime bị làm giả phải bị từ chối/ghi đè theo nguồn");
        // Thời lượng phải được khóa chính xác theo projection nguồn
        CruiseSchedulePreviewResponse projection = scheduleMaterializationService.previewSchedule(version.getVersionId(), departure);
        assertEquals(projection.getReturnDatetime(), created.getReturnDatetime(), "Return datetime phải bị khóa khớp với projection nguồn");
    }

    @Test
    @DisplayName("BE-03: Cập nhật draft cruise dời ngày khởi hành phải khóa và dời ngày kết thúc tương ứng")
    void test_BE03_updateDraft_locksDuration_whenShiftingDeparture() {
        TripScheduleVersion version = createPublishedTripVersion("TR-SHIFT-" + UUID.randomUUID().toString().substring(0, 4), "DOMESTIC");
        Instant departure = Instant.parse("2026-12-01T01:00:00Z");

        CruiseCreateRequest req = new CruiseCreateRequest();
        req.setCruiseName("Cruise Shifting Test");
        req.setDepartureDatetime(departure);
        req.setSourceVersionId(version.getVersionId());

        CruiseResponse created = cruiseService.create(req);
        long originalDuration = created.getReturnDatetime().toEpochMilli() - created.getDepartureDatetime().toEpochMilli();

        // Dời departure thêm 5 ngày
        Instant newDep = departure.plus(5, ChronoUnit.DAYS);
        CruiseUpdateDraftRequest updateReq = new CruiseUpdateDraftRequest();
        updateReq.setDepartureDatetime(newDep);
        updateReq.setExpectedRevision(created.getRevision());

        CruiseResponse updated = cruiseService.updateDraft(created.getCruiseId(), updateReq);
        assertEquals(newDep, updated.getDepartureDatetime());
        long newDuration = updated.getReturnDatetime().toEpochMilli() - updated.getDepartureDatetime().toEpochMilli();
        assertEquals(originalDuration, newDuration, "Thời lượng chuyến đi từ nguồn phải được giữ nguyên không đổi khi dời lịch");
    }
}
