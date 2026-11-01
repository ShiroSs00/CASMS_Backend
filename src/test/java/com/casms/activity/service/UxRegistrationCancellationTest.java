package com.casms.activity.service;

import com.casms.activity.entity.*;
import com.casms.activity.enums.*;
import com.casms.activity.repository.*;
import com.casms.booking.entity.*;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.identity.entity.Passenger;
import com.casms.ship.repository.CruiseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class UxRegistrationCancellationTest {
    @Mock OnboardActivityRepository activities;
    @Mock RegistrationRepository registrations;
    @Mock ParticipationRepository participations;
    @Mock BookingPassengerRepository bookingPassengers;
    @Mock CruiseRepository cruises;
    @Mock ShoreExcursionRepository excursions;
    @InjectMocks ActivityService service;

    Registration registration(boolean started) {
        var passenger=new Passenger(); passenger.setPassengerId(UUID.randomUUID());
        var booking=new Booking(); booking.setBookingStatus(BookingStatus.Confirmed);
        var bp=new BookingPassenger(); bp.setPassenger(passenger); bp.setBooking(booking);
        var a=new OnboardActivity(); a.setOnboardActivityId(UUID.randomUUID()); a.setStatus(OnboardActivityStatus.FULL); a.setCapacity(5);
        a.setStartTime(Instant.now().plusSeconds(started ? -120 : 120));
        var r=new Registration(); r.setRegistrationId(UUID.randomUUID()); r.setBookingPassenger(bp); r.setPassenger(passenger);
        r.setOnboardActivity(a); r.setStatus(RegistrationStatus.CONFIRMED); r.setQuantity(3);
        when(registrations.lockById(r.getRegistrationId())).thenReturn(Optional.of(r)); return r;
    }
    @Test void cancellationChangesOnlySelectedRegistrationParticipationAndAvailability() {
        var r=registration(false); var p=new Participation(); p.setStatus(ParticipationStatus.REGISTERED);
        when(participations.findByRegistration_RegistrationId(r.getRegistrationId())).thenReturn(Optional.of(p));
        when(registrations.sumOnboardParticipants(eq(r.getOnboardActivity().getOnboardActivityId()),any())).thenReturn(2L);
        service.cancelRegistration(r.getRegistrationId(),"test reason",UUID.randomUUID(),true);
        assertEquals(RegistrationStatus.CANCELLED,r.getStatus()); assertEquals(ParticipationStatus.CANCELLED,p.getStatus());
        assertEquals(OnboardActivityStatus.OPEN,r.getOnboardActivity().getStatus()); assertEquals(3,r.getQuantity());
        assertEquals(BookingStatus.Confirmed,r.getBookingPassenger().getBooking().getBookingStatus());
        verifyNoInteractions(bookingPassengers,cruises);
    }
    @Test void cancellationAfterStartRejectsWithoutUpdatingRelatedRecords() {
        var r=registration(true);
        assertThrows(IllegalStateException.class,()->service.cancelRegistration(r.getRegistrationId(),"",UUID.randomUUID(),true));
        assertEquals(RegistrationStatus.CONFIRMED,r.getStatus()); assertEquals(OnboardActivityStatus.FULL,r.getOnboardActivity().getStatus());
        verifyNoInteractions(participations);
    }
    @Test void duplicateRegistrationRejectsBeforeReservingMoreCapacity() {
        var passenger=new Passenger(); var bp=new BookingPassenger(); bp.setPassenger(passenger);
        var cruise=new com.casms.ship.entity.Cruise(); cruise.setCruiseId(UUID.randomUUID());
        var booking=new Booking(); booking.setBookingStatus(BookingStatus.Confirmed); booking.setCruise(cruise); bp.setBooking(booking);
        UUID bpId=UUID.randomUUID(), activityId=UUID.randomUUID();
        var a=new OnboardActivity(); a.setOnboardActivityId(activityId); a.setCruise(cruise); a.setStatus(OnboardActivityStatus.OPEN);
        a.setEndTime(Instant.now().plusSeconds(120));
        when(bookingPassengers.findById(bpId)).thenReturn(Optional.of(bp)); when(activities.lockActivity(activityId)).thenReturn(Optional.of(a));
        when(registrations.existsByBookingPassenger_BookingPassengerIdAndOnboardActivity_OnboardActivityIdAndStatusIn(eq(bpId),eq(activityId),any())).thenReturn(true);
        assertThrows(IllegalStateException.class,()->service.register(bpId,activityId,2,UUID.randomUUID(),true));
        verify(registrations,never()).save(any()); verifyNoInteractions(participations);
    }
}
