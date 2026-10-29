package com.casms.servicepackage.service;

import com.casms.booking.entity.BookingPassenger;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.identity.entity.Passenger;
import com.casms.identity.entity.User;
import com.casms.identity.repository.PassengerRepository;
import com.casms.identity.repository.UserRepository;
import com.casms.servicepackage.entity.Service;
import com.casms.servicepackage.entity.ServiceRequest;
import com.casms.servicepackage.entity.ServiceSchedule;
import com.casms.servicepackage.enums.ServiceRequestStatus;
import com.casms.servicepackage.enums.ServiceStatus;
import com.casms.servicepackage.repository.ServiceRepository;
import com.casms.servicepackage.repository.ServiceRequestRepository;
import com.casms.servicepackage.repository.ServiceScheduleRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@org.springframework.stereotype.Service @RequiredArgsConstructor @Transactional
public class ServiceRequestService {
 private final ServiceRequestRepository requests;
 private final ServiceRepository services;
 private final BookingPassengerRepository bookingPassengers;
 private final PassengerRepository passengers;
 private final UserRepository users;
 private final ServiceScheduleRepository schedules;

 public List<ServiceRequest> list(UUID passengerId, boolean staff) {
  if (!staff) return requests.findByPassenger_User_UserId(passengerId);
  return requests.findAll();
 }
 public ServiceRequest get(UUID id, UUID userId, boolean staff) {
  ServiceRequest r=requests.findById(id).orElseThrow(()->missing("Service request",id));
  assertOwner(r,userId,staff); return r;
 }
 public ServiceRequest create(UUID userId, UUID serviceId, UUID bookingPassengerId, Integer quantity, String note) {
  return create(userId, serviceId, bookingPassengerId, null, quantity, note, null);
 }

 public ServiceRequest create(UUID userId, UUID serviceId, UUID bookingPassengerId, UUID scheduleId,
                              Integer quantity, String note, String idempotencyKey) {
  if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      var existing = requests.findByIdempotencyKey(idempotencyKey);
      if (existing.isPresent()) {
          ServiceRequest request = existing.get();
          assertOwner(request, userId, false);
          if (!request.getService().getServiceId().equals(serviceId)
                  || !Objects.equals(request.getServiceSchedule() == null ? null : request.getServiceSchedule().getServiceScheduleId(), scheduleId)
                  || request.getQuantity() != (quantity == null ? 1 : quantity)) {
              throw new IllegalArgumentException("Idempotency key was already used for a different service request");
          }
          return request;
      }
  }
  Passenger passenger=passengers.findByUser_UserId(userId).orElseThrow(()->new IllegalStateException("Authenticated user has no passenger profile"));
  // Serialize capacity checks for this service to prevent concurrent reservations exceeding capacity.
  var s=services.findByIdForUpdate(serviceId).orElseThrow(()->missing("Service",serviceId));
  if(s.getStatus()!=ServiceStatus.ACTIVE) throw new IllegalStateException("Service is not available");
  int effectiveQuantity = quantity == null ? 1 : quantity;
  if(effectiveQuantity<1)throw new IllegalArgumentException("Quantity must be positive");
  if (scheduleId == null) assertCapacity(s, effectiveQuantity, 0);
  ServiceSchedule schedule = null;
  if (scheduleId != null) {
      schedule = schedules.lockById(scheduleId).orElseThrow(() -> missing("Service schedule", scheduleId));
      if (!schedule.getService().getServiceId().equals(serviceId)) throw new IllegalArgumentException("Schedule does not belong to the requested service");
      if (!"OPEN".equals(schedule.getStatus())) throw new IllegalStateException("Service schedule is not open");
      long reserved = requests.sumActiveQuantityForSchedule(scheduleId,
              List.of(ServiceRequestStatus.REQUESTED, ServiceRequestStatus.ACCEPTED, ServiceRequestStatus.IN_PROGRESS));
      if (reserved + effectiveQuantity > schedule.getCapacity()) throw new IllegalStateException("Requested quantity exceeds remaining schedule capacity");
  } else if (s.isRequiresSchedule()) {
      throw new IllegalArgumentException("This service requires a schedule reservation");
  }
  BookingPassenger bp=null;
  if(bookingPassengerId!=null){
      bp=bookingPassengers.findById(bookingPassengerId).orElseThrow(()->missing("Booking passenger",bookingPassengerId));
      assertSamePassenger(bp,passenger);
      if(bp.getBooking().getBookingStatus()!=BookingStatus.Confirmed)throw new IllegalStateException("Booking is not confirmed");
  } else {
      var candidates = bookingPassengers.findByPassenger_PassengerId(passenger.getPassengerId())
          .stream().filter(b -> b.getBooking().getBookingStatus() == BookingStatus.Confirmed).toList();
      if (candidates.size() == 1) {
          bp = candidates.get(0);
      }
  }
  ServiceRequest r=new ServiceRequest(); r.setPassenger(passenger); r.setService(s); r.setServiceSchedule(schedule); r.setUnitPriceSnapshot(s.getPrice()); r.setCurrencySnapshot(s.getCurrencyCode()); r.setIdempotencyKey(idempotencyKey==null||idempotencyKey.isBlank()?null:idempotencyKey); r.setBookingPassenger(bp); r.setBooking(bp==null?null:bp.getBooking()); r.setQuantity(effectiveQuantity); r.setNote(note); r.setRequestTime(Instant.now()); r.setStatus(ServiceRequestStatus.REQUESTED); return requests.save(r);
 }
 public ServiceRequest updatePending(UUID id, UUID userId, boolean staff, Integer quantity, String note) {
  ServiceRequest r=requests.lockById(id).orElseThrow(()->missing("Service request",id));assertOwner(r,userId,staff); if(r.getStatus()!=ServiceRequestStatus.REQUESTED)throw new IllegalStateException("Only requested service requests may be edited");
  if(quantity==null||quantity<1)throw new IllegalArgumentException("Quantity must be positive");
  var service=services.findByIdForUpdate(r.getService().getServiceId()).orElseThrow(()->missing("Service",r.getService().getServiceId()));
  assertCapacity(service, quantity, r.getQuantity());
  r.setQuantity(quantity); r.setNote(note); return r;
 }
 public ServiceRequest transition(UUID id, UUID userId, String action) {
  ServiceRequest r=requests.lockById(id).orElseThrow(()->missing("Service request",id));
  switch(action){
   case "assign"->{if(r.getStatus()!=ServiceRequestStatus.REQUESTED)invalid(); User user=users.findById(userId).orElseThrow(()->missing("User",userId));r.setAssignedUser(user);}
   case "accept"->{if(r.getStatus()!=ServiceRequestStatus.REQUESTED)invalid();r.setStatus(ServiceRequestStatus.ACCEPTED);}
   case "reject"->{if(r.getStatus()!=ServiceRequestStatus.REQUESTED)invalid();r.setStatus(ServiceRequestStatus.REJECTED);}
   case "start"->{if(r.getStatus()!=ServiceRequestStatus.ACCEPTED||r.getAssignedUser()==null||!r.getAssignedUser().getUserId().equals(userId))invalid();r.setStatus(ServiceRequestStatus.IN_PROGRESS);}
   case "complete"->{if(r.getStatus()!=ServiceRequestStatus.IN_PROGRESS||r.getAssignedUser()==null||!r.getAssignedUser().getUserId().equals(userId))invalid();r.setStatus(ServiceRequestStatus.COMPLETED);r.setCompletedAt(Instant.now());}
   case "cancel"->{if(r.getStatus()!=ServiceRequestStatus.REQUESTED&&r.getStatus()!=ServiceRequestStatus.ACCEPTED)invalid();r.setStatus(ServiceRequestStatus.CANCELLED);}
   default->throw new IllegalArgumentException("Unsupported request action");
  }
  return r;
 }
 public void assertOwner(ServiceRequest r, UUID userId, boolean staff){if(staff)return; if(r.getPassenger().getUser()==null||!r.getPassenger().getUser().getUserId().equals(userId))throw new AccessDeniedException("Service request belongs to another passenger");}
 private void assertCapacity(Service service, long requestedQuantity, int existingQuantity) {
  if (service.getCapacity() == null) return;
  long used = activeQuantity(service.getServiceId()) - existingQuantity;
  if (used + requestedQuantity > service.getCapacity()) {
   throw new IllegalStateException("Requested quantity exceeds remaining service capacity");
  }
 }
 private long activeQuantity(UUID serviceId) {
  return requests.sumActiveQuantity(serviceId, List.of(ServiceRequestStatus.REQUESTED, ServiceRequestStatus.ACCEPTED, ServiceRequestStatus.IN_PROGRESS));
 }
 private void assertSamePassenger(BookingPassenger bp,Passenger passenger){if(!bp.getPassenger().getPassengerId().equals(passenger.getPassengerId()))throw new AccessDeniedException("Booking passenger does not belong to authenticated passenger");}
 private static void invalid(){throw new IllegalStateException("Invalid service request transition");}
 private static EntityNotFoundException missing(String what,UUID id){return new EntityNotFoundException(what+" not found: "+id);}
}
