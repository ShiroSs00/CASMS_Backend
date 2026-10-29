package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduleMaterializationServiceTest {

    @Mock private TripScheduleVersionRepository tripScheduleVersionRepository;
    @Mock private ItineraryRepository itineraryRepository;
    @Mock private ItineraryDayRepository itineraryDayRepository;
    @Mock private CruiseStopRepository cruiseStopRepository;
    @Mock private CruiseRepository cruiseRepository;
    @Mock private PortRepository portRepository;
    @Mock private com.casms.ship.service.OperationalProgramMappingService operationalProgramMappingService;
    @Mock private com.casms.activity.repository.OnboardActivityRepository onboardActivityRepository;
    @Mock private com.casms.activity.repository.ShoreExcursionRepository shoreExcursionRepository;

    @InjectMocks
    private ScheduleMaterializationService materializationService;

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

        departureTime = Instant.parse("2026-11-01T01:00:00Z"); // 08:00 UTC+7
    }

    @Test
    @DisplayName("E02: previewSchedule - tính toán chính xác ngày, giờ, sequence không commit DB")
    void previewSchedule_success() {
        when(tripScheduleVersionRepository.findById(testVersion.getVersionId()))
                .thenReturn(Optional.of(testVersion));

        CruiseSchedulePreviewResponse res = materializationService.previewSchedule(testVersion.getVersionId(), departureTime);

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

        // DB không bị ghi
        verify(itineraryRepository, never()).save(any());
        verify(cruiseStopRepository, never()).save(any());
    }

    @Test
    @DisplayName("previewSchedule: ném lỗi nếu version chưa PUBLISHED")
    void previewSchedule_throwsIfNotPublished() {
        testVersion.setStatus(TripScheduleVersionStatus.DRAFT);
        when(tripScheduleVersionRepository.findById(testVersion.getVersionId()))
                .thenReturn(Optional.of(testVersion));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> materializationService.previewSchedule(testVersion.getVersionId(), departureTime));
        assertEquals("VERSION_NOT_PUBLISHED", ex.getCode());
    }

    @Test
    @DisplayName("previewSchedule: ném lỗi nếu Trip bị ARCHIVED")
    void previewSchedule_throwsIfTripArchived() {
        testTrip.setStatus(TripStatus.ARCHIVED);
        when(tripScheduleVersionRepository.findById(testVersion.getVersionId()))
                .thenReturn(Optional.of(testVersion));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> materializationService.previewSchedule(testVersion.getVersionId(), departureTime));
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

        when(itineraryRepository.findByCruiseCruiseId(cruise.getCruiseId())).thenReturn(Optional.empty());
        when(itineraryRepository.save(any(Itinerary.class))).thenAnswer(inv -> {
            Itinerary it = inv.getArgument(0);
            it.setItineraryId(UUID.randomUUID());
            return it;
        });
        when(itineraryDayRepository.save(any(ItineraryDay.class))).thenAnswer(inv -> {
            ItineraryDay day = inv.getArgument(0);
            day.setItineraryDayId(UUID.randomUUID());
            return day;
        });
        when(cruiseStopRepository.save(any(CruiseStop.class))).thenAnswer(inv -> {
            CruiseStop stop = inv.getArgument(0);
            stop.setCruiseStopId(UUID.randomUUID());
            return stop;
        });

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
        stop1.setStopOrder(1);
        stop1.setPlannedArrival(departureTime);
        stop1.setPlannedDeparture(departureTime.plusSeconds(36000));
        stop1.setStatus(CruiseStopStatus.PLANNED);
        stop1.setCallType(CruiseStopCallType.NORMAL);
        stop1.setNotes("sourceStopId:" + testVersion.getStops().get(0).getStopId());

        when(cruiseRepository.findById(cruise.getCruiseId())).thenReturn(Optional.of(cruise));
        when(itineraryRepository.findByCruiseCruiseId(cruise.getCruiseId())).thenReturn(Optional.of(itinerary));
        when(itineraryDayRepository.findByItineraryItineraryIdOrderByDayNumberAsc(itinerary.getItineraryId()))
                .thenReturn(List.of(day1));
        when(cruiseStopRepository.findByItineraryItineraryIdOrderByStopOrderAsc(itinerary.getItineraryId()))
                .thenReturn(List.of(stop1));
        when(onboardActivityRepository.findByCruise_CruiseId(cruise.getCruiseId())).thenReturn(Collections.emptyList());
        when(shoreExcursionRepository.findByCruiseStop_Itinerary_Cruise_CruiseId(cruise.getCruiseId())).thenReturn(Collections.emptyList());

        CruiseSchedulePreviewResponse res = materializationService.getCruiseSchedule(cruise.getCruiseId());

        assertNotNull(res);
        assertTrue(res.getMaterialized());
        assertEquals(itinerary.getItineraryId(), res.getItineraryId());
        assertEquals(testTrip.getTripId(), res.getSourceTripId());
        assertEquals(1, res.getDays().size());
        assertEquals(1, res.getDays().get(0).getStops().size());
        assertEquals("VN-HPH", res.getDays().get(0).getStops().get(0).getPortCode());
        assertEquals(stop1.getCruiseStopId(), res.getDays().get(0).getStops().get(0).getCruiseStopId());
        assertEquals(testVersion.getStops().get(0).getStopId(), res.getDays().get(0).getStops().get(0).getSourceStopId());
    }
}
