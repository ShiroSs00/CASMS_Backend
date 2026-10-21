package com.casms.activity.service;

import com.casms.activity.entity.*;
import com.casms.activity.enums.*;
import com.casms.activity.repository.*;
import com.casms.booking.entity.BookingPassenger;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.ship.repository.CruiseRepository;
import com.casms.servicepackage.repository.TourPackageActivityRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Domain operations for onboard activities, registrations and attendance. */
@Service
@RequiredArgsConstructor
@Transactional
public class ActivityService {
    private final OnboardActivityRepository activities;
    private final RegistrationRepository registrations;
    private final ParticipationRepository participations;
    private final BookingPassengerRepository bookingPassengers;
    private final CruiseRepository cruises;
    private final ShoreExcursionRepository excursions;
    private final TourPackageActivityRepository packageActivities;

    @Transactional(readOnly = true)
    public List<OnboardActivity> listActivities() { return activities.findAll(); }
    @Transactional(readOnly = true)
    public OnboardActivity activity(UUID id) { return activities.findById(id).orElseThrow(() -> missing("Activity", id)); }
    @Transactional(readOnly = true)
    public int availableSeats(UUID id){OnboardActivity a=activity(id);if(a.getStatus()!=OnboardActivityStatus.OPEN||!a.getEndTime().isAfter(Instant.now()))return 0;return (int)Math.max(0,a.getCapacity()-occupied(id));}

    public OnboardActivity createActivity(OnboardActivity a, UUID cruiseId) {
        a.setCruise(cruises.findById(cruiseId).orElseThrow(() -> missing("Cruise", cruiseId)));
        if (a.getActivityCode() == null || a.getActivityCode().isBlank()) {
            a.setActivityCode(com.casms.common.util.CodeGenerator.generate("ACTIVITY", activities.count() + 1, activities::existsByActivityCode));
        }
        if (a.getCurrencyCode() == null || a.getCurrencyCode().isBlank()) {
            a.setCurrencyCode("USD");
        }
        if (a.getPrice() == null) {
            a.setPrice(java.math.BigDecimal.ZERO);
        }
        validateActivity(a, null);
        a.setStatus(OnboardActivityStatus.PLANNED);
        return activities.save(a);
    }
    public OnboardActivity updateActivity(UUID id, OnboardActivity input) {
        OnboardActivity a = activity(id);
        if (a.getStatus() != OnboardActivityStatus.PLANNED) throw new IllegalStateException("Only planned activities can be edited");
        a.setActivityCode(input.getActivityCode()); a.setActivityName(input.getActivityName()); a.setDescription(input.getDescription());
        a.setStartTime(input.getStartTime()); a.setEndTime(input.getEndTime()); a.setLocation(input.getLocation()); a.setCapacity(input.getCapacity());
        a.setPrice(input.getPrice()); a.setCurrencyCode(input.getCurrencyCode()); validateActivity(a, id); return a;
    }
    public void deleteActivity(UUID id) {
        OnboardActivity a = activity(id);
        if (a.getStatus() != OnboardActivityStatus.PLANNED || registrations.existsByOnboardActivity_OnboardActivityId(id)||packageActivities.existsByOnboardActivity_OnboardActivityId(id)) throw new IllegalStateException("Only an unused planned activity can be deleted");
        activities.delete(a);
    }
    public OnboardActivity lifecycle(UUID id, String action) {
        OnboardActivity a = activity(id);
        switch (action) {
            case "open" -> { if (a.getStatus()!=OnboardActivityStatus.PLANNED) invalid(); a.setStatus(OnboardActivityStatus.OPEN); }
            case "close" -> throw new UnsupportedOperationException("OnboardActivity has no CLOSED status; closing registrations cannot be represented without changing the approved schema");
            case "start" -> { if (a.getStatus()!=OnboardActivityStatus.OPEN && a.getStatus()!=OnboardActivityStatus.FULL) invalid(); if (a.getStartTime().isAfter(Instant.now())) invalid(); a.setStatus(OnboardActivityStatus.IN_PROGRESS); }
            case "complete" -> { if (a.getStatus()!=OnboardActivityStatus.IN_PROGRESS) invalid(); a.setStatus(OnboardActivityStatus.COMPLETED); }
            case "cancel" -> { if (a.getStatus()==OnboardActivityStatus.COMPLETED || a.getStatus()==OnboardActivityStatus.CANCELLED) invalid(); a.setStatus(OnboardActivityStatus.CANCELLED); }
            default -> throw new IllegalArgumentException("Unsupported activity action");
        }
        return a;
    }
    public void validateActivity(OnboardActivity a, UUID exclude) {
        if (a.getActivityCode()==null || a.getActivityCode().isBlank() || a.getActivityName()==null || a.getActivityName().isBlank()) throw new IllegalArgumentException("Activity code and name are required");
        if (a.getStartTime()==null || a.getEndTime()==null || !a.getEndTime().isAfter(a.getStartTime())) throw new IllegalArgumentException("Activity end must be after start");
        if (a.getCapacity()==null || a.getCapacity()<1) throw new IllegalArgumentException("Capacity must be positive");
        if (a.getPrice()!=null && (a.getPrice().signum()<0 || a.getCurrencyCode()==null || a.getCurrencyCode().isBlank())) throw new IllegalArgumentException("A non-negative price requires a currency");
        activities.findByActivityCode(a.getActivityCode()).filter(x -> !x.getOnboardActivityId().equals(exclude)).ifPresent(x -> { throw new IllegalArgumentException("Activity code already exists"); });
        if(a.getCruise()!=null&&a.getLocation()!=null&&!a.getLocation().isBlank()&&activities.countScheduleConflicts(a.getCruise().getCruiseId(),a.getLocation(),a.getStartTime(),a.getEndTime(),List.of(OnboardActivityStatus.PLANNED,OnboardActivityStatus.OPEN,OnboardActivityStatus.FULL,OnboardActivityStatus.IN_PROGRESS),exclude)>0)throw new IllegalArgumentException("Activity schedule conflicts at the same location on this cruise");
    }
    @Transactional(readOnly=true)
    public long scheduleConflicts(UUID id){OnboardActivity a=activity(id);if(a.getLocation()==null||a.getLocation().isBlank())return 0;return activities.countScheduleConflicts(a.getCruise().getCruiseId(),a.getLocation(),a.getStartTime(),a.getEndTime(),List.of(OnboardActivityStatus.PLANNED,OnboardActivityStatus.OPEN,OnboardActivityStatus.FULL,OnboardActivityStatus.IN_PROGRESS),id);}

    @Transactional(readOnly = true)
    public List<Registration> listRegistrations(UUID activityId) { return activityId==null ? registrations.findAll() : registrations.findByOnboardActivity_OnboardActivityId(activityId); }
    @Transactional(readOnly = true)
    public List<Registration> listRegistrations(UUID activityId,UUID userId,boolean staff){if(staff)return listRegistrations(activityId);if(activityId==null)return registrations.findByBookingPassenger_Passenger_User_UserId(userId);return registrations.findByBookingPassenger_Passenger_User_UserId(userId).stream().filter(r->r.getOnboardActivity()!=null&&r.getOnboardActivity().getOnboardActivityId().equals(activityId)).toList();}
    @Transactional(readOnly = true)
    public Registration registration(UUID id) { return registrations.findById(id).orElseThrow(() -> missing("Registration", id)); }
    public Registration updatePending(UUID id,Integer quantity,String notes,UUID userId,boolean staff){Registration r=registrations.lockById(id).orElseThrow(()->missing("Registration",id));assertRegistrationOwner(r,userId,staff);if(r.getStatus()!=RegistrationStatus.PENDING)invalid();if(quantity==null||quantity<1)throw new IllegalArgumentException("Quantity must be positive");if(r.getOnboardActivity()!=null){OnboardActivity a=activities.lockActivity(r.getOnboardActivity().getOnboardActivityId()).orElseThrow(()->missing("Activity",r.getOnboardActivity().getOnboardActivityId()));long other=occupied(a.getOnboardActivityId())-r.getQuantity();if(other+quantity>a.getCapacity())throw new IllegalStateException("Activity capacity exceeded");r.setQuantity(quantity);r.setNotes(notes);if(other+quantity>=a.getCapacity())a.setStatus(OnboardActivityStatus.FULL);else if(a.getStatus()==OnboardActivityStatus.FULL)a.setStatus(OnboardActivityStatus.OPEN);}else if(r.getShoreExcursion()!=null){var e=excursions.lockById(r.getShoreExcursion().getShoreExcursionId()).orElseThrow(()->missing("Excursion",r.getShoreExcursion().getShoreExcursionId()));long other=occupiedExcursion(e.getShoreExcursionId())-r.getQuantity();if(other+quantity>e.getMaximumCapacity())throw new IllegalStateException("Excursion capacity exceeded");r.setQuantity(quantity);r.setNotes(notes);if(other+quantity>=e.getMaximumCapacity())e.setStatus(ShoreExcursionStatus.FULL);else if(e.getStatus()==ShoreExcursionStatus.FULL)e.setStatus(ShoreExcursionStatus.OPEN);}else throw new IllegalStateException("Registration has no supported target");return r;}
    public Registration getRegistration(UUID id,UUID userId,boolean staff){Registration r=registration(id);assertRegistrationOwner(r,userId,staff);return r;}
    public Registration register(UUID bookingPassengerId, UUID activityId, Integer quantity, UUID userId, boolean staff) {
        BookingPassenger bp = bookingPassengers.findById(bookingPassengerId).orElseThrow(() -> missing("Booking passenger", bookingPassengerId));
        int effectiveQuantity = (quantity == null || quantity < 1) ? 1 : quantity;
        assertBookingPassengerOwner(bp,userId,staff);
        if (bp.getBooking().getBookingStatus() != BookingStatus.Confirmed) throw new IllegalStateException("Booking is not confirmed");
        OnboardActivity a = activities.lockActivity(activityId).orElseThrow(() -> missing("Activity", activityId));
        if(!bp.getBooking().getCruise().getCruiseId().equals(a.getCruise().getCruiseId()))throw new IllegalStateException("Activity does not belong to the passenger's booking cruise");
        if (a.getStatus()!=OnboardActivityStatus.OPEN || !a.getEndTime().isAfter(Instant.now())) throw new IllegalStateException("Activity is not open for registration");
        if (registrations.existsByBookingPassenger_BookingPassengerIdAndOnboardActivity_OnboardActivityIdAndStatusIn(bookingPassengerId, activityId, List.of(RegistrationStatus.PENDING, RegistrationStatus.CONFIRMED))) throw new IllegalStateException("Passenger is already registered");
        if (occupied(activityId) + effectiveQuantity > a.getCapacity()) throw new IllegalStateException("Activity capacity exceeded");
        Registration r=new Registration(); r.setPassenger(bp.getPassenger()); r.setBookingPassenger(bp); r.setOnboardActivity(a); r.setRegistrationTime(Instant.now()); r.setQuantity(effectiveQuantity); r.setStatus(RegistrationStatus.PENDING);
        Registration saved=registrations.save(r);
        if (occupied(activityId) >= a.getCapacity()) a.setStatus(OnboardActivityStatus.FULL);
        return saved;
    }
    public Registration registerExcursion(UUID bookingPassengerId,UUID excursionId,Integer quantity,UUID userId,boolean staff){
        BookingPassenger bp=bookingPassengers.findById(bookingPassengerId).orElseThrow(()->missing("Booking passenger",bookingPassengerId));assertBookingPassengerOwner(bp,userId,staff);if(bp.getBooking().getBookingStatus()!=BookingStatus.Confirmed)throw new IllegalStateException("Booking is not confirmed");int effectiveQuantity=(quantity==null||quantity<1)?1:quantity;var e=excursions.lockById(excursionId).orElseThrow(()->missing("Excursion",excursionId));if(!bp.getBooking().getCruise().getCruiseId().equals(e.getCruiseStop().getItinerary().getCruise().getCruiseId()))throw new IllegalStateException("Excursion does not belong to the passenger's booking cruise");if(e.getStatus()!=ShoreExcursionStatus.OPEN||!e.getPlannedReturnTime().isAfter(Instant.now()))throw new IllegalStateException("Excursion is not open for registration");if(registrations.existsByBookingPassenger_BookingPassengerIdAndShoreExcursion_ShoreExcursionIdAndStatusIn(bookingPassengerId,excursionId,List.of(RegistrationStatus.PENDING,RegistrationStatus.CONFIRMED)))throw new IllegalStateException("Passenger is already registered");if(occupiedExcursion(excursionId)+effectiveQuantity>e.getMaximumCapacity())throw new IllegalStateException("Excursion capacity exceeded");Registration r=new Registration();r.setPassenger(bp.getPassenger());r.setBookingPassenger(bp);r.setShoreExcursion(e);r.setRegistrationTime(Instant.now());r.setQuantity(effectiveQuantity);r.setStatus(RegistrationStatus.PENDING);Registration saved=registrations.save(r);if(occupiedExcursion(excursionId)>=e.getMaximumCapacity())e.setStatus(ShoreExcursionStatus.FULL);return saved;
    }
    public Registration confirmRegistration(UUID id,UUID userId,boolean staff) {
        Registration r=registrations.lockById(id).orElseThrow(()->missing("Registration",id)); assertRegistrationOwner(r,userId,staff);if(r.getStatus()!=RegistrationStatus.PENDING)invalid();
        OnboardActivity a=r.getOnboardActivity();var excursion=r.getShoreExcursion();
        if(a!=null){UUID targetId=a.getOnboardActivityId();a=activities.lockActivity(targetId).orElseThrow(()->missing("Activity",targetId));if(a.getStatus()!=OnboardActivityStatus.OPEN&&a.getStatus()!=OnboardActivityStatus.FULL)throw new IllegalStateException("Activity is not open for confirmation");if(!a.getEndTime().isAfter(Instant.now()))throw new IllegalStateException("Activity has ended");if(occupied(a.getOnboardActivityId())>a.getCapacity())throw new IllegalStateException("Activity capacity exceeded");}
        else if(excursion!=null){UUID targetId=excursion.getShoreExcursionId();excursion=excursions.lockById(targetId).orElseThrow(()->missing("Excursion",targetId));if(excursion.getStatus()!=ShoreExcursionStatus.OPEN&&excursion.getStatus()!=ShoreExcursionStatus.FULL)throw new IllegalStateException("Excursion is not open for confirmation");if(!excursion.getPlannedReturnTime().isAfter(Instant.now()))throw new IllegalStateException("Excursion has ended");if(occupiedExcursion(targetId)>excursion.getMaximumCapacity())throw new IllegalStateException("Excursion capacity exceeded");}else throw new IllegalStateException("Registration has no supported target");
        r.setStatus(RegistrationStatus.CONFIRMED);
        if(participations.findByRegistration_RegistrationId(id).isEmpty()){Participation p=new Participation();p.setRegistration(r);p.setPassenger(r.getPassenger());p.setOnboardActivity(a);p.setShoreExcursion(excursion);p.setStatus(ParticipationStatus.REGISTERED);participations.save(p);}
        return r;
    }
    public Registration cancelRegistration(UUID id, String reason,UUID userId,boolean staff) {
        Registration r=registrations.lockById(id).orElseThrow(()->missing("Registration",id)); assertRegistrationOwner(r,userId,staff); if (r.getStatus()!=RegistrationStatus.PENDING && r.getStatus()!=RegistrationStatus.CONFIRMED) invalid();
        if (r.getOnboardActivity()!=null && !r.getOnboardActivity().getStartTime().isAfter(Instant.now())) throw new IllegalStateException("Activity has started");if(r.getShoreExcursion()!=null&&!r.getShoreExcursion().getStartTime().isAfter(Instant.now()))throw new IllegalStateException("Excursion has started");
        r.setStatus(RegistrationStatus.CANCELLED); r.setCancellationReason(reason);
        participations.findByRegistration_RegistrationId(id).ifPresent(p -> p.setStatus(ParticipationStatus.CANCELLED));
        if (r.getOnboardActivity()!=null && r.getOnboardActivity().getStatus()==OnboardActivityStatus.FULL && occupied(r.getOnboardActivity().getOnboardActivityId()) < r.getOnboardActivity().getCapacity()) r.getOnboardActivity().setStatus(OnboardActivityStatus.OPEN);
        if(r.getShoreExcursion()!=null&&r.getShoreExcursion().getStatus()==ShoreExcursionStatus.FULL&&occupiedExcursion(r.getShoreExcursion().getShoreExcursionId())<r.getShoreExcursion().getMaximumCapacity())r.getShoreExcursion().setStatus(ShoreExcursionStatus.OPEN);
        return r;
    }
    @Transactional(readOnly = true)
    public List<Participation> listParticipations() { return participations.findAll(); }
    @Transactional(readOnly = true)
    public Participation participation(UUID id) { return participations.findById(id).orElseThrow(() -> missing("Participation", id)); }
    public Participation attendance(UUID id, String action) {
        Participation p=participations.lockById(id).orElseThrow(() -> missing("Participation",id)); Instant now=Instant.now();
        switch(action) {
            case "check-in" -> { if (p.getStatus()!=ParticipationStatus.REGISTERED || p.getRegistration().getStatus()!=RegistrationStatus.CONFIRMED) invalid(); if(p.getOnboardActivity()!=null&&p.getOnboardActivity().getStatus()!=OnboardActivityStatus.IN_PROGRESS)invalid();if(p.getShoreExcursion()!=null&&p.getShoreExcursion().getStatus()!=ShoreExcursionStatus.IN_PROGRESS)invalid();p.setStatus(ParticipationStatus.CHECKED_IN); p.setCheckInTime(now); }
            case "checkout" -> { if (p.getStatus()!=ParticipationStatus.CHECKED_IN) invalid(); p.setStatus(ParticipationStatus.IN_PROGRESS); p.setCheckOutTime(now); }
            case "complete" -> { if (p.getStatus()!=ParticipationStatus.IN_PROGRESS) invalid(); p.setStatus(ParticipationStatus.COMPLETED); p.getRegistration().setStatus(RegistrationStatus.COMPLETED); }
            case "no-show" -> { if (p.getStatus()!=ParticipationStatus.REGISTERED || (p.getOnboardActivity()!=null && p.getOnboardActivity().getEndTime().isAfter(now)) || (p.getShoreExcursion()!=null&&p.getShoreExcursion().getPlannedReturnTime().isAfter(now))) invalid(); p.setStatus(ParticipationStatus.NO_SHOW); p.getRegistration().setStatus(RegistrationStatus.COMPLETED); }
            default -> throw new IllegalArgumentException("Unsupported participation action");
        }
        return p;
    }
    private long occupied(UUID id) { return registrations.sumOnboardParticipants(id, List.of(RegistrationStatus.PENDING, RegistrationStatus.CONFIRMED)); }
    private long occupiedExcursion(UUID id){return registrations.sumParticipants(id,List.of(RegistrationStatus.PENDING,RegistrationStatus.CONFIRMED));}
    private void assertRegistrationOwner(Registration r,UUID userId,boolean staff){assertBookingPassengerOwner(r.getBookingPassenger(),userId,staff);if(!r.getPassenger().getPassengerId().equals(r.getBookingPassenger().getPassenger().getPassengerId()))throw new IllegalStateException("Registration passenger does not match booking passenger");}
    private void assertBookingPassengerOwner(BookingPassenger bp,UUID userId,boolean staff){if(!staff&&(bp.getPassenger().getUser()==null||!bp.getPassenger().getUser().getUserId().equals(userId)))throw new org.springframework.security.access.AccessDeniedException("Booking passenger belongs to another passenger");}
    private static void invalid() { throw new IllegalStateException("Invalid lifecycle transition"); }
    private static EntityNotFoundException missing(String type, UUID id) { return new EntityNotFoundException(type+" not found: "+id); }
}
