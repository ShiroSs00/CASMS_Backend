package com.casms.ship.service;

import static org.junit.jupiter.api.Assertions.*;

import com.casms.common.exception.BusinessException;
import com.casms.ship.dto.request.CruiseCreateRequest;
import com.casms.ship.dto.request.CruiseUpdateDraftRequest;
import com.casms.ship.entity.Port;
import com.casms.ship.enums.CruiseCruiseType;
import com.casms.ship.enums.ItineraryDayType;
import com.casms.ship.enums.ItineraryType;
import com.casms.ship.enums.PortStatus;
import com.casms.ship.repository.PortRepository;
import com.casms.trip.dto.request.TripCreateRequest;
import com.casms.trip.entity.TripPlanStop;
import com.casms.trip.enums.TripDurationGroup;
import com.casms.trip.enums.TripScheduleVersionStatus;
import com.casms.trip.repository.TripRepository;
import com.casms.trip.repository.TripScheduleVersionRepository;
import com.casms.trip.service.TripService;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Explicit opt-in; refuses every datasource except an isolated ITN-01 test database. */
@SpringBootTest
@Transactional
@EnabledIfSystemProperty(named = "itn.duration.integration", matches = "true")
class ItnDurationPostgresTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        String url = System.getProperty("itn.duration.jdbcUrl", "");
        if (!url.matches(
                "jdbc:postgresql://(?:localhost|127\\.0\\.0\\.1):5432/casms_itn01_[0-9_]+")) {
            throw new IllegalStateException(
                    "ITN-01 integration requires an isolated casms_itn01_* database");
        }
        String password = System.getenv("DB_PASSWORD");
        if (password == null)
            throw new IllegalStateException("Test database credentials are required");
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> "postgres");
        properties.add("spring.datasource.password", () -> password);
        properties.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired TripService trips;
    @Autowired TripRepository tripRepository;
    @Autowired TripScheduleVersionRepository versions;
    @Autowired PortRepository ports;
    @Autowired CruiseService cruises;
    @Autowired ScheduleMaterializationService schedules;
    @Autowired EntityManager em;

    @ParameterizedTest
    @CsvSource({"SHORT,1", "SHORT,2", "LONG,3", "LONG,4", "LONG,5"})
    void createsTripThenDepartureWithAuthoritativeSourceDuration(String group, int days) {
        TripCreateRequest tripInput = new TripCreateRequest();
        tripInput.setName("ITN-01 duration fixture");
        tripInput.setDurationGroup(TripDurationGroup.valueOf(group));
        tripInput.setDurationDays(days);
        tripInput.setTripType(ItineraryType.ROUND_TRIP);
        tripInput.setDomesticRegions(List.of("BAC_BO"));
        var trip = trips.createTrip(tripInput, null);
        em.flush();
        em.clear();
        var version =
                versions.findByTrip_TripIdOrderByVersionNumberAsc(trip.getTripId()).getFirst();
        Port port = new Port();
        port.setPortCode("VN-ITN-" + UUID.randomUUID().toString().substring(0, 8));
        port.setPortName("ITN duration test port");
        port.setCountryCode("VN");
        port.setDomesticRegion("BAC_BO");
        port.setTimeZoneId("Asia/Ho_Chi_Minh");
        port.setStatus(PortStatus.ACTIVE);
        port = ports.save(port);
        for (var day : version.getDays()) {
            day.setDayType(
                    day.getDayNumber() == 1 || day.getDayNumber() == days
                            ? ItineraryDayType.PORT_DAY
                            : ItineraryDayType.SEA_DAY);
        }
        var first = new TripPlanStop();
        first.setVersion(version);
        first.setClientKey("origin");
        first.setPort(port);
        first.setStopOrder(1);
        first.setArrivalDayNumber(1);
        first.setDepartureDayNumber(1);
        first.setPlannedArrivalTime(LocalTime.of(8, 0));
        first.setPlannedDepartureTime(LocalTime.of(days == 1 ? 18 : 9, 0));
        first.setIsEmbarkation(true);
        first.setIsDisembarkation(days == 1);
        var stops = new ArrayList<TripPlanStop>();
        stops.add(first);
        if (days > 1) {
            var last = new TripPlanStop();
            last.setVersion(version);
            last.setClientKey("return");
            last.setPort(port);
            last.setStopOrder(2);
            last.setArrivalDayNumber(days);
            last.setDepartureDayNumber(days);
            last.setPlannedArrivalTime(LocalTime.of(7, 0));
            last.setPlannedDepartureTime(LocalTime.of(8, 0));
            last.setIsDisembarkation(true);
            stops.add(last);
        }
        version.getStops().addAll(stops);
        version.setStatus(
                TripScheduleVersionStatus
                        .PUBLISHED); // controlled source fixture, not review testing
        versions.saveAndFlush(version);
        var expectedType = days == 1 ? CruiseCruiseType.ONE_DAY : CruiseCruiseType.MULTI_DAYS;
        var wrongType = days == 1 ? CruiseCruiseType.MULTI_DAYS : CruiseCruiseType.ONE_DAY;
        Instant departure = Instant.parse("2032-01-10T02:00:00Z");
        Instant forgedReturn = departure.plusSeconds(20 * 86400L);
        var input = new CruiseCreateRequest();
        input.setCruiseName("ITN inherited duration");
        input.setSourceVersionId(version.getVersionId());
        input.setExpectedSourceVersionRevision(version.getRevision());
        input.setDepartureDatetime(departure);
        input.setReturnDatetime(forgedReturn);
        input.setCruiseType(wrongType);
        var created = cruises.create(input);
        assertEquals(expectedType, created.getCruiseType());
        assertNotEquals(forgedReturn, created.getReturnDatetime());
        var schedule = schedules.getCruiseSchedule(created.getCruiseId());
        assertEquals(days, schedule.getDurationDays());
        assertEquals(days - 1, schedule.getDurationNights());
        assertEquals(days, schedule.getDays().size());
        assertEquals(
                days, tripRepository.findById(trip.getTripId()).orElseThrow().getDurationDays());
        if (days == 2)
            assertTrue(
                    created.getReturnDatetime().isBefore(departure.plusSeconds(86400)),
                    "Two calendar days inherit MULTI_DAYS even with fewer than 24 elapsed hours");
        var edit = new CruiseUpdateDraftRequest();
        edit.setCruiseType(wrongType);
        assertEquals(
                "SOURCE_CONFIGURATION_LOCKED",
                assertThrows(
                                BusinessException.class,
                                () -> cruises.updateDraft(created.getCruiseId(), edit))
                        .getCode());
    }
}
