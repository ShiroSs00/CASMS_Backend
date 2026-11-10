package com.casms.ship.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.casms.activity.entity.OnboardActivity;
import com.casms.activity.repository.*;
import com.casms.ship.entity.Cruise;
import com.casms.ship.repository.*;
import com.casms.trip.entity.*;
import com.casms.trip.enums.TripPlanEntryKind;
import com.casms.trip.repository.TripPlanEntryRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@ExtendWith(MockitoExtension.class)
class OperationalProgramUnknownInputTest {
    @Mock CruiseRepository cruises;
    @Mock ShipRepository ships;
    @Mock PortRepository ports;
    @Mock TripPlanEntryRepository entries;
    @Mock ItineraryRepository itineraries;
    @Mock CruiseStopRepository stops;
    @Mock OnboardActivityRepository activities;
    @Mock ShoreExcursionRepository excursions;
    @Mock SupplierRepository suppliers;
    @Mock RegistrationRepository registrations;
    @InjectMocks OperationalProgramMappingService service;

    private OnboardActivity map(
            Integer capacity, BigDecimal price, String currency, boolean hasSource) {
        var version = new TripScheduleVersion();
        version.setVersionId(UUID.randomUUID());
        var cruise = new Cruise();
        cruise.setCruiseId(UUID.randomUUID());
        cruise.setCruiseCode("QA-UNKNOWN");
        cruise.setDepartureDatetime(Instant.parse("2028-03-01T01:00:00Z"));
        cruise.setReturnDatetime(Instant.parse("2028-03-04T01:00:00Z"));
        var entry = new TripPlanEntry();
        entry.setEntryId(UUID.randomUUID());
        entry.setClientKey("draft-activity");
        entry.setKind(TripPlanEntryKind.ONBOARD_ACTIVITY);
        entry.setTitle("Unconfigured source program");
        entry.setStartDayNumber(1);
        entry.setEndDayNumber(1);
        entry.setPlannedStartTime(LocalTime.of(10, 0));
        entry.setPlannedEndTime(LocalTime.of(11, 0));
        entry.setLocationText("Lounge");
        if (hasSource) {
            var source = new OnboardActivity();
            source.setOnboardActivityId(UUID.randomUUID());
            source.setCapacity(capacity);
            source.setPrice(price);
            source.setCurrencyCode(currency);
            entry.setSourceActivityId(source.getOnboardActivityId());
            when(activities.findById(source.getOnboardActivityId()))
                    .thenReturn(Optional.of(source));
        }
        when(entries.findByVersion_VersionIdOrderByStartDayNumberAscPlannedStartTimeAsc(
                        version.getVersionId()))
                .thenReturn(List.of(entry));
        when(activities.save(any()))
                .thenAnswer(
                        i -> {
                            OnboardActivity a = i.getArgument(0);
                            a.setOnboardActivityId(UUID.randomUUID());
                            return a;
                        });
        var report = service.mapOperationalPrograms(cruise, version);
        assertEquals(
                hasSource && currency != null ? "CREATED" : "INCOMPLETE",
                report.getItems().getFirst().getStatus());
        var saved = ArgumentCaptor.forClass(OnboardActivity.class);
        verify(activities).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void missingOperationalInputsStayUnknownInsteadOfThirtySeats() {
        var a = map(null, null, null, false);
        assertNull(a.getCapacity());
        assertNull(a.getPrice());
        assertNull(a.getCurrencyCode());
        assertNull(a.getCategory());
    }

    @Test
    void missingSourceScheduleCannotInventEightOClockOrTwoHourDuration() {
        var version = new TripScheduleVersion();
        version.setVersionId(UUID.randomUUID());
        var cruise = new Cruise();
        cruise.setCruiseId(UUID.randomUUID());
        cruise.setDepartureDatetime(Instant.parse("2028-03-01T01:00:00Z"));
        cruise.setReturnDatetime(Instant.parse("2028-03-04T01:00:00Z"));
        var entry = new TripPlanEntry();
        entry.setEntryId(UUID.randomUUID());
        entry.setKind(TripPlanEntryKind.ONBOARD_ACTIVITY);
        entry.setStartDayNumber(1);
        entry.setEndDayNumber(1);
        entry.setPlannedEndTime(LocalTime.NOON);
        when(entries.findByVersion_VersionIdOrderByStartDayNumberAscPlannedStartTimeAsc(
                        version.getVersionId()))
                .thenReturn(List.of(entry));
        var failure =
                assertThrows(
                        com.casms.common.exception.BusinessException.class,
                        () -> service.mapOperationalPrograms(cruise, version));
        assertEquals("SOURCE_PROGRAM_SCHEDULE_REQUIRED", failure.getCode());
        verify(activities, never()).save(any());
    }

    @Test
    void sameTitleOnDifferentDaysKeepsTwoOccurrencesAndStableRetryIds() {
        var version = new TripScheduleVersion();
        version.setVersionId(UUID.randomUUID());
        var cruise = new Cruise();
        cruise.setCruiseId(UUID.randomUUID());
        cruise.setDepartureDatetime(Instant.parse("2028-03-01T01:00:00Z"));
        cruise.setReturnDatetime(Instant.parse("2028-03-04T01:00:00Z"));
        var source = new ArrayList<TripPlanEntry>();
        for (int day = 1; day <= 2; day++) {
            var entry = new TripPlanEntry();
            entry.setEntryId(UUID.randomUUID());
            entry.setClientKey(day == 1 ? "yoga!" : "yoga?");
            entry.setKind(TripPlanEntryKind.ONBOARD_ACTIVITY);
            entry.setTitle("Yoga");
            entry.setStartDayNumber(day);
            entry.setEndDayNumber(day);
            entry.setPlannedStartTime(LocalTime.of(10, 0));
            entry.setPlannedEndTime(LocalTime.of(11, 0));
            source.add(entry);
        }
        when(entries.findByVersion_VersionIdOrderByStartDayNumberAscPlannedStartTimeAsc(
                        version.getVersionId()))
                .thenReturn(source);
        var stored = new HashMap<String, OnboardActivity>();
        when(activities.findByActivityCode(anyString()))
                .thenAnswer(i -> Optional.ofNullable(stored.get(i.getArgument(0))));
        when(activities.save(any()))
                .thenAnswer(
                        i -> {
                            OnboardActivity row = i.getArgument(0);
                            if (row.getOnboardActivityId() == null)
                                row.setOnboardActivityId(UUID.randomUUID());
                            stored.put(row.getActivityCode(), row);
                            return row;
                        });
        var initial = service.mapOperationalPrograms(cruise, version);
        assertEquals(2, stored.size());
        assertNotEquals(
                initial.getItems().get(0).getTargetId(), initial.getItems().get(1).getTargetId());
        assertEquals(
                2, stored.values().stream().map(OnboardActivity::getStartTime).distinct().count());
        var retry = service.mapOperationalPrograms(cruise, version);
        assertEquals(2, stored.size());
        assertEquals(
                initial.getItems().stream().map(x -> x.getTargetId()).toList(),
                retry.getItems().stream().map(x -> x.getTargetId()).toList());
        verify(activities, never()).findByCruise_CruiseId(any());
    }

    @Test
    void zeroPriceDoesNotInventVietnameseCurrency() {
        var a = map(12, BigDecimal.ZERO, null, true);
        assertEquals(12, a.getCapacity());
        assertEquals(BigDecimal.ZERO, a.getPrice());
        assertNull(a.getCurrencyCode());
    }

    @Test
    void explicitFreeProgramPreservesConfiguredCurrency() {
        var a = map(12, BigDecimal.ZERO, "VND", true);
        assertEquals(12, a.getCapacity());
        assertEquals(BigDecimal.ZERO, a.getPrice());
        assertEquals("VND", a.getCurrencyCode());
    }

    @Test
    void shoreProgramUsesItsOwnStartAndEndCalendarDaysInPortTime() {
        var version = new TripScheduleVersion();
        version.setVersionId(UUID.randomUUID());
        var cruise = new Cruise();
        cruise.setCruiseId(UUID.randomUUID());
        cruise.setDepartureDatetime(Instant.parse("2028-03-01T01:00:00Z"));
        cruise.setReturnDatetime(Instant.parse("2028-03-04T01:00:00Z"));
        var port = new com.casms.ship.entity.Port();
        port.setPortId(UUID.randomUUID());
        port.setTimeZoneId("Asia/Singapore");
        var sourceStop = new TripPlanStop();
        sourceStop.setStopId(UUID.randomUUID());
        sourceStop.setPort(port);
        sourceStop.setStopOrder(1);
        var stop = new com.casms.ship.entity.CruiseStop();
        stop.setCruiseStopId(UUID.randomUUID());
        stop.setPort(port);
        stop.setStopOrder(1);
        stop.setNotes("sourceStopId:" + sourceStop.getStopId());
        stop.setSourceStopId(sourceStop.getStopId());
        stop.setPlannedArrival(Instant.parse("2028-03-01T14:00:00Z"));
        stop.setPlannedDeparture(Instant.parse("2028-03-02T04:00:00Z"));
        var itinerary = new com.casms.ship.entity.Itinerary();
        itinerary.setItineraryId(UUID.randomUUID());
        when(itineraries.findByCruiseCruiseId(cruise.getCruiseId()))
                .thenReturn(Optional.of(itinerary));
        when(stops.findByItineraryItineraryIdOrderByStopOrderAsc(itinerary.getItineraryId()))
                .thenReturn(List.of(stop));
        when(ports.findById(port.getPortId())).thenReturn(Optional.of(port));
        var supplier = new com.casms.activity.entity.Supplier();
        supplier.setStatus(com.casms.activity.enums.SupplierStatus.ACTIVE);
        supplier.setVerificationStatus(
                com.casms.activity.enums.SupplierVerificationStatus.VERIFIED);
        var template = new com.casms.activity.entity.ShoreExcursion();
        template.setShoreExcursionId(UUID.randomUUID());
        template.setSupplier(supplier);
        template.setMaximumCapacity(10);
        template.setMinimumParticipants(1);
        template.setPrice(BigDecimal.ZERO);
        template.setCurrencyCode("USD");
        template.setMeetingPoint("Pier");
        when(excursions.findById(template.getShoreExcursionId())).thenReturn(Optional.of(template));
        var entry = new TripPlanEntry();
        entry.setEntryId(UUID.randomUUID());
        entry.setKind(TripPlanEntryKind.SHORE_EXCURSION);
        entry.setStop(sourceStop);
        entry.setSourceExcursionId(template.getShoreExcursionId());
        entry.setTitle("Overnight shore program");
        entry.setStartDayNumber(1);
        entry.setEndDayNumber(2);
        entry.setPlannedStartTime(LocalTime.of(23, 0));
        entry.setPlannedEndTime(LocalTime.of(1, 0));
        when(entries.findByVersion_VersionIdOrderByStartDayNumberAscPlannedStartTimeAsc(
                        version.getVersionId()))
                .thenReturn(List.of(entry));
        when(excursions.save(any()))
                .thenAnswer(
                        i -> {
                            var row = (com.casms.activity.entity.ShoreExcursion) i.getArgument(0);
                            row.setShoreExcursionId(UUID.randomUUID());
                            return row;
                        });
        var report = service.mapOperationalPrograms(cruise, version);
        assertEquals(1, report.getShoreExcursionsCount());
        var saved = ArgumentCaptor.forClass(com.casms.activity.entity.ShoreExcursion.class);
        verify(excursions).save(saved.capture());
        assertEquals(Instant.parse("2028-03-01T15:00:00Z"), saved.getValue().getStartTime());
        assertEquals(
                Instant.parse("2028-03-01T17:00:00Z"), saved.getValue().getPlannedReturnTime());
    }

    @Test
    void legacyResyncCannotOverwriteCruiseOwnedPrograms() {
        var cruise = new Cruise();
        cruise.setCruiseId(UUID.randomUUID());
        cruise.setSourceVersion(new TripScheduleVersion());
        when(cruises.findById(cruise.getCruiseId())).thenReturn(Optional.of(cruise));
        when(activities.countByCruise_CruiseId(cruise.getCruiseId())).thenReturn(1L);
        var error =
                assertThrows(
                        com.casms.common.exception.BusinessException.class,
                        () -> service.syncProgramsForCruise(cruise.getCruiseId()));
        assertEquals("PROGRAMS_ALREADY_MATERIALIZED", error.getCode());
        assertEquals(409, error.getStatus().value());
        verify(activities, never()).save(any());
        verifyNoInteractions(entries);
    }
}
