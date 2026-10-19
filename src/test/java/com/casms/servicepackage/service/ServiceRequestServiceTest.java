package com.casms.servicepackage.service;

import com.casms.identity.entity.Passenger;
import com.casms.identity.entity.User;
import com.casms.servicepackage.entity.ServiceRequest;
import com.casms.servicepackage.enums.ServiceRequestStatus;
import com.casms.servicepackage.repository.ServiceRepository;
import com.casms.servicepackage.repository.ServiceRequestRepository;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.identity.repository.PassengerRepository;
import com.casms.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceRequestServiceTest {
 @Mock ServiceRequestRepository requests;
 @Mock ServiceRepository services;
 @Mock BookingPassengerRepository bookingPassengers;
 @Mock PassengerRepository passengers;
 @Mock UserRepository users;
 @InjectMocks ServiceRequestService service;

 @Test void lifecycle_assignStartComplete_setsCompletionTimeAndRejectsRepeat(){
  UUID id=UUID.randomUUID(),staffId=UUID.randomUUID();ServiceRequest r=new ServiceRequest();r.setStatus(ServiceRequestStatus.REQUESTED);User staff=new User();staff.setUserId(staffId);
  when(requests.lockById(id)).thenReturn(Optional.of(r));when(users.findById(staffId)).thenReturn(Optional.of(staff));
  service.transition(id,staffId,"assign");service.transition(id,staffId,"accept");service.transition(id,staffId,"start");service.transition(id,staffId,"complete");
  assertEquals(ServiceRequestStatus.COMPLETED,r.getStatus());assertNotNull(r.getCompletedAt());assertThrows(IllegalStateException.class,()->service.transition(id,staffId,"complete"));
 }

 @Test void updatePending_rejectsAnotherPassengersRequest(){
  UUID id=UUID.randomUUID(),userId=UUID.randomUUID();User owner=new User();owner.setUserId(UUID.randomUUID());Passenger passenger=new Passenger();passenger.setUser(owner);ServiceRequest r=new ServiceRequest();r.setPassenger(passenger);r.setStatus(ServiceRequestStatus.REQUESTED);
  when(requests.lockById(id)).thenReturn(Optional.of(r));
  assertThrows(AccessDeniedException.class,()->service.updatePending(id,userId,false,2,"change"));
 }

 @Test void transition_rejectsStartingBeforeAcceptance(){
  UUID id=UUID.randomUUID(),staffId=UUID.randomUUID();ServiceRequest r=new ServiceRequest();r.setStatus(ServiceRequestStatus.REQUESTED);User staff=new User();staff.setUserId(staffId);r.setAssignedUser(staff);
  when(requests.lockById(id)).thenReturn(Optional.of(r));
  assertThrows(IllegalStateException.class,()->service.transition(id,staffId,"start"));
 }
}
