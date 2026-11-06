package com.casms.activity.service;

import com.casms.activity.entity.*;
import com.casms.activity.enums.*;
import com.casms.activity.repository.*;
import com.casms.booking.entity.BookingPassenger;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.common.service.BusinessCodeGenerator;
import com.casms.servicepackage.repository.TourPackageActivityRepository;
import com.casms.ship.entity.Cabin;
import com.casms.ship.entity.Deck;
import com.casms.ship.repository.CabinRepository;
import com.casms.ship.repository.CruiseRepository;
import com.casms.ship.repository.DeckRepository;

import jakarta.persistence.EntityNotFoundException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Domain operations for onboard activities, registrations and attendance. */
@Service
@RequiredArgsConstructor
@Transactional
public class ActivityService {

    private static final String ENTITY_ACTIVITY = "Activity";
    private static final String ENTITY_REGISTRATION = "Registration";
    private static final String ENTITY_EXCURSION = "Excursion";
    private static final String FIELD_PATH = "fieldPath";

    private final OnboardActivityRepository activities;
    private final RegistrationRepository registrations;
    private final ParticipationRepository participations;
    private final BookingPassengerRepository bookingPassengers;
    private final CruiseRepository cruises;
    private final ShoreExcursionRepository excursions;
    private final TourPackageActivityRepository packageActivities;
    private final BusinessCodeGenerator businessCodeGenerator;
    private final DeckRepository deckRepository;
    private final CabinRepository cabinRepository;

    @Transactional(readOnly = true)
    public List<OnboardActivity> listActivities() {
        return activities.findByCatalogEntryFalse();
    }

    @Transactional(readOnly = true)
    public List<OnboardActivity> listActivitiesForCruise(UUID cruiseId) {
        return activities.findByCruise_CruiseId(cruiseId);
    }

    @Transactional(readOnly = true)
    public OnboardActivity activity(UUID id) {
        return findActivityOrThrow(id);
    }

    private OnboardActivity findActivityOrThrow(UUID id) {
        return activities.findById(id).orElseThrow(() -> missing(ENTITY_ACTIVITY, id));
    }

    @Transactional(readOnly = true)
    public int availableSeats(UUID id) {
        OnboardActivity a = findActivityOrThrow(id);
        if (a.getStatus() != OnboardActivityStatus.OPEN || !a.getEndTime().isAfter(Instant.now())) {
            return 0;
        }
        return (int) Math.max(0, a.getCapacity() - occupied(id));
    }

    @Transactional(readOnly = true)
    public List<OnboardActivity> catalog() {
        return activities.findByCatalogEntryTrue();
    }

    public OnboardActivity saveCatalog(UUID id, OnboardActivity input) {
        OnboardActivity a = id == null ? new OnboardActivity()
                : activities.lockActivity(id).orElseThrow(() -> missing(ENTITY_ACTIVITY, id));
        if (id != null && !a.isCatalogEntry()) {
            throw new IllegalArgumentException("Use the occurrence update API for scheduled activities");
        }
        if (id == null) {
            a.setActivityCode(businessCodeGenerator.next("AC"));
        }
        if (input.getActivityName() == null || input.getActivityName().isBlank()
                || input.getCapacity() == null || input.getCapacity() < 1) {
            throw new IllegalArgumentException("Activity name and positive capacity are required");
        }
        if (input.getCurrencyCode() == null) {
            input.setCurrencyCode("USD");
        }
        com.casms.common.util.NewWriteCurrency.requireUsd(input.getCurrencyCode());
        if (input.getPrice() != null && input.getPrice().signum() < 0) {
            throw new IllegalArgumentException("Price must be non-negative");
        }
        a.setCatalogEntry(true);
        a.setActivityName(input.getActivityName());
        a.setDescription(input.getDescription());
        a.setCategory(input.getCategory());
        a.setCapacity(input.getCapacity());
        a.setPrice(input.getPrice() == null ? BigDecimal.ZERO : input.getPrice());
        a.setCurrencyCode(input.getCurrencyCode() == null ? "USD" : input.getCurrencyCode());
        a.setStatus(OnboardActivityStatus.PLANNED);
        return activities.save(a);
    }

    public OnboardActivity scheduleCatalog(UUID cruiseId,
            com.casms.activity.controller.ActivityController.ActivityScheduleRequest input) {
        var source = activities.lockActivity(input.sourceActivityId())
                .orElseThrow(() -> missing(ENTITY_ACTIVITY, input.sourceActivityId()));
        if (!source.isCatalogEntry() || source.getStatus() != OnboardActivityStatus.PLANNED) {
            throw new IllegalArgumentException("Select an active independent activity catalog entry");
        }
        var a = new OnboardActivity();
        a.setSourceActivityId(source.getOnboardActivityId());
        a.setActivityName(source.getActivityName());
        a.setDescription(source.getDescription());
        a.setCategory(source.getCategory());
        a.setPrice(source.getPrice());
        a.setCurrencyCode(source.getCurrencyCode());
        a.setCapacity(input.capacity() == null ? source.getCapacity() : input.capacity());
        a.setStartTime(input.startTime());
        a.setEndTime(input.endTime());
        a.setLocation(input.location());
        return doCreateActivity(a, cruiseId, input.deckId(), input.cabinId());
    }

    /** Reusable content; no invented date, venue, capacity or component price. */
    public OnboardActivity createDefinition(String name, String description) {
        var a = new OnboardActivity();
        a.setActivityCode(businessCodeGenerator.next("AC"));
        a.setCatalogEntry(true);
        a.setActivityName(name.trim());
        a.setDescription(description);
        a.setStatus(OnboardActivityStatus.PLANNED);
        return activities.save(a);
    }

    public OnboardActivity createActivity(OnboardActivity a, UUID cruiseId) {
        return doCreateActivity(a, cruiseId, null, null);
    }

    public OnboardActivity createActivity(
            OnboardActivity a, UUID cruiseId, UUID deckId, UUID cabinId) {
        return doCreateActivity(a, cruiseId, deckId, cabinId);
    }

    private OnboardActivity doCreateActivity(
            OnboardActivity a, UUID cruiseId, UUID deckId, UUID cabinId) {
        com.casms.common.util.NewWriteCurrency.requireUsd(a.getCurrencyCode());
        a.setCruise(cruises.findById(cruiseId).orElseThrow(() -> missing("Cruise", cruiseId)));
        a.setActivityCode(businessCodeGenerator.next("AC"));
        if (a.getCurrencyCode() == null || a.getCurrencyCode().isBlank()) {
            a.setCurrencyCode("USD");
        }
        if (a.getPrice() == null) {
            a.setPrice(BigDecimal.ZERO);
        }
        resolveDeckAndCabin(a, deckId, cabinId);
        validateActivity(a, null);
        a.setStatus(OnboardActivityStatus.PLANNED);
        return activities.save(a);
    }

    public OnboardActivity updateActivity(UUID id, OnboardActivity input) {
        return doUpdateActivity(
                id,
                input,
                input.getDeck() != null ? input.getDeck().getDeckId() : null,
                input.getCabin() != null ? input.getCabin().getCabinId() : null);
    }

    public OnboardActivity updateActivity(
            UUID id, OnboardActivity input, UUID deckId, UUID cabinId) {
        return doUpdateActivity(id, input, deckId, cabinId);
    }

    private OnboardActivity doUpdateActivity(
            UUID id, OnboardActivity input, UUID deckId, UUID cabinId) {
        com.casms.common.util.NewWriteCurrency.requireUsd(input.getCurrencyCode());
        OnboardActivity a = activities.lockActivity(id).orElseThrow(() -> missing(ENTITY_ACTIVITY, id));
        if (a.isCatalogEntry()) {
            throw new IllegalArgumentException("Use PUT /activities/catalog/{id} to edit catalog metadata");
        }
        if (a.getStatus() != OnboardActivityStatus.PLANNED) {
            throw new IllegalStateException("Only planned activities can be edited");
        }
        long allocated = occupied(id);
        if (input.getCapacity() != null && input.getCapacity() < allocated) {
            throw new com.casms.common.exception.BusinessException(
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "CAPACITY_BELOW_ALLOCATED",
                    "Activity capacity cannot be below reserved participants",
                    java.util.Map.of(FIELD_PATH, "capacity", "allocatedParticipants", allocated));
        }
        OnboardActivity venue = new OnboardActivity();
        venue.setCruise(a.getCruise());
        resolveDeckAndCabin(venue, deckId, cabinId);
        a.setActivityName(input.getActivityName());
        a.setDescription(input.getDescription());
        a.setStartTime(input.getStartTime());
        a.setEndTime(input.getEndTime());
        a.setLocation(input.getLocation());
        a.setCapacity(input.getCapacity());
        a.setPrice(input.getPrice());
        a.setCurrencyCode(input.getCurrencyCode());
        a.setDeck(venue.getDeck());
        a.setCabin(venue.getCabin());
        validateActivity(a, id);
        return a;
    }

    private void resolveDeckAndCabin(OnboardActivity a, UUID deckId, UUID cabinId) {
        UUID shipId = extractShipId(a);
        Deck deck = resolveDeck(shipId, deckId);
        Cabin cabin = resolveCabin(shipId, deckId, cabinId);
        a.setDeck(deck != null ? deck : (cabin != null ? cabin.getDeck() : null));
        a.setCabin(cabin);
    }

    private static UUID extractShipId(OnboardActivity a) {
        if (a.getCruise() != null && a.getCruise().getShip() != null) {
            return a.getCruise().getShip().getShipId();
        }
        return null;
    }

    private Deck resolveDeck(UUID shipId, UUID deckId) {
        if (deckId == null) {
            return null;
        }
        Deck deck = deckRepository.findById(deckId)
                .orElseThrow(() -> new EntityNotFoundException("Deck not found: " + deckId));
        if (shipId == null || deck.getShip() == null || !shipId.equals(deck.getShip().getShipId())) {
            throw venueMismatch("deckId");
        }
        return deck;
    }

    private Cabin resolveCabin(UUID shipId, UUID deckId, UUID cabinId) {
        if (cabinId == null) {
            return null;
        }
        Cabin cabin = cabinRepository.findById(cabinId)
                .orElseThrow(() -> new EntityNotFoundException("Cabin not found: " + cabinId));
        if (!isCabinValidForShipAndDeck(cabin, shipId, deckId)) {
            throw venueMismatch("cabinId");
        }
        return cabin;
    }

    private static boolean isCabinValidForShipAndDeck(Cabin cabin, UUID shipId, UUID deckId) {
        if (shipId == null || cabin.getShip() == null || !shipId.equals(cabin.getShip().getShipId())) {
            return false;
        }
        if (cabin.getDeck() == null || cabin.getDeck().getShip() == null
                || !shipId.equals(cabin.getDeck().getShip().getShipId())) {
            return false;
        }
        return deckId == null || deckId.equals(cabin.getDeck().getDeckId());
    }

    private static com.casms.common.exception.BusinessException venueMismatch(String field) {
        return new com.casms.common.exception.BusinessException(
                org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                "ACTIVITY_VENUE_MISMATCH",
                "Activity venue must belong to the assigned ship and selected deck",
                java.util.Map.of(FIELD_PATH, field));
    }

    public void deleteActivity(UUID id) {
        OnboardActivity a = findActivityOrThrow(id);
        if (a.getStatus() != OnboardActivityStatus.PLANNED
                || registrations.existsByOnboardActivity_OnboardActivityId(id)
                || packageActivities.existsByOnboardActivity_OnboardActivityId(id)
                || activities.isReferencedSource(id)) {
            throw new IllegalStateException("Only an unused planned activity can be deleted");
        }
        activities.delete(a);
    }

    public OnboardActivity lifecycle(UUID id, String action) {
        OnboardActivity a = findActivityOrThrow(id);
        if (a.isCatalogEntry()) {
            throw new IllegalArgumentException("Schedule the catalog activity on a cruise before lifecycle actions");
        }
        switch (action) {
            case "open" -> handleOpenLifecycle(a, id);
            case "close" -> throw new UnsupportedOperationException(
                    "OnboardActivity has no CLOSED status; closing registrations cannot be represented without changing the approved schema");
            case "start" -> handleStartLifecycle(a);
            case "complete" -> handleCompleteLifecycle(a);
            case "cancel" -> handleCancelLifecycle(a);
            default -> throw new IllegalArgumentException("Unsupported activity action");
        }
        return a;
    }

    private void handleOpenLifecycle(OnboardActivity a, UUID id) {
        if (a.getStatus() != OnboardActivityStatus.PLANNED) {
            invalid();
        }
        validateActivity(a, id);
        a.setStatus(OnboardActivityStatus.OPEN);
    }

    private static void handleStartLifecycle(OnboardActivity a) {
        if (a.getStatus() != OnboardActivityStatus.OPEN && a.getStatus() != OnboardActivityStatus.FULL) {
            invalid();
        }
        if (a.getStartTime().isAfter(Instant.now())) {
            invalid();
        }
        a.setStatus(OnboardActivityStatus.IN_PROGRESS);
    }

    private static void handleCompleteLifecycle(OnboardActivity a) {
        if (a.getStatus() != OnboardActivityStatus.IN_PROGRESS) {
            invalid();
        }
        a.setStatus(OnboardActivityStatus.COMPLETED);
    }

    private static void handleCancelLifecycle(OnboardActivity a) {
        if (a.getStatus() == OnboardActivityStatus.COMPLETED || a.getStatus() == OnboardActivityStatus.CANCELLED) {
            invalid();
        }
        a.setStatus(OnboardActivityStatus.CANCELLED);
    }

    public void validateActivity(OnboardActivity a, UUID exclude) {
        validateActivityBasicInfo(a);
        validateActivitySchedule(a);
        validateActivityPricing(a);
        validateUniqueActivityCode(a.getActivityCode(), exclude);
        validateLocationConflicts(a, exclude);
    }

    private static void validateActivityBasicInfo(OnboardActivity a) {
        if (a.getActivityCode() == null || a.getActivityCode().isBlank()
                || a.getActivityName() == null || a.getActivityName().isBlank()) {
            throw new IllegalArgumentException("Activity code and name are required");
        }
        if (a.getCapacity() == null || a.getCapacity() < 1) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
    }

    private static void validateActivitySchedule(OnboardActivity a) {
        if (a.getStartTime() == null || a.getEndTime() == null || !a.getEndTime().isAfter(a.getStartTime())) {
            throw new IllegalArgumentException("Activity end must be after start");
        }
        if (a.getCruise() != null) {
            var departure = a.getCruise().getDepartureDatetime();
            var end = a.getCruise().getReturnDatetime();
            if (departure == null || end == null || a.getStartTime().isBefore(departure) || a.getEndTime().isAfter(end)) {
                String errorField = (departure == null || a.getStartTime().isBefore(departure)) ? "startTime" : "endTime";
                throw new com.casms.common.exception.BusinessException(
                        org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                        "ACTIVITY_OUTSIDE_CRUISE",
                        "Activity must fit the Cruise schedule",
                        java.util.Map.of(FIELD_PATH, errorField));
            }
        }
    }

    private static void validateActivityPricing(OnboardActivity a) {
        if (a.getPrice() != null && (a.getPrice().signum() < 0 || a.getCurrencyCode() == null || a.getCurrencyCode().isBlank())) {
            throw new IllegalArgumentException("A non-negative price requires a currency");
        }
    }

    private void validateUniqueActivityCode(String activityCode, UUID exclude) {
        activities.findByActivityCode(activityCode)
                .filter(x -> !x.getOnboardActivityId().equals(exclude))
                .ifPresent(x -> {
                    throw new IllegalArgumentException("Activity code already exists");
                });
    }

    private void validateLocationConflicts(OnboardActivity a, UUID exclude) {
        if (a.getCruise() == null || a.getLocation() == null || a.getLocation().isBlank()) {
            return;
        }
        long conflicts = activities.countScheduleConflicts(
                a.getCruise().getCruiseId(),
                a.getLocation(),
                a.getStartTime(),
                a.getEndTime(),
                List.of(
                        OnboardActivityStatus.PLANNED,
                        OnboardActivityStatus.OPEN,
                        OnboardActivityStatus.FULL,
                        OnboardActivityStatus.IN_PROGRESS),
                exclude);
        if (conflicts > 0) {
            throw new IllegalArgumentException("Activity schedule conflicts at the same location on this cruise");
        }
    }

    @Transactional(readOnly = true)
    public long scheduleConflicts(UUID id) {
        OnboardActivity a = findActivityOrThrow(id);
        if (a.getCruise() == null || a.getLocation() == null || a.getLocation().isBlank()) {
            return 0;
        }
        return activities.countScheduleConflicts(
                a.getCruise().getCruiseId(),
                a.getLocation(),
                a.getStartTime(),
                a.getEndTime(),
                List.of(
                        OnboardActivityStatus.PLANNED,
                        OnboardActivityStatus.OPEN,
                        OnboardActivityStatus.FULL,
                        OnboardActivityStatus.IN_PROGRESS),
                id);
    }

    @Transactional(readOnly = true)
    public List<Registration> listRegistrations(UUID activityId) {
        return findRegistrationsByActivity(activityId);
    }

    private List<Registration> findRegistrationsByActivity(UUID activityId) {
        return activityId == null
                ? registrations.findAll()
                : registrations.findByOnboardActivity_OnboardActivityId(activityId);
    }

    @Transactional(readOnly = true)
    public List<Registration> listRegistrations(UUID activityId, UUID userId, boolean staff) {
        if (staff) {
            return findRegistrationsByActivity(activityId);
        }
        if (activityId == null) {
            return registrations.findByBookingPassenger_Passenger_User_UserId(userId);
        }
        return registrations.findByBookingPassenger_Passenger_User_UserId(userId).stream()
                .filter(r -> r.getOnboardActivity() != null
                        && r.getOnboardActivity().getOnboardActivityId().equals(activityId))
                .toList();
    }

    @Transactional(readOnly = true)
    public Registration registration(UUID id) {
        return findRegistrationOrThrow(id);
    }

    private Registration findRegistrationOrThrow(UUID id) {
        return registrations.findById(id).orElseThrow(() -> missing(ENTITY_REGISTRATION, id));
    }

    public Registration updatePending(
            UUID id, Integer quantity, String notes, UUID userId, boolean staff) {
        Registration r = registrations.lockById(id).orElseThrow(() -> missing(ENTITY_REGISTRATION, id));
        assertRegistrationOwner(r, userId, staff);
        if (r.getStatus() != RegistrationStatus.PENDING) {
            invalid();
        }
        if (quantity == null || quantity < 1) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (r.getOnboardActivity() != null) {
            updatePendingOnboardActivity(r, quantity, notes);
        } else if (r.getShoreExcursion() != null) {
            updatePendingShoreExcursion(r, quantity, notes);
        } else {
            throw new IllegalStateException("Registration has no supported target");
        }
        return r;
    }

    private void updatePendingOnboardActivity(Registration r, int quantity, String notes) {
        UUID activityId = r.getOnboardActivity().getOnboardActivityId();
        OnboardActivity a = activities.lockActivity(activityId)
                .orElseThrow(() -> missing(ENTITY_ACTIVITY, activityId));
        long other = occupied(activityId) - r.getQuantity();
        if (other + quantity > a.getCapacity()) {
            throw new IllegalStateException("Activity capacity exceeded");
        }
        r.setQuantity(quantity);
        r.setNotes(notes);
        if (other + quantity >= a.getCapacity()) {
            a.setStatus(OnboardActivityStatus.FULL);
        } else if (a.getStatus() == OnboardActivityStatus.FULL) {
            a.setStatus(OnboardActivityStatus.OPEN);
        }
    }

    private void updatePendingShoreExcursion(Registration r, int quantity, String notes) {
        UUID excursionId = r.getShoreExcursion().getShoreExcursionId();
        ShoreExcursion e = excursions.lockById(excursionId)
                .orElseThrow(() -> missing(ENTITY_EXCURSION, excursionId));
        long other = occupiedExcursion(excursionId) - r.getQuantity();
        if (other + quantity > e.getMaximumCapacity()) {
            throw new IllegalStateException("Excursion capacity exceeded");
        }
        r.setQuantity(quantity);
        r.setNotes(notes);
        if (other + quantity >= e.getMaximumCapacity()) {
            e.setStatus(ShoreExcursionStatus.FULL);
        } else if (e.getStatus() == ShoreExcursionStatus.FULL) {
            e.setStatus(ShoreExcursionStatus.OPEN);
        }
    }

    public Registration getRegistration(UUID id, UUID userId, boolean staff) {
        Registration r = findRegistrationOrThrow(id);
        assertRegistrationOwner(r, userId, staff);
        return r;
    }

    public Registration register(
            UUID bookingPassengerId,
            UUID activityId,
            Integer quantity,
            UUID userId,
            boolean staff) {
        BookingPassenger bp = bookingPassengers.findById(bookingPassengerId)
                .orElseThrow(() -> missing("Booking passenger", bookingPassengerId));
        int effectiveQuantity = (quantity == null || quantity < 1) ? 1 : quantity;
        assertBookingPassengerOwner(bp, userId, staff);
        if (bp.getBooking().getBookingStatus() != BookingStatus.Confirmed) {
            throw new IllegalStateException("Booking is not confirmed");
        }
        OnboardActivity a = activities.lockActivity(activityId)
                .orElseThrow(() -> missing(ENTITY_ACTIVITY, activityId));
        if (a.getCruise() == null
                || bp.getBooking().getCruise() == null
                || !bp.getBooking().getCruise().getCruiseId().equals(a.getCruise().getCruiseId())) {
            throw new IllegalStateException("Activity does not belong to the passenger's booking cruise");
        }
        if (a.getStatus() != OnboardActivityStatus.OPEN || !a.getEndTime().isAfter(Instant.now())) {
            throw new IllegalStateException("Activity is not open for registration");
        }
        if (registrations.existsByBookingPassenger_BookingPassengerIdAndOnboardActivity_OnboardActivityIdAndStatusIn(
                bookingPassengerId,
                activityId,
                List.of(RegistrationStatus.PENDING, RegistrationStatus.CONFIRMED))) {
            throw new IllegalStateException("Passenger is already registered");
        }
        if (occupied(activityId) + effectiveQuantity > a.getCapacity()) {
            throw new IllegalStateException("Activity capacity exceeded");
        }
        Registration r = new Registration();
        r.setPassenger(bp.getPassenger());
        r.setBookingPassenger(bp);
        r.setOnboardActivity(a);
        r.setRegistrationTime(Instant.now());
        r.setQuantity(effectiveQuantity);
        r.setStatus(RegistrationStatus.PENDING);
        Registration saved = registrations.save(r);
        if (occupied(activityId) >= a.getCapacity()) {
            a.setStatus(OnboardActivityStatus.FULL);
        }
        return saved;
    }

    public Registration registerExcursion(
            UUID bookingPassengerId,
            UUID excursionId,
            Integer quantity,
            UUID userId,
            boolean staff) {
        BookingPassenger bp = bookingPassengers.findById(bookingPassengerId)
                .orElseThrow(() -> missing("Booking passenger", bookingPassengerId));
        assertBookingPassengerOwner(bp, userId, staff);
        if (bp.getBooking().getBookingStatus() != BookingStatus.Confirmed) {
            throw new IllegalStateException("Booking is not confirmed");
        }
        int effectiveQuantity = (quantity == null || quantity < 1) ? 1 : quantity;
        var e = excursions.lockById(excursionId)
                .orElseThrow(() -> missing(ENTITY_EXCURSION, excursionId));
        if (!bp.getBooking().getCruise().getCruiseId().equals(e.getCruiseStop().getItinerary().getCruise().getCruiseId())) {
            throw new IllegalStateException("Excursion does not belong to the passenger's booking cruise");
        }
        if (e.getStatus() != ShoreExcursionStatus.OPEN || !e.getPlannedReturnTime().isAfter(Instant.now())) {
            throw new IllegalStateException("Excursion is not open for registration");
        }
        if (registrations.existsByBookingPassenger_BookingPassengerIdAndShoreExcursion_ShoreExcursionIdAndStatusIn(
                bookingPassengerId,
                excursionId,
                List.of(RegistrationStatus.PENDING, RegistrationStatus.CONFIRMED))) {
            throw new IllegalStateException("Passenger is already registered");
        }
        if (occupiedExcursion(excursionId) + effectiveQuantity > e.getMaximumCapacity()) {
            throw new IllegalStateException("Excursion capacity exceeded");
        }
        Registration r = new Registration();
        r.setPassenger(bp.getPassenger());
        r.setBookingPassenger(bp);
        r.setShoreExcursion(e);
        r.setRegistrationTime(Instant.now());
        r.setQuantity(effectiveQuantity);
        r.setStatus(RegistrationStatus.PENDING);
        Registration saved = registrations.save(r);
        if (occupiedExcursion(excursionId) >= e.getMaximumCapacity()) {
            e.setStatus(ShoreExcursionStatus.FULL);
        }
        return saved;
    }

    public Registration confirmRegistration(UUID id, UUID userId, boolean staff) {
        Registration r = registrations.lockById(id).orElseThrow(() -> missing(ENTITY_REGISTRATION, id));
        assertRegistrationOwner(r, userId, staff);
        if (r.getStatus() != RegistrationStatus.PENDING) {
            invalid();
        }
        OnboardActivity a = r.getOnboardActivity();
        ShoreExcursion excursion = r.getShoreExcursion();
        if (a != null) {
            a = lockAndValidateActivityForConfirmation(a.getOnboardActivityId());
        } else if (excursion != null) {
            excursion = lockAndValidateExcursionForConfirmation(excursion.getShoreExcursionId());
        } else {
            throw new IllegalStateException("Registration has no supported target");
        }
        r.setStatus(RegistrationStatus.CONFIRMED);
        ensureParticipationRegistered(r, a, excursion, id);
        return r;
    }

    private OnboardActivity lockAndValidateActivityForConfirmation(UUID activityId) {
        OnboardActivity a = activities.lockActivity(activityId)
                .orElseThrow(() -> missing(ENTITY_ACTIVITY, activityId));
        if (a.getStatus() != OnboardActivityStatus.OPEN && a.getStatus() != OnboardActivityStatus.FULL) {
            throw new IllegalStateException("Activity is not open for confirmation");
        }
        if (!a.getEndTime().isAfter(Instant.now())) {
            throw new IllegalStateException("Activity has ended");
        }
        if (occupied(activityId) > a.getCapacity()) {
            throw new IllegalStateException("Activity capacity exceeded");
        }
        return a;
    }

    private ShoreExcursion lockAndValidateExcursionForConfirmation(UUID excursionId) {
        ShoreExcursion e = excursions.lockById(excursionId)
                .orElseThrow(() -> missing(ENTITY_EXCURSION, excursionId));
        if (e.getStatus() != ShoreExcursionStatus.OPEN && e.getStatus() != ShoreExcursionStatus.FULL) {
            throw new IllegalStateException("Excursion is not open for confirmation");
        }
        if (!e.getPlannedReturnTime().isAfter(Instant.now())) {
            throw new IllegalStateException("Excursion has ended");
        }
        if (occupiedExcursion(excursionId) > e.getMaximumCapacity()) {
            throw new IllegalStateException("Excursion capacity exceeded");
        }
        return e;
    }

    private void ensureParticipationRegistered(Registration r, OnboardActivity a, ShoreExcursion excursion, UUID id) {
        if (participations.findByRegistration_RegistrationId(id).isEmpty()) {
            Participation p = new Participation();
            p.setRegistration(r);
            p.setPassenger(r.getPassenger());
            p.setOnboardActivity(a);
            p.setShoreExcursion(excursion);
            p.setStatus(ParticipationStatus.REGISTERED);
            participations.save(p);
        }
    }

    public Registration cancelRegistration(UUID id, String reason, UUID userId, boolean staff) {
        Registration r = registrations.lockById(id).orElseThrow(() -> missing(ENTITY_REGISTRATION, id));
        assertRegistrationOwner(r, userId, staff);
        if (r.getStatus() != RegistrationStatus.PENDING && r.getStatus() != RegistrationStatus.CONFIRMED) {
            invalid();
        }
        validateNotStartedBeforeCancellation(r);
        r.setStatus(RegistrationStatus.CANCELLED);
        r.setCancellationReason(reason);
        participations.findByRegistration_RegistrationId(id)
                .ifPresent(p -> p.setStatus(ParticipationStatus.CANCELLED));
        reopenTargetIfCapacityBecameAvailable(r);
        return r;
    }

    private static void validateNotStartedBeforeCancellation(Registration r) {
        Instant now = Instant.now();
        if (r.getOnboardActivity() != null && !r.getOnboardActivity().getStartTime().isAfter(now)) {
            throw new IllegalStateException("Activity has started");
        }
        if (r.getShoreExcursion() != null && !r.getShoreExcursion().getStartTime().isAfter(now)) {
            throw new IllegalStateException("Excursion has started");
        }
    }

    private void reopenTargetIfCapacityBecameAvailable(Registration r) {
        if (r.getOnboardActivity() != null) {
            var a = r.getOnboardActivity();
            if (a.getStatus() == OnboardActivityStatus.FULL && occupied(a.getOnboardActivityId()) < a.getCapacity()) {
                a.setStatus(OnboardActivityStatus.OPEN);
            }
        }
        if (r.getShoreExcursion() != null) {
            var e = r.getShoreExcursion();
            if (e.getStatus() == ShoreExcursionStatus.FULL && occupiedExcursion(e.getShoreExcursionId()) < e.getMaximumCapacity()) {
                e.setStatus(ShoreExcursionStatus.OPEN);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<Participation> listParticipations() {
        return participations.findAll();
    }

    @Transactional(readOnly = true)
    public Participation participation(UUID id) {
        return participations.findById(id).orElseThrow(() -> missing("Participation", id));
    }

    public Participation attendance(UUID id, String action) {
        Participation p = participations.lockById(id).orElseThrow(() -> missing("Participation", id));
        Instant now = Instant.now();
        switch (action) {
            case "check-in" -> handleCheckIn(p, now);
            case "checkout" -> handleCheckOut(p, now);
            case "complete" -> handleCompleteAttendance(p);
            case "no-show" -> handleNoShow(p, now);
            default -> throw new IllegalArgumentException("Unsupported participation action");
        }
        return p;
    }

    private static void handleCheckIn(Participation p, Instant now) {
        if (p.getStatus() != ParticipationStatus.REGISTERED
                || p.getRegistration().getStatus() != RegistrationStatus.CONFIRMED) {
            invalid();
        }
        if (p.getOnboardActivity() != null && p.getOnboardActivity().getStatus() != OnboardActivityStatus.IN_PROGRESS) {
            invalid();
        }
        if (p.getShoreExcursion() != null && p.getShoreExcursion().getStatus() != ShoreExcursionStatus.IN_PROGRESS) {
            invalid();
        }
        p.setStatus(ParticipationStatus.CHECKED_IN);
        p.setCheckInTime(now);
    }

    private static void handleCheckOut(Participation p, Instant now) {
        if (p.getStatus() != ParticipationStatus.CHECKED_IN) {
            invalid();
        }
        p.setStatus(ParticipationStatus.IN_PROGRESS);
        p.setCheckOutTime(now);
    }

    private static void handleCompleteAttendance(Participation p) {
        if (p.getStatus() != ParticipationStatus.IN_PROGRESS) {
            invalid();
        }
        p.setStatus(ParticipationStatus.COMPLETED);
        p.getRegistration().setStatus(RegistrationStatus.COMPLETED);
    }

    private static void handleNoShow(Participation p, Instant now) {
        if (p.getStatus() != ParticipationStatus.REGISTERED
                || (p.getOnboardActivity() != null && p.getOnboardActivity().getEndTime().isAfter(now))
                || (p.getShoreExcursion() != null && p.getShoreExcursion().getPlannedReturnTime().isAfter(now))) {
            invalid();
        }
        p.setStatus(ParticipationStatus.NO_SHOW);
        p.getRegistration().setStatus(RegistrationStatus.COMPLETED);
    }

    private long occupied(UUID id) {
        return registrations.sumOnboardParticipants(
                id, List.of(RegistrationStatus.PENDING, RegistrationStatus.CONFIRMED));
    }

    private long occupiedExcursion(UUID id) {
        return registrations.sumParticipants(
                id, List.of(RegistrationStatus.PENDING, RegistrationStatus.CONFIRMED));
    }

    private void assertRegistrationOwner(Registration r, UUID userId, boolean staff) {
        assertBookingPassengerOwner(r.getBookingPassenger(), userId, staff);
        if (!r.getPassenger().getPassengerId().equals(r.getBookingPassenger().getPassenger().getPassengerId())) {
            throw new IllegalStateException("Registration passenger does not match booking passenger");
        }
    }

    private void assertBookingPassengerOwner(BookingPassenger bp, UUID userId, boolean staff) {
        if (!staff && (bp.getPassenger().getUser() == null || !bp.getPassenger().getUser().getUserId().equals(userId))) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Booking passenger belongs to another passenger");
        }
    }

    private static void invalid() {
        throw new IllegalStateException("Invalid lifecycle transition");
    }

    private static EntityNotFoundException missing(String type, UUID id) {
        return new EntityNotFoundException(type + " not found: " + id);
    }
}
