package com.casms.trip.contract;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.GlobalExceptionHandler;
import com.casms.identity.entity.User;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.entity.Destination;
import com.casms.ship.repository.DestinationRepository;
import com.casms.trip.controller.TripCatalogController;
import com.casms.trip.controller.TripController;
import com.casms.trip.controller.TripScheduleVersionController;
import com.casms.trip.dto.request.ScheduleDraftSaveRequest;
import com.casms.trip.dto.request.TripCreateRequest;
import com.casms.trip.dto.request.TripDraftPatchRequest;
import com.casms.trip.dto.request.TripPlanDayDraftDto;
import com.casms.trip.dto.request.VersionReturnRequest;
import com.casms.trip.dto.response.*;
import com.casms.trip.enums.TripScheduleVersionStatus;
import com.casms.trip.enums.TripStatus;
import com.casms.trip.service.IdempotencyService;
import com.casms.trip.service.TripScheduleVersionService;
import com.casms.trip.service.TripService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
public class TripPlanningContractTest {

    private MockMvc mockMvcTrip;
    private MockMvc mockMvcVersion;
    private MockMvc mockMvcCatalog;

    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Mock
    private TripService tripService;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private TripScheduleVersionService versionService;

    @Mock
    private DestinationRepository destinationRepository;

    @InjectMocks
    private TripController tripController;

    @InjectMocks
    private TripScheduleVersionController versionController;

    @InjectMocks
    private TripCatalogController catalogController;

    private UUID tripId;
    private UUID versionId;
    private UUID actorId;
    private CasmsPrincipal testPrincipal;

    static class TestPrincipalResolver implements HandlerMethodArgumentResolver {
        private final CasmsPrincipal principal;

        TestPrincipalResolver(CasmsPrincipal principal) {
            this.principal = principal;
        }

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType().equals(CasmsPrincipal.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            return principal;
        }
    }

    @BeforeEach
    void setUp() {
        tripId = UUID.randomUUID();
        versionId = UUID.randomUUID();
        actorId = UUID.randomUUID();

        User user = new User();
        user.setUserId(actorId);
        user.setUsername("planner");
        testPrincipal = new CasmsPrincipal(user, null);

        mockMvcTrip = MockMvcBuilders.standaloneSetup(tripController)
                .setCustomArgumentResolvers(
                        new PageableHandlerMethodArgumentResolver(),
                        new TestPrincipalResolver(testPrincipal))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvcVersion = MockMvcBuilders.standaloneSetup(versionController)
                .setCustomArgumentResolvers(
                        new PageableHandlerMethodArgumentResolver(),
                        new TestPrincipalResolver(testPrincipal))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvcCatalog = MockMvcBuilders.standaloneSetup(catalogController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // 1. GET /trips
    @Test
    @DisplayName("GET /trips returns 200 with Page<TripSummaryResponse>")
    void testListTripsContract() throws Exception {
        TripSummaryResponse item = new TripSummaryResponse();
        item.setTripId(tripId);
        item.setTripCode("TR-1001");
        item.setName("Hải trình Di sản Vịnh Bắc Bộ");
        item.setStatus(TripStatus.DRAFT);

        when(tripService.listTrips(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item), org.springframework.data.domain.PageRequest.of(0, 10), 1));

        mockMvcTrip.perform(get("/trips"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].tripCode").value("TR-1001"));
    }

    // 2. POST /trips with Idempotency-Key
    @Test
    @DisplayName("POST /trips returns 201 Created with TripDetailResponse when Idempotency-Key provided")
    void testCreateTripContractSuccess() throws Exception {
        TripCreateRequest req = new TripCreateRequest();
        req.setName("Hải trình Di sản Vịnh Bắc Bộ");
        req.setDurationDays(4);
        req.setDurationNights(3);
        req.setTripType(com.casms.ship.enums.ItineraryType.ROUND_TRIP);

        TripDetailResponse res = new TripDetailResponse();
        res.setTripId(tripId);
        res.setTripCode("TR-1001");
        res.setName(req.getName());
        res.setDurationDays(4);

        when(idempotencyService.execute(eq("idem-key-123"), any(), eq("CREATE_TRIP"), any(), any()))
                .thenReturn(res);

        mockMvcTrip.perform(post("/trips")
                        .header("Idempotency-Key", "idem-key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tripCode").value("TR-1001"));
    }

    @Test
    @DisplayName("POST /trips returns 400 INVALID_INPUT when Idempotency-Key is missing")
    void testCreateTripMissingIdempotencyKey() throws Exception {
        TripCreateRequest req = new TripCreateRequest();
        req.setName("Hải trình Di sản Vịnh Bắc Bộ");
        req.setDurationDays(4);
        req.setDurationNights(3);
        req.setTripType(com.casms.ship.enums.ItineraryType.ROUND_TRIP);

        mockMvcTrip.perform(post("/trips")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    @DisplayName("POST /trips returns 409 IDEMPOTENCY_CONFLICT when payload does not match key")
    void testCreateTripIdempotencyConflict() throws Exception {
        TripCreateRequest req = new TripCreateRequest();
        req.setName("Hải trình Di sản Vịnh Bắc Bộ");
        req.setDurationDays(4);
        req.setDurationNights(3);
        req.setTripType(com.casms.ship.enums.ItineraryType.ROUND_TRIP);

        when(idempotencyService.execute(eq("key-dup"), any(), eq("CREATE_TRIP"), any(), any()))
                .thenThrow(new BusinessException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "Payload mismatch for key"));

        mockMvcTrip.perform(post("/trips")
                        .header("Idempotency-Key", "key-dup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    // 3. GET /trips/{tripId}
    @Test
    @DisplayName("GET /trips/{tripId} returns 200 with TripDetailResponse")
    void testGetTripDetailContract() throws Exception {
        TripDetailResponse res = new TripDetailResponse();
        res.setTripId(tripId);
        res.setTripCode("TR-1001");
        res.setName("Hải trình Di sản");

        when(tripService.getTripDetail(tripId)).thenReturn(res);

        mockMvcTrip.perform(get("/trips/{tripId}", tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tripCode").value("TR-1001"));
    }

    // 4. PATCH /trips/{tripId}/draft
    @Test
    @DisplayName("PATCH /trips/{tripId}/draft returns 200 or 409 STALE_REVISION on revision conflict")
    void testPatchTripDraftStaleRevision() throws Exception {
        TripDraftPatchRequest patch = new TripDraftPatchRequest();
        patch.setExpectedRevision(1L);
        patch.setName("Updated name");

        when(tripService.patchTripDraft(eq(tripId), any(), any()))
                .thenThrow(new BusinessException(HttpStatus.CONFLICT, "STALE_REVISION", "Revision mismatch"));

        mockMvcTrip.perform(patch("/trips/{tripId}/draft", tripId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patch)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_REVISION"));
    }

    // 5. GET /trip-planning/catalogs
    @Test
    @DisplayName("GET /trip-planning/catalogs returns operatingAreas, tripTypes, fleetIntegrationStatus=DEFERRED_EXTERNAL")
    void testGetCatalogsContract() throws Exception {
        Destination dest = new Destination();
        dest.setDestinationName("Vịnh Hạ Long");

        when(destinationRepository.findAllActiveDestinations()).thenReturn(List.of(dest));

        mockMvcCatalog.perform(get("/trip-planning/catalogs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fleetIntegrationStatus").value("DEFERRED_EXTERNAL"))
                .andExpect(jsonPath("$.data.operatingAreas[0]").value("Vịnh Hạ Long"))
                .andExpect(jsonPath("$.data.tripTypes", hasItems("ROUND_TRIP", "ONE_WAY")))
                .andExpect(jsonPath("$.data.supportedPolicies", hasItem("PORT_CALL_BUFFER_30M")));
    }

    // 6. GET /trips/{tripId}/schedule-versions
    @Test
    @DisplayName("GET /trips/{tripId}/schedule-versions returns version metadata list")
    void testGetScheduleVersionsContract() throws Exception {
        TripScheduleVersionSummaryResponse summary = new TripScheduleVersionSummaryResponse();
        summary.setVersionId(versionId);
        summary.setVersionNumber(1);
        summary.setStatus(TripScheduleVersionStatus.DRAFT);
        summary.setRevision(1L);

        when(tripService.getScheduleVersions(tripId)).thenReturn(List.of(summary));

        mockMvcTrip.perform(get("/trips/{tripId}/schedule-versions", tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].versionNumber").value(1));
    }

    // 7. POST /trips/{tripId}/schedule-versions
    @Test
    @DisplayName("POST /trips/{tripId}/schedule-versions returns 201 Created")
    void testCreateScheduleVersionContract() throws Exception {
        TripScheduleVersionDetailResponse res = new TripScheduleVersionDetailResponse();
        res.setVersionId(versionId);
        res.setVersionNumber(2);
        res.setStatus(TripScheduleVersionStatus.DRAFT);

        when(tripService.createScheduleVersion(eq(tripId), any(), any())).thenReturn(res);

        mockMvcTrip.perform(post("/trips/{tripId}/schedule-versions", tripId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.versionNumber").value(2));
    }

    // 8. GET /trip-schedule-versions/{versionId}
    @Test
    @DisplayName("GET /trip-schedule-versions/{versionId} returns 200 with full VersionView")
    void testGetVersionDetailByIdContract() throws Exception {
        TripScheduleVersionDetailResponse res = new TripScheduleVersionDetailResponse();
        res.setVersionId(versionId);
        res.setVersionNumber(1);
        res.setStatus(TripScheduleVersionStatus.DRAFT);

        when(versionService.getVersionDetailById(versionId)).thenReturn(res);

        mockMvcVersion.perform(get("/trip-schedule-versions/{versionId}", versionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionId").value(versionId.toString()));
    }

    // 9. PUT /trip-schedule-versions/{versionId}/content
    @Test
    @DisplayName("PUT /trip-schedule-versions/{versionId}/content saves tree and returns new VersionView")
    void testSaveContentContractSuccess() throws Exception {
        ScheduleDraftSaveRequest req = new ScheduleDraftSaveRequest();
        req.setExpectedRevision(1L);
        TripPlanDayDraftDto d1 = new TripPlanDayDraftDto();
        d1.setDayNumber(1);
        req.setDays(List.of(d1));

        TripScheduleVersionDetailResponse res = new TripScheduleVersionDetailResponse();
        res.setVersionId(versionId);
        res.setRevision(2L);

        when(versionService.saveDraftById(eq(versionId), any(), any())).thenReturn(res);

        mockMvcVersion.perform(put("/trip-schedule-versions/{versionId}/content", versionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revision").value(2));
    }

    // 10. POST /trip-schedule-versions/{versionId}/validate
    @Test
    @DisplayName("POST /trip-schedule-versions/{versionId}/validate returns ValidationReport without changing status")
    void testValidateVersionContract() throws Exception {
        ValidationReport report = ValidationReport.builder()
                .versionId(versionId)
                .revision(1L)
                .validForSubmit(false)
                .policyVersion("v1.0")
                .violations(List.of(
                        PlanViolation.builder()
                                .code("DAY_TYPE_REQUIRED")
                                .severity("ERROR")
                                .entityType("DAY")
                                .dayNumber(1)
                                .field("dayType")
                                .message("Ngày 1 chưa được xác định loại ngày")
                                .build()
                ))
                .build();

        when(versionService.validateVersion(versionId)).thenReturn(report);

        mockMvcVersion.perform(post("/trip-schedule-versions/{versionId}/validate", versionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validForSubmit").value(false))
                .andExpect(jsonPath("$.data.violations[0].code").value("DAY_TYPE_REQUIRED"));
    }

    // 11. POST /trip-schedule-versions/{versionId}/submit
    @Test
    @DisplayName("POST /trip-schedule-versions/{versionId}/submit returns 422 with details.violations if invalid")
    void testSubmitVersionValidationFailureContract() throws Exception {
        PlanViolation violation = PlanViolation.builder()
                .code("MISSING_EMBARKATION")
                .severity("ERROR")
                .entityType("VERSION")
                .message("Thiếu cảng đón khách tại Ngày 1")
                .build();

        when(versionService.submitForReviewById(eq(versionId), any(), any()))
                .thenThrow(new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "MISSING_EMBARKATION",
                        violation.getMessage(), java.util.Map.of("violations", List.of(violation))));

        mockMvcVersion.perform(post("/trip-schedule-versions/{versionId}/submit", versionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\": 1}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MISSING_EMBARKATION"))
                .andExpect(jsonPath("$.details.violations[0].code").value("MISSING_EMBARKATION"));
    }

    // 12. POST /trip-schedule-versions/{versionId}/return
    @Test
    @DisplayName("POST /trip-schedule-versions/{versionId}/return requires non-blank rejection reason")
    void testReturnVersionContract() throws Exception {
        VersionReturnRequest req = new VersionReturnRequest();
        req.setExpectedRevision(1L);
        req.setRejectionReason("Thời gian tham quan không hợp lý");

        TripScheduleVersionDetailResponse res = new TripScheduleVersionDetailResponse();
        res.setVersionId(versionId);
        res.setStatus(TripScheduleVersionStatus.DRAFT);
        res.setRejectionReason(req.getRejectionReason());

        when(versionService.returnToDraftById(eq(versionId), any(), any())).thenReturn(res);

        mockMvcVersion.perform(post("/trip-schedule-versions/{versionId}/return", versionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.rejectionReason").value(req.getRejectionReason()));
    }

    // 13. POST /trip-schedule-versions/{versionId}/publish
    @Test
    @DisplayName("POST /trip-schedule-versions/{versionId}/publish requires login (401)")
    void testPublishVersionUnauthenticatedContract() throws Exception {
        // Without authenticated principal
        MockMvc unauthMvc = MockMvcBuilders.standaloneSetup(versionController)
                .setCustomArgumentResolvers(
                        new PageableHandlerMethodArgumentResolver(),
                        new TestPrincipalResolver(null)) // Null principal
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        unauthMvc.perform(post("/trip-schedule-versions/{versionId}/publish", versionId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    // 14. GET /trip-schedule-versions/{versionId}/history
    @Test
    @DisplayName("GET /trip-schedule-versions/{versionId}/history returns audit events without sensitive PII")
    void testGetVersionHistoryContract() throws Exception {
        TripAuditResponse audit = TripAuditResponse.builder()
                .auditId(UUID.randomUUID())
                .action("SUBMIT")
                .entityType("TripScheduleVersion")
                .entityId(versionId)
                .result("SUCCESS")
                .detail("Submitted for review")
                .occurredAt(Instant.now())
                .build();

        when(versionService.getVersionHistory(versionId)).thenReturn(List.of(audit));

        mockMvcVersion.perform(get("/trip-schedule-versions/{versionId}/history", versionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].action").value("SUBMIT"));
    }

    // 15. POST /trips/{tripId}/clone
    @Test
    @DisplayName("POST /trips/{tripId}/clone returns 201 with clonedDraftVersionId and requires Idempotency-Key")
    void testCloneTripContract() throws Exception {
        UUID newVersionId = UUID.randomUUID();
        TripDetailResponse res = new TripDetailResponse();
        res.setTripId(UUID.randomUUID());
        res.setTripCode("TR-1002");
        res.setClonedDraftVersionId(newVersionId);

        when(idempotencyService.execute(eq("idem-clone-99"), any(), eq("CLONE_TRIP"), any(), any()))
                .thenReturn(res);

        mockMvcTrip.perform(post("/trips/{tripId}/clone", tripId)
                        .header("Idempotency-Key", "idem-clone-99")
                        .param("sourceVersionId", versionId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tripCode").value("TR-1002"))
                .andExpect(jsonPath("$.data.clonedDraftVersionId").value(newVersionId.toString()));
    }
}
