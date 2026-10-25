package com.casms.trip.service;

import com.casms.activity.entity.OnboardActivity;
import com.casms.activity.entity.ShoreExcursion;
import com.casms.activity.repository.OnboardActivityRepository;
import com.casms.activity.repository.ShoreExcursionRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.response.ApiResponse;
import com.casms.common.service.BusinessCodeGenerator;
import com.casms.identity.entity.User;
import com.casms.identity.repository.UserRepository;
import com.casms.servicepackage.entity.Service;
import com.casms.servicepackage.repository.ServiceRepository;
import com.casms.ship.entity.Cabin;
import com.casms.ship.entity.Deck;
import com.casms.ship.entity.Destination;
import com.casms.ship.entity.Port;
import com.casms.ship.enums.ItineraryDayType;
import com.casms.ship.enums.ItineraryType;
import com.casms.ship.repository.CabinRepository;
import com.casms.ship.repository.DeckRepository;
import com.casms.ship.repository.DestinationRepository;
import com.casms.ship.repository.PortRepository;
import com.casms.trip.controller.TripCatalogController;
import com.casms.trip.dto.request.TripCreateRequest;
import com.casms.trip.dto.request.TripDayManageRequest;
import com.casms.trip.dto.response.*;
import com.casms.trip.entity.*;
import com.casms.trip.enums.*;
import com.casms.trip.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TripPlanningEnhancementTest {

    @Mock
    private TripRepository tripRepository;

    @Mock
    private TripScheduleVersionRepository versionRepository;

    @Mock
    private TripPlanDayRepository dayRepository;

    @Mock
    private TripPlanStopRepository stopRepository;

    @Mock
    private TripPlanEntryRepository entryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BusinessCodeGenerator businessCodeGenerator;

    @Mock
    private com.casms.audit.AuditService auditService;

    @Mock
    private DestinationRepository destinationRepository;

    @Mock
    private OnboardActivityRepository onboardActivityRepository;

    @Mock
    private ShoreExcursionRepository shoreExcursionRepository;

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private IdempotencyRecordRepository idempotencyRepository;

    @Mock
    private com.casms.audit.AuditRepository auditRepository;

    @Mock
    private DeckRepository deckRepository;

    @Mock
    private CabinRepository cabinRepository;

    @Mock
    private PortRepository portRepository;

    private TripService tripService;
    private TripScheduleVersionService versionService;
    private IdempotencyService idempotencyService;
    private TripCatalogController catalogController;

    private UUID actorId;
    private User actor;

    @BeforeEach
    void setUp() {
        tripService = new TripService(tripRepository, versionRepository, dayRepository,
                userRepository, businessCodeGenerator, auditService, auditRepository);

        versionService = new TripScheduleVersionService(tripRepository, versionRepository,
                dayRepository, stopRepository, entryRepository, mock(com.casms.ship.repository.PortRepository.class),
                destinationRepository, userRepository, auditRepository, auditService);
        versionService.setOnboardActivityRepository(onboardActivityRepository);
        versionService.setShoreExcursionRepository(shoreExcursionRepository);

        idempotencyService = new IdempotencyService(new IdempotencyReservationService(idempotencyRepository), new ObjectMapper());

        catalogController = new TripCatalogController(destinationRepository,
                onboardActivityRepository, shoreExcursionRepository, serviceRepository);
        catalogController.setDeckRepository(deckRepository);
        catalogController.setCabinRepository(cabinRepository);
        catalogController.setPortRepository(portRepository);

        versionService.setDeckRepository(deckRepository);
        versionService.setCabinRepository(cabinRepository);

        actorId = UUID.randomUUID();
        actor = new User();
        actor.setUserId(actorId);
        actor.setUsername("planner@casms.com");
    }

    // =========================================================================
    // BE-02: Duration Group & Scope Tests
    // =========================================================================

    @Test
    @DisplayName("BE-02: Tạo Trip với SHORT tự động khởi tạo 1 ngày khi không nhập durationDays")
    void testCreateTripShortDefaultsToOneDay() {
        TripCreateRequest req = new TripCreateRequest();
        req.setName("Chuyến tàu ven vịnh 1 ngày");
        req.setDurationGroup(TripDurationGroup.SHORT);
        req.setPlannedScope("DOMESTIC");
        req.setDomesticRegions(List.of("BAC_BO"));

        when(businessCodeGenerator.next("TR")).thenReturn("TR-2001");
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));
        when(versionRepository.save(any(TripScheduleVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        TripDetailResponse res = tripService.createTrip(req, actorId);

        assertNotNull(res);
        assertEquals(TripDurationGroup.SHORT, res.getDurationGroup());
        assertEquals(1, res.getDurationDays());
        assertEquals(0, res.getDurationNights());
        assertEquals("DOMESTIC", res.getPlannedScope());
        assertEquals(List.of("BAC_BO"), res.getDomesticRegions());
    }

    @Test
    @DisplayName("BE-02: Tạo Trip với LONG tự động khởi tạo 3 ngày khi không nhập durationDays")
    void testCreateTripLongDefaultsToThreeDays() {
        TripCreateRequest req = new TripCreateRequest();
        req.setName("Hải trình xuyên quốc gia");
        req.setDurationGroup(TripDurationGroup.LONG);
        req.setPlannedScope("INTERNATIONAL");
        req.setCountryCodes(List.of("VN", "TH", "SG"));

        when(businessCodeGenerator.next("TR")).thenReturn("TR-2002");
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));
        when(versionRepository.save(any(TripScheduleVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        TripDetailResponse res = tripService.createTrip(req, actorId);

        assertNotNull(res);
        assertEquals(TripDurationGroup.LONG, res.getDurationGroup());
        assertEquals(3, res.getDurationDays());
        assertEquals(2, res.getDurationNights());
        assertEquals("INTERNATIONAL", res.getPlannedScope());
        assertEquals(List.of("VN", "TH", "SG"), res.getCountryCodes());
    }

    @Test
    @DisplayName("BE-02: Kiểm tra số ngày vượt quá giới hạn của nhóm thời lượng")
    void testCreateTripDurationOutOfBounds() {
        TripCreateRequest reqShort = new TripCreateRequest();
        reqShort.setName("Trip Short sai số ngày");
        reqShort.setDurationGroup(TripDurationGroup.SHORT);
        reqShort.setDurationDays(3); // SHORT chỉ cho phép 1..2 ngày

        BusinessException ex1 = assertThrows(BusinessException.class, () -> tripService.createTrip(reqShort, actorId));
        assertEquals("INVALID_DURATION", ex1.getCode());

        TripCreateRequest reqLong = new TripCreateRequest();
        reqLong.setName("Trip Long sai số ngày");
        reqLong.setDurationGroup(TripDurationGroup.LONG);
        reqLong.setDurationDays(6); // LONG chỉ cho phép 3..5 ngày

        BusinessException ex2 = assertThrows(BusinessException.class, () -> tripService.createTrip(reqLong, actorId));
        assertEquals("INVALID_DURATION", ex2.getCode());
    }

    // =========================================================================
    // BE-03: Safe Day Management (manageDays) Tests
    // =========================================================================

    @Test
    @DisplayName("BE-03: Thêm ngày thành công cập nhật dual revision và số ngày Trip")
    void testManageDaysAddSuccess() {
        UUID tripId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();

        Trip trip = new Trip();
        trip.setTripId(tripId);
        trip.setTripCode("TR-2003");
        trip.setDurationGroup(TripDurationGroup.SHORT);
        trip.setDurationDays(1);
        trip.setDurationNights(0);
        trip.setRevision(1L);
        trip.setStatus(TripStatus.DRAFT);

        TripScheduleVersion version = new TripScheduleVersion();
        version.setVersionId(versionId);
        version.setTrip(trip);
        version.setVersionNumber(1);
        version.setStatus(TripScheduleVersionStatus.DRAFT);
        version.setRevision(1L);
        List<TripPlanDay> days = new ArrayList<>();
        TripPlanDay d1 = new TripPlanDay();
        d1.setDayNumber(1);
        days.add(d1);
        version.setDays(days);
        version.setStops(new ArrayList<>());
        version.setEntries(new ArrayList<>());

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(versionRepository.findByTrip_TripIdOrderByVersionNumberAsc(tripId)).thenReturn(List.of(version));
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> {
            Trip t = inv.getArgument(0);
            t.setRevision((t.getRevision() != null ? t.getRevision() : 0L) + 1);
            return t;
        });
        when(versionRepository.save(any(TripScheduleVersion.class))).thenAnswer(inv -> {
            TripScheduleVersion v = inv.getArgument(0);
            v.setRevision((v.getRevision() != null ? v.getRevision() : 0L) + 1);
            return v;
        });

        TripDayManageRequest req = new TripDayManageRequest();
        req.setAction(TripDayManageAction.ADD);
        req.setExpectedTripRevision(1L);
        req.setExpectedVersionRevision(1L);
        req.setDayType(ItineraryDayType.SEA_DAY);

        TripDayManageResponse res = tripService.manageDays(tripId, versionId, req, actorId);

        assertNotNull(res);
        assertEquals(2, res.getTrip().getDurationDays());
        assertEquals(2L, res.getTrip().getRevision());
        assertEquals(2L, res.getVersion().getRevision());
        assertEquals(2, res.getVersion().getDays().size());
    }

    @Test
    @DisplayName("BE-03: Thêm ngày vượt quá giới hạn nhóm thời lượng ném lỗi DURATION_LIMIT_EXCEEDED")
    void testManageDaysAddExceedLimit() {
        UUID tripId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();

        Trip trip = new Trip();
        trip.setTripId(tripId);
        trip.setDurationGroup(TripDurationGroup.SHORT);
        trip.setDurationDays(2); // đã ở max 2 ngày
        trip.setRevision(1L);

        TripScheduleVersion version = new TripScheduleVersion();
        version.setVersionId(versionId);
        version.setTrip(trip);
        version.setStatus(TripScheduleVersionStatus.DRAFT);
        version.setRevision(1L);

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));

        TripDayManageRequest req = new TripDayManageRequest();
        req.setAction(TripDayManageAction.ADD);
        req.setExpectedTripRevision(1L);
        req.setExpectedVersionRevision(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tripService.manageDays(tripId, versionId, req, actorId));
        assertEquals("DURATION_LIMIT_EXCEEDED", ex.getCode());
    }

    @Test
    @DisplayName("BE-03: Xóa ngày có dữ liệu điểm dừng/hoạt động bị chặn bởi DAY_CONTENT_CONFLICT")
    void testManageDaysRemoveConflict() {
        UUID tripId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();

        Trip trip = new Trip();
        trip.setTripId(tripId);
        trip.setDurationGroup(TripDurationGroup.LONG);
        trip.setDurationDays(4);
        trip.setRevision(1L);

        TripScheduleVersion version = new TripScheduleVersion();
        version.setVersionId(versionId);
        version.setTrip(trip);
        version.setStatus(TripScheduleVersionStatus.DRAFT);
        version.setRevision(1L);

        TripPlanStop stop = new TripPlanStop();
        stop.setClientKey("stop-1");
        stop.setArrivalDayNumber(3);
        stop.setDepartureDayNumber(3);
        version.setStops(List.of(stop));
        version.setEntries(new ArrayList<>());

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(versionRepository.findByTrip_TripIdOrderByVersionNumberAsc(tripId)).thenReturn(List.of(version));

        TripDayManageRequest req = new TripDayManageRequest();
        req.setAction(TripDayManageAction.REMOVE);
        req.setDayNumber(3);
        req.setExpectedTripRevision(1L);
        req.setExpectedVersionRevision(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tripService.manageDays(tripId, versionId, req, actorId));
        assertEquals("DAY_CONTENT_CONFLICT", ex.getCode());
    }

    @Test
    @DisplayName("BE-03: Kiểm tra dual revision lệch trả về STALE_REVISION")
    void testManageDaysStaleRevision() {
        UUID tripId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();

        Trip trip = new Trip();
        trip.setTripId(tripId);
        trip.setRevision(2L); // current revision is 2

        TripScheduleVersion version = new TripScheduleVersion();
        version.setVersionId(versionId);
        version.setTrip(trip);
        version.setStatus(TripScheduleVersionStatus.DRAFT);
        version.setRevision(1L);

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));

        TripDayManageRequest req = new TripDayManageRequest();
        req.setAction(TripDayManageAction.ADD);
        req.setExpectedTripRevision(1L); // stale
        req.setExpectedVersionRevision(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tripService.manageDays(tripId, versionId, req, actorId));
        assertEquals("STALE_REVISION", ex.getCode());
    }

    @Test
    @DisplayName("BE-03: Trip đã xuất bản bị khóa thời lượng TRIP_DURATION_LOCKED")
    void testManageDaysTripDurationLocked() {
        UUID tripId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();

        Trip trip = new Trip();
        trip.setTripId(tripId);
        trip.setStructuralLockedAt(Instant.now()); // locked

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        TripDayManageRequest req = new TripDayManageRequest();
        req.setAction(TripDayManageAction.ADD);
        req.setExpectedTripRevision(1L);
        req.setExpectedVersionRevision(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tripService.manageDays(tripId, versionId, req, actorId));
        assertEquals("TRIP_DURATION_LOCKED", ex.getCode());
    }

    // =========================================================================
    // BE-04: Source Catalogs Tests
    // =========================================================================

    @Test
    @DisplayName("BE-04: Planner có quyền đọc nguồn qua TripCatalogController mà không cần ACTIVITY_READ")
    void testGetSourceCatalogs() {
        OnboardActivity act = new OnboardActivity();
        act.setOnboardActivityId(UUID.randomUUID());
        act.setActivityCode("ACT-GALA");
        act.setActivityName("Gala Dinner trên boong tàu");
        act.setLocation("Boong 8");

        when(onboardActivityRepository.findAll()).thenReturn(List.of(act));

        ApiResponse<List<PlanningSourceActivityResponse>> actRes = catalogController.getSourceActivities();
        assertNotNull(actRes);
        assertEquals(1, actRes.getData().size());
        assertEquals("ACT-GALA", actRes.getData().get(0).getActivityCode());
        assertEquals(act.getOnboardActivityId(), actRes.getData().get(0).getSourceActivityId());

        Service svc = new Service();
        svc.setServiceId(UUID.randomUUID());
        svc.setServiceCode("SVC-BUFFET");
        svc.setServiceName("Buffet sáng Quốc tế");
        svc.setServiceType("DINING");

        when(serviceRepository.findAll()).thenReturn(List.of(svc));

        ApiResponse<List<PlanningSourceServiceResponse>> svcRes = catalogController.getSourceServices();
        assertNotNull(svcRes);
        assertEquals(1, svcRes.getData().size());
        assertEquals("SVC-BUFFET", svcRes.getData().get(0).getServiceCode());
    }

    // =========================================================================
    // BE-05: Validation Rules (All-aboard, Cross-day interval) Tests
    // =========================================================================

    @Test
    @DisplayName("BE-05: Điểm dừng có tour tham quan trên bờ nhưng thiếu allAboardTime vi phạm ALL_ABOARD_REQUIRED_FOR_EXCURSION")
    void testValidationAllAboardRequiredForExcursion() {
        Trip trip = new Trip();
        trip.setDurationDays(3);

        TripScheduleVersion ver = new TripScheduleVersion();
        ver.setTrip(trip);

        List<TripPlanDay> days = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            TripPlanDay d = new TripPlanDay();
            d.setDayNumber(i);
            d.setDayType(i == 2 ? ItineraryDayType.PORT_DAY : ItineraryDayType.SEA_DAY);
            days.add(d);
        }
        ver.setDays(days);

        TripPlanStop stop = new TripPlanStop();
        stop.setClientKey("stop-port");
        stop.setArrivalDayNumber(2);
        stop.setDepartureDayNumber(2);
        stop.setPlannedArrivalTime(LocalTime.of(8, 0));
        stop.setPlannedDepartureTime(LocalTime.of(18, 0));
        stop.setAllAboardTime(null); // Missing allAboardTime!
        ver.setStops(List.of(stop));

        TripPlanEntry excursion = new TripPlanEntry();
        excursion.setClientKey("entry-excursion");
        excursion.setTitle("Tour vịnh Hạ Long");
        excursion.setKind(TripPlanEntryKind.SHORE_EXCURSION);
        excursion.setStop(stop);
        excursion.setStartDayNumber(2);
        excursion.setPlannedStartTime(LocalTime.of(9, 0));
        excursion.setEndDayNumber(2);
        excursion.setPlannedEndTime(LocalTime.of(16, 0));
        ver.setEntries(List.of(excursion));

        List<PlanViolation> violations = versionService.validateVersionInternal(ver);

        boolean hasExcursionViolation = violations.stream()
                .anyMatch(v -> "ALL_ABOARD_REQUIRED_FOR_EXCURSION".equals(v.getCode()));
        assertTrue(hasExcursionViolation, "Phải phát hiện lỗi thiếu allAboardTime khi có shore excursion");
    }

    @Test
    @DisplayName("BE-05: Giờ tất cả lên tàu sau giờ rời cảng vi phạm INVALID_ALL_ABOARD_TIME")
    void testValidationInvalidAllAboardTime() {
        Trip trip = new Trip();
        trip.setDurationDays(2);

        TripScheduleVersion ver = new TripScheduleVersion();
        ver.setTrip(trip);
        ver.setDays(List.of(new TripPlanDay(), new TripPlanDay()));

        TripPlanStop stop = new TripPlanStop();
        stop.setClientKey("stop-1");
        stop.setArrivalDayNumber(1);
        stop.setDepartureDayNumber(1);
        stop.setPlannedArrivalTime(LocalTime.of(8, 0));
        stop.setPlannedDepartureTime(LocalTime.of(17, 0));
        stop.setAllAboardTime(LocalTime.of(17, 30)); // after departure time!
        ver.setStops(List.of(stop));
        ver.setEntries(new ArrayList<>());

        List<PlanViolation> violations = versionService.validateVersionInternal(ver);

        boolean hasViolation = violations.stream()
                .anyMatch(v -> "INVALID_ALL_ABOARD_TIME".equals(v.getCode()));
        assertTrue(hasViolation, "Giờ lên tàu sau giờ rời cảng phải vi phạm INVALID_ALL_ABOARD_TIME");
    }

    // =========================================================================
    // BE-07: Anti-Replay Idempotency Tests
    // =========================================================================

    @Test
    @DisplayName("BE-07: Replay cùng Idempotency-Key và cùng payload trả lại kết quả cũ, không chạy lại action")
    void testIdempotencyReplaySamePayload() {
        String key = "idemp-key-1001";
        String payload = "{\"name\":\"Trip 1\"}";

        IdempotencyRecord record = new IdempotencyRecord();
        record.setIdempotencyKey(key);
        record.setOperation("CREATE_TRIP");
        record.setActorId(actorId);
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            record.setRequestHash(sb.toString());
        } catch (Exception ignored) {}
        record.setResponsePayload("{\"tripCode\":\"TR-CACHED\"}");
        record.setCreatedAt(Instant.now());

        when(idempotencyRepository.findByActorIdAndOperationAndIdempotencyKey(actorId, "CREATE_TRIP", key))
                .thenReturn(Optional.of(record));

        java.util.function.Supplier<TripDetailResponse> mockSupplier = mock(java.util.function.Supplier.class);

        TripDetailResponse res = idempotencyService.execute(key, actorId, "CREATE_TRIP", payload, mockSupplier);

        assertNotNull(res);
        assertEquals("TR-CACHED", res.getTripCode());
        verify(mockSupplier, never()).get(); // Ensure action was NOT called again
    }

    @Test
    @DisplayName("BE-07: Idempotency-Key trùng nhưng khác payload trả 409 IDEMPOTENCY_CONFLICT")
    void testIdempotencyConflictDifferentPayload() {
        String key = "idemp-key-1002";
        String newPayload = "{\"name\":\"Trip Different\"}";

        IdempotencyRecord record = new IdempotencyRecord();
        record.setIdempotencyKey(key);
        record.setOperation("CREATE_TRIP");
        record.setActorId(actorId);
        record.setRequestHash("old_different_hash_value");

        when(idempotencyRepository.findByActorIdAndOperationAndIdempotencyKey(actorId, "CREATE_TRIP", key))
                .thenReturn(Optional.of(record));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> idempotencyService.execute(key, actorId, "CREATE_TRIP", newPayload, () -> null));
        assertEquals("IDEMPOTENCY_CONFLICT", ex.getCode());
    }

    // =========================================================================
    // BE-08: Archive Protection Tests
    // =========================================================================

    @Test
    @DisplayName("BE-08: Thao tác sửa đổi trên Trip đã ARCHIVED bị từ chối với TRIP_ARCHIVED (409)")
    void testArchivedTripRejectsModifications() {
        UUID tripId = UUID.randomUUID();
        Trip trip = new Trip();
        trip.setTripId(tripId);
        trip.setStatus(TripStatus.ARCHIVED);

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tripService.archiveTrip(tripId, actorId));
        assertEquals("TRIP_ARCHIVED", ex.getCode());

        BusinessException exVer = assertThrows(BusinessException.class,
                () -> tripService.createScheduleVersion(tripId, null, actorId));
        assertEquals("TRIP_ARCHIVED", exVer.getCode());
    }

    // =========================================================================
    // P1: Series Key & Violation Navigation Metadata Tests
    // =========================================================================

    @Test
    @DisplayName("P1: Quan hệ nhóm lặp lưu và trả lại seriesClientKey cho khung phục vụ / hoạt động")
    void testServingWindowSeriesClientKeyRelation() {
        TripPlanEntry entry = new TripPlanEntry();
        entry.setClientKey("entry-breakfast-d1");
        entry.setKind(TripPlanEntryKind.SERVING_WINDOW);
        entry.setSeriesClientKey("series-buffet-breakfast");
        entry.setTitle("Buffet sáng tự chọn");
        entry.setStartDayNumber(1);
        entry.setPlannedStartTime(LocalTime.of(6, 30));
        entry.setEndDayNumber(1);
        entry.setPlannedEndTime(LocalTime.of(9, 30));

        TripPlanEntryResponse res = versionService.mapToEntryResponse(entry);
        assertNotNull(res);
        assertEquals("series-buffet-breakfast", res.getSeriesClientKey());
        assertEquals("Buffet sáng tự chọn", res.getTitle());
    }

    @Test
    @DisplayName("P1: Mọi vi phạm thẩm định đều có fieldPath và actionHint để FE điều hướng UI")
    void testViolationsHaveFieldPathAndActionHint() {
        Trip trip = new Trip();
        trip.setDurationDays(3);

        TripScheduleVersion ver = new TripScheduleVersion();
        ver.setTrip(trip);

        // Days count mismatch (has 2 days, expected 3)
        List<TripPlanDay> days = new ArrayList<>();
        TripPlanDay d1 = new TripPlanDay();
        d1.setDayNumber(1);
        d1.setDayType(null); // missing day type
        days.add(d1);

        TripPlanDay d2 = new TripPlanDay();
        d2.setDayNumber(2);
        d2.setDayType(ItineraryDayType.PORT_DAY); // port day without stop
        days.add(d2);

        ver.setDays(days);
        ver.setStops(new ArrayList<>());
        ver.setEntries(new ArrayList<>());

        List<PlanViolation> violations = versionService.validateVersionInternal(ver);
        assertFalse(violations.isEmpty());

        for (PlanViolation v : violations) {
            assertNotNull(v.getCode(), "Mỗi vi phạm phải có code");
            assertNotNull(v.getSeverity(), "Mỗi vi phạm phải có severity");
            assertNotNull(v.getEntityType(), "Mỗi vi phạm phải có entityType");
            assertNotNull(v.getFieldPath(), "Mỗi vi phạm phải có fieldPath để FE highlight: " + v.getCode());
            assertNotNull(v.getActionHint(), "Mỗi vi phạm phải có actionHint để FE điều hướng: " + v.getCode());
        }
    }

    @Test
    @DisplayName("P0: Cho phép chưa xác định tripType tại form tạo và tự động xác nhận khi có cảng đón/trả")
    void testTripTypeAutoConfirmationFromStops() {
        Trip trip = new Trip();
        trip.setDurationDays(2);
        trip.setTripType(null); // Chưa xác định tại form tạo

        Port portA = new Port();
        portA.setPortId(UUID.randomUUID());
        portA.setPortCode("VN-SGN");

        Port portB = new Port();
        portB.setPortId(UUID.randomUUID());
        portB.setPortCode("VN-DAD");

        TripPlanStop s1 = new TripPlanStop();
        s1.setClientKey("stop-1");
        s1.setPort(portA);
        s1.setArrivalDayNumber(1);
        s1.setDepartureDayNumber(1);
        s1.setIsEmbarkation(true);

        TripPlanStop s2 = new TripPlanStop();
        s2.setClientKey("stop-2");
        s2.setPort(portA); // Cùng cảng A -> ROUND_TRIP
        s2.setArrivalDayNumber(2);
        s2.setDepartureDayNumber(2);
        s2.setIsDisembarkation(true);

        TripScheduleVersion ver = new TripScheduleVersion();
        ver.setTrip(trip);
        ver.setDays(List.of(new TripPlanDay(), new TripPlanDay()));
        ver.setStops(List.of(s1, s2));
        ver.setEntries(new ArrayList<>());

        versionService.syncTripTypeFromStops(ver);

        assertEquals(ItineraryType.ROUND_TRIP, trip.getTripType(),
                "Khi cảng đón và cảng trả trùng nhau, tripType phải tự động xác nhận là ROUND_TRIP");

        // Đổi s2 sang cảng B khác cảng A -> ONE_WAY
        trip.setTripType(null);
        s2.setPort(portB);
        versionService.syncTripTypeFromStops(ver);

        assertEquals(ItineraryType.ONE_WAY, trip.getTripType(),
                "Khi cảng đón và cảng trả khác nhau, tripType phải tự động xác nhận là ONE_WAY");
    }

    @Test
    @DisplayName("P0: Báo lỗi vi phạm TRIP_TYPE_MISMATCH nếu kiểu tuyến chọn trước xung đột với cảng thực tế")
    void testTripTypeMismatchViolation() {
        Trip trip = new Trip();
        trip.setDurationDays(2);
        trip.setTripType(ItineraryType.ROUND_TRIP); // Planner chọn khứ hồi

        Port portA = new Port();
        portA.setPortId(UUID.randomUUID());
        portA.setPortCode("VN-SGN");

        Port portB = new Port();
        portB.setPortId(UUID.randomUUID());
        portB.setPortCode("VN-DAD");

        TripPlanStop s1 = new TripPlanStop();
        s1.setClientKey("stop-1");
        s1.setPort(portA);
        s1.setArrivalDayNumber(1);
        s1.setDepartureDayNumber(1);
        s1.setIsEmbarkation(true);

        TripPlanStop s2 = new TripPlanStop();
        s2.setClientKey("stop-2");
        s2.setPort(portB); // Khác cảng A!
        s2.setArrivalDayNumber(2);
        s2.setDepartureDayNumber(2);
        s2.setIsDisembarkation(true);

        TripScheduleVersion ver = new TripScheduleVersion();
        ver.setTrip(trip);
        ver.setDays(List.of(new TripPlanDay(), new TripPlanDay()));
        ver.setStops(List.of(s1, s2));
        ver.setEntries(new ArrayList<>());

        List<PlanViolation> violations = versionService.validateVersionInternal(ver);

        PlanViolation mismatch = violations.stream()
                .filter(v -> "TRIP_TYPE_MISMATCH".equals(v.getCode()))
                .findFirst()
                .orElse(null);

        assertNotNull(mismatch, "Phải phát hiện vi phạm TRIP_TYPE_MISMATCH");
        assertEquals("trip.tripType", mismatch.getFieldPath());
        assertEquals("MATCH_PORTS_OR_CHANGE_TYPE", mismatch.getActionHint());
    }

    @Test
    @DisplayName("BE-05: Validation không được ghi dữ liệu vào database (read-only)")
    void testBE05_ValidationDoesNotWriteData() {
        Trip trip = new Trip();
        trip.setDurationDays(2);
        trip.setTripType(null); // Chưa xác định

        Port portA = new Port();
        portA.setPortId(UUID.randomUUID());
        portA.setPortCode("VNHAP");

        TripPlanStop s1 = new TripPlanStop();
        s1.setClientKey("s1");
        s1.setPort(portA);
        s1.setArrivalDayNumber(1);
        s1.setDepartureDayNumber(1);
        s1.setIsEmbarkation(true);

        TripPlanStop s2 = new TripPlanStop();
        s2.setClientKey("s2");
        s2.setPort(portA);
        s2.setArrivalDayNumber(2);
        s2.setDepartureDayNumber(2);
        s2.setIsDisembarkation(true);

        TripScheduleVersion ver = new TripScheduleVersion();
        ver.setTrip(trip);
        ver.setDays(List.of(new TripPlanDay(), new TripPlanDay()));
        ver.setStops(List.of(s1, s2));
        ver.setEntries(new ArrayList<>());

        versionService.validateVersionInternal(ver);

        verify(tripRepository, never()).save(any(Trip.class));
    }

    @Test
    @DisplayName("BE-08: Shore excursion cùng ngày có end <= start vi phạm INVALID_EXCURSION_TIME")
    void testBE08_ShoreExcursionSameDayEndBeforeStartViolation() {
        Trip trip = new Trip();
        trip.setDurationDays(2);

        TripPlanStop stop = new TripPlanStop();
        stop.setClientKey("stop-1");
        stop.setArrivalDayNumber(1);
        stop.setDepartureDayNumber(1);
        stop.setPlannedArrivalTime(LocalTime.of(8, 0));
        stop.setPlannedDepartureTime(LocalTime.of(20, 0));
        stop.setAllAboardTime(LocalTime.of(19, 0));

        TripPlanEntry excursion = new TripPlanEntry();
        excursion.setClientKey("entry-excursion-err");
        excursion.setTitle("Tour xe bus đảo");
        excursion.setKind(TripPlanEntryKind.SHORE_EXCURSION);
        excursion.setStop(stop);
        excursion.setStartDayNumber(1);
        excursion.setPlannedStartTime(LocalTime.of(14, 0));
        excursion.setEndDayNumber(1);
        excursion.setPlannedEndTime(LocalTime.of(13, 0)); // end <= start!
        excursion.setMeetingPoint("Cổng số 2");

        TripScheduleVersion ver = new TripScheduleVersion();
        ver.setTrip(trip);
        ver.setDays(List.of(new TripPlanDay(), new TripPlanDay()));
        ver.setStops(List.of(stop));
        ver.setEntries(List.of(excursion));

        List<PlanViolation> violations = versionService.validateVersionInternal(ver);

        boolean hasTimingViolation = violations.stream()
                .anyMatch(v -> "INVALID_EXCURSION_TIME".equals(v.getCode()));
        assertTrue(hasTimingViolation, "Phải phát hiện vi phạm INVALID_EXCURSION_TIME khi end <= start");
    }

    @Test
    @DisplayName("BE-13: Hoạt động thiếu meetingPoint vi phạm MEETING_POINT_REQUIRED")
    void testBE13_MeetingPointRequiredViolation() {
        Trip trip = new Trip();
        trip.setDurationDays(2);

        TripPlanEntry entry = new TripPlanEntry();
        entry.setClientKey("entry-no-meeting-point");
        entry.setTitle("Lớp Yoga sáng");
        entry.setKind(TripPlanEntryKind.ONBOARD_ACTIVITY);
        entry.setStartDayNumber(1);
        entry.setPlannedStartTime(LocalTime.of(6, 0));
        entry.setEndDayNumber(1);
        entry.setPlannedEndTime(LocalTime.of(7, 0));
        entry.setMeetingPoint("   "); // Blank whitespace

        TripScheduleVersion ver = new TripScheduleVersion();
        ver.setTrip(trip);
        ver.setDays(List.of(new TripPlanDay(), new TripPlanDay()));
        ver.setStops(new ArrayList<>());
        ver.setEntries(List.of(entry));

        List<PlanViolation> violations = versionService.validateVersionInternal(ver);

        PlanViolation meetingPointViolation = violations.stream()
                .filter(v -> "MEETING_POINT_REQUIRED".equals(v.getCode()))
                .findFirst()
                .orElse(null);

        assertNotNull(meetingPointViolation, "Phải có vi phạm MEETING_POINT_REQUIRED");
        assertEquals("ERROR", meetingPointViolation.getSeverity());
        assertEquals("meetingPoint", meetingPointViolation.getField());
        assertEquals("SET_MEETING_POINT", meetingPointViolation.getActionHint());
    }

    @Test
    @DisplayName("BE-14: Hủy bản nháp dở dang (cancelDraft) thành công và ghi nhận lý do")
    void testBE14_CancelDraftSuccess() {
        UUID vId = UUID.randomUUID();
        Trip trip = new Trip();
        trip.setTripId(UUID.randomUUID());
        trip.setStatus(TripStatus.DRAFT);

        TripScheduleVersion ver = new TripScheduleVersion();
        ver.setVersionId(vId);
        ver.setVersionNumber(2);
        ver.setTrip(trip);
        ver.setStatus(TripScheduleVersionStatus.DRAFT);
        ver.setRevision(1L);

        when(versionRepository.findById(vId)).thenReturn(Optional.of(ver));
        when(versionRepository.save(any(TripScheduleVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        com.casms.trip.dto.request.VersionCancelRequest req = new com.casms.trip.dto.request.VersionCancelRequest();
        req.setExpectedRevision(1L);
        req.setReason("Hủy phương án này để làm lại từ đầu");

        TripScheduleVersionDetailResponse res = versionService.cancelDraftById(vId, req, actorId);

        assertEquals(TripScheduleVersionStatus.CANCELLED, res.getStatus());
        assertEquals("Hủy phương án này để làm lại từ đầu", res.getRejectionReason());
        verify(auditService).record(eq(actorId), eq("CANCEL_DRAFT"), eq("TripScheduleVersion"), eq(vId), eq("SUCCESS"), anyString());
    }

    @Test
    @DisplayName("BE-14: Hủy phiên bản khi không ở DRAFT ném INVALID_STATUS")
    void testBE14_CancelNonDraftFails() {
        UUID vId = UUID.randomUUID();
        Trip trip = new Trip();
        trip.setStatus(TripStatus.DRAFT);

        TripScheduleVersion ver = new TripScheduleVersion();
        ver.setVersionId(vId);
        ver.setTrip(trip);
        ver.setStatus(TripScheduleVersionStatus.IN_REVIEW);

        when(versionRepository.findById(vId)).thenReturn(Optional.of(ver));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> versionService.cancelDraftById(vId, new com.casms.trip.dto.request.VersionCancelRequest(), actorId));
        assertEquals("INVALID_STATUS", ex.getCode());
    }

    @Test
    @DisplayName("BE-06B: Chuyến nội địa (DOMESTIC) ghé cảng quốc tế vi phạm PORT_OUTSIDE_PLANNED_SCOPE")
    void testBE06B_DomesticTripRejectsForeignPort() {
        Trip trip = new Trip();
        trip.setDurationDays(2);
        trip.setPlannedScope("DOMESTIC");

        Port portForeign = new Port();
        portForeign.setPortName("Bangkok Port");
        portForeign.setCountryCode("TH"); // Thái Lan

        TripPlanStop stop = new TripPlanStop();
        stop.setClientKey("stop-th");
        stop.setPort(portForeign);
        stop.setArrivalDayNumber(1);
        stop.setDepartureDayNumber(1);

        TripScheduleVersion ver = new TripScheduleVersion();
        ver.setTrip(trip);
        ver.setDays(List.of(new TripPlanDay(), new TripPlanDay()));
        ver.setStops(List.of(stop));
        ver.setEntries(new ArrayList<>());

        List<PlanViolation> violations = versionService.validateVersionInternal(ver);

        PlanViolation scopeViolation = violations.stream()
                .filter(v -> "PORT_OUTSIDE_PLANNED_SCOPE".equals(v.getCode()))
                .findFirst()
                .orElse(null);

        assertNotNull(scopeViolation, "Phải có vi phạm PORT_OUTSIDE_PLANNED_SCOPE khi chuyến nội địa ghé cảng nước ngoài");
        assertEquals("ERROR", scopeViolation.getSeverity());
        assertEquals("REMOVE_OR_CHANGE_PORT", scopeViolation.getActionHint());
    }

    @Test
    @DisplayName("BE-07: Tham chiếu hoạt động nguồn không tồn tại vi phạm SOURCE_ACTIVITY_NOT_FOUND")
    void testBE07_SourceActivityNotFoundViolation() {
        Trip trip = new Trip();
        trip.setDurationDays(2);

        UUID nonExistentId = UUID.randomUUID();
        when(onboardActivityRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        TripPlanEntry entry = new TripPlanEntry();
        entry.setClientKey("entry-act-src");
        entry.setTitle("Tham gia show ảo thuật");
        entry.setKind(TripPlanEntryKind.ONBOARD_ACTIVITY);
        entry.setSourceActivityId(nonExistentId);
        entry.setStartDayNumber(1);
        entry.setPlannedStartTime(LocalTime.of(19, 0));
        entry.setEndDayNumber(1);
        entry.setPlannedEndTime(LocalTime.of(20, 0));
        entry.setMeetingPoint("Nhà hát tầng 5");

        TripScheduleVersion ver = new TripScheduleVersion();
        ver.setTrip(trip);
        ver.setDays(List.of(new TripPlanDay(), new TripPlanDay()));
        ver.setStops(new ArrayList<>());
        ver.setEntries(List.of(entry));

        List<PlanViolation> violations = versionService.validateVersionInternal(ver);

        PlanViolation srcViolation = violations.stream()
                .filter(v -> "SOURCE_ACTIVITY_NOT_FOUND".equals(v.getCode()))
                .findFirst()
                .orElse(null);

        assertNotNull(srcViolation, "Phải báo lỗi khi sourceActivityId không tồn tại");
        assertEquals("ERROR", srcViolation.getSeverity());
        assertEquals("SELECT_VALID_SOURCE", srcViolation.getActionHint());
    }

    @Test
    @DisplayName("BE-14/BE-DEC-03: Thu hồi phiên bản IN_REVIEW về DRAFT thành công")
    void testBE14_WithdrawVersionSuccessfully() {
        UUID tripId = UUID.randomUUID();
        Trip trip = new Trip();
        trip.setTripId(tripId);
        trip.setTripCode("TR-2026");
        trip.setStatus(TripStatus.DRAFT);
        trip.setDurationDays(2);

        UUID versionId = UUID.randomUUID();
        TripScheduleVersion ver = new TripScheduleVersion();
        ver.setVersionId(versionId);
        ver.setVersionNumber(1);
        ver.setTrip(trip);
        ver.setStatus(TripScheduleVersionStatus.IN_REVIEW);
        ver.setRevision(3L);
        ver.setSubmittedRevision(3L);
        ver.setSubmittedAt(Instant.now());
        ver.setDays(new ArrayList<>());
        ver.setStops(new ArrayList<>());
        ver.setEntries(new ArrayList<>());

        when(versionRepository.findById(versionId)).thenReturn(Optional.of(ver));
        when(versionRepository.save(any(TripScheduleVersion.class))).thenAnswer(i -> i.getArgument(0));

        com.casms.trip.dto.request.VersionWithdrawRequest req = new com.casms.trip.dto.request.VersionWithdrawRequest();
        req.setExpectedRevision(3L);
        req.setReason("Cần chỉnh sửa lại giờ tham quan");

        TripScheduleVersionDetailResponse res = versionService.withdrawById(versionId, req, actorId);

        assertNotNull(res);
        assertEquals(TripScheduleVersionStatus.DRAFT, res.getStatus());
        assertEquals(ver.getVersionId(), res.getVersionId());
        verify(versionRepository).save(argThat(v -> v.getStatus() == TripScheduleVersionStatus.DRAFT && v.getSubmittedRevision() == null));
        verify(auditService).record(eq(actorId), eq("WITHDRAW"), eq("TripScheduleVersion"), eq(versionId), eq("SUCCESS"), contains("Cần chỉnh sửa lại giờ tham quan"));
    }

    @Test
    @DisplayName("BE-14: Thu hồi thất bại khi phiên bản không ở trạng thái IN_REVIEW")
    void testBE14_WithdrawVersionFailsWhenNotReview() {
        UUID versionId = UUID.randomUUID();
        TripScheduleVersion ver = new TripScheduleVersion();
        ver.setVersionId(versionId);
        ver.setTrip(new Trip());
        ver.setStatus(TripScheduleVersionStatus.DRAFT); // already draft

        when(versionRepository.findById(versionId)).thenReturn(Optional.of(ver));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                versionService.withdrawById(versionId, null, actorId));

        assertEquals("INVALID_STATUS", ex.getCode());
    }

    @Test
    @DisplayName("BE-12/16: Tra cứu lịch sử Trip có thông tin người thực hiện mà không cần quyền USER_READ")
    void testBE12_TripHistoryQuery() {
        UUID tripId = UUID.randomUUID();
        Trip trip = new Trip();
        trip.setTripId(tripId);
        trip.setTripCode("TR-AUDIT-1");

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        com.casms.audit.AuditLog logEntry = new com.casms.audit.AuditLog();
        logEntry.setAuditId(UUID.randomUUID());
        logEntry.setActorUserId(actorId);
        logEntry.setAction("PATCH_DRAFT");
        logEntry.setEntityType("Trip");
        logEntry.setEntityId(tripId);
        logEntry.setResult("SUCCESS");
        logEntry.setDetail("Patched Trip TR-AUDIT-1");
        logEntry.setOccurredAt(Instant.now());

        when(auditRepository.findByEntityIdOrderByOccurredAtDesc(tripId)).thenReturn(List.of(logEntry));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));

        List<TripAuditResponse> history = tripService.getTripHistory(tripId);

        assertNotNull(history);
        assertEquals(1, history.size());
        assertEquals("PATCH_DRAFT", history.get(0).getAction());
        assertEquals("planner@casms.com", history.get(0).getActorName());
        assertEquals(actorId, history.get(0).getActorUserId());
    }

    // =========================================================================
    // BE-13 & BE-07: Additional Validation Tests (Meeting point, source checks)
    // =========================================================================

    @Test
    @DisplayName("BE-13: Khung phục vụ (SERVING_WINDOW) KHÔNG bắt buộc có điểm tập trung (meetingPoint)")
    void testBE13_ServingWindowDoesNotRequireMeetingPoint() {
        Trip trip = new Trip();
        trip.setDurationDays(2);

        TripPlanEntry entry = new TripPlanEntry();
        entry.setClientKey("entry-serving");
        entry.setTitle("Buffet sáng nhà hàng Horizon");
        entry.setKind(TripPlanEntryKind.SERVING_WINDOW);
        entry.setStartDayNumber(1);
        entry.setPlannedStartTime(LocalTime.of(6, 30));
        entry.setEndDayNumber(1);
        entry.setPlannedEndTime(LocalTime.of(10, 0));
        entry.setMeetingPoint(null); // Không có meeting point
        entry.setLocationText(null); // Không có location text

        TripScheduleVersion ver = new TripScheduleVersion();
        ver.setTrip(trip);
        ver.setDays(List.of(new TripPlanDay(), new TripPlanDay()));
        ver.setStops(new ArrayList<>());
        ver.setEntries(List.of(entry));

        List<PlanViolation> violations = versionService.validateVersionInternal(ver);

        boolean hasMeetingPointViolation = violations.stream()
                .anyMatch(v -> "MEETING_POINT_REQUIRED".equals(v.getCode()));
        assertFalse(hasMeetingPointViolation, "Khung phục vụ không được báo lỗi MEETING_POINT_REQUIRED");
    }

    @Test
    @DisplayName("BE-07: Tour tham quan nguồn không khớp cảng ghé báo lỗi SHORE_EXCURSION_PORT_MISMATCH")
    void testBE07_ShoreExcursionPortMismatchViolation() {
        Trip trip = new Trip();
        trip.setDurationDays(2);

        Port portA = new Port();
        portA.setPortId(UUID.randomUUID());
        portA.setPortCode("VNSGN");
        portA.setPortName("Cảng Sài Gòn");

        Port portB = new Port();
        portB.setPortId(UUID.randomUUID());
        portB.setPortCode("VNDAD");
        portB.setPortName("Cảng Đà Nẵng");

        TripPlanStop stop = new TripPlanStop();
        stop.setClientKey("stop-sgn");
        stop.setPort(portA);
        stop.setArrivalDayNumber(1);
        stop.setDepartureDayNumber(1);

        UUID sourceExcursionId = UUID.randomUUID();
        com.casms.activity.entity.ShoreExcursion masterTour = new com.casms.activity.entity.ShoreExcursion();
        masterTour.setShoreExcursionId(sourceExcursionId);
        masterTour.setExcursionName("Tour Bà Nà Hills");
        masterTour.setPort(portB); // Cảng Đà Nẵng != Cảng Sài Gòn
        masterTour.setStatus(com.casms.activity.enums.ShoreExcursionStatus.OPEN);

        when(shoreExcursionRepository.findById(sourceExcursionId)).thenReturn(Optional.of(masterTour));

        TripPlanEntry entry = new TripPlanEntry();
        entry.setClientKey("entry-tour-mismatch");
        entry.setTitle("Tour Bà Nà Hills");
        entry.setKind(TripPlanEntryKind.SHORE_EXCURSION);
        entry.setStop(stop);
        entry.setSourceExcursionId(sourceExcursionId);
        entry.setStartDayNumber(1);
        entry.setPlannedStartTime(LocalTime.of(8, 0));
        entry.setEndDayNumber(1);
        entry.setPlannedEndTime(LocalTime.of(12, 0));
        entry.setMeetingPoint("Cửa ga cáp treo");

        TripScheduleVersion ver = new TripScheduleVersion();
        ver.setTrip(trip);
        ver.setDays(List.of(new TripPlanDay(), new TripPlanDay()));
        ver.setStops(List.of(stop));
        ver.setEntries(List.of(entry));

        List<PlanViolation> violations = versionService.validateVersionInternal(ver);

        PlanViolation mismatch = violations.stream()
                .filter(v -> "SHORE_EXCURSION_PORT_MISMATCH".equals(v.getCode()))
                .findFirst().orElse(null);
        assertNotNull(mismatch, "Phải báo lỗi SHORE_EXCURSION_PORT_MISMATCH khi tour nguồn thuộc cảng khác");
        assertEquals("ERROR", mismatch.getSeverity());
    }

    @Test
    @DisplayName("BE-07: Tour tham quan nguồn bị CANCELLED báo lỗi SOURCE_EXCURSION_INACTIVE")
    void testBE07_SourceExcursionInactiveViolation() {
        Trip trip = new Trip();
        trip.setDurationDays(2);

        Port portA = new Port();
        portA.setPortId(UUID.randomUUID());
        portA.setPortCode("VNSGN");

        TripPlanStop stop = new TripPlanStop();
        stop.setClientKey("stop-sgn");
        stop.setPort(portA);
        stop.setArrivalDayNumber(1);
        stop.setDepartureDayNumber(1);

        UUID sourceExcursionId = UUID.randomUUID();
        com.casms.activity.entity.ShoreExcursion cancelledTour = new com.casms.activity.entity.ShoreExcursion();
        cancelledTour.setShoreExcursionId(sourceExcursionId);
        cancelledTour.setExcursionName("Tour Củ Chi ngừng phục vụ");
        cancelledTour.setPort(portA);
        cancelledTour.setStatus(com.casms.activity.enums.ShoreExcursionStatus.CANCELLED);

        when(shoreExcursionRepository.findById(sourceExcursionId)).thenReturn(Optional.of(cancelledTour));

        TripPlanEntry entry = new TripPlanEntry();
        entry.setClientKey("entry-tour-cancelled");
        entry.setTitle("Tour Củ Chi");
        entry.setKind(TripPlanEntryKind.SHORE_EXCURSION);
        entry.setStop(stop);
        entry.setSourceExcursionId(sourceExcursionId);
        entry.setStartDayNumber(1);
        entry.setPlannedStartTime(LocalTime.of(8, 0));
        entry.setEndDayNumber(1);
        entry.setPlannedEndTime(LocalTime.of(12, 0));
        entry.setMeetingPoint("Cổng địa đạo");

        TripScheduleVersion ver = new TripScheduleVersion();
        ver.setTrip(trip);
        ver.setDays(List.of(new TripPlanDay(), new TripPlanDay()));
        ver.setStops(List.of(stop));
        ver.setEntries(List.of(entry));

        List<PlanViolation> violations = versionService.validateVersionInternal(ver);

        PlanViolation inactive = violations.stream()
                .filter(v -> "SOURCE_EXCURSION_INACTIVE".equals(v.getCode()))
                .findFirst().orElse(null);
        assertNotNull(inactive, "Phải báo lỗi SOURCE_EXCURSION_INACTIVE khi tour nguồn bị CANCELLED");
    }

    @Test
    @DisplayName("BE-06B: Kiểm tra vi phạm miền địa lý (PORT_OUTSIDE_DOMESTIC_REGION) và quốc gia (PORT_OUTSIDE_COUNTRY_SCOPE)")
    void testBE06B_DomesticRegionAndCountryScopeViolations() {
        // Case 1: Chuyến nội địa Miền Bắc nhưng cảng thuộc Miền Nam (VNSGN)
        Trip tripDomestic = new Trip();
        tripDomestic.setDurationDays(2);
        tripDomestic.setPlannedScope("DOMESTIC");
        tripDomestic.setDomesticRegions(List.of("BAC_BO"));

        Port portSGN = new Port();
        portSGN.setPortId(UUID.randomUUID());
        portSGN.setPortCode("VNSGN");
        portSGN.setCountryCode("VN");

        TripPlanStop stopDomestic = new TripPlanStop();
        stopDomestic.setClientKey("stop-dom");
        stopDomestic.setPort(portSGN);
        stopDomestic.setArrivalDayNumber(1);
        stopDomestic.setDepartureDayNumber(1);

        TripScheduleVersion verDom = new TripScheduleVersion();
        verDom.setTrip(tripDomestic);
        verDom.setDays(List.of(new TripPlanDay(), new TripPlanDay()));
        verDom.setStops(List.of(stopDomestic));
        verDom.setEntries(new ArrayList<>());

        List<PlanViolation> violationsDom = versionService.validateVersionInternal(verDom);
        boolean hasRegionViolation = violationsDom.stream()
                .anyMatch(v -> "PORT_OUTSIDE_DOMESTIC_REGION".equals(v.getCode()));
        assertTrue(hasRegionViolation, "Phải báo lỗi PORT_OUTSIDE_DOMESTIC_REGION khi cảng Sài Gòn không thuộc miền Bắc");

        // Case 2: Chuyến quốc tế giới hạn VN, TH nhưng ghé cảng Nhật Bản (JP)
        Trip tripIntl = new Trip();
        tripIntl.setDurationDays(2);
        tripIntl.setPlannedScope("INTERNATIONAL");
        tripIntl.setCountryCodes(List.of("VN", "TH"));

        Port portTokyo = new Port();
        portTokyo.setPortId(UUID.randomUUID());
        portTokyo.setPortCode("JPTYO");
        portTokyo.setCountryCode("JP");

        TripPlanStop stopIntl = new TripPlanStop();
        stopIntl.setClientKey("stop-intl");
        stopIntl.setPort(portTokyo);
        stopIntl.setArrivalDayNumber(1);
        stopIntl.setDepartureDayNumber(1);

        TripScheduleVersion verIntl = new TripScheduleVersion();
        verIntl.setTrip(tripIntl);
        verIntl.setDays(List.of(new TripPlanDay(), new TripPlanDay()));
        verIntl.setStops(List.of(stopIntl));
        verIntl.setEntries(new ArrayList<>());

        List<PlanViolation> violationsIntl = versionService.validateVersionInternal(verIntl);
        boolean hasCountryViolation = violationsIntl.stream()
                .anyMatch(v -> "PORT_OUTSIDE_COUNTRY_SCOPE".equals(v.getCode()));
        assertTrue(hasCountryViolation, "Phải báo lỗi PORT_OUTSIDE_COUNTRY_SCOPE khi cảng Tokyo không nằm trong phạm vi quốc gia đã đăng ký");
    }

    @Test
    @DisplayName("BE-12/14: cloneTrip từ chối clone phiên bản bị CANCELLED")
    void testCloneTrip_CannotCloneCancelledVersion() {
        UUID tripId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();

        Trip trip = new Trip();
        trip.setTripId(tripId);
        trip.setTripCode("TR-CANCEL-1");
        trip.setStatus(TripStatus.READY);

        TripScheduleVersion cancelledVer = new TripScheduleVersion();
        cancelledVer.setVersionId(versionId);
        cancelledVer.setTrip(trip);
        cancelledVer.setStatus(TripScheduleVersionStatus.CANCELLED);

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(cancelledVer));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                tripService.cloneTrip(tripId, versionId, actorId));
        assertEquals("CANNOT_CLONE_CANCELLED_VERSION", ex.getCode());
    }

    @Test
    @DisplayName("BE-12/14: createNextDraftVersion tính maxVersionNumber + 1 ngay cả khi có phiên bản bị CANCELLED")
    void testCreateNextVersion_HandlesCancelledVersionsWithoutUniqueCollision() {
        UUID tripId = UUID.randomUUID();
        Trip trip = new Trip();
        trip.setTripId(tripId);
        trip.setTripCode("TR-MULTI-VER");
        trip.setStatus(TripStatus.READY);

        TripScheduleVersion v1Published = new TripScheduleVersion();
        v1Published.setVersionId(UUID.randomUUID());
        v1Published.setVersionNumber(1);
        v1Published.setTrip(trip);
        v1Published.setStatus(TripScheduleVersionStatus.PUBLISHED);
        v1Published.setDays(new ArrayList<>());
        v1Published.setStops(new ArrayList<>());
        v1Published.setEntries(new ArrayList<>());

        TripScheduleVersion v2Cancelled = new TripScheduleVersion();
        v2Cancelled.setVersionId(UUID.randomUUID());
        v2Cancelled.setVersionNumber(2);
        v2Cancelled.setTrip(trip);
        v2Cancelled.setStatus(TripScheduleVersionStatus.CANCELLED);
        v2Cancelled.setDays(new ArrayList<>());
        v2Cancelled.setStops(new ArrayList<>());
        v2Cancelled.setEntries(new ArrayList<>());

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(versionRepository.findByTrip_TripIdOrderByVersionNumberAsc(tripId))
                .thenReturn(List.of(v1Published, v2Cancelled));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(versionRepository.save(any(TripScheduleVersion.class))).thenAnswer(i -> {
            TripScheduleVersion v = i.getArgument(0);
            v.setVersionId(UUID.randomUUID());
            return v;
        });

        TripScheduleVersionDetailResponse nextVerRes = versionService.createNextDraftVersion(tripId, actorId);

        assertNotNull(nextVerRes);
        // Phiên bản tiếp theo phải là 3 (max(1, 2) + 1), không được là 2 (tránh uq_trip_version trùng lặp)
        assertEquals(3, nextVerRes.getVersionNumber());
        assertEquals(TripScheduleVersionStatus.DRAFT, nextVerRes.getStatus());
    }

    // =========================================================================
    // Deck & Cabin Catalog and Physical Location Tests
    // =========================================================================

    @Test
    @DisplayName("Deck/Cabin/Port API: Cung cấp API chọn boong tàu, phòng cabin và cảng ghé")
    void testCatalog_GetDecksAndCabinsAndPorts() {
        UUID shipId = UUID.randomUUID();
        com.casms.ship.entity.Ship ship = new com.casms.ship.entity.Ship();
        ship.setShipId(shipId);
        ship.setShipName("OceVie Paradise of the Seas");

        Deck deck2 = new Deck();
        deck2.setDeckId(UUID.randomUUID());
        deck2.setDeckNumber(2);
        deck2.setDeckName("Lotus Wellness & Spa Deck");
        deck2.setShip(ship);

        Cabin cabin202 = new Cabin();
        cabin202.setCabinId(UUID.randomUUID());
        cabin202.setCabinNumber("202");
        cabin202.setRoomName("Phòng Massage Đá Nóng VIP");
        cabin202.setCabinType(com.casms.ship.enums.CabinType.SUITE);
        cabin202.setCapacity(15);
        cabin202.setDeck(deck2);
        cabin202.setShip(ship);
        cabin202.setLocationCode("MIDSHIP-PORT");

        Port portSGN = new Port();
        portSGN.setPortId(UUID.randomUUID());
        portSGN.setPortCode("VNSGN");
        portSGN.setPortName("Saigon Hiep Phuoc Cruise Port");
        portSGN.setCountryCode("VN");
        portSGN.setTimeZoneId("Asia/Ho_Chi_Minh");
        portSGN.setStatus(com.casms.ship.enums.PortStatus.ACTIVE);
        portSGN.setLatitude(java.math.BigDecimal.valueOf(10.6333));
        portSGN.setLongitude(java.math.BigDecimal.valueOf(106.7500));

        when(deckRepository.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(deck2));
        when(cabinRepository.findAll()).thenReturn(List.of(cabin202));
        when(portRepository.findAll()).thenReturn(List.of(portSGN));

        // 1. Test getSourceDecks
        ApiResponse<List<PlanningSourceDeckResponse>> deckRes = catalogController.getSourceDecks();
        assertNotNull(deckRes);
        assertEquals(1, deckRes.getData().size());
        assertEquals(2, deckRes.getData().get(0).getDeckNumber());
        assertEquals("Lotus Wellness & Spa Deck", deckRes.getData().get(0).getDeckName());
        assertEquals("OceVie Paradise of the Seas", deckRes.getData().get(0).getShipName());

        // 2. Test getSourceCabins
        ApiResponse<List<PlanningSourceCabinResponse>> cabinRes = catalogController.getSourceCabins();
        assertNotNull(cabinRes);
        assertEquals(1, cabinRes.getData().size());
        assertEquals("202", cabinRes.getData().get(0).getCabinNumber());
        assertEquals("Phòng Massage Đá Nóng VIP", cabinRes.getData().get(0).getRoomName());
        assertEquals("Phòng 202 - Phòng Massage Đá Nóng VIP", cabinRes.getData().get(0).getDisplayName());
        assertEquals(2, cabinRes.getData().get(0).getDeckNumber());
        assertEquals("MIDSHIP-PORT", cabinRes.getData().get(0).getLocationCode());

        // 3. Test getSourcePorts
        ApiResponse<List<PlanningSourcePortResponse>> portRes = catalogController.getSourcePorts();
        assertNotNull(portRes);
        assertEquals(1, portRes.getData().size());
        assertEquals("VNSGN", portRes.getData().get(0).getPortCode());
        assertEquals("Saigon Hiep Phuoc Cruise Port", portRes.getData().get(0).getPortName());

        // 4. Test getActivityCategories catalog
        ApiResponse<List<ActivityCategoryResponse>> catRes = catalogController.getActivityCategories();
        assertNotNull(catRes);
        assertEquals(5, catRes.getData().size());
        assertTrue(catRes.getData().stream().anyMatch(c -> c.getCode() == com.casms.activity.enums.OnboardActivityCategory.ENTERTAINMENT));
        assertTrue(catRes.getData().stream().anyMatch(c -> c.getCode() == com.casms.activity.enums.OnboardActivityCategory.SPORTS_WELLNESS));
        assertTrue(catRes.getData().stream().anyMatch(c -> c.getCode() == com.casms.activity.enums.OnboardActivityCategory.FAMILY_KIDS));
        assertTrue(catRes.getData().stream().anyMatch(c -> c.getCode() == com.casms.activity.enums.OnboardActivityCategory.SPECIAL_EVENTS));
        assertTrue(catRes.getData().stream().anyMatch(c -> c.getCode() == com.casms.activity.enums.OnboardActivityCategory.WORKSHOPS_CLASSES));
    }

    @Test
    @DisplayName("Lưu nháp lịch trình: Lưu thành công Deck/Cabin/Category và định dạng phân tách không gian FUNCTIONAL_ROOM")
    void testSaveDraft_SavesDeckAndCabinAndSupportsCopyOnSelect() {
        UUID tripId = UUID.randomUUID();
        Trip trip = new Trip();
        trip.setTripId(tripId);
        trip.setTripCode("TR-DECK-CABIN");
        trip.setStatus(TripStatus.READY);
        trip.setDurationDays(1);

        UUID versionId = UUID.randomUUID();
        TripScheduleVersion ver = new TripScheduleVersion();
        ver.setVersionId(versionId);
        ver.setVersionNumber(1);
        ver.setTrip(trip);
        ver.setStatus(TripScheduleVersionStatus.DRAFT);
        ver.setRevision(1L);

        TripPlanDay day1 = new TripPlanDay();
        day1.setDayId(UUID.randomUUID());
        day1.setDayNumber(1);
        day1.setDayType(ItineraryDayType.SEA_DAY);
        ver.setDays(new ArrayList<>(List.of(day1)));
        ver.setStops(new ArrayList<>());
        ver.setEntries(new ArrayList<>());

        UUID deckId = UUID.randomUUID();
        Deck deck = new Deck();
        deck.setDeckId(deckId);
        deck.setDeckNumber(2);
        deck.setDeckName("Lotus Wellness & Spa Deck");

        UUID cabinId = UUID.randomUUID();
        Cabin cabin = new Cabin();
        cabin.setCabinId(cabinId);
        cabin.setCabinNumber("202");
        cabin.setRoomName("Phòng Massage Đá Nóng VIP");
        cabin.setDeck(deck);

        when(versionRepository.findByTrip_TripIdAndVersionNumber(tripId, 1)).thenReturn(Optional.of(ver));
        when(versionRepository.save(any(TripScheduleVersion.class))).thenAnswer(i -> i.getArgument(0));
        when(deckRepository.findById(deckId)).thenReturn(Optional.of(deck));
        when(cabinRepository.findById(cabinId)).thenReturn(Optional.of(cabin));

        // Tạo entry DTO có deckId, cabinId và category rõ ràng
        com.casms.trip.dto.request.TripPlanEntryDraftDto entryDto = new com.casms.trip.dto.request.TripPlanEntryDraftDto();
        entryDto.setClientKey("entry-spa-202");
        entryDto.setDayNumber(1);
        entryDto.setKind(TripPlanEntryKind.ONBOARD_ACTIVITY);
        entryDto.setCategory(com.casms.activity.enums.OnboardActivityCategory.SPORTS_WELLNESS);
        entryDto.setTitle("Massage đá nóng Lotus Spa");
        entryDto.setStartDayNumber(1);
        entryDto.setPlannedStartTime(LocalTime.of(9, 0));
        entryDto.setEndDayNumber(1);
        entryDto.setPlannedEndTime(LocalTime.of(10, 30));
        entryDto.setDeckId(deckId);
        entryDto.setCabinId(cabinId);

        com.casms.trip.dto.request.ScheduleDraftSaveRequest saveReq =
                new com.casms.trip.dto.request.ScheduleDraftSaveRequest();
        saveReq.setExpectedRevision(1L);

        com.casms.trip.dto.request.TripPlanDayDraftDto dayDto = new com.casms.trip.dto.request.TripPlanDayDraftDto();
        dayDto.setDayNumber(1);
        dayDto.setDayType(ItineraryDayType.SEA_DAY);
        saveReq.setDays(List.of(dayDto));
        saveReq.setStops(new ArrayList<>());
        saveReq.setEntries(List.of(entryDto));

        TripScheduleVersionDetailResponse res = versionService.saveDraft(tripId, 1, saveReq, actorId);

        assertNotNull(res);
        assertEquals(1, res.getEntries().size());
        TripPlanEntryResponse savedEntry = res.getEntries().get(0);
        assertEquals(deckId, savedEntry.getDeckId());
        assertEquals(2, savedEntry.getDeckNumber());
        assertEquals("Lotus Wellness & Spa Deck", savedEntry.getDeckName());
        assertEquals(cabinId, savedEntry.getCabinId());
        assertEquals("202", savedEntry.getCabinNumber());
        assertEquals("Phòng Massage Đá Nóng VIP", savedEntry.getRoomName());
        assertEquals("FUNCTIONAL_ROOM", savedEntry.getLocationType());
        assertEquals(com.casms.activity.enums.OnboardActivityCategory.SPORTS_WELLNESS, savedEntry.getCategory());
        assertEquals("Thể thao & Sức khỏe", savedEntry.getCategoryName());
        assertTrue(savedEntry.getDisplayLocation().contains("Phòng 202"));
    }

    @Test
    @DisplayName("Kế thừa tự động: Tự động copy category và location từ OnboardActivity nguồn khi lập lịch")
    void testCopyOnSelect_InheritsCategoryAndLocationFromSource() {
        UUID tripId = UUID.randomUUID();
        Trip trip = new Trip();
        trip.setTripId(tripId);
        trip.setStatus(TripStatus.READY);
        trip.setDurationDays(1);

        UUID versionId = UUID.randomUUID();
        TripScheduleVersion ver = new TripScheduleVersion();
        ver.setVersionId(versionId);
        ver.setVersionNumber(1);
        ver.setTrip(trip);
        ver.setStatus(TripScheduleVersionStatus.DRAFT);
        ver.setRevision(1L);

        TripPlanDay day1 = new TripPlanDay();
        day1.setDayId(UUID.randomUUID());
        day1.setDayNumber(1);
        day1.setDayType(ItineraryDayType.SEA_DAY);
        ver.setDays(new ArrayList<>(List.of(day1)));
        ver.setStops(new ArrayList<>());
        ver.setEntries(new ArrayList<>());

        UUID srcActId = UUID.randomUUID();
        Deck deck10 = new Deck();
        deck10.setDeckId(UUID.randomUUID());
        deck10.setDeckNumber(10);
        deck10.setDeckName("Sun Deck");

        OnboardActivity srcAct = new OnboardActivity();
        srcAct.setOnboardActivityId(srcActId);
        srcAct.setActivityName("Lớp Yoga & Thiền Đón Bình Minh");
        srcAct.setCategory(com.casms.activity.enums.OnboardActivityCategory.SPORTS_WELLNESS);
        srcAct.setDeck(deck10);
        srcAct.setLocation("Boong ngắm cảnh phía mũi");

        when(versionRepository.findByTrip_TripIdAndVersionNumber(tripId, 1)).thenReturn(Optional.of(ver));
        when(versionRepository.save(any(TripScheduleVersion.class))).thenAnswer(i -> i.getArgument(0));
        when(onboardActivityRepository.findById(srcActId)).thenReturn(Optional.of(srcAct));

        // DTO không truyền deckId, cabinId hay category
        com.casms.trip.dto.request.TripPlanEntryDraftDto entryDto = new com.casms.trip.dto.request.TripPlanEntryDraftDto();
        entryDto.setClientKey("entry-yoga-inherited");
        entryDto.setDayNumber(1);
        entryDto.setKind(TripPlanEntryKind.ONBOARD_ACTIVITY);
        entryDto.setSourceActivityId(srcActId);
        entryDto.setTitle("Lớp Yoga & Thiền Đón Bình Minh");
        entryDto.setStartDayNumber(1);
        entryDto.setPlannedStartTime(LocalTime.of(6, 0));
        entryDto.setEndDayNumber(1);
        entryDto.setPlannedEndTime(LocalTime.of(7, 15));

        com.casms.trip.dto.request.ScheduleDraftSaveRequest saveReq =
                new com.casms.trip.dto.request.ScheduleDraftSaveRequest();
        saveReq.setExpectedRevision(1L);

        com.casms.trip.dto.request.TripPlanDayDraftDto dayDto = new com.casms.trip.dto.request.TripPlanDayDraftDto();
        dayDto.setDayNumber(1);
        dayDto.setDayType(ItineraryDayType.SEA_DAY);
        saveReq.setDays(List.of(dayDto));
        saveReq.setStops(new ArrayList<>());
        saveReq.setEntries(List.of(entryDto));

        TripScheduleVersionDetailResponse res = versionService.saveDraft(tripId, 1, saveReq, actorId);

        assertNotNull(res);
        TripPlanEntryResponse savedEntry = res.getEntries().get(0);
        assertEquals(deck10.getDeckId(), savedEntry.getDeckId());
        assertEquals(10, savedEntry.getDeckNumber());
        assertEquals("VENUE_OPEN_AREA", savedEntry.getLocationType());
        assertEquals(com.casms.activity.enums.OnboardActivityCategory.SPORTS_WELLNESS, savedEntry.getCategory());
        assertEquals("Thể thao & Sức khỏe", savedEntry.getCategoryName());
        assertEquals("🧘", savedEntry.getCategoryIcon());
    }
}
