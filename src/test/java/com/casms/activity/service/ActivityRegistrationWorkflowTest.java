package com.casms.activity.service;

import com.casms.activity.entity.OnboardActivity;
import com.casms.activity.entity.Registration;
import com.casms.activity.enums.OnboardActivityStatus;
import com.casms.activity.enums.RegistrationStatus;
import com.casms.activity.repository.*;
import com.casms.booking.entity.Booking;
import com.casms.booking.entity.BookingPassenger;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.identity.entity.Passenger;
import com.casms.ship.entity.Cruise;
import com.casms.ship.repository.CruiseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityRegistrationWorkflowTest {
 @Mock OnboardActivityRepository activities;
 @Mock RegistrationRepository registrations;
 @Mock ParticipationRepository participations;
 @Mock BookingPassengerRepository bookingPassengers;
 @Mock CruiseRepository cruises;
 @Mock ShoreExcursionRepository excursions;
 @InjectMocks ActivityService service;

 @Test void register_reservesCapacityAsPendingAndValidatesBookingCruise(){
  UUID bpId=UUID.randomUUID(),activityId=UUID.randomUUID(),cruiseId=UUID.randomUUID(),userId=UUID.randomUUID();
  Passenger passenger=new Passenger();passenger.setPassengerId(UUID.randomUUID());Booking booking=new Booking();booking.setBookingStatus(BookingStatus.Confirmed);Cruise cruise=new Cruise();cruise.setCruiseId(cruiseId);booking.setCruise(cruise);BookingPassenger bp=new BookingPassenger();bp.setPassenger(passenger);bp.setBooking(booking);
  OnboardActivity a=new OnboardActivity();a.setOnboardActivityId(activityId);a.setCruise(cruise);a.setStatus(OnboardActivityStatus.OPEN);a.setCapacity(3);a.setEndTime(Instant.now().plusSeconds(120));
  when(bookingPassengers.findById(bpId)).thenReturn(Optional.of(bp));when(activities.lockActivity(activityId)).thenReturn(Optional.of(a));when(registrations.existsByBookingPassenger_BookingPassengerIdAndOnboardActivity_OnboardActivityIdAndStatusIn(eq(bpId),eq(activityId),any())).thenReturn(false);when(registrations.sumOnboardParticipants(eq(activityId),any())).thenReturn(0L);when(registrations.save(any(Registration.class))).thenAnswer(invocation->invocation.getArgument(0));
  Registration result=service.register(bpId,activityId,2,userId,true);
  assertEquals(RegistrationStatus.PENDING,result.getStatus());assertSame(passenger,result.getPassenger());verify(participations,never()).save(any());
 }

 @Test void register_rejectsWhenCapacityWouldBeExceeded(){
  UUID bpId=UUID.randomUUID(),activityId=UUID.randomUUID(),cruiseId=UUID.randomUUID(),userId=UUID.randomUUID();Passenger passenger=new Passenger();Booking booking=new Booking();booking.setBookingStatus(BookingStatus.Confirmed);Cruise cruise=new Cruise();cruise.setCruiseId(cruiseId);booking.setCruise(cruise);BookingPassenger bp=new BookingPassenger();bp.setPassenger(passenger);bp.setBooking(booking);OnboardActivity a=new OnboardActivity();a.setOnboardActivityId(activityId);a.setCruise(cruise);a.setStatus(OnboardActivityStatus.OPEN);a.setCapacity(1);a.setEndTime(Instant.now().plusSeconds(120));
  when(bookingPassengers.findById(bpId)).thenReturn(Optional.of(bp));when(activities.lockActivity(activityId)).thenReturn(Optional.of(a));when(registrations.existsByBookingPassenger_BookingPassengerIdAndOnboardActivity_OnboardActivityIdAndStatusIn(eq(bpId),eq(activityId),any())).thenReturn(false);when(registrations.sumOnboardParticipants(eq(activityId),any())).thenReturn(0L);
  assertThrows(IllegalStateException.class,()->service.register(bpId,activityId,2,userId,true));verify(registrations,never()).save(any());
 }

 @Test void register_rejectsAnotherPassengersBookingForPassengerActor(){
  UUID bpId=UUID.randomUUID(),activityId=UUID.randomUUID(),userId=UUID.randomUUID();Passenger passenger=new Passenger();passenger.setPassengerId(UUID.randomUUID());com.casms.identity.entity.User owner=new com.casms.identity.entity.User();owner.setUserId(UUID.randomUUID());passenger.setUser(owner);Booking booking=new Booking();booking.setBookingStatus(BookingStatus.Confirmed);BookingPassenger bp=new BookingPassenger();bp.setPassenger(passenger);bp.setBooking(booking);OnboardActivity a=new OnboardActivity();a.setOnboardActivityId(activityId);
  when(bookingPassengers.findById(bpId)).thenReturn(Optional.of(bp));
  assertThrows(AccessDeniedException.class,()->service.register(bpId,activityId,1,userId,false));verify(activities,never()).lockActivity(any());
 }
}
