package com.casms.trip.service;

import com.casms.activity.entity.ShoreExcursion;
import com.casms.activity.repository.OnboardActivityRepository;
import com.casms.activity.repository.ShoreExcursionRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.response.ApiResponse;
import com.casms.common.service.BusinessCodeGenerator;
import com.casms.identity.entity.User;
import com.casms.identity.repository.UserRepository;
import com.casms.servicepackage.repository.ServiceRepository;
import com.casms.ship.dto.request.CruiseStopCreateRequest;
import com.casms.ship.dto.request.CruiseStopUpdateRequest;
import com.casms.ship.dto.response.PortResponse;
import com.casms.ship.entity.*;
import com.casms.ship.enums.*;
import com.casms.ship.repository.*;
import com.casms.ship.service.CruiseStopService;
import com.casms.ship.service.PortService;
import com.casms.trip.controller.TripCatalogController;
import com.casms.trip.dto.request.ScheduleDraftSaveRequest;
import com.casms.trip.dto.request.TripDraftPatchRequest;
import com.casms.trip.dto.request.TripPlanDayDraftDto;
import com.casms.trip.dto.request.TripPlanEntryDraftDto;
import com.casms.trip.dto.request.TripPlanStopDraftDto;
import com.casms.trip.dto.response.PlanningSourcePortResponse;
import com.casms.trip.dto.response.ValidationReport;
import com.casms.trip.entity.*;
import com.casms.trip.enums.TripPlanEntryKind;
import com.casms.trip.enums.TripScheduleVersionStatus;
import com.casms.trip.enums.TripStatus;
import com.casms.trip.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.time.Instant;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Acceptance test suite for BE-11 (Port Country Scope) and BE-12 (Port Call Overlap & Tour Window).
 * Verifies scenarios E31 through E44 as specified in OcvViet BE Spec v1.2.
 */
@ExtendWith(MockitoExtension.class)
public class TripAndCruiseP0PortValidationTest {

    // Mocks for PortService and TripCatalogController
    @Mock
    private PortRepository portRepository;

    @Mock
    private DestinationRepository destinationRepository;

    @Mock
    private OnboardActivityRepository onboardActivityRepository;

    @Mock
    private ShoreExcursionRepository shoreExcursionRepository;

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private TripRepository tripRepository;

    // Mocks for TripScheduleVersionService
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
    private com.casms.audit.AuditRepository auditRepository;

    @Mock
    private com.casms.audit.AuditService auditService;

    // Mocks for TripService
    @Mock
    private BusinessCodeGenerator businessCodeGenerator;

    // Mocks for CruiseStopService
    @Mock
    private CruiseStopRepository cruiseStopRepository;

    @Mock
    private ItineraryRepository itineraryRepository;

    @Mock
    private ItineraryDayRepository itineraryDayRepository;

    private PortService portService;
    private TripCatalogController tripCatalogController;
    private TripScheduleVersionService versionService;
    private TripService tripService;
    private CruiseStopService cruiseStopService;

    private UUID actorId;
    private User testUser;
    private Trip domesticTrip;
    private Trip internationalTrip;
    private Port portVN1;
    private Port portVN2;
    private Port portTH;
    private Port portSG;

    @BeforeEach
    void setUp() {
        actorId = UUID.randomUUID();
        testUser = new User();
        testUser.setUserId(actorId);
        testUser.setUsername("test-planner");

        portService = new PortService(portRepository, destinationRepository);
        tripCatalogController = new TripCatalogController(destinationRepository, onboardActivityRepository, shoreExcursionRepository, serviceRepository);
        tripCatalogController.setPortRepository(portRepository);
        tripCatalogController.setTripRepository(tripRepository);

        versionService = new TripScheduleVersionService(
                tripRepository,
                versionRepository,
                dayRepository,
                stopRepository,
                entryRepository,
                portRepository,
                destinationRepository,
                userRepository,
                auditRepository,
                auditService
        );
        versionService.setShoreExcursionRepository(shoreExcursionRepository);

        tripService = new TripService(
                tripRepository,
                versionRepository,
                dayRepository,
                userRepository,
                businessCodeGenerator,
                auditService
        );

        cruiseStopService = new CruiseStopService(
                cruiseStopRepository,
                itineraryRepository,
                itineraryDayRepository,
                portRepository,
                destinationRepository
        );
        cruiseStopService.setShoreExcursionRepository(shoreExcursionRepository);

        // Sample Ports
        portVN1 = new Port();
        portVN1.setPortId(UUID.randomUUID());
        portVN1.setPortName("Cảng Sài Gòn");
        portVN1.setCountryCode("VN");
        portVN1.setStatus(PortStatus.ACTIVE);

        portVN2 = new Port();
        portVN2.setPortId(UUID.randomUUID());
        portVN2.setPortName("Cảng Nha Trang");
        portVN2.setCountryCode("VN");
        portVN2.setStatus(PortStatus.ACTIVE);

        portTH = new Port();
        portTH.setPortId(UUID.randomUUID());
        portTH.setPortName("Bangkok Port");
        portTH.setCountryCode("TH");
        portTH.setStatus(PortStatus.ACTIVE);

        portSG = new Port();
        portSG.setPortId(UUID.randomUUID());
        portSG.setPortName("Singapore Marina Bay");
        portSG.setCountryCode("SG");
        portSG.setStatus(PortStatus.ACTIVE);

        // Sample Trips
        domesticTrip = new Trip();
        domesticTrip.setTripId(UUID.randomUUID());
        domesticTrip.setTripCode("VN-DOM-01");
        domesticTrip.setName("Hành trình Xuyên Việt");
        domesticTrip.setPlannedScope("DOMESTIC");
        domesticTrip.setCountryCodes(List.of("VN"));
        domesticTrip.setDurationDays(2);
        domesticTrip.setDurationNights(1);
        domesticTrip.setStatus(TripStatus.DRAFT);

        internationalTrip = new Trip();
        internationalTrip.setTripId(UUID.randomUUID());
        internationalTrip.setTripCode("INT-TH-01");
        internationalTrip.setName("Hành trình Thái Lan");
        internationalTrip.setPlannedScope("INTERNATIONAL");
        internationalTrip.setCountryCodes(List.of("TH"));
        internationalTrip.setDurationDays(2);
        internationalTrip.setDurationNights(1);
        internationalTrip.setStatus(TripStatus.DRAFT);
    }

    private List<TripPlanDayDraftDto> createDefaultDays(int count) {
        List<TripPlanDayDraftDto> days = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            TripPlanDayDraftDto day = new TripPlanDayDraftDto();
            day.setDayNumber(i);
            day.setDayType(ItineraryDayType.PORT_DAY);
            day.setTitle("Day " + i);
            days.add(day);
        }
        return days;
    }

    // ==========================================
    // BE-11 / E31 - E35: Country Scope Tests
    // ==========================================

    @Test
    @DisplayName("E31: Scope VN or TH, search and pagination only returns matching ports")
    void test_E31_portLookupFilteringByCountryScope() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Port> vnPage = new PageImpl<>(List.of(portVN1, portVN2), pageable, 2);
        Page<Port> thPage = new PageImpl<>(List.of(portTH), pageable, 1);

        when(portRepository.findPortsWithFilters(eq(List.of("VN")), any(), any(), eq(pageable))).thenReturn(vnPage);
        when(portRepository.findPortsWithFilters(eq(List.of("TH")), any(), any(), eq(pageable))).thenReturn(thPage);

        // Test PortService directly
        Page<PortResponse> resultVN = portService.list(List.of("VN"), null, null, pageable);
        assertEquals(2, resultVN.getTotalElements());
        assertTrue(resultVN.getContent().stream().allMatch(p -> "VN".equals(p.getCountryCode())));

        Page<PortResponse> resultTH = portService.list(List.of("th"), null, null, pageable); // tests case-insensitivity
        assertEquals(1, resultTH.getTotalElements());
        assertEquals("TH", resultTH.getContent().get(0).getCountryCode());

        // Test TripCatalogController authoritative resolution from tripId
        when(portRepository.findAll()).thenReturn(List.of(portVN1, portVN2, portTH));
        when(tripRepository.findById(domesticTrip.getTripId())).thenReturn(Optional.of(domesticTrip));
        ApiResponse<List<PlanningSourcePortResponse>> catalogResponse = tripCatalogController.getSourcePorts(
                null, null, null, null, domesticTrip.getTripId()
        );
        assertTrue(catalogResponse.isSuccess());
        assertEquals(2, catalogResponse.getData().size());
        assertTrue(catalogResponse.getData().stream().allMatch(p -> "VN".equals(p.getCountryCode())));
    }

    @Test
    @DisplayName("E32: Direct submission of port ID outside country scope is rejected")
    void test_E32_directPortSubmissionOutsideCountryScope_rejected() {
        UUID versionId = UUID.randomUUID();
        TripScheduleVersion version = new TripScheduleVersion();
        version.setVersionId(versionId);
        version.setTrip(domesticTrip); // Scope is DOMESTIC (VN)
        version.setStatus(TripScheduleVersionStatus.DRAFT);
        version.setRevision(1L);

        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(portRepository.findById(portTH.getPortId())).thenReturn(Optional.of(portTH)); // Port is in Thailand

        ScheduleDraftSaveRequest request = new ScheduleDraftSaveRequest();
        request.setExpectedRevision(1L);
        request.setDays(createDefaultDays(2));

        TripPlanStopDraftDto stopDraft = new TripPlanStopDraftDto();
        stopDraft.setClientKey("stop-th-01");
        stopDraft.setPortId(portTH.getPortId());
        stopDraft.setArrivalDayNumber(1);
        stopDraft.setPlannedArrivalTime(LocalTime.of(10, 0));
        stopDraft.setDepartureDayNumber(1);
        stopDraft.setPlannedDepartureTime(LocalTime.of(18, 0));
        stopDraft.setStopOrder(1);
        request.setStops(List.of(stopDraft));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                versionService.saveDraftById(versionId, request, actorId)
        );
        assertEquals("PORT_OUTSIDE_COUNTRY_SCOPE", ex.getCode());
        assertTrue(ex.getMessage().contains("Bangkok Port"));
    }

    @Test
    @DisplayName("E33: Changing trip country scope when existing stops are outside new scope is rejected")
    void test_E33_changeTripCountryScope_whenExistingStopsIncompatible_rejected() {
        Trip trip = new Trip();
        trip.setTripId(UUID.randomUUID());
        trip.setTripCode("VN-DOM-01");
        trip.setPlannedScope("DOMESTIC");
        trip.setCountryCodes(List.of("VN"));
        trip.setStatus(TripStatus.DRAFT);
        trip.setDurationDays(2);
        trip.setDurationNights(1);

        TripScheduleVersion version = new TripScheduleVersion();
        version.setVersionId(UUID.randomUUID());
        version.setTrip(trip);
        version.setStatus(TripScheduleVersionStatus.DRAFT);

        TripPlanStop stopVN = new TripPlanStop();
        stopVN.setStopId(UUID.randomUUID());
        stopVN.setVersion(version);
        stopVN.setPort(portVN1);

        version.getStops().add(stopVN);

        when(tripRepository.findById(trip.getTripId())).thenReturn(Optional.of(trip));
        when(versionRepository.findByTrip_TripIdOrderByVersionNumberAsc(trip.getTripId())).thenReturn(List.of(version));

        // Attempt to change scope to INTERNATIONAL with countryCodes: ["TH"]
        TripDraftPatchRequest patchRequest = new TripDraftPatchRequest();
        patchRequest.setPlannedScope("INTERNATIONAL");
        patchRequest.setCountryCodes(List.of("TH"));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                tripService.patchTripDraft(trip.getTripId(), patchRequest, actorId)
        );
        assertEquals("EXISTING_STOPS_OUTSIDE_NEW_SCOPE", ex.getCode());
        assertTrue(ex.getMessage().contains("VN"));
    }

    @Test
    @DisplayName("E34: Lookup without country scope or unconfigured trip is rejected without fallback")
    void test_E34_missingOrInvalidCountryScopeInLookup_rejectedWithoutFallback() {
        Trip unconfiguredTrip = new Trip();
        unconfiguredTrip.setTripId(UUID.randomUUID());
        unconfiguredTrip.setTripCode("INT-UNCONFIG");
        unconfiguredTrip.setPlannedScope("INTERNATIONAL");
        unconfiguredTrip.setCountryCodes(Collections.emptyList());

        when(tripRepository.findById(unconfiguredTrip.getTripId())).thenReturn(Optional.of(unconfiguredTrip));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                tripCatalogController.getSourcePorts(null, null, null, null, unconfiguredTrip.getTripId())
        );
        assertEquals("SCOPE_COUNTRY_REQUIRED", ex.getCode());
        // Verify we never fetch all ports without filters
        verify(portRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("E35: Multi-country scope allows ports in configured countries and rejects others")
    void test_E35_multiCountryScopeHandling() {
        Trip multiCountryTrip = new Trip();
        multiCountryTrip.setTripId(UUID.randomUUID());
        multiCountryTrip.setTripCode("INT-MULTI");
        multiCountryTrip.setPlannedScope("INTERNATIONAL");
        multiCountryTrip.setCountryCodes(List.of("VN", "TH"));
        multiCountryTrip.setDurationDays(2);
        multiCountryTrip.setStatus(TripStatus.DRAFT);

        TripScheduleVersion version = new TripScheduleVersion();
        UUID versionId = UUID.randomUUID();
        version.setVersionId(versionId);
        version.setTrip(multiCountryTrip);
        version.setStatus(TripScheduleVersionStatus.DRAFT);
        version.setRevision(1L);

        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(portRepository.findById(portSG.getPortId())).thenReturn(Optional.of(portSG));

        // Submit a stop in Singapore (SG), not in ["VN", "TH"]
        ScheduleDraftSaveRequest request = new ScheduleDraftSaveRequest();
        request.setExpectedRevision(1L);
        request.setDays(createDefaultDays(2));

        TripPlanStopDraftDto stopDraft = new TripPlanStopDraftDto();
        stopDraft.setClientKey("stop-sg-01");
        stopDraft.setPortId(portSG.getPortId());
        stopDraft.setArrivalDayNumber(1);
        stopDraft.setPlannedArrivalTime(LocalTime.of(8, 0));
        stopDraft.setDepartureDayNumber(1);
        stopDraft.setPlannedDepartureTime(LocalTime.of(17, 0));
        stopDraft.setStopOrder(1);
        request.setStops(List.of(stopDraft));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                versionService.saveDraftById(versionId, request, actorId)
        );
        assertEquals("PORT_OUTSIDE_COUNTRY_SCOPE", ex.getCode());
    }

    // ==========================================
    // BE-12 / E36 - E44: Port Call Overlap & Tour Window Tests
    // ==========================================

    @Test
    @DisplayName("E36: Overlap table - all 5 overlap cases are rejected with PORT_CALL_OVERLAP")
    void test_E36_portCallOverlapTable() {
        // Port 1 on Day 1: 15:30 to 16:40
        // Test 5 variations for Port 2:
        // 1. Inside: 15:45 - 16:00
        // 2. Start overlap: 15:00 - 16:00
        // 3. End overlap: 16:00 - 17:00
        // 4. Enclosing: 15:00 - 17:00
        // 5. Identical: 15:30 - 16:40
        List<LocalTime[]> testCases = List.of(
                new LocalTime[]{LocalTime.of(15, 45), LocalTime.of(16, 0)},
                new LocalTime[]{LocalTime.of(15, 0), LocalTime.of(16, 0)},
                new LocalTime[]{LocalTime.of(16, 0), LocalTime.of(17, 0)},
                new LocalTime[]{LocalTime.of(15, 0), LocalTime.of(17, 0)},
                new LocalTime[]{LocalTime.of(15, 30), LocalTime.of(16, 40)}
        );

        UUID versionId = UUID.randomUUID();
        TripScheduleVersion version = new TripScheduleVersion();
        version.setVersionId(versionId);
        version.setTrip(domesticTrip);
        version.setStatus(TripScheduleVersionStatus.DRAFT);
        version.setRevision(1L);

        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(portRepository.findById(portVN1.getPortId())).thenReturn(Optional.of(portVN1));
        when(portRepository.findById(portVN2.getPortId())).thenReturn(Optional.of(portVN2));

        for (LocalTime[] times : testCases) {
            ScheduleDraftSaveRequest request = new ScheduleDraftSaveRequest();
            request.setExpectedRevision(1L);
            request.setDays(createDefaultDays(2));

            TripPlanStopDraftDto stop1 = new TripPlanStopDraftDto();
            stop1.setClientKey("stop-1");
            stop1.setPortId(portVN1.getPortId());
            stop1.setArrivalDayNumber(1);
            stop1.setPlannedArrivalTime(LocalTime.of(15, 30));
            stop1.setDepartureDayNumber(1);
            stop1.setPlannedDepartureTime(LocalTime.of(16, 40));
            stop1.setStopOrder(1);

            TripPlanStopDraftDto stop2 = new TripPlanStopDraftDto();
            stop2.setClientKey("stop-2");
            stop2.setPortId(portVN2.getPortId());
            stop2.setArrivalDayNumber(1);
            stop2.setPlannedArrivalTime(times[0]);
            stop2.setDepartureDayNumber(1);
            stop2.setPlannedDepartureTime(times[1]);
            stop2.setStopOrder(2);

            request.setStops(List.of(stop1, stop2));

            BusinessException ex = assertThrows(BusinessException.class, () ->
                    versionService.saveDraftById(versionId, request, actorId),
                    "Expected overlap failure for Port 2 times: " + times[0] + " - " + times[1]
            );
            assertEquals("PORT_CALL_OVERLAP", ex.getCode());
        }
    }

    @Test
    @DisplayName("E37: Boundary arrival at 16:40 rejected with NON_POSITIVE_TRANSIT_TIME; 17:10 allowed")
    void test_E37_boundaryAndTransitTime() {
        UUID versionId = UUID.randomUUID();
        TripScheduleVersion version = new TripScheduleVersion();
        version.setVersionId(versionId);
        version.setTrip(domesticTrip);
        version.setStatus(TripScheduleVersionStatus.DRAFT);
        version.setRevision(1L);

        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(portRepository.findById(portVN1.getPortId())).thenReturn(Optional.of(portVN1));
        when(portRepository.findById(portVN2.getPortId())).thenReturn(Optional.of(portVN2));

        // Case 1: Stop 2 arrives at 16:40 (0 transit time from Stop 1 departure 16:40 at different port)
        ScheduleDraftSaveRequest requestZeroTransit = new ScheduleDraftSaveRequest();
        requestZeroTransit.setExpectedRevision(1L);
        requestZeroTransit.setDays(createDefaultDays(2));

        TripPlanStopDraftDto stop1 = new TripPlanStopDraftDto();
        stop1.setClientKey("stop-1");
        stop1.setPortId(portVN1.getPortId());
        stop1.setArrivalDayNumber(1);
        stop1.setPlannedArrivalTime(LocalTime.of(15, 30));
        stop1.setDepartureDayNumber(1);
        stop1.setPlannedDepartureTime(LocalTime.of(16, 40));
        stop1.setStopOrder(1);

        TripPlanStopDraftDto stop2ZeroTransit = new TripPlanStopDraftDto();
        stop2ZeroTransit.setClientKey("stop-2");
        stop2ZeroTransit.setPortId(portVN2.getPortId());
        stop2ZeroTransit.setArrivalDayNumber(1);
        stop2ZeroTransit.setPlannedArrivalTime(LocalTime.of(16, 40));
        stop2ZeroTransit.setDepartureDayNumber(1);
        stop2ZeroTransit.setPlannedDepartureTime(LocalTime.of(17, 30));
        stop2ZeroTransit.setStopOrder(2);

        requestZeroTransit.setStops(List.of(stop1, stop2ZeroTransit));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                versionService.saveDraftById(versionId, requestZeroTransit, actorId)
        );
        assertEquals("NON_POSITIVE_TRANSIT_TIME", ex.getCode());

        // Case 2: Stop 2 arrives at 17:10 (positive transit time = 30 min) -> passes time checks
        ScheduleDraftSaveRequest requestValidTransit = new ScheduleDraftSaveRequest();
        requestValidTransit.setExpectedRevision(1L);
        requestValidTransit.setDays(createDefaultDays(2));

        TripPlanStopDraftDto stop2ValidTransit = new TripPlanStopDraftDto();
        stop2ValidTransit.setClientKey("stop-2");
        stop2ValidTransit.setPortId(portVN2.getPortId());
        stop2ValidTransit.setArrivalDayNumber(1);
        stop2ValidTransit.setPlannedArrivalTime(LocalTime.of(17, 10));
        stop2ValidTransit.setDepartureDayNumber(1);
        stop2ValidTransit.setPlannedDepartureTime(LocalTime.of(18, 0));
        stop2ValidTransit.setStopOrder(2);

        requestValidTransit.setStops(List.of(stop1, stop2ValidTransit));
        when(versionRepository.save(any(TripScheduleVersion.class))).thenReturn(version);

        // saveDraftById should proceed past validation without time exceptions
        assertDoesNotThrow(() -> versionService.saveDraftById(versionId, requestValidTransit, actorId));
    }

    @Test
    @DisplayName("E38: Overnight overlap rejected, same HH:mm on different days allowed")
    void test_E38_overnightAndCrossDayOverlap() {
        UUID versionId = UUID.randomUUID();
        TripScheduleVersion version = new TripScheduleVersion();
        version.setVersionId(versionId);
        version.setTrip(domesticTrip);
        version.setStatus(TripScheduleVersionStatus.DRAFT);
        version.setRevision(1L);

        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(portRepository.findById(portVN1.getPortId())).thenReturn(Optional.of(portVN1));
        when(portRepository.findById(portVN2.getPortId())).thenReturn(Optional.of(portVN2));

        // Part A: Overnight overlap (Stop 1: D1 23:00 to D2 02:00 vs Stop 2: D2 01:30 to 03:00)
        ScheduleDraftSaveRequest requestOvernight = new ScheduleDraftSaveRequest();
        requestOvernight.setExpectedRevision(1L);
        requestOvernight.setDays(createDefaultDays(2));

        TripPlanStopDraftDto stop1 = new TripPlanStopDraftDto();
        stop1.setClientKey("stop-1");
        stop1.setPortId(portVN1.getPortId());
        stop1.setArrivalDayNumber(1);
        stop1.setPlannedArrivalTime(LocalTime.of(23, 0));
        stop1.setDepartureDayNumber(2);
        stop1.setPlannedDepartureTime(LocalTime.of(2, 0));
        stop1.setStopOrder(1);

        TripPlanStopDraftDto stop2 = new TripPlanStopDraftDto();
        stop2.setClientKey("stop-2");
        stop2.setPortId(portVN2.getPortId());
        stop2.setArrivalDayNumber(2);
        stop2.setPlannedArrivalTime(LocalTime.of(1, 30));
        stop2.setDepartureDayNumber(2);
        stop2.setPlannedDepartureTime(LocalTime.of(3, 0));
        stop2.setStopOrder(2);

        requestOvernight.setStops(List.of(stop1, stop2));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                versionService.saveDraftById(versionId, requestOvernight, actorId)
        );
        assertEquals("PORT_CALL_OVERLAP", ex.getCode());

        // Part B: Same HH:mm but on different days (Day 1 15:30-16:40 and Day 2 15:30-16:40) -> Allowed!
        ScheduleDraftSaveRequest requestDifferentDays = new ScheduleDraftSaveRequest();
        requestDifferentDays.setExpectedRevision(1L);
        requestDifferentDays.setDays(createDefaultDays(2));

        TripPlanStopDraftDto stopDay1 = new TripPlanStopDraftDto();
        stopDay1.setClientKey("stop-day1");
        stopDay1.setPortId(portVN1.getPortId());
        stopDay1.setArrivalDayNumber(1);
        stopDay1.setPlannedArrivalTime(LocalTime.of(15, 30));
        stopDay1.setDepartureDayNumber(1);
        stopDay1.setPlannedDepartureTime(LocalTime.of(16, 40));
        stopDay1.setStopOrder(1);

        TripPlanStopDraftDto stopDay2 = new TripPlanStopDraftDto();
        stopDay2.setClientKey("stop-day2");
        stopDay2.setPortId(portVN2.getPortId());
        stopDay2.setArrivalDayNumber(2);
        stopDay2.setPlannedArrivalTime(LocalTime.of(15, 30));
        stopDay2.setDepartureDayNumber(2);
        stopDay2.setPlannedDepartureTime(LocalTime.of(16, 40));
        stopDay2.setStopOrder(2);

        requestDifferentDays.setStops(List.of(stopDay1, stopDay2));
        when(versionRepository.save(any(TripScheduleVersion.class))).thenReturn(version);

        assertDoesNotThrow(() -> versionService.saveDraftById(versionId, requestDifferentDays, actorId));
    }

    @Test
    @DisplayName("E39: Departure <= arrival rejected, inconsistent stopOrder rejected")
    void test_E39_invalidPortCallTimesAndSequence() {
        UUID versionId = UUID.randomUUID();
        TripScheduleVersion version = new TripScheduleVersion();
        version.setVersionId(versionId);
        version.setTrip(domesticTrip);
        version.setStatus(TripScheduleVersionStatus.DRAFT);
        version.setRevision(1L);

        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(portRepository.findById(portVN1.getPortId())).thenReturn(Optional.of(portVN1));

        // Part A: departure <= arrival
        ScheduleDraftSaveRequest reqDepBeforeArr = new ScheduleDraftSaveRequest();
        reqDepBeforeArr.setExpectedRevision(1L);
        reqDepBeforeArr.setDays(createDefaultDays(2));

        TripPlanStopDraftDto badStop = new TripPlanStopDraftDto();
        badStop.setClientKey("bad-stop");
        badStop.setPortId(portVN1.getPortId());
        badStop.setArrivalDayNumber(1);
        badStop.setPlannedArrivalTime(LocalTime.of(16, 0));
        badStop.setDepartureDayNumber(1);
        badStop.setPlannedDepartureTime(LocalTime.of(15, 0)); // Before arrival!
        badStop.setStopOrder(1);
        reqDepBeforeArr.setStops(List.of(badStop));

        BusinessException ex1 = assertThrows(BusinessException.class, () ->
                versionService.saveDraftById(versionId, reqDepBeforeArr, actorId)
        );
        assertEquals("INVALID_PORT_CALL_TIMES", ex1.getCode());

        // Part B: stopOrder inconsistent with timeline (stopOrder 1 arrives later than stopOrder 2)
        when(portRepository.findById(portVN2.getPortId())).thenReturn(Optional.of(portVN2));
        ScheduleDraftSaveRequest reqBadSequence = new ScheduleDraftSaveRequest();
        reqBadSequence.setExpectedRevision(1L);
        reqBadSequence.setDays(createDefaultDays(2));

        TripPlanStopDraftDto stopOrder1 = new TripPlanStopDraftDto();
        stopOrder1.setClientKey("stop-order-1");
        stopOrder1.setPortId(portVN1.getPortId());
        stopOrder1.setArrivalDayNumber(1);
        stopOrder1.setPlannedArrivalTime(LocalTime.of(17, 0));
        stopOrder1.setDepartureDayNumber(1);
        stopOrder1.setPlannedDepartureTime(LocalTime.of(18, 0));
        stopOrder1.setStopOrder(1);

        TripPlanStopDraftDto stopOrder2 = new TripPlanStopDraftDto();
        stopOrder2.setClientKey("stop-order-2");
        stopOrder2.setPortId(portVN2.getPortId());
        stopOrder2.setArrivalDayNumber(1);
        stopOrder2.setPlannedArrivalTime(LocalTime.of(10, 0)); // earlier than stopOrder 1!
        stopOrder2.setDepartureDayNumber(1);
        stopOrder2.setPlannedDepartureTime(LocalTime.of(11, 0));
        stopOrder2.setStopOrder(2);

        reqBadSequence.setStops(List.of(stopOrder1, stopOrder2));

        BusinessException ex2 = assertThrows(BusinessException.class, () ->
                versionService.saveDraftById(versionId, reqBadSequence, actorId)
        );
        assertEquals("INVALID_STOP_SEQUENCE_TIME", ex2.getCode());
    }

    @Test
    @DisplayName("E40: CruiseStop update does not collide with itself on multi-call itinerary")
    void test_E40_cruiseStopUpdate_doesNotCollideWithSelf() {
        UUID itineraryId = UUID.randomUUID();
        Itinerary itinerary = new Itinerary();
        itinerary.setItineraryId(itineraryId);
        itinerary.setStatus(ItineraryStatus.DRAFT);

        ItineraryDay day1 = new ItineraryDay();
        day1.setItineraryDayId(UUID.randomUUID());
        day1.setDayNumber(1);

        UUID stopId1 = UUID.randomUUID();
        CruiseStop stop1 = new CruiseStop();
        stop1.setCruiseStopId(stopId1);
        stop1.setItinerary(itinerary);
        stop1.setArrivalItineraryDay(day1);
        stop1.setDepartureItineraryDay(day1);
        stop1.setPort(portVN1);
        stop1.setPlannedArrival(Instant.parse("2026-11-01T15:30:00Z"));
        stop1.setPlannedDeparture(Instant.parse("2026-11-01T16:40:00Z"));
        stop1.setStopOrder(1);

        when(cruiseStopRepository.findById(stopId1)).thenReturn(Optional.of(stop1));
        when(cruiseStopRepository.findByItineraryItineraryIdOrderByStopOrderAsc(itineraryId)).thenReturn(List.of(stop1));
        when(shoreExcursionRepository.findByCruiseStop_CruiseStopId(stopId1)).thenReturn(Collections.emptyList());
        when(cruiseStopRepository.save(any(CruiseStop.class))).thenReturn(stop1);

        // Update stop 1 times slightly (15:35 to 16:45)
        CruiseStopUpdateRequest updateRequest = new CruiseStopUpdateRequest();
        updateRequest.setPlannedArrival(Instant.parse("2026-11-01T15:35:00Z"));
        updateRequest.setPlannedDeparture(Instant.parse("2026-11-01T16:45:00Z"));
        updateRequest.setStopOrder(1);

        assertDoesNotThrow(() -> cruiseStopService.update(stopId1, updateRequest));
        verify(cruiseStopRepository).save(any(CruiseStop.class));
    }

    @Test
    @DisplayName("E41-A: In Trip Planning, shore excursion ending after port departure/all-aboard is rejected")
    void test_E41_shoreExcursionWindowValidation_tripPlanning() {
        UUID versionId = UUID.randomUUID();
        TripScheduleVersion version = new TripScheduleVersion();
        version.setVersionId(versionId);
        version.setTrip(domesticTrip);
        version.setStatus(TripScheduleVersionStatus.DRAFT);
        version.setRevision(1L);

        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(portRepository.findById(portVN1.getPortId())).thenReturn(Optional.of(portVN1));

        ScheduleDraftSaveRequest request = new ScheduleDraftSaveRequest();
        request.setExpectedRevision(1L);
        request.setDays(createDefaultDays(2));

        TripPlanStopDraftDto stopDraft = new TripPlanStopDraftDto();
        stopDraft.setClientKey("stop-1");
        stopDraft.setPortId(portVN1.getPortId());
        stopDraft.setArrivalDayNumber(1);
        stopDraft.setPlannedArrivalTime(LocalTime.of(15, 30));
        stopDraft.setDepartureDayNumber(1);
        stopDraft.setPlannedDepartureTime(LocalTime.of(16, 40));
        stopDraft.setAllAboardDayNumber(1);
        stopDraft.setAllAboardTime(LocalTime.of(16, 30));
        stopDraft.setStopOrder(1);

        TripPlanEntryDraftDto excursionEntry = new TripPlanEntryDraftDto();
        excursionEntry.setClientKey("entry-excursion-1");
        excursionEntry.setStopClientKey("stop-1");
        excursionEntry.setDayNumber(1);
        excursionEntry.setStartDayNumber(1);
        excursionEntry.setPlannedStartTime(LocalTime.of(15, 45));
        excursionEntry.setEndDayNumber(1);
        excursionEntry.setPlannedEndTime(LocalTime.of(16, 35)); // ends after allAboardTime 16:30!
        excursionEntry.setKind(TripPlanEntryKind.SHORE_EXCURSION);
        excursionEntry.setTitle("Tour Tham Quan Cảng");

        request.setStops(List.of(stopDraft));
        request.setEntries(List.of(excursionEntry));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                versionService.saveDraftById(versionId, request, actorId)
        );
        assertEquals("SHORE_EXCURSION_OUTSIDE_PORT_WINDOW", ex.getCode());
    }

    @Test
    @DisplayName("E41-B: In CruiseStopService, updating port departure earlier than existing excursion return is rejected")
    void test_E41_shoreExcursionWindowValidation_cruiseStop() {
        UUID stopId = UUID.randomUUID();
        CruiseStop stop = new CruiseStop();
        stop.setCruiseStopId(stopId);
        Itinerary itinerary = new Itinerary();
        itinerary.setItineraryId(UUID.randomUUID());
        itinerary.setStatus(ItineraryStatus.DRAFT);
        stop.setItinerary(itinerary);
        stop.setPort(portVN1);
        stop.setPlannedArrival(Instant.parse("2026-11-01T15:30:00Z"));
        stop.setPlannedDeparture(Instant.parse("2026-11-01T16:40:00Z"));
        stop.setStopOrder(1);

        ShoreExcursion existingTour = new ShoreExcursion();
        existingTour.setShoreExcursionId(UUID.randomUUID());
        existingTour.setExcursionName("Tour Chợ Nổi");
        existingTour.setStartTime(Instant.parse("2026-11-01T15:45:00Z"));
        existingTour.setPlannedReturnTime(Instant.parse("2026-11-01T16:30:00Z"));

        when(cruiseStopRepository.findById(stopId)).thenReturn(Optional.of(stop));
        when(cruiseStopRepository.findByItineraryItineraryIdOrderByStopOrderAsc(itinerary.getItineraryId())).thenReturn(List.of(stop));
        when(shoreExcursionRepository.findByCruiseStop_CruiseStopId(stopId)).thenReturn(List.of(existingTour));

        // Rút ngắn giờ rời cảng thành 16:20 (trước giờ về 16:30 của tour)
        CruiseStopUpdateRequest updateReq = new CruiseStopUpdateRequest();
        updateReq.setPlannedArrival(Instant.parse("2026-11-01T15:30:00Z"));
        updateReq.setPlannedDeparture(Instant.parse("2026-11-01T16:20:00Z")); // Before 16:30!
        updateReq.setStopOrder(1);

        BusinessException exCruise = assertThrows(BusinessException.class, () ->
                cruiseStopService.update(stopId, updateReq)
        );
        assertEquals("EXCURSION_OUTSIDE_PORT_WINDOW", exCruise.getCode());
        assertTrue(exCruise.getMessage().contains("Tour Chợ Nổi"));
    }

    @Test
    @DisplayName("E42: CruiseStop overlap prevention in CruiseStopService.add")
    void test_E42_cruiseStopOverlapPrevention() {
        UUID itineraryId = UUID.randomUUID();
        Itinerary itinerary = new Itinerary();
        itinerary.setItineraryId(itineraryId);
        itinerary.setStatus(ItineraryStatus.DRAFT);

        UUID dayId = UUID.randomUUID();
        ItineraryDay day = new ItineraryDay();
        day.setItineraryDayId(dayId);
        day.setItinerary(itinerary);
        day.setDayNumber(1);

        CruiseStop existingStop = new CruiseStop();
        existingStop.setCruiseStopId(UUID.randomUUID());
        existingStop.setItinerary(itinerary);
        existingStop.setPort(portVN1);
        existingStop.setPlannedArrival(Instant.parse("2026-11-01T15:30:00Z"));
        existingStop.setPlannedDeparture(Instant.parse("2026-11-01T16:40:00Z"));
        existingStop.setStopOrder(1);

        when(itineraryRepository.findById(itineraryId)).thenReturn(Optional.of(itinerary));
        when(itineraryDayRepository.findById(dayId)).thenReturn(Optional.of(day));
        when(portRepository.findById(portVN2.getPortId())).thenReturn(Optional.of(portVN2));
        when(cruiseStopRepository.findByItineraryItineraryIdOrderByStopOrderAsc(itineraryId)).thenReturn(List.of(existingStop));

        // Attempt to add overlapping stop (16:00 to 17:00)
        CruiseStopCreateRequest reqOverlap = new CruiseStopCreateRequest();
        reqOverlap.setItineraryId(itineraryId);
        reqOverlap.setArrivalItineraryDayId(dayId);
        reqOverlap.setDepartureItineraryDayId(dayId);
        reqOverlap.setPortId(portVN2.getPortId());
        reqOverlap.setPlannedArrival(Instant.parse("2026-11-01T16:00:00Z"));
        reqOverlap.setPlannedDeparture(Instant.parse("2026-11-01T17:00:00Z"));
        reqOverlap.setStopOrder(2);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                cruiseStopService.add(reqOverlap)
        );
        assertEquals("PORT_CALL_OVERLAP", ex.getCode());
    }

    @Test
    @DisplayName("E43: validateVersion catches overlap and sequence issues")
    void test_E43_validatePlan_catchesAllTimeInvariants() {
        UUID versionId = UUID.randomUUID();
        TripScheduleVersion version = new TripScheduleVersion();
        version.setVersionId(versionId);
        version.setTrip(domesticTrip);
        version.setStatus(TripScheduleVersionStatus.DRAFT);

        TripPlanStop stop1 = new TripPlanStop();
        stop1.setStopId(UUID.randomUUID());
        stop1.setVersion(version);
        stop1.setPort(portVN1);
        stop1.setArrivalDayNumber(1);
        stop1.setPlannedArrivalTime(LocalTime.of(15, 30));
        stop1.setDepartureDayNumber(1);
        stop1.setPlannedDepartureTime(LocalTime.of(16, 40));
        stop1.setStopOrder(1);

        TripPlanStop stop2 = new TripPlanStop();
        stop2.setStopId(UUID.randomUUID());
        stop2.setVersion(version);
        stop2.setPort(portVN2);
        stop2.setArrivalDayNumber(1);
        stop2.setPlannedArrivalTime(LocalTime.of(16, 0)); // Overlap!
        stop2.setDepartureDayNumber(1);
        stop2.setPlannedDepartureTime(LocalTime.of(17, 0));
        stop2.setStopOrder(2);

        version.getStops().add(stop1);
        version.getStops().add(stop2);

        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));

        ValidationReport report = versionService.validateVersion(versionId);
        assertFalse(report.getValidForSubmit());
        assertTrue(report.getViolations().stream().anyMatch(v -> "PORT_CALL_OVERLAP".equals(v.getCode())));
    }

    @Test
    @DisplayName("E44: Two independent itineraries can visit the same port at the same time without error")
    void test_E44_independentCruisesAtSamePort_allowed() {
        UUID itinerary2 = UUID.randomUUID();

        Itinerary it2 = new Itinerary();
        it2.setItineraryId(itinerary2);
        it2.setStatus(ItineraryStatus.DRAFT);

        UUID dayId2 = UUID.randomUUID();
        ItineraryDay day2 = new ItineraryDay();
        day2.setItineraryDayId(dayId2);
        day2.setItinerary(it2);
        day2.setDayNumber(1);

        // Itinerary 2 currently has no stops
        when(itineraryRepository.findById(itinerary2)).thenReturn(Optional.of(it2));
        when(itineraryDayRepository.findById(dayId2)).thenReturn(Optional.of(day2));
        when(portRepository.findById(portVN1.getPortId())).thenReturn(Optional.of(portVN1));
        when(cruiseStopRepository.findByItineraryItineraryIdOrderByStopOrderAsc(itinerary2)).thenReturn(Collections.emptyList());

        CruiseStop savedStop = new CruiseStop();
        savedStop.setCruiseStopId(UUID.randomUUID());
        savedStop.setItinerary(it2);
        savedStop.setArrivalItineraryDay(day2);
        savedStop.setDepartureItineraryDay(day2);
        savedStop.setPort(portVN1);
        savedStop.setPlannedArrival(Instant.parse("2026-11-01T15:30:00Z"));
        savedStop.setPlannedDeparture(Instant.parse("2026-11-01T16:40:00Z"));
        when(cruiseStopRepository.save(any(CruiseStop.class))).thenReturn(savedStop);

        // Add stop to Itinerary 2 at the exact same port and time as Cruise 1
        CruiseStopCreateRequest request = new CruiseStopCreateRequest();
        request.setItineraryId(itinerary2);
        request.setArrivalItineraryDayId(dayId2);
        request.setDepartureItineraryDayId(dayId2);
        request.setPortId(portVN1.getPortId());
        request.setPlannedArrival(Instant.parse("2026-11-01T15:30:00Z"));
        request.setPlannedDeparture(Instant.parse("2026-11-01T16:40:00Z"));
        request.setStopOrder(1);

        assertDoesNotThrow(() -> cruiseStopService.add(request));
        verify(cruiseStopRepository).save(any(CruiseStop.class));
    }
}
