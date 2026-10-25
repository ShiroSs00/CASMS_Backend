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
import com.casms.ship.entity.Destination;
import com.casms.ship.entity.Port;
import com.casms.ship.enums.ItineraryDayType;
import com.casms.ship.enums.ItineraryType;
import com.casms.ship.repository.DestinationRepository;
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

    private TripService tripService;
    private TripScheduleVersionService versionService;
    private IdempotencyService idempotencyService;
    private TripCatalogController catalogController;

    private UUID actorId;
    private User actor;

    @BeforeEach
    void setUp() {
        tripService = new TripService(tripRepository, versionRepository, dayRepository,
                userRepository, businessCodeGenerator, auditService);

        versionService = new TripScheduleVersionService(tripRepository, versionRepository,
                dayRepository, stopRepository, entryRepository, mock(com.casms.ship.repository.PortRepository.class),
                destinationRepository, userRepository, mock(com.casms.audit.AuditRepository.class), auditService);

        idempotencyService = new IdempotencyService(idempotencyRepository, new ObjectMapper());

        catalogController = new TripCatalogController(destinationRepository,
                onboardActivityRepository, shoreExcursionRepository, serviceRepository);

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
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));
        when(versionRepository.save(any(TripScheduleVersion.class))).thenAnswer(inv -> inv.getArgument(0));

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

        versionService.validateVersionInternal(ver);

        assertEquals(ItineraryType.ROUND_TRIP, trip.getTripType(),
                "Khi cảng đón và cảng trả trùng nhau, tripType phải tự động xác nhận là ROUND_TRIP");

        // Đổi s2 sang cảng B khác cảng A -> ONE_WAY
        trip.setTripType(null);
        s2.setPort(portB);
        versionService.validateVersionInternal(ver);

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
}
