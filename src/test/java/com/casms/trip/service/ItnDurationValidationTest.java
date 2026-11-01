package com.casms.trip.service;

import com.casms.common.exception.BusinessException;
import com.casms.ship.enums.ItineraryDayType;
import com.casms.trip.entity.Trip;
import com.casms.trip.entity.TripPlanDay;
import com.casms.trip.entity.TripScheduleVersion;
import com.casms.trip.enums.TripDurationGroup;
import com.casms.trip.enums.TripScheduleVersionStatus;
import com.casms.trip.enums.TripStatus;
import com.casms.trip.repository.TripScheduleVersionRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ItnDurationValidationTest {
    private final TripScheduleVersionRepository versions = mock(TripScheduleVersionRepository.class);
    private final TripScheduleVersionService service = new TripScheduleVersionService(
            null, versions, null, null, null, null, null, null, null, null);

    private TripScheduleVersion itinerary(String group, int count) {
        Trip trip = new Trip();
        trip.setDurationGroup(TripDurationGroup.valueOf(group));
        trip.setDurationDays(count);
        trip.setDurationNights(Math.max(0, count - 1));
        trip.setStatus(TripStatus.DRAFT);
        TripScheduleVersion version = new TripScheduleVersion();
        version.setTrip(trip);
        version.setStatus(TripScheduleVersionStatus.DRAFT);
        version.setRevision(1L);
        version.setDays(new ArrayList<>());
        for (int i = 1; i <= count; i++) {
            TripPlanDay day = new TripPlanDay();
            day.setDayNumber(i);
            day.setDayType(ItineraryDayType.PORT_DAY);
            day.setShipTimeZoneId("Asia/Ho_Chi_Minh");
            version.getDays().add(day);
        }
        return version;
    }

    @ParameterizedTest
    @CsvSource({"SHORT,1", "SHORT,2", "LONG,3", "LONG,4", "LONG,5"})
    void builtDayCountFitsSelectedGroup(String group, int count) {
        var violations = service.validateVersionInternal(itinerary(group, count));
        assertTrue(violations.stream().noneMatch(v -> "INVALID_DURATION".equals(v.getCode())));
        // Other completeness requirements (ports/stops) are deliberately not claimed valid here.
    }

    @ParameterizedTest
    @CsvSource({"SHORT,3", "LONG,2", "LONG,6"})
    void validationAndFinalizationRejectWrongGroup(String group, int count) {
        var version = itinerary(group, count);
        assertTrue(service.validateVersionInternal(version).stream()
                .anyMatch(v -> "INVALID_DURATION".equals(v.getCode())));
        UUID id = UUID.randomUUID();
        when(versions.findById(id)).thenReturn(Optional.of(version));
        assertEquals("INVALID_DURATION", assertThrows(BusinessException.class,
                () -> service.finalizeVersion(id, 1L, UUID.randomUUID())).getCode());
        verify(versions, never()).save(any());
    }
}
