package com.casms.servicepackage.service;

import com.casms.servicepackage.entity.*;
import com.casms.servicepackage.enums.*;
import com.casms.servicepackage.repository.*;
import com.casms.booking.entity.BookingPassenger;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.activity.enums.OnboardActivityStatus;
import com.casms.activity.enums.ShoreExcursionStatus;
import com.casms.activity.repository.OnboardActivityRepository;
import com.casms.activity.repository.ShoreExcursionRepository;
import com.casms.common.service.BusinessCodeGenerator;
import com.casms.servicepackage.entity.Service;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Set;

/** Catalogue and package lifecycle behavior. Financial sales stay in the payment module. */
@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Transactional
public class ServicepackageService {
    private final ServiceRepository serviceRepo;
    private final ProductRepository productRepo;
    private final TourPackageRepository packageRepo;
    private final ServiceRequestRepository requestRepo;
    private final BookingPassengerPackageRepository assignmentRepo;
    private final TourPackageServiceRepository packageServiceRepo;
    private final TourPackageActivityRepository packageActivityRepo;
    private final TourPackageExcursionRepository packageExcursionRepo;
    private final BookingPassengerRepository bookingPassengerRepo;
    private final OnboardActivityRepository activityRepo;
    private final ShoreExcursionRepository excursionRepo;
    private final FeedbackRepository feedbackRepo;
    private final BusinessCodeGenerator businessCodeGenerator;

    @Transactional(readOnly=true) public List<Service> services() { return serviceRepo.findAll(); }
    @Transactional(readOnly=true) public Service service(UUID id) { return serviceRepo.findById(id).orElseThrow(()->missing("Service",id)); }
    public Service saveService(Service s) {
        if(s.getServiceId()==null) s.setServiceCode(businessCodeGenerator.next("SV"));
        if(s.getServiceName()==null||s.getServiceName().isBlank()) throw new IllegalArgumentException("Service code and name are required");
        if(s.getCurrencyCode()==null||s.getCurrencyCode().isBlank()) s.setCurrencyCode("USD");
        com.casms.common.util.NewWriteCurrency.requireUsd(s.getCurrencyCode());
        if(s.getPrice()==null) s.setPrice(BigDecimal.ZERO);
        if(s.getPrice().signum()<0) throw new IllegalArgumentException("Non-negative service price requires currency");
        if(s.getCapacity()!=null&&s.getCapacity()<1) throw new IllegalArgumentException("Capacity must be positive");
        if(serviceRepo.existsByServiceCodeAndServiceIdNot(s.getServiceCode(),s.getServiceId()==null?new UUID(0,0):s.getServiceId())) throw new IllegalArgumentException("Service code already exists");
        if(s.getStatus()==null) s.setStatus(ServiceStatus.ACTIVE); return serviceRepo.save(s);
    }
    public Service serviceStatus(UUID id, String action) {
        var s=service(id); switch(action) {
            case "activate" -> { if(s.getStatus()!=ServiceStatus.INACTIVE) invalid(); s.setStatus(ServiceStatus.ACTIVE); }
            case "suspend" -> { if(s.getStatus()!=ServiceStatus.ACTIVE) invalid(); s.setStatus(ServiceStatus.SUSPENDED); }
            default -> throw new IllegalArgumentException("Unknown service action");
        } return s;
    }
    public boolean serviceAvailable(UUID id,Integer quantity){var s=service(id);if(quantity==null||quantity<1)throw new IllegalArgumentException("Quantity must be positive");if(s.getStatus()!=ServiceStatus.ACTIVE)return false;if(s.getCapacity()==null)return true;long used=requestRepo.sumActiveQuantity(id,List.of(ServiceRequestStatus.REQUESTED,ServiceRequestStatus.ACCEPTED,ServiceRequestStatus.IN_PROGRESS));return used+quantity<=s.getCapacity();}
    public void deleteService(UUID id) { var s=service(id); if(requestRepo.existsByService_ServiceId(id)||packageServiceRepo.existsByService_ServiceId(id)||feedbackRepo.existsByService_ServiceId(id)) throw new IllegalStateException("Service is referenced by a request, package or feedback"); serviceRepo.delete(s); }

    @Transactional(readOnly=true) public List<Product> products(){return productRepo.findAll();}
    @Transactional(readOnly=true) public Product product(UUID id){return productRepo.findById(id).orElseThrow(()->missing("Product",id));}
    public Product saveProduct(Product p){
        if(p.getProductId()==null) p.setProductCode(businessCodeGenerator.next("PR"));
        if(p.getProductName()==null||p.getProductName().isBlank()) throw new IllegalArgumentException("Product code and name are required");
        if(p.getCurrencyCode()==null||p.getCurrencyCode().isBlank()) p.setCurrencyCode("USD");
        com.casms.common.util.NewWriteCurrency.requireUsd(p.getCurrencyCode());
        if(p.getUnitPrice()==null||p.getUnitPrice().signum()<0) throw new IllegalArgumentException("Product requires a non-negative price and currency");
        if(productRepo.existsByProductCodeAndProductIdNot(p.getProductCode(),p.getProductId()==null?new UUID(0,0):p.getProductId())) throw new IllegalArgumentException("Product code already exists");
        if(p.getStatus()==null)p.setStatus(ProductStatus.ACTIVE); return productRepo.save(p);
    }
    public Product productStatus(UUID id,String action){var p=product(id); switch(action){case "activate"->{if(p.getStatus()!=ProductStatus.INACTIVE)invalid();p.setStatus(ProductStatus.ACTIVE);} case "discontinue"->{if(p.getStatus()!=ProductStatus.ACTIVE)invalid();p.setStatus(ProductStatus.DISCONTINUED);} default->throw new IllegalArgumentException("Unknown product action");} return p;}
    public void deleteProduct(UUID id){productRepo.delete(product(id));}
    public void recordSale(UUID id){product(id); throw new UnsupportedOperationException("Product sales are not represented by a product sale/POS workflow in the current schema; use the established payment transaction workflow when a sale source is defined.");}

    @Transactional(readOnly=true) public List<TourPackage> packages(){return packageRepo.findAll();}
    @Transactional(readOnly=true) public TourPackage tourPackage(UUID id){return packageRepo.findById(id).orElseThrow(()->missing("Tour package",id));}
    public TourPackage savePackage(TourPackage p){
        if(p.getTourPackageId()==null) p.setPackageCode(businessCodeGenerator.next("PK"));
        if(p.getPackageName()==null||p.getPackageName().isBlank())throw new IllegalArgumentException("Package code and name are required");
        if(p.getCurrencyCode()==null||p.getCurrencyCode().isBlank()) p.setCurrencyCode("USD");
        com.casms.common.util.NewWriteCurrency.requireUsd(p.getCurrencyCode());
        if(p.getPrice()==null||p.getPrice().signum()<0)throw new IllegalArgumentException("Package requires non-negative price and currency");
        if(p.getStartDate()!=null&&p.getEndDate()!=null&&p.getEndDate().isBefore(p.getStartDate()))throw new IllegalArgumentException("Package end date precedes start date");
        if(packageRepo.existsByPackageCodeAndTourPackageIdNot(p.getPackageCode(),p.getTourPackageId()==null?new UUID(0,0):p.getTourPackageId()))throw new IllegalArgumentException("Package code already exists");
        if(p.getStatus()==null)p.setStatus(TourPackageStatus.DRAFT); else if(p.getTourPackageId()==null&&p.getStatus()!=TourPackageStatus.DRAFT)throw new IllegalArgumentException("New packages must start as draft"); return packageRepo.save(p);
    }
    public TourPackage packageStatus(UUID id,String action){var p=tourPackage(id); if("activate".equals(action)){if(p.getStatus()!=TourPackageStatus.DRAFT&&p.getStatus()!=TourPackageStatus.INACTIVE)invalid(); if(packageServiceRepo.countByTourPackage_TourPackageId(id)+packageActivityRepo.countByTourPackage_TourPackageId(id)+packageExcursionRepo.countByTourPackage_TourPackageId(id)==0)throw new IllegalStateException("Package requires at least one component before activation"); if(packageServiceRepo.findByTourPackage_TourPackageId(id).stream().anyMatch(x->x.getService().getStatus()!=ServiceStatus.ACTIVE))throw new IllegalStateException("Package contains an unavailable service");if(packageActivityRepo.findByTourPackage_TourPackageId(id).stream().anyMatch(x->x.getOnboardActivity().getStatus()!=OnboardActivityStatus.PLANNED&&x.getOnboardActivity().getStatus()!=OnboardActivityStatus.OPEN))throw new IllegalStateException("Package contains an unavailable activity");if(packageExcursionRepo.findByTourPackage_TourPackageId(id).stream().anyMatch(x->x.getShoreExcursion().getStatus()!=ShoreExcursionStatus.PLANNED&&x.getShoreExcursion().getStatus()!=ShoreExcursionStatus.OPEN))throw new IllegalStateException("Package contains an unavailable excursion");p.setStatus(TourPackageStatus.ACTIVE);}else if("deactivate".equals(action)){if(p.getStatus()!=TourPackageStatus.ACTIVE)invalid();p.setStatus(TourPackageStatus.INACTIVE);}else throw new IllegalArgumentException("Unknown package action"); return p;}
    public void deletePackage(UUID id){var p=tourPackage(id);if(p.getStatus()!=TourPackageStatus.DRAFT||assignmentRepo.existsByTourPackage_TourPackageId(id))throw new IllegalStateException("Only an unused draft package can be deleted");packageServiceRepo.deleteAll(packageServiceRepo.findByTourPackage_TourPackageId(id));packageActivityRepo.deleteAll(packageActivityRepo.findByTourPackage_TourPackageId(id));packageExcursionRepo.deleteAll(packageExcursionRepo.findByTourPackage_TourPackageId(id));packageRepo.delete(p);}
    @Transactional(readOnly=true) public BookingPassengerPackage packageAssignment(UUID bpId,UUID userId,boolean staff){BookingPassengerPackage a=assignmentRepo.findById(bpId).orElseThrow(()->missing("Package assignment",bpId));assertAssignmentAccess(a.getBookingPassenger(),userId,staff);return a;}
    @Transactional(readOnly=true) public List<BookingPassengerPackage> packageAssignments(UUID bookingId,UUID userId,boolean staff){if(bookingId!=null){if(!staff){var bpList=bookingPassengerRepo.findByBookingBookingId(bookingId);boolean isOwner=bpList.stream().anyMatch(bp->bp.getBooking().getBookedByUser()!=null&&bp.getBooking().getBookedByUser().getUserId().equals(userId))||bpList.stream().anyMatch(bp->bp.getPassenger().getUser()!=null&&bp.getPassenger().getUser().getUserId().equals(userId));if(!isOwner){throw new AccessDeniedException("Passenger cannot list assignments for an arbitrary booking");}}return assignmentRepo.findByBookingPassenger_Booking_BookingId(bookingId);}if(staff)return assignmentRepo.findAll();return assignmentRepo.findByBookingPassenger_Passenger_User_UserId(userId);}
    public BookingPassengerPackage assignPackage(UUID bpId,UUID packageId,UUID userId,boolean staff){BookingPassenger bp=bookingPassengerRepo.lockById(bpId).orElseThrow(()->missing("Booking passenger",bpId));assertAssignmentAccess(bp,userId,staff);if(bp.getBooking().getBookingStatus()!=BookingStatus.Draft)throw new IllegalStateException("Packages can only be assigned while the booking is draft; confirmed fare/charge treatment is not modeled");if(assignmentRepo.existsById(bpId))throw new IllegalStateException("Booking passenger already has a package assignment");TourPackage p=availablePackage(packageId,bp);BookingPassengerPackage a=new BookingPassengerPackage();a.setBookingPassenger(bp);a.setTourPackage(p);a.setAssignedAt(Instant.now());a.setAgreedPrice(p.getPrice());a.setCurrency(p.getCurrencyCode());return assignmentRepo.save(a);}
    public BookingPassengerPackage changePackage(UUID bpId,UUID packageId,UUID userId,boolean staff){BookingPassengerPackage a=assignmentRepo.lockByBookingPassengerId(bpId).orElseThrow(()->missing("Package assignment",bpId));BookingPassenger bp=a.getBookingPassenger();assertAssignmentAccess(bp,userId,staff);if(bp.getBooking().getBookingStatus()!=BookingStatus.Draft)throw new IllegalStateException("An agreed package cannot be changed after booking confirmation");TourPackage p=availablePackage(packageId,bp);a.setTourPackage(p);a.setAgreedPrice(p.getPrice());a.setCurrency(p.getCurrencyCode());a.setAssignedAt(Instant.now());return a;}
    public void removePackageAssignment(UUID bpId,UUID userId,boolean staff){BookingPassengerPackage a=assignmentRepo.lockByBookingPassengerId(bpId).orElseThrow(()->missing("Package assignment",bpId));assertAssignmentAccess(a.getBookingPassenger(),userId,staff);if(a.getBookingPassenger().getBooking().getBookingStatus()!=BookingStatus.Draft)throw new IllegalStateException("An agreed package cannot be removed after booking confirmation");assignmentRepo.delete(a);}
    public TourPackageService addPackageService(UUID packageId,UUID serviceId,BenefitType benefit){TourPackage p=editablePackage(packageId);var s=service(serviceId);if(benefit==null)throw new IllegalArgumentException("Benefit type is required");var key=new TourPackageService.TourPackageServiceId();key.setTourPackage(packageId);key.setService(serviceId);if(packageServiceRepo.existsById(key))throw new IllegalStateException("Package service already exists");var link=new TourPackageService();link.setTourPackage(p);link.setService(s);link.setBenefitType(benefit);return packageServiceRepo.save(link);}
    public TourPackageService updatePackageService(UUID packageId,UUID serviceId,BenefitType benefit){editablePackage(packageId);if(benefit==null)throw new IllegalArgumentException("Benefit type is required");var key=new TourPackageService.TourPackageServiceId();key.setTourPackage(packageId);key.setService(serviceId);var link=packageServiceRepo.findById(key).orElseThrow(()->missing("Package service",serviceId));link.setBenefitType(benefit);return link;}
    public void removePackageService(UUID packageId,UUID serviceId){editablePackage(packageId);var key=new TourPackageService.TourPackageServiceId();key.setTourPackage(packageId);key.setService(serviceId);packageServiceRepo.delete(packageServiceRepo.findById(key).orElseThrow(()->missing("Package service",serviceId)));}
    public TourPackageActivity addPackageActivity(UUID packageId,UUID activityId,BenefitType benefit){TourPackage p=editablePackage(packageId);var a=activityRepo.findById(activityId).orElseThrow(()->missing("Activity",activityId));if(benefit==null)throw new IllegalArgumentException("Benefit type is required");var key=new TourPackageActivity.TourPackageActivityId();key.setTourPackage(packageId);key.setOnboardActivity(activityId);if(packageActivityRepo.existsById(key))throw new IllegalStateException("Package activity already exists");var link=new TourPackageActivity();link.setTourPackage(p);link.setOnboardActivity(a);link.setBenefitType(benefit);return packageActivityRepo.save(link);}
    public TourPackageActivity updatePackageActivity(UUID packageId,UUID activityId,BenefitType benefit){editablePackage(packageId);if(benefit==null)throw new IllegalArgumentException("Benefit type is required");var key=new TourPackageActivity.TourPackageActivityId();key.setTourPackage(packageId);key.setOnboardActivity(activityId);var link=packageActivityRepo.findById(key).orElseThrow(()->missing("Package activity",activityId));link.setBenefitType(benefit);return link;}
    public void removePackageActivity(UUID packageId,UUID activityId){editablePackage(packageId);var key=new TourPackageActivity.TourPackageActivityId();key.setTourPackage(packageId);key.setOnboardActivity(activityId);packageActivityRepo.delete(packageActivityRepo.findById(key).orElseThrow(()->missing("Package activity",activityId)));}
    public TourPackageExcursion addPackageExcursion(UUID packageId,UUID excursionId,BenefitType benefit){TourPackage p=editablePackage(packageId);var e=excursionRepo.findById(excursionId).orElseThrow(()->missing("Excursion",excursionId));if(benefit==null)throw new IllegalArgumentException("Benefit type is required");var key=new TourPackageExcursion.TourPackageExcursionId();key.setTourPackage(packageId);key.setShoreExcursion(excursionId);if(packageExcursionRepo.existsById(key))throw new IllegalStateException("Package excursion already exists");var link=new TourPackageExcursion();link.setTourPackage(p);link.setShoreExcursion(e);link.setBenefitType(benefit);return packageExcursionRepo.save(link);}
    public TourPackageExcursion updatePackageExcursion(UUID packageId,UUID excursionId,BenefitType benefit){editablePackage(packageId);if(benefit==null)throw new IllegalArgumentException("Benefit type is required");var key=new TourPackageExcursion.TourPackageExcursionId();key.setTourPackage(packageId);key.setShoreExcursion(excursionId);var link=packageExcursionRepo.findById(key).orElseThrow(()->missing("Package excursion",excursionId));link.setBenefitType(benefit);return link;}
    public void removePackageExcursion(UUID packageId,UUID excursionId){editablePackage(packageId);var key=new TourPackageExcursion.TourPackageExcursionId();key.setTourPackage(packageId);key.setShoreExcursion(excursionId);packageExcursionRepo.delete(packageExcursionRepo.findById(key).orElseThrow(()->missing("Package excursion",excursionId)));}
    private TourPackage editablePackage(UUID id){TourPackage p=tourPackage(id);if(p.getStatus()!=TourPackageStatus.DRAFT||assignmentRepo.existsByTourPackage_TourPackageId(id))throw new IllegalStateException("Only an unassigned draft package can be edited");return p;}
    private TourPackage availablePackage(UUID id,BookingPassenger bp){TourPackage p=tourPackage(id);if(p.getStatus()!=TourPackageStatus.ACTIVE)throw new IllegalStateException("Package is not active");var cruise=bp.getBooking().getCruise();LocalDate departure=cruise.getDepartureDatetime().atZone(ZoneOffset.UTC).toLocalDate();LocalDate returning=cruise.getReturnDatetime().atZone(ZoneOffset.UTC).toLocalDate();if(p.getStartDate()!=null&&departure.isBefore(p.getStartDate())||p.getEndDate()!=null&&returning.isAfter(p.getEndDate()))throw new IllegalStateException("Package is not valid for the full cruise date range");return p;}
    private static void assertAssignmentAccess(BookingPassenger bp,UUID userId,boolean staff){if(!staff&&(bp.getPassenger().getUser()==null||!bp.getPassenger().getUser().getUserId().equals(userId)))throw new AccessDeniedException("Booking passenger does not belong to authenticated passenger");}
    @Transactional(readOnly=true) public List<TourPackageService> packageServices(UUID id){
        Map<UUID,TourPackageService> inherited=new LinkedHashMap<>();
        for(TourPackage p:packageLineage(id)) for(TourPackageService x:packageServiceRepo.findByTourPackage_TourPackageId(p.getTourPackageId())) inherited.putIfAbsent(x.getService().getServiceId(),x);
        return new ArrayList<>(inherited.values());
    }
    @Transactional(readOnly=true) public List<TourPackageActivity> packageActivities(UUID id){
        Map<UUID,TourPackageActivity> inherited=new LinkedHashMap<>();
        for(TourPackage p:packageLineage(id)) for(TourPackageActivity x:packageActivityRepo.findByTourPackage_TourPackageId(p.getTourPackageId())) inherited.putIfAbsent(x.getOnboardActivity().getOnboardActivityId(),x);
        return new ArrayList<>(inherited.values());
    }
    @Transactional(readOnly=true) public List<TourPackageExcursion> packageExcursions(UUID id){
        Map<UUID,TourPackageExcursion> inherited=new LinkedHashMap<>();
        for(TourPackage p:packageLineage(id)) for(TourPackageExcursion x:packageExcursionRepo.findByTourPackage_TourPackageId(p.getTourPackageId())) inherited.putIfAbsent(x.getShoreExcursion().getShoreExcursionId(),x);
        return new ArrayList<>(inherited.values());
    }
    private List<TourPackage> packageLineage(UUID id){
        List<TourPackage> lineage=new ArrayList<>();Set<UUID> seen=new HashSet<>();TourPackage current=tourPackage(id);
        while(current!=null){
            if(!seen.add(current.getTourPackageId()))throw new IllegalStateException("Package inheritance cycle detected");
            lineage.add(current);
            current=current.getParentPackage();
        }
        return lineage;
    }
    private static void invalid(){throw new IllegalStateException("Invalid lifecycle transition");}
    private static EntityNotFoundException missing(String what,UUID id){return new EntityNotFoundException(what+" not found: "+id);}
}
