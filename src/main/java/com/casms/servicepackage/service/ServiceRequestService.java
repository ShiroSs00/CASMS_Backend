package com.casms.servicepackage.service;

import com.casms.booking.entity.BookingPassenger;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.identity.entity.Passenger;
import com.casms.identity.entity.User;
import com.casms.identity.repository.PassengerRepository;
import com.casms.identity.repository.UserRepository;
import com.casms.servicepackage.entity.ServiceRequest;
import com.casms.servicepackage.enums.ServiceRequestStatus;
import com.casms.servicepackage.repository.ServiceRepository;
import com.casms.servicepackage.repository.ServiceRequestRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service @RequiredArgsConstructor @Transactional
public class ServiceRequestService {
 private final ServiceRequestRepository requests;
 private final ServiceRepository services;
 private final BookingPassengerRepository bookingPassengers;
 private final PassengerRepository passengers;
 private final UserRepository users;

 public List<ServiceRequest> list(UUID passengerId, boolean staff) {
  if (!staff) return requests.findByPassenger_User_UserId(passengerId);
  return requests.findAll();
 }
 public ServiceRequest get(UUID id, UUID userId, boolean staff) {
  ServiceRequest r=requests.findById(id).orElseThrow(()->missing("Service request",id));
  assertOwner(r,userId,staff); return r;
 }
 public ServiceRequest create(UUID userId, UUID serviceId, UUID bookingPassengerId, Integer quantity, String note) {
  Passenger passenger=passengers.findByUser_UserId(userId).orElseThrow(()->new IllegalStateException("Authenticated user has no passenger profile"));
  var s=services.findById(serviceId).orElseThrow(()->missing("Service",serviceId));
  if(s.getStatus()!=com.casms.servicepackage.enums.ServiceStatus.ACTIVE) throw new IllegalStateException("Service is not available");
  int effectiveQuantity = (quantity==null||quantity<1) ? 1 : quantity;
  if(s.getCapacity()!=null&&effectiveQuantity>s.getCapacity())throw new IllegalStateException("Requested quantity exceeds service capacity");
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
  ServiceRequest r=new ServiceRequest(); r.setPassenger(passenger); r.setService(s); r.setBookingPassenger(bp); r.setBooking(bp==null?null:bp.getBooking()); r.setQuantity(effectiveQuantity); r.setNote(note); r.setRequestTime(Instant.now()); r.setStatus(ServiceRequestStatus.REQUESTED); return requests.save(r);
 }
 public ServiceRequest updatePending(UUID id, UUID userId, boolean staff, Integer quantity, String note) {
  ServiceRequest r=requests.lockById(id).orElseThrow(()->missing("Service request",id));assertOwner(r,userId,staff); if(r.getStatus()!=ServiceRequestStatus.REQUESTED)throw new IllegalStateException("Only requested service requests may be edited");
  if(quantity==null||quantity<1)throw new IllegalArgumentException("Quantity must be positive"); r.setQuantity(quantity); r.setNote(note); return r;
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
 public void assertOwner(ServiceRequest r, UUID userId, boolean staff){if(staff)return; if(r.getPassenger().getUser()==null||!r.getPassenger().getUser().getUserId().equals(userId))throw new org.springframework.security.access.AccessDeniedException("Service request belongs to another passenger");}
 private void assertSamePassenger(BookingPassenger bp,Passenger passenger){if(!bp.getPassenger().getPassengerId().equals(passenger.getPassengerId()))throw new org.springframework.security.access.AccessDeniedException("Booking passenger does not belong to authenticated passenger");}
 private static void invalid(){throw new IllegalStateException("Invalid service request transition");}
 private static EntityNotFoundException missing(String what,UUID id){return new EntityNotFoundException(what+" not found: "+id);}
}
