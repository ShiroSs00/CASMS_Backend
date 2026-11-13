package com.casms.activity.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.casms.activity.dto.request.ShoreExcursionRequest;
import com.casms.activity.entity.*;
import com.casms.activity.enums.*;
import com.casms.activity.repository.*;
import com.casms.common.exception.BusinessException;
import com.casms.ship.entity.*;
import com.casms.ship.repository.CabinRepository;
import com.casms.ship.repository.CruiseStopRepository;
import com.casms.ship.repository.DeckRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@ExtendWith(MockitoExtension.class)
class ProgramCapacityGuardTest {
    @Test
    void newActivityAndTourWritesRejectHistoricalCurrencyBeforeLookingUpRecords() {
        var input = activity(10);
        input.setCurrencyCode("VND");
        var activityError =
                assertThrows(
                        BusinessException.class,
                        () -> onboard.updateActivity(UUID.randomUUID(), input, null, null));
        assertEquals("CURRENCY_USD_REQUIRED", activityError.getCode());
        var tourError =
                assertThrows(
                        BusinessException.class,
                        () ->
                                shore.updateCommercial(
                                        UUID.randomUUID(), 10, BigDecimal.ZERO, "VND"));
        assertEquals("CURRENCY_USD_REQUIRED", tourError.getCode());
        verifyNoInteractions(activities, excursionRepository);
    }

    @Mock OnboardActivityRepository activities;
    @Mock ShoreExcursionRepository excursionRepository;
    @Mock RegistrationRepository registrationRepository;
    @Mock SupplierRepository supplierRepository;
    @Mock CruiseStopRepository cruiseStopRepository;
    @Mock DeckRepository deckRepository;
    @Mock CabinRepository cabinRepository;
    @InjectMocks ActivityService onboard;
    @InjectMocks ShoreExcursionService shore;

    private OnboardActivity activity(int capacity) {
        var a = new OnboardActivity();
        a.setOnboardActivityId(UUID.randomUUID());
        a.setActivityCode("QA-CAP");
        a.setActivityName("Capacity fixture");
        a.setStartTime(Instant.parse("2028-03-01T02:00:00Z"));
        a.setEndTime(Instant.parse("2028-03-01T03:00:00Z"));
        a.setCapacity(capacity);
        a.setStatus(OnboardActivityStatus.PLANNED);
        a.setPrice(BigDecimal.ZERO);
        a.setCurrencyCode("USD");
        return a;
    }

    @Test
    void onboardRejectsBelowAllocatedBeforeChangingManagedEntity() {
        var existing = activity(10);
        UUID id = existing.getOnboardActivityId();
        when(activities.lockActivity(id)).thenReturn(Optional.of(existing));
        when(registrationRepository.sumOnboardParticipants(eq(id), any())).thenReturn(5L);
        var error =
                assertThrows(
                        BusinessException.class,
                        () -> onboard.updateActivity(id, activity(4), null, null));
        assertEquals("CAPACITY_BELOW_ALLOCATED", error.getCode());
        assertEquals(5L, error.getDetails().get("allocatedParticipants"));
        assertEquals(10, existing.getCapacity());
        verify(activities, never()).save(any());
    }

    @Test
    void onboardAllowsExactAllocatedCountAndKeepsZeroPrice() {
        var existing = activity(10);
        UUID id = existing.getOnboardActivityId();
        when(activities.lockActivity(id)).thenReturn(Optional.of(existing));
        when(registrationRepository.sumOnboardParticipants(eq(id), any())).thenReturn(5L);
        var saved = onboard.updateActivity(id, activity(5), null, null);
        assertEquals(5, saved.getCapacity());
        assertEquals(BigDecimal.ZERO, saved.getPrice());
        verify(activities).lockActivity(id);
        verify(activities, never()).findById(id);
    }

    private ShoreExcursionRequest input(int capacity, UUID stop, UUID supplier) {
        return new ShoreExcursionRequest(
                stop,
                UUID.randomUUID(),
                supplier,
                "Capacity shore",
                Instant.parse("2028-03-01T02:00:00Z"),
                Instant.parse("2028-03-01T03:00:00Z"),
                "Gate",
                1,
                capacity,
                BigDecimal.ZERO,
                "USD");
    }

    @Test
    void shoreRejectsBelowAllocatedBeforeApplyingRequest() {
        UUID id = UUID.randomUUID();
        var e = new ShoreExcursion();
        e.setShoreExcursionId(id);
        e.setStatus(ShoreExcursionStatus.OPEN);
        e.setMaximumCapacity(10);
        when(excursionRepository.lockById(id)).thenReturn(Optional.of(e));
        when(registrationRepository.sumParticipants(eq(id), any())).thenReturn(5L);
        var error =
                assertThrows(
                        BusinessException.class,
                        () -> shore.update(id, input(4, UUID.randomUUID(), UUID.randomUUID())));
        assertEquals("CAPACITY_BELOW_ALLOCATED", error.getCode());
        assertEquals("maximumCapacity", error.getDetails().get("fieldPath"));
        assertEquals(10, e.getMaximumCapacity());
        verify(excursionRepository, never()).save(any());
        verifyNoInteractions(supplierRepository, cruiseStopRepository);
    }

    @Test
    void activityTimesMustFitCruiseEvenWhenPriceAndCapacityAreValid() {
        for (boolean beforeDeparture : List.of(true, false)) {
            var a = activity(5);
            var c = new Cruise();
            c.setDepartureDatetime(
                    Instant.parse(
                            beforeDeparture ? "2028-03-01T02:30:00Z" : "2028-03-01T01:00:00Z"));
            c.setReturnDatetime(Instant.parse("2028-03-01T02:45:00Z"));
            a.setCruise(c);
            var error =
                    assertThrows(BusinessException.class, () -> onboard.validateActivity(a, null));
            assertEquals("ACTIVITY_OUTSIDE_CRUISE", error.getCode());
            assertEquals(
                    beforeDeparture ? "startTime" : "endTime", error.getDetails().get("fieldPath"));
        }
        verifyNoInteractions(activities);
    }

    @Test
    void commercialExcursionPatchCannotReduceReservedCapacity() {
        UUID id = UUID.randomUUID();
        var e = new ShoreExcursion();
        e.setShoreExcursionId(id);
        e.setStatus(ShoreExcursionStatus.PLANNED);
        e.setMaximumCapacity(10);
        when(excursionRepository.lockById(id)).thenReturn(Optional.of(e));
        when(registrationRepository.sumParticipants(eq(id), any())).thenReturn(5L);
        var error =
                assertThrows(
                        BusinessException.class,
                        () -> shore.updateCommercial(id, 4, BigDecimal.ZERO, "USD"));
        assertEquals("CAPACITY_BELOW_ALLOCATED", error.getCode());
        assertEquals(10, e.getMaximumCapacity());
        verify(excursionRepository, never()).save(any());
    }

    @Test
    void unconfiguredMaterializedActivityCannotOpenRegistrations() {
        var a = activity(10);
        a.setCapacity(null);
        when(activities.findById(a.getOnboardActivityId())).thenReturn(Optional.of(a));
        assertThrows(
                IllegalArgumentException.class,
                () -> onboard.lifecycle(a.getOnboardActivityId(), "open"));
        assertEquals(OnboardActivityStatus.PLANNED, a.getStatus());
    }

    @Test
    void crossShipAndCrossDeckVenueRejectBeforeChangingActivity() {
        var ship = new Ship();
        ship.setShipId(UUID.randomUUID());
        var foreignShip = new Ship();
        foreignShip.setShipId(UUID.randomUUID());
        var cruise = new Cruise();
        cruise.setShip(ship);
        var ownDeck = new Deck();
        ownDeck.setDeckId(UUID.randomUUID());
        ownDeck.setShip(ship);
        var otherDeck = new Deck();
        otherDeck.setDeckId(UUID.randomUUID());
        otherDeck.setShip(foreignShip);
        var cabin = new Cabin();
        cabin.setCabinId(UUID.randomUUID());
        cabin.setShip(foreignShip);
        cabin.setDeck(otherDeck);
        var existing = activity(10);
        existing.setCruise(cruise);
        existing.setDeck(ownDeck);
        when(activities.lockActivity(existing.getOnboardActivityId()))
                .thenReturn(Optional.of(existing));
        when(deckRepository.findById(otherDeck.getDeckId())).thenReturn(Optional.of(otherDeck));
        var error =
                assertThrows(
                        BusinessException.class,
                        () ->
                                onboard.updateActivity(
                                        existing.getOnboardActivityId(),
                                        activity(5),
                                        otherDeck.getDeckId(),
                                        null));
        assertEquals("ACTIVITY_VENUE_MISMATCH", error.getCode());
        assertEquals("deckId", error.getDetails().get("fieldPath"));
        assertEquals(10, existing.getCapacity());
        assertSame(ownDeck, existing.getDeck());
        when(deckRepository.findById(ownDeck.getDeckId())).thenReturn(Optional.of(ownDeck));
        when(cabinRepository.findById(cabin.getCabinId())).thenReturn(Optional.of(cabin));
        error =
                assertThrows(
                        BusinessException.class,
                        () ->
                                onboard.updateActivity(
                                        existing.getOnboardActivityId(),
                                        activity(5),
                                        ownDeck.getDeckId(),
                                        cabin.getCabinId()));
        assertEquals("cabinId", error.getDetails().get("fieldPath"));
        assertEquals(10, existing.getCapacity());
        cabin.setShip(ship);
        otherDeck.setShip(ship);
        error =
                assertThrows(
                        BusinessException.class,
                        () ->
                                onboard.updateActivity(
                                        existing.getOnboardActivityId(),
                                        activity(5),
                                        ownDeck.getDeckId(),
                                        cabin.getCabinId()));
        assertEquals("cabinId", error.getDetails().get("fieldPath"));
        assertNull(existing.getCabin());
        verify(activities, never()).save(any());
    }
}
