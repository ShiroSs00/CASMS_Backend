package com.casms.servicepackage.service;

import com.casms.identity.entity.Passenger;
import com.casms.identity.entity.User;
import com.casms.servicepackage.entity.ServiceRequest;
import com.casms.servicepackage.entity.Service;
import com.casms.servicepackage.entity.ServiceSchedule;
import com.casms.servicepackage.enums.ServiceRequestStatus;
import com.casms.servicepackage.repository.ServiceRepository;
import com.casms.servicepackage.repository.ServiceRequestRepository;
import com.casms.servicepackage.repository.ServiceScheduleRepository;
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
import java.util.List;
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
 @Mock ServiceScheduleRepository schedules;
 @Mock com.casms.pos.PosLedgerService posLedger;
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

 @Test void create_rejectsQuantityBeyondRemainingCapacity(){
  UUID userId=UUID.randomUUID(), serviceId=UUID.randomUUID(), passengerId=UUID.randomUUID();
  User user=new User();user.setUserId(userId);Passenger passenger=new Passenger();passenger.setPassengerId(passengerId);passenger.setUser(user);
  Service catalogService=new Service();catalogService.setServiceId(serviceId);catalogService.setCapacity(5);catalogService.setStatus(com.casms.servicepackage.enums.ServiceStatus.ACTIVE);
  when(passengers.findByUser_UserId(userId)).thenReturn(Optional.of(passenger));
  when(services.findByIdForUpdate(serviceId)).thenReturn(Optional.of(catalogService));
  when(requests.sumActiveQuantity(eq(serviceId), any())).thenReturn(4L);

  assertThrows(IllegalStateException.class,()->service.create(userId,serviceId,null,2,"demo"));
  verify(services).findByIdForUpdate(serviceId);
  verify(requests,never()).save(any());
 }

 @Test void updatePending_rechecksCapacityAgainstOtherRequests(){
  UUID requestId=UUID.randomUUID(),userId=UUID.randomUUID(),serviceId=UUID.randomUUID();
  Service catalogService=new Service();catalogService.setServiceId(serviceId);catalogService.setCapacity(5);
  ServiceRequest request=new ServiceRequest();request.setStatus(ServiceRequestStatus.REQUESTED);request.setService(catalogService);request.setQuantity(3);
  when(requests.lockById(requestId)).thenReturn(Optional.of(request));
  when(services.findByIdForUpdate(serviceId)).thenReturn(Optional.of(catalogService));
  when(requests.sumActiveQuantity(eq(serviceId),any())).thenReturn(4L);

  assertThrows(IllegalStateException.class,()->service.updatePending(requestId,userId,true,5,"update"));
 }

 @Test void create_rejectsScheduleWhenLastSlotsAreAlreadyReserved(){
  UUID userId=UUID.randomUUID(), serviceId=UUID.randomUUID(), scheduleId=UUID.randomUUID();
  User user=new User();user.setUserId(userId);Passenger passenger=new Passenger();passenger.setUser(user);
  Service catalogService=new Service();catalogService.setServiceId(serviceId);catalogService.setStatus(com.casms.servicepackage.enums.ServiceStatus.ACTIVE);catalogService.setRequiresSchedule(true);
  ServiceSchedule schedule=new ServiceSchedule();schedule.setServiceScheduleId(scheduleId);schedule.setService(catalogService);schedule.setCapacity(4);schedule.setStatus("OPEN");
  when(passengers.findByUser_UserId(userId)).thenReturn(Optional.of(passenger));
  when(services.findByIdForUpdate(serviceId)).thenReturn(Optional.of(catalogService));
  when(schedules.lockById(scheduleId)).thenReturn(Optional.of(schedule));
  when(requests.sumActiveQuantityForSchedule(eq(scheduleId),any())).thenReturn(3L);

  assertThrows(IllegalStateException.class,()->service.create(userId,serviceId,null,scheduleId,2,"demo","request-1"));
  verify(schedules).lockById(scheduleId);
  verify(requests,never()).save(any());
 }
}
