package com.casms.trip.service;

import com.casms.common.exception.BusinessException;
import com.casms.common.service.BusinessCodeGenerator;
import com.casms.identity.entity.User;
import com.casms.identity.repository.UserRepository;
import com.casms.ship.enums.ItineraryType;
import com.casms.trip.dto.request.TripCreateRequest;
import com.casms.trip.dto.request.TripUpdateRequest;
import com.casms.trip.dto.response.TripDetailResponse;
import com.casms.trip.entity.Trip;
import com.casms.trip.entity.TripPlanDay;
import com.casms.trip.entity.TripScheduleVersion;
import com.casms.trip.enums.TripScheduleVersionStatus;
import com.casms.trip.enums.TripStatus;
import com.casms.trip.repository.TripPlanDayRepository;
import com.casms.trip.repository.TripRepository;
import com.casms.trip.repository.TripScheduleVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {

    @Mock
    private TripRepository tripRepository;

    @Mock
    private TripScheduleVersionRepository versionRepository;

    @Mock
    private TripPlanDayRepository dayRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BusinessCodeGenerator businessCodeGenerator;

    @Mock
    private com.casms.audit.AuditService auditService;

    @InjectMocks
    private TripService tripService;

    private UUID actorId;
    private User actor;

    @BeforeEach
    void setUp() {
        actorId = UUID.randomUUID();
        actor = new User();
        actor.setUserId(actorId);
        actor.setUsername("cruise_manager");
    }

    @Test
    void testCreateTripAutoGeneratesVersion1AndNRelativeDays() {
        TripCreateRequest request = new TripCreateRequest();
        request.setName("Vịnh Hạ Long 4N3Đ");
        request.setDurationDays(4);
        request.setTripType(ItineraryType.ROUND_TRIP);
        request.setShipRequirements("Tàu dưới 50k GT");

        when(businessCodeGenerator.next("TR")).thenReturn("TR-1001");
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> {
            Trip t = inv.getArgument(0);
            t.setTripId(UUID.randomUUID());
            return t;
        });
        when(versionRepository.save(any(TripScheduleVersion.class))).thenAnswer(inv -> {
            TripScheduleVersion v = inv.getArgument(0);
            v.setVersionId(UUID.randomUUID());
            return v;
        });

        TripDetailResponse res = tripService.createTrip(request, actorId);

        assertNotNull(res);
        assertEquals("TR-1001", res.getTripCode());
        assertEquals(4, res.getDurationDays());
        assertEquals(3, res.getDurationNights()); // Default N-1
        assertEquals(TripStatus.DRAFT, res.getStatus());
        assertEquals(1, res.getVersions().size());
        assertEquals(1, res.getVersions().get(0).getVersionNumber());
        assertEquals(TripScheduleVersionStatus.DRAFT, res.getVersions().get(0).getStatus());

        verify(versionRepository).save(argThat(v ->
                v.getVersionNumber() == 1
                && v.getDays().size() == 4
                && v.getDays().get(0).getDayNumber() == 1
                && v.getDays().get(0).getDayType() == null
                && v.getDays().get(3).getDayNumber() == 4
        ));
    }

    @Test
    void testCreateTripCustomDurationNights() {
        TripCreateRequest request = new TripCreateRequest();
        request.setName("Đà Nẵng Express 3N3Đ");
        request.setDurationDays(3);
        request.setDurationNights(3); // User overrides N-1
        request.setTripType(ItineraryType.ONE_WAY);

        when(businessCodeGenerator.next("TR")).thenReturn("TR-1002");
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> {
            Trip t = inv.getArgument(0);
            t.setTripId(UUID.randomUUID());
            return t;
        });
        when(versionRepository.save(any(TripScheduleVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        TripDetailResponse res = tripService.createTrip(request, actorId);

        assertNotNull(res);
        assertEquals(3, res.getDurationDays());
        assertEquals(3, res.getDurationNights());
    }

    @Test
    void testUpdateTripLockedWhenStructuralLockedOrPublished() {
        UUID tripId = UUID.randomUUID();
        Trip trip = new Trip();
        trip.setTripId(tripId);
        trip.setDurationDays(4);
        trip.setDurationNights(3);
        trip.setStructuralLockedAt(Instant.now());

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

        TripUpdateRequest updateRequest = new TripUpdateRequest();
        updateRequest.setDurationDays(5);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tripService.updateTrip(tripId, updateRequest, actorId));
        assertEquals("TRIP_DURATION_LOCKED", ex.getCode());
    }

    @Test
    void testUpdateTripAllowsModifyingDurationWhenOnlyDraft() {
        UUID tripId = UUID.randomUUID();
        Trip trip = new Trip();
        trip.setTripId(tripId);
        trip.setTripCode("TR-1001");
        trip.setDurationDays(3);
        trip.setDurationNights(2);

        TripScheduleVersion draftVersion = new TripScheduleVersion();
        draftVersion.setVersionNumber(1);
        draftVersion.setStatus(TripScheduleVersionStatus.DRAFT);
        draftVersion.setRevision(1L);

        List<TripPlanDay> days = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            TripPlanDay day = new TripPlanDay();
            day.setDayNumber(i);
            days.add(day);
        }
        draftVersion.setDays(days);

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(versionRepository.findByTrip_TripIdOrderByVersionNumberAsc(tripId))
                .thenReturn(List.of(draftVersion));
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));

        TripUpdateRequest updateRequest = new TripUpdateRequest();
        updateRequest.setDurationDays(5);
        updateRequest.setDurationNights(4);

        TripDetailResponse res = tripService.updateTrip(tripId, updateRequest, actorId);

        assertEquals(5, res.getDurationDays());
        assertEquals(4, res.getDurationNights());
        assertEquals(5, draftVersion.getDays().size());
        verify(versionRepository).save(draftVersion);
    }
}
