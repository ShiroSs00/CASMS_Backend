package com.casms.servicepackage.service;

import com.casms.activity.repository.OnboardActivityRepository;
import com.casms.activity.repository.ShoreExcursionRepository;
import com.casms.booking.entity.Booking;
import com.casms.booking.entity.BookingPassenger;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.servicepackage.entity.*;
import com.casms.servicepackage.enums.BenefitType;
import com.casms.servicepackage.enums.TourPackageStatus;
import com.casms.servicepackage.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertSame;

@ExtendWith(MockitoExtension.class)
class ServicepackageWorkflowTest {
 @Mock ServiceRepository serviceRepo; @Mock ProductRepository productRepo; @Mock TourPackageRepository packageRepo;
 @Mock ServiceRequestRepository requestRepo; @Mock BookingPassengerPackageRepository assignmentRepo;
 @Mock TourPackageServiceRepository packageServiceRepo; @Mock TourPackageActivityRepository packageActivityRepo;
 @Mock TourPackageExcursionRepository packageExcursionRepo; @Mock BookingPassengerRepository bookingPassengerRepo;
 @Mock OnboardActivityRepository activityRepo; @Mock ShoreExcursionRepository excursionRepo;
 @InjectMocks ServicepackageService service;

 @Test void assignPackage_snapshotsPriceOnlyForDraftBooking(){
  UUID bpId=UUID.randomUUID(),packageId=UUID.randomUUID();Booking booking=new Booking();booking.setBookingStatus(BookingStatus.Draft);com.casms.ship.entity.Cruise cruise=new com.casms.ship.entity.Cruise();cruise.setDepartureDatetime(java.time.Instant.now().plusSeconds(3600));cruise.setReturnDatetime(java.time.Instant.now().plusSeconds(7200));booking.setCruise(cruise);BookingPassenger bp=new BookingPassenger();bp.setBooking(booking);TourPackage p=new TourPackage();p.setTourPackageId(packageId);p.setStatus(TourPackageStatus.ACTIVE);p.setPrice(new BigDecimal("39.50"));p.setCurrencyCode("USD");
  when(bookingPassengerRepo.lockById(bpId)).thenReturn(Optional.of(bp));when(assignmentRepo.existsById(bpId)).thenReturn(false);when(packageRepo.findById(packageId)).thenReturn(Optional.of(p));when(assignmentRepo.save(any())).thenAnswer(i->i.getArgument(0));
  BookingPassengerPackage result=service.assignPackage(bpId,packageId,UUID.randomUUID(),true);
  assertEquals(new BigDecimal("39.50"),result.getAgreedPrice());assertEquals("USD",result.getCurrency());assertNotNull(result.getAssignedAt());
 }

 @Test void assignPackage_rejectsInactivePackage(){
  UUID bpId=UUID.randomUUID(),packageId=UUID.randomUUID();Booking booking=new Booking();booking.setBookingStatus(BookingStatus.Draft);com.casms.ship.entity.Cruise cruise=new com.casms.ship.entity.Cruise();cruise.setDepartureDatetime(java.time.Instant.now().plusSeconds(3600));cruise.setReturnDatetime(java.time.Instant.now().plusSeconds(7200));booking.setCruise(cruise);BookingPassenger bp=new BookingPassenger();bp.setBooking(booking);TourPackage p=new TourPackage();p.setStatus(TourPackageStatus.INACTIVE);
  when(bookingPassengerRepo.lockById(bpId)).thenReturn(Optional.of(bp));when(assignmentRepo.existsById(bpId)).thenReturn(false);when(packageRepo.findById(packageId)).thenReturn(Optional.of(p));
  assertThrows(IllegalStateException.class,()->service.assignPackage(bpId,packageId,UUID.randomUUID(),true));verify(assignmentRepo,never()).save(any());
 }

 @Test void addPackageService_rejectsDuplicateCompositeKey(){
  UUID packageId=UUID.randomUUID(),serviceId=UUID.randomUUID();TourPackage p=new TourPackage();p.setStatus(TourPackageStatus.DRAFT);com.casms.servicepackage.entity.Service s=new com.casms.servicepackage.entity.Service();
  when(packageRepo.findById(packageId)).thenReturn(Optional.of(p));when(assignmentRepo.existsByTourPackage_TourPackageId(packageId)).thenReturn(false);when(serviceRepo.findById(serviceId)).thenReturn(Optional.of(s));when(packageServiceRepo.existsById(any())).thenReturn(true);
  assertThrows(IllegalStateException.class,()->service.addPackageService(packageId,serviceId,BenefitType.INCLUDED));verify(packageServiceRepo,never()).save(any());
 }

 @Test void packageServices_inheritsParentBenefitsAndChildOverridesDuplicates(){
  UUID childId=UUID.randomUUID(),parentId=UUID.randomUUID(),serviceId=UUID.randomUUID();
  TourPackage parent=new TourPackage();parent.setTourPackageId(parentId);
  TourPackage child=new TourPackage();child.setTourPackageId(childId);child.setParentPackage(parent);
  com.casms.servicepackage.entity.Service catalogService=new com.casms.servicepackage.entity.Service();catalogService.setServiceId(serviceId);
  TourPackageService parentLink=new TourPackageService();parentLink.setTourPackage(parent);parentLink.setService(catalogService);parentLink.setBenefitType(BenefitType.INCLUDED);
  TourPackageService childLink=new TourPackageService();childLink.setTourPackage(child);childLink.setService(catalogService);childLink.setBenefitType(BenefitType.OPTIONAL);
  when(packageRepo.findById(childId)).thenReturn(Optional.of(child));
  when(packageServiceRepo.findByTourPackage_TourPackageId(childId)).thenReturn(java.util.List.of(childLink));
  when(packageServiceRepo.findByTourPackage_TourPackageId(parentId)).thenReturn(java.util.List.of(parentLink));

  var result=service.packageServices(childId);
  assertEquals(1,result.size());assertSame(childLink,result.get(0));
 }
}
