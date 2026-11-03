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
 private final com.casms.pos.PosLedgerService posLedger;
 private final com.casms.cruisepackage.service.CruisePackageEntitlementService fareEntitlements;
 private final com.casms.servicepackage.repository.ServiceUsageRepository usages;
 private final jakarta.persistence.EntityManager entityManager;
 private final com.casms.audit.AuditService audit;

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
  return reserve(passenger,serviceId,bookingPassengerId,scheduleId,quantity,note,idempotencyKey);
 }
 /** The POS caller must validate employee/device scope and spending membership before reserving. */
 public ServiceRequest createForPos(UUID bookingPassengerId,UUID serviceId,UUID scheduleId,Integer quantity) {
  var bp=bookingPassengers.findById(bookingPassengerId).orElseThrow(()->missing("Booking passenger",bookingPassengerId));
  return reserve(bp.getPassenger(),serviceId,bookingPassengerId,scheduleId,quantity,null,null);
 }
 private ServiceRequest reserve(Passenger passenger,UUID serviceId,UUID bookingPassengerId,UUID scheduleId,
                                Integer quantity,String note,String idempotencyKey) {
  BookingPassenger farePassenger=bookingPassengerId==null?null:bookingPassengers.findById(bookingPassengerId).orElseThrow(()->missing("Booking passenger",bookingPassengerId));
  if(farePassenger==null){var candidates=bookingPassengers.findByPassenger_PassengerId(passenger.getPassengerId()).stream().filter(b->b.getBooking().getBookingStatus()==BookingStatus.Confirmed).toList();if(candidates.size()==1)farePassenger=candidates.getFirst();}
  boolean fare=farePassenger!=null&&farePassenger.getFareOfferId()!=null;
  // Serialize capacity checks for this service to prevent concurrent reservations exceeding capacity.
  var s=services.findByIdForUpdate(serviceId).orElseThrow(()->missing("Service",serviceId));
  if(s.getStatus()!=ServiceStatus.ACTIVE) throw new IllegalStateException("Service is not available");
  if (s.getCurrencyCode() != null && !s.getCurrencyCode().isBlank())
      com.casms.common.util.NewWriteCurrency.requireUsd(s.getCurrencyCode());
  int effectiveQuantity = quantity == null ? 1 : quantity;
  if(effectiveQuantity<1)throw new IllegalArgumentException("Quantity must be positive");
  if (scheduleId == null) assertCapacity(s, effectiveQuantity, 0,fare);
  ServiceSchedule schedule = null;
  if (scheduleId != null) {
      schedule = schedules.lockById(scheduleId).orElseThrow(() -> missing("Service schedule", scheduleId));
      if (!schedule.getService().getServiceId().equals(serviceId)) throw new IllegalArgumentException("Schedule does not belong to the requested service");
      if (!"OPEN".equals(schedule.getStatus())) throw new IllegalStateException("Service schedule is not open");
      long reserved = requests.sumActiveQuantityForSchedule(scheduleId,
              List.of(ServiceRequestStatus.REQUESTED, ServiceRequestStatus.ACCEPTED, ServiceRequestStatus.IN_PROGRESS));
      if (reserved + effectiveQuantity > schedule.getCapacity()) {if(fare)throw soldOut();throw new IllegalStateException("Requested quantity exceeds remaining schedule capacity");}
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
  if (schedule != null && (bp == null || scheduleCruise(schedule) == null
          || bp.getBooking().getCruise() == null
          || !scheduleCruise(schedule).equals(bp.getBooking().getCruise().getCruiseId()))) {
      throw new com.casms.common.exception.BusinessException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
          "SERVICE_SCHEDULE_CRUISE_MISMATCH", "Lịch phục vụ không thuộc chuyến khởi hành của hành khách.");
  }
  if(bp!=null&&bp.getFareOfferId()!=null)validateServiceScheduleTime(schedule,false);
  UUID grant=bp==null?null:fareEntitlements.reserve(bp,com.casms.cruisepackage.dto.CruisePackageContracts.ResourceType.SERVICE,serviceId,effectiveQuantity);
  fareEntitlements.validateServiceWindow(grant,schedule);
  ServiceRequest r=new ServiceRequest(); r.setFareBenefitSnapshotId(grant);r.setPassenger(passenger); r.setService(s); r.setServiceSchedule(schedule); r.setUnitPriceSnapshot(grant==null?s.getPrice():java.math.BigDecimal.ZERO); r.setCurrencySnapshot(grant==null?s.getCurrencyCode():"USD"); r.setIdempotencyKey(idempotencyKey==null||idempotencyKey.isBlank()?null:idempotencyKey); r.setBookingPassenger(bp); r.setBooking(bp==null?null:bp.getBooking()); r.setQuantity(effectiveQuantity); r.setNote(note); r.setRequestTime(Instant.now()); r.setStatus(ServiceRequestStatus.REQUESTED); return requests.save(r);
 }
 public ServiceRequest updatePending(UUID id, UUID userId, boolean staff, Integer quantity, String note) {
  ServiceRequest r=requests.lockById(id).orElseThrow(()->missing("Service request",id));assertOwner(r,userId,staff); if(r.getStatus()!=ServiceRequestStatus.REQUESTED)throw new IllegalStateException("Only requested service requests may be edited");
  if(posLedger.isPosRequest(id))throw new com.casms.common.exception.BusinessException("POS_CHARGE_IMMUTABLE","Cancel the POS request to restore its allowance, then record a new one; charged quantities cannot be edited");
  if(quantity==null||quantity<1)throw new IllegalArgumentException("Quantity must be positive");
  var service=services.findByIdForUpdate(r.getService().getServiceId()).orElseThrow(()->missing("Service",r.getService().getServiceId()));
  if (r.getServiceSchedule() == null) {
      assertCapacity(service, quantity, r.getQuantity(),r.getFareBenefitSnapshotId()!=null);
  } else {
      var schedule = schedules.lockById(r.getServiceSchedule().getServiceScheduleId())
          .orElseThrow(() -> missing("Service schedule", r.getServiceSchedule().getServiceScheduleId()));
      if (!schedule.getService().getServiceId().equals(service.getServiceId()) || !"OPEN".equals(schedule.getStatus()))
          throw new IllegalStateException("Service schedule is not open for this service");
      long other = requests.sumActiveQuantityForSchedule(schedule.getServiceScheduleId(),
          List.of(ServiceRequestStatus.REQUESTED, ServiceRequestStatus.ACCEPTED, ServiceRequestStatus.IN_PROGRESS)) - r.getQuantity();
      if (other + quantity > schedule.getCapacity()) {if(r.getFareBenefitSnapshotId()!=null)throw soldOut();throw new IllegalStateException("Requested quantity exceeds remaining schedule capacity");}
  }
  if(r.getBookingPassenger()!=null&&r.getBookingPassenger().getFareOfferId()!=null){validateServiceScheduleTime(r.getServiceSchedule(),false);fareEntitlements.validateReservation(r.getFareBenefitSnapshotId(),r.getBookingPassenger(),com.casms.cruisepackage.dto.CruisePackageContracts.ResourceType.SERVICE,service.getServiceId(),quantity);}
  fareEntitlements.validateServiceWindow(r.getFareBenefitSnapshotId(),r.getServiceSchedule());
  r.setQuantity(quantity); r.setNote(note); return r;
 }
 public ServiceRequest transition(UUID id, UUID userId, String action) {
  return transition(id,userId,action,null,null);
 }
 public ServiceRequest transition(UUID id, UUID userId, String action,Integer fulfilledQuantity,String unitCode) {
  ServiceRequest r=requests.lockById(id).orElseThrow(()->missing("Service request",id));
  boolean fare=r.getBookingPassenger()!=null&&r.getBookingPassenger().getFareOfferId()!=null;
  if(fare) {
   entityManager.refresh(r);
   if("complete".equals(action)&&r.getStatus()==ServiceRequestStatus.COMPLETED) {
    var previous=usages.findByOperationIdentity("SERVICE_REQUEST:"+id).orElseThrow(()->new com.casms.common.exception.BusinessException("ENTITLEMENT_FULFILLMENT_MISSING","Yêu cầu đã hoàn tất nhưng thiếu usage đã ghi nhận; không tự ghi lại từ yêu cầu."));
    if(r.getAssignedUser()==null||!r.getAssignedUser().getUserId().equals(userId))throw new org.springframework.security.access.AccessDeniedException("Only the assigned operator may confirm fulfillment");
    if(fulfilledQuantity!=null&&!previous.getQuantity().equals(fulfilledQuantity)||unitCode!=null&&!unitCode.equals(previous.getUnitCodeSnapshot()))
       throw new com.casms.common.exception.BusinessException("ENTITLEMENT_OPERATION_CONFLICT","Operation fulfillment đã có nội dung khác; không tiêu thụ lần hai.");
    return r;
   }
   if(java.util.Set.of("accept","start","complete").contains(action)) {
    var service=services.findByIdForUpdate(r.getService().getServiceId()).orElseThrow();
    if(service.getStatus()!=ServiceStatus.ACTIVE)throw new com.casms.common.exception.BusinessException("ENTITLEMENT_NOT_ACTIVE","Dịch vụ hiện không hoạt động.");
    ServiceSchedule schedule=r.getServiceSchedule()==null?null:schedules.lockById(r.getServiceSchedule().getServiceScheduleId()).orElseThrow();
    if(service.isRequiresSchedule()&&schedule==null)throw new com.casms.common.exception.BusinessException("BENEFIT_REQUIRES_REGISTRATION","Dịch vụ yêu cầu lịch phục vụ hợp lệ trước khi fulfillment.");
    validateServiceScheduleTime(schedule,"complete".equals(action));
    fareEntitlements.validateServiceWindow(r.getFareBenefitSnapshotId(),schedule);
    if(schedule!=null&&!Objects.equals(scheduleCruise(schedule),r.getBooking().getCruise().getCruiseId()))throw new com.casms.common.exception.BusinessException("ENTITLEMENT_RESOURCE_MISMATCH","Lịch phục vụ không thuộc chuyến đã mua.");
    if(schedule!=null&&requests.sumActiveQuantityForSchedule(schedule.getServiceScheduleId(),List.of(ServiceRequestStatus.REQUESTED,ServiceRequestStatus.ACCEPTED,ServiceRequestStatus.IN_PROGRESS))>schedule.getCapacity())throw soldOut();
    if(schedule==null)assertCapacity(service,0,0,true);
    if("complete".equals(action)) {
     if(fulfilledQuantity==null||fulfilledQuantity<1||fulfilledQuantity>r.getQuantity()||unitCode==null||!unitCode.equals(service.getUnitCode()))
      throw new com.casms.common.exception.BusinessException("ENTITLEMENT_FULFILLED_QUANTITY_REQUIRED","Phải ghi rõ số lượng đã phục vụ thực tế, không vượt số lượng yêu cầu, và đúng unitCode của dịch vụ.");
     fareEntitlements.fulfill(r.getFareBenefitSnapshotId(),r.getBookingPassenger(),com.casms.cruisepackage.dto.CruisePackageContracts.ResourceType.SERVICE,service.getServiceId(),fulfilledQuantity,unitCode,Instant.now());
    } else fareEntitlements.validateReservation(r.getFareBenefitSnapshotId(),r.getBookingPassenger(),com.casms.cruisepackage.dto.CruisePackageContracts.ResourceType.SERVICE,service.getServiceId(),r.getQuantity());
   }
  }
  switch(action){
   case "assign"->{if(r.getStatus()!=ServiceRequestStatus.REQUESTED)invalid(); User user=users.findById(userId).orElseThrow(()->missing("User",userId));r.setAssignedUser(user);}
   case "accept"->{if(r.getStatus()!=ServiceRequestStatus.REQUESTED)invalid();r.setStatus(ServiceRequestStatus.ACCEPTED);}
   case "reject"->{if(r.getStatus()!=ServiceRequestStatus.REQUESTED)invalid();r.setStatus(ServiceRequestStatus.REJECTED);}
   case "start"->{if(r.getStatus()!=ServiceRequestStatus.ACCEPTED||r.getAssignedUser()==null||!r.getAssignedUser().getUserId().equals(userId))invalid();r.setStatus(ServiceRequestStatus.IN_PROGRESS);}
   case "complete"->{if(r.getStatus()!=ServiceRequestStatus.IN_PROGRESS||r.getAssignedUser()==null||!r.getAssignedUser().getUserId().equals(userId))invalid();r.setStatus(ServiceRequestStatus.COMPLETED);r.setCompletedAt(Instant.now());}
   case "cancel"->{if(r.getStatus()!=ServiceRequestStatus.REQUESTED&&r.getStatus()!=ServiceRequestStatus.ACCEPTED)invalid();r.setStatus(ServiceRequestStatus.CANCELLED);}
   default->throw new IllegalArgumentException("Unsupported request action");
  }
  if(fare&&"complete".equals(action)) {
   var usage=new com.casms.servicepackage.entity.ServiceUsage();usage.setServiceRequest(r);usage.setFareBenefitSnapshotId(r.getFareBenefitSnapshotId());
   usage.setOperationIdentity("SERVICE_REQUEST:"+id);usage.setQuantity(fulfilledQuantity);usage.setUnitCodeSnapshot(unitCode);usage.setUsedAt(r.getCompletedAt());
   usage.setRecordedBy(users.findById(userId).orElseThrow());usage.setNote("Fulfillment thực tế của quyền lợi gói vé; không tính từ requested quantity.");r.getUsageRecords().add(usage);usages.saveAndFlush(usage);
   audit.recordFareFulfillment(r.getFareBenefitSnapshotId(),"ServiceRequest",id,fulfilledQuantity,"SERVICE_UNIT:"+unitCode);
  }
  if(r.getStatus()==ServiceRequestStatus.REJECTED || r.getStatus()==ServiceRequestStatus.CANCELLED)posLedger.reverseForRequest(id,userId);
  return r;
 }
 public void assertOwner(ServiceRequest r, UUID userId, boolean staff){if(staff)return; if(r.getPassenger().getUser()==null||!r.getPassenger().getUser().getUserId().equals(userId))throw new AccessDeniedException("Service request belongs to another passenger");}
 private void assertCapacity(Service service, long requestedQuantity, int existingQuantity) {
  assertCapacity(service,requestedQuantity,existingQuantity,false);
 }
 private void assertCapacity(Service service, long requestedQuantity, int existingQuantity,boolean fare) {
  if (service.getCapacity() == null) return;
  long used = activeQuantity(service.getServiceId()) - existingQuantity;
  if (used + requestedQuantity > service.getCapacity()) {
   if(fare)throw soldOut();
   throw new IllegalStateException("Requested quantity exceeds remaining service capacity");
  }
 }
 private void validateServiceScheduleTime(ServiceSchedule schedule,boolean fulfillment) {
  if(schedule==null)return;
  Instant now=Instant.now();
  if(!"OPEN".equals(schedule.getStatus())||!schedule.getEndsAt().isAfter(now)||fulfillment&&now.isBefore(schedule.getStartsAt()))
   throw new com.casms.common.exception.BusinessException("BENEFIT_USAGE_WINDOW_INVALID","Lịch phục vụ không hoạt động hoặc thời điểm fulfillment nằm ngoài lịch thực tế.");
 }
 private UUID scheduleCruise(ServiceSchedule schedule) {
  if(schedule.getCruise()!=null)return schedule.getCruise().getCruiseId();
  return schedule.getCruiseStop()==null||schedule.getCruiseStop().getItinerary()==null?null:schedule.getCruiseStop().getItinerary().getCruise().getCruiseId();
 }
 private com.casms.common.exception.BusinessException soldOut(){return new com.casms.common.exception.BusinessException(org.springframework.http.HttpStatus.CONFLICT,"SOLD_OUT","Dịch vụ đã hết chỗ; chưa tạo yêu cầu hoặc tiêu thụ quyền lợi.");}
 private long activeQuantity(UUID serviceId) {
  return requests.sumActiveQuantity(serviceId, List.of(ServiceRequestStatus.REQUESTED, ServiceRequestStatus.ACCEPTED, ServiceRequestStatus.IN_PROGRESS));
 }
 private void assertSamePassenger(BookingPassenger bp,Passenger passenger){if(!bp.getPassenger().getPassengerId().equals(passenger.getPassengerId()))throw new AccessDeniedException("Booking passenger does not belong to authenticated passenger");}
 private static void invalid(){throw new IllegalStateException("Invalid service request transition");}
 private static EntityNotFoundException missing(String what,UUID id){return new EntityNotFoundException(what+" not found: "+id);}
}
