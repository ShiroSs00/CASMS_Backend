package com.casms.ship.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.casms.activity.repository.OnboardActivityRepository;
import com.casms.activity.repository.ShoreExcursionRepository;
import com.casms.common.exception.BusinessException;
import com.casms.ship.dto.response.CruiseSchedulePreviewResponse;
import com.casms.ship.entity.*;
import com.casms.ship.enums.*;
import com.casms.ship.repository.*;
import com.casms.trip.entity.*;
import com.casms.trip.enums.TripScheduleVersionStatus;
import com.casms.trip.enums.TripStatus;
import com.casms.trip.repository.TripScheduleVersionRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@ExtendWith(MockitoExtension.class)
class ScheduleMaterializationServiceTest {

    @Mock private TripScheduleVersionRepository tripScheduleVersionRepository;
    @Mock private ItineraryRepository itineraryRepository;
    @Mock private ItineraryDayRepository itineraryDayRepository;
    @Mock private CruiseStopRepository cruiseStopRepository;
    @Mock private CruiseRepository cruiseRepository;
    @Mock private PortRepository portRepository;
    @Mock private PortWindowEvaluator portWindowEvaluator;

    @Mock
    private OperationalProgramMappingService
            operationalProgramMappingService;

    @Mock private OnboardActivityRepository onboardActivityRepository;
    @Mock private ShoreExcursionRepository shoreExcursionRepository;

    @InjectMocks private ScheduleMaterializationService materializationService;

    private Trip testTrip;
    private TripScheduleVersion testVersion;
    private Port portHph;
    private Port portDan;
    private Instant departureTime;

    @BeforeEach
    void setUp() {
        testTrip = new Trip();
        testTrip.setTripId(UUID.randomUUID());
        testTrip.setTripCode("TR-TEST");
        testTrip.setName("Hải Trình Di Sản Miền Trung");
        testTrip.setStatus(TripStatus.READY);
        testTrip.setPlannedScope("DOMESTIC");
        testTrip.setDurationDays(3);
        testTrip.setDurationGroup(com.casms.trip.enums.TripDurationGroup.LONG);
        testTrip.setDurationNights(2);

        portHph = new Port();
        portHph.setPortId(UUID.randomUUID());
        portHph.setPortCode("VN-HPH");
        portHph.setPortName("Cảng Hải Phòng");
        portHph.setCountryCode("VN");
        portHph.setStatus(PortStatus.ACTIVE);
        portHph.setTimeZoneId("Asia/Ho_Chi_Minh");

        portDan = new Port();
        portDan.setPortId(UUID.randomUUID());
        portDan.setPortCode("VN-DAN");
        portDan.setPortName("Cảng Đà Nẵng");
        portDan.setCountryCode("VN");
        portDan.setStatus(PortStatus.ACTIVE);
        portDan.setTimeZoneId("Asia/Ho_Chi_Minh");

        testVersion = new TripScheduleVersion();
        testVersion.setVersionId(UUID.randomUUID());
        testVersion.setTrip(testTrip);
        testVersion.setVersionNumber(1);
        testVersion.setRevision(1L);
        testVersion.setStatus(TripScheduleVersionStatus.PUBLISHED);

        // Days
        List<TripPlanDay> days = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            TripPlanDay d = new TripPlanDay();
            d.setDayId(UUID.randomUUID());
            d.setVersion(testVersion);
            d.setDayNumber(i);
            d.setTitle("Ngày " + i);
            d.setDayType(i == 2 ? ItineraryDayType.SEA_DAY : ItineraryDayType.PORT_DAY);
            days.add(d);
        }
        testVersion.setDays(days);

        // Stops
        List<TripPlanStop> stops = new ArrayList<>();
        TripPlanStop s1 = new TripPlanStop();
        s1.setStopId(UUID.randomUUID());
        s1.setVersion(testVersion);
        s1.setPort(portHph);
        s1.setStopOrder(1);
        s1.setArrivalDayNumber(1);
        s1.setPlannedArrivalTime(LocalTime.of(8, 0));
        s1.setDepartureDayNumber(1);
        s1.setPlannedDepartureTime(LocalTime.of(18, 0));
        s1.setCallType(CruiseStopCallType.NORMAL);
        s1.setArrivalMethod(CruiseStopArrivalMethod.BERTH);
        stops.add(s1);

        TripPlanStop s2 = new TripPlanStop();
        s2.setStopId(UUID.randomUUID());
        s2.setVersion(testVersion);
        s2.setPort(portDan);
        s2.setStopOrder(2);
        s2.setArrivalDayNumber(3);
        s2.setPlannedArrivalTime(LocalTime.of(9, 0));
        s2.setDepartureDayNumber(3);
        s2.setPlannedDepartureTime(LocalTime.of(17, 0));
        s2.setCallType(CruiseStopCallType.NORMAL);
        s2.setArrivalMethod(CruiseStopArrivalMethod.BERTH);
        stops.add(s2);

        testVersion.setStops(stops);
        testVersion.setEntries(new ArrayList<>());

        lenient()
                .when(portRepository.lockDirectory(any()))
                .thenAnswer(
                        i ->
                                Optional.of(
                                        i.getArgument(0).equals(portHph.getPortId())
                                                ? portHph
                                                : portDan));
        lenient()
                .when(portWindowEvaluator.evaluate(any(), any(), any(), any()))
                .thenReturn(
                        List.of(
                                new com.casms.ship.dto.PortWindowContract.Check(
                                        portHph.getPortId(),
                                        "ARRIVAL",
                                        "PASS",
                                        "MATCHED",
                                        "Matched",
                                        null,
                                        "Asia/Ho_Chi_Minh",
                                        UUID.randomUUID(),
                                        1L,
                                        "Authority",
                                        null,
                                        null,
                                        Instant.now(),
                                        List.of())));
        lenient()
                .when(portWindowEvaluator.departureMovement(any(), any(), any()))
                .thenReturn(List.of());
        departureTime = Instant.parse("2026-11-01T01:00:00Z"); // 08:00 UTC+7
    }

    @Test
    @DisplayName("E02: previewSchedule - tính toán chính xác ngày, giờ, sequence không commit DB")
    void previewSchedule_success() {
        when(tripScheduleVersionRepository.findById(testVersion.getVersionId()))
                .thenReturn(Optional.of(testVersion));

        CruiseSchedulePreviewResponse res =
                materializationService.previewSchedule(testVersion.getVersionId(), departureTime);

        assertNotNull(res);
        assertEquals(testTrip.getTripId(), res.getSourceTripId());
        assertEquals(testVersion.getVersionId(), res.getSourceVersionId());
        assertEquals(3, res.getDays().size());

        // Ngày 1 có stop 1
        CruiseSchedulePreviewResponse.ProjectedDayDto day1 = res.getDays().get(0);
        assertEquals(1, day1.getDayNumber());
        assertEquals(LocalDate.of(2026, 11, 1), day1.getCalendarDate());
        assertEquals(1, day1.getStops().size());
        assertEquals("VN-HPH", day1.getStops().get(0).getPortCode());
        assertFalse(day1.getStops().getFirst().getIsDisembarkation());

        // Ngày 2 là sea day
        CruiseSchedulePreviewResponse.ProjectedDayDto day2 = res.getDays().get(1);
        assertEquals(2, day2.getDayNumber());
        assertEquals(LocalDate.of(2026, 11, 2), day2.getCalendarDate());
        assertTrue(day2.getStops().isEmpty());

        // Ngày 3 có stop 2
        CruiseSchedulePreviewResponse.ProjectedDayDto day3 = res.getDays().get(2);
        assertEquals(3, day3.getDayNumber());
        assertEquals(LocalDate.of(2026, 11, 3), day3.getCalendarDate());
        assertEquals(1, day3.getStops().size());
        assertEquals("VN-DAN", day3.getStops().get(0).getPortCode());
        assertTrue(day3.getStops().getFirst().getIsDisembarkation());

        // DB không bị ghi
        verify(itineraryRepository, never()).save(any());
        verify(cruiseStopRepository, never()).save(any());
    }

    @Test
    void previewLabelsOnlyTheLastVisitWhenReturningToAnEarlierPort() {
        TripPlanStop intermediate = new TripPlanStop();
        intermediate.setStopId(UUID.randomUUID());
        intermediate.setStopOrder(2);
        intermediate.setPort(portDan);
        intermediate.setArrivalDayNumber(2);
        intermediate.setDepartureDayNumber(2);
        intermediate.setPlannedArrivalTime(LocalTime.of(9, 0));
        intermediate.setPlannedDepartureTime(LocalTime.of(18, 0));
        var finalCall = testVersion.getStops().getLast();
        finalCall.setStopOrder(3);
        finalCall.setPort(portHph);
        finalCall.setIsDisembarkation(true);
        testVersion.getStops().add(1, intermediate);
        testVersion.getDays().get(1).setDayType(ItineraryDayType.PORT_DAY);
        when(tripScheduleVersionRepository.findById(testVersion.getVersionId()))
                .thenReturn(Optional.of(testVersion));

        var result = materializationService.previewSchedule(testVersion.getVersionId(), departureTime);
        var calls = result.getDays().stream().flatMap(day -> day.getStops().stream()).toList();
        assertEquals(List.of(false, false, true), calls.stream()
                .map(CruiseSchedulePreviewResponse.ProjectedStopDto::getIsDisembarkation).toList());
        assertEquals(calls.getFirst().getPortId(), calls.getLast().getPortId());
        assertEquals(calls.getLast().getPortId(), result.getReturnPortId());
    }

    @Test
    @DisplayName("previewSchedule: ném lỗi nếu version chưa PUBLISHED")
    void previewSchedule_throwsIfNotPublished() {
        testVersion.setStatus(TripScheduleVersionStatus.DRAFT);
        when(tripScheduleVersionRepository.findById(testVersion.getVersionId()))
                .thenReturn(Optional.of(testVersion));

        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () ->
                                materializationService.previewSchedule(
                                        testVersion.getVersionId(), departureTime));
        assertEquals("VERSION_NOT_PUBLISHED", ex.getCode());
    }

    @Test
    @DisplayName("previewSchedule: ném lỗi nếu Trip bị ARCHIVED")
    void previewSchedule_throwsIfTripArchived() {
        testTrip.setStatus(TripStatus.ARCHIVED);
        when(tripScheduleVersionRepository.findById(testVersion.getVersionId()))
                .thenReturn(Optional.of(testVersion));

        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () ->
                                materializationService.previewSchedule(
                                        testVersion.getVersionId(), departureTime));
        assertEquals("TRIP_ARCHIVED", ex.getCode());
    }

    @Test
    @DisplayName("E03: materializeSchedule - tạo Itinerary, Days và CruiseStops thực tế trong DB")
    void materializeSchedule_success() {
        Cruise cruise = new Cruise();
        cruise.setCruiseId(UUID.randomUUID());
        cruise.setCruiseCode("CR-001");
        cruise.setCruiseName("Chuyến đi 001");
        cruise.setDepartureDatetime(departureTime);
        cruise.setReturnDatetime(departureTime.plusSeconds(86400 * 3));
        cruise.setDeparturePort(portHph);
        cruise.setReturnPort(portDan);
        cruise.setSourceTrip(testTrip);
        cruise.setSourceVersion(testVersion);
        cruise.setSourceVersionRevision(testVersion.getRevision());

        when(itineraryRepository.findByCruiseCruiseId(cruise.getCruiseId()))
                .thenReturn(Optional.empty());
        when(itineraryRepository.save(any(Itinerary.class)))
                .thenAnswer(
                        inv -> {
                            Itinerary it = inv.getArgument(0);
                            it.setItineraryId(UUID.randomUUID());
                            return it;
                        });
        when(itineraryDayRepository.save(any(ItineraryDay.class)))
                .thenAnswer(
                        inv -> {
                            ItineraryDay day = inv.getArgument(0);
                            day.setItineraryDayId(UUID.randomUUID());
                            return day;
                        });
        when(cruiseStopRepository.save(any(CruiseStop.class)))
                .thenAnswer(
                        inv -> {
                            CruiseStop stop = inv.getArgument(0);
                            stop.setCruiseStopId(UUID.randomUUID());
                            return stop;
                        });

        when(operationalProgramMappingService.mapOperationalPrograms(cruise, testVersion))
                .thenReturn(
                        com.casms.ship.dto.response.OperationalProgramSyncResponse.builder()
                                .items(List.of())
                                .build());
        Itinerary result = materializationService.materializeSchedule(cruise, testVersion);

        assertNotNull(result);
        assertEquals(ItineraryStatus.DRAFT, result.getStatus());
        verify(itineraryRepository).save(any(Itinerary.class));
        verify(itineraryDayRepository, times(3)).save(any(ItineraryDay.class));
        verify(cruiseStopRepository, times(2)).save(any(CruiseStop.class));
    }

    @Test
    @DisplayName("E04: getCruiseSchedule - đọc lại lịch thực tế từ database")
    void getCruiseSchedule_success() {
        Cruise cruise = new Cruise();
        cruise.setCruiseId(UUID.randomUUID());
        cruise.setCruiseCode("CR-001");
        cruise.setCruiseName("Chuyến đi 001");
        cruise.setDepartureDatetime(departureTime);
        cruise.setReturnDatetime(departureTime.plusSeconds(86400 * 3));
        cruise.setDeparturePort(portHph);
        cruise.setReturnPort(portDan);
        cruise.setSourceTrip(testTrip);
        cruise.setSourceVersion(testVersion);
        cruise.setSourceVersionRevision(testVersion.getRevision());

        Itinerary itinerary = new Itinerary();
        itinerary.setItineraryId(UUID.randomUUID());
        itinerary.setCruise(cruise);
        itinerary.setStatus(ItineraryStatus.DRAFT);

        ItineraryDay day1 = new ItineraryDay();
        day1.setItineraryDayId(UUID.randomUUID());
        day1.setItinerary(itinerary);
        day1.setDayNumber(1);
        day1.setDate(LocalDate.of(2026, 11, 1));
        day1.setDayType(ItineraryDayType.PORT_DAY);

        CruiseStop stop1 = new CruiseStop();
        stop1.setCruiseStopId(UUID.randomUUID());
        stop1.setItinerary(itinerary);
        stop1.setArrivalItineraryDay(day1);
        stop1.setDepartureItineraryDay(day1);
        stop1.setPort(portHph);
        stop1.setPortFacts(com.casms.ship.dto.PortFacts.capture(portHph));
        stop1.setStopOrder(1);
        stop1.setPlannedArrival(departureTime);
        stop1.setPlannedDeparture(departureTime.plusSeconds(36000));
        stop1.setStatus(CruiseStopStatus.PLANNED);
        stop1.setCallType(CruiseStopCallType.NORMAL);
        stop1.setNotes("sourceStopId:" + testVersion.getStops().get(0).getStopId());

        when(cruiseRepository.findById(cruise.getCruiseId())).thenReturn(Optional.of(cruise));
        when(itineraryRepository.findByCruiseCruiseId(cruise.getCruiseId()))
                .thenReturn(Optional.of(itinerary));
        when(itineraryDayRepository.findByItineraryItineraryIdOrderByDayNumberAsc(
                        itinerary.getItineraryId()))
                .thenReturn(List.of(day1));
        when(cruiseStopRepository.findByItineraryItineraryIdOrderByStopOrderAsc(
                        itinerary.getItineraryId()))
                .thenReturn(List.of(stop1));
        when(onboardActivityRepository.findByCruise_CruiseId(cruise.getCruiseId()))
                .thenReturn(Collections.emptyList());
        when(shoreExcursionRepository.findByCruiseStop_Itinerary_Cruise_CruiseId(
                        cruise.getCruiseId()))
                .thenReturn(Collections.emptyList());

        CruiseSchedulePreviewResponse res =
                materializationService.getCruiseSchedule(cruise.getCruiseId());

        assertNotNull(res);
        assertTrue(res.getMaterialized());
        assertEquals(itinerary.getItineraryId(), res.getItineraryId());
        assertEquals(testTrip.getTripId(), res.getSourceTripId());
        assertEquals(1, res.getDays().size());
        assertEquals(1, res.getDays().get(0).getStops().size());
        assertEquals("VN-HPH", res.getDays().get(0).getStops().get(0).getPortCode());
        assertEquals(
                stop1.getCruiseStopId(), res.getDays().get(0).getStops().get(0).getCruiseStopId());
        assertEquals(
                testVersion.getStops().get(0).getStopId(),
                res.getDays().get(0).getStops().get(0).getSourceStopId());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "missing-group",
                "missing-days",
                "wrong-group",
                "missing-nights",
                "wrong-count",
                "duplicate-day"
            })
    void invalidSourceDurationNeverFallsBackToDayCount(String invalid) {
        switch (invalid) {
            case "missing-group" -> testTrip.setDurationGroup(null);
            case "missing-days" -> testTrip.setDurationDays(null);
            case "wrong-group" ->
                    testTrip.setDurationGroup(com.casms.trip.enums.TripDurationGroup.SHORT);
            case "missing-nights" -> testTrip.setDurationNights(null);
            case "wrong-count" -> testVersion.getDays().removeLast();
            case "duplicate-day" -> testVersion.getDays().getLast().setDayNumber(1);
        }
        when(tripScheduleVersionRepository.findById(testVersion.getVersionId()))
                .thenReturn(Optional.of(testVersion));
        var error =
                assertThrows(
                        BusinessException.class,
                        () ->
                                materializationService.previewSchedule(
                                        testVersion.getVersionId(), departureTime));
        assertEquals("SOURCE_DURATION_INVALID", error.getCode());
        verifyNoInteractions(
                cruiseRepository,
                itineraryRepository,
                itineraryDayRepository,
                cruiseStopRepository);
    }

    @Test
    void seaDepartureProjectionKeepsRoleAndDoesNotInventDeparturePort() {
        testVersion.getDays().get(0).setDayType(ItineraryDayType.SEA_DAY);
        testVersion.getStops().removeFirst();
        testVersion.getStops().getFirst().setIsDisembarkation(true);
        when(tripScheduleVersionRepository.findById(testVersion.getVersionId()))
                .thenReturn(Optional.of(testVersion));
        var response =
                materializationService.previewSchedule(testVersion.getVersionId(), departureTime);
        assertEquals(ItineraryDayRole.DEPARTURE, response.getDays().getFirst().getDayRole());
        assertEquals(ItineraryDayType.SEA_DAY, response.getDays().getFirst().getDayType());
        assertTrue(response.getDays().getFirst().getStops().isEmpty());
        assertEquals(ItineraryDayRole.ARRIVAL, response.getDays().getLast().getDayRole());
        assertNull(response.getDeparturePortId());
        assertEquals(portDan.getPortId(), response.getReturnPortId());
    }

    @Test
    void seaDepartureUsesExplicitOriginWithoutCreatingAPortCall() {
        testVersion.getDays().getFirst().setDayType(ItineraryDayType.SEA_DAY);
        testVersion.getDays().getFirst().setDeparturePort(portHph);
        testVersion.getDays().getFirst().setPlannedDepartureTime(LocalTime.of(8, 15));
        testVersion.getStops().removeFirst();
        when(tripScheduleVersionRepository.findById(testVersion.getVersionId()))
                .thenReturn(Optional.of(testVersion));
        var response =
                materializationService.previewSchedule(testVersion.getVersionId(), departureTime);
        assertEquals(portHph.getPortId(), response.getDeparturePortId());
        assertTrue(response.getDays().getFirst().getStops().isEmpty());
        assertEquals(ItineraryDayType.SEA_DAY, response.getDays().getFirst().getDayType());
    }
}
