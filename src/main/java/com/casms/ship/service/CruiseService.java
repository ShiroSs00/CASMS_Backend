package com.casms.ship.service;

import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.booking.repository.BookingRepository;
import com.casms.booking.repository.CabinAssignmentRepository;
import com.casms.checkin.repository.CruiseCheckinRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.common.service.BusinessCodeGenerator;
import com.casms.ship.dto.request.CruiseCreateRequest;
import com.casms.ship.dto.request.CruiseUpdateDraftRequest;
import com.casms.ship.dto.request.CruiseOpenSaleRequest;
import com.casms.ship.dto.request.CruiseStopSaleRequest;
import com.casms.ship.dto.request.CruiseResumeSaleRequest;
import com.casms.ship.dto.response.CabinTypeInventoryItem;
import com.casms.ship.dto.response.CruiseCapacityValidationResponse;
import com.casms.ship.dto.response.CruiseDownstreamHandoffResponse;
import com.casms.ship.dto.response.CruiseInventorySummaryResponse;
import com.casms.ship.dto.response.CruiseManifestResponse;
import com.casms.ship.dto.response.CruiseReadinessResponse;
import com.casms.ship.dto.response.CruiseResponse;
import com.casms.ship.dto.response.CruiseSaleReadinessResponse;
import com.casms.ship.dto.response.CruiseStaffResponse;
import com.casms.ship.dto.response.ShipCompatibilityResponse;
import com.casms.ship.dto.response.ShipChangeImpactResponse;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.CruiseTourPackage;
import com.casms.ship.entity.Itinerary;
import com.casms.ship.entity.Port;
import com.casms.ship.entity.Ship;
import com.casms.ship.enums.CabinStatus;
import com.casms.ship.enums.CabinType;
import com.casms.ship.enums.CruiseCruiseType;
import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.enums.ItineraryStatus;
import com.casms.ship.enums.PortStatus;
import com.casms.ship.enums.ShipStatus;
import com.casms.ship.repository.*;
import com.casms.trip.entity.TripScheduleVersion;
import com.casms.trip.enums.TripScheduleVersionStatus;
import com.casms.trip.repository.TripScheduleVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CruiseService {

    private final CruiseRepository cruiseRepository;
    private final ShipRepository shipRepository;
    private final PortRepository portRepository;
    private final DeckRepository deckRepository;
    private final CabinRepository cabinRepository;
    private final ItineraryRepository itineraryRepository;
    private final BookingRepository bookingRepository;
    private final BookingPassengerRepository bookingPassengerRepository;
    private final CabinAssignmentRepository cabinAssignmentRepository;
    private final CruiseCheckinRepository checkinRepository;
    private final BusinessCodeGenerator businessCodeGenerator;
    private final TripScheduleVersionRepository tripScheduleVersionRepository;
    private final ScheduleMaterializationService scheduleMaterializationService;
    private final CruiseStaffRepository cruiseStaffRepository;
    private final CruiseRegulationService cruiseRegulationService;
    private final com.casms.activity.repository.OnboardActivityRepository onboardActivityRepository;
    private final com.casms.activity.repository.ShoreExcursionRepository shoreExcursionRepository;
    private final CruiseTourPackageRepository cruiseTourPackageRepository;
    private final CruiseStopRepository cruiseStopRepository;
    private final CruiseRegulationRepository cruiseRegulationRepository;
    private final CruiseImpactTaskRepository cruiseImpactTaskRepository;

    @Transactional
    public CruiseResponse create(CruiseCreateRequest request) {
        String cruiseCode = businessCodeGenerator.next("CR");
        log.info("Creating new cruise with code: {}", cruiseCode);

        if (request.getDepartureDatetime() == null || request.getReturnDatetime() == null
                || !request.getReturnDatetime().isAfter(request.getDepartureDatetime())) {
            throw new BusinessException("INVALID_SCHEDULE", "Departure and return datetimes are required; return must be after departure");
        }

        CruiseCruiseType cruiseType = request.getCruiseType();
        if (cruiseType == null) {
            long hours = java.time.Duration.between(request.getDepartureDatetime(), request.getReturnDatetime()).toHours();
            cruiseType = (hours <= 24 ? CruiseCruiseType.ONE_DAY : CruiseCruiseType.MULTI_DAYS);
        }

        Port departurePort = portRepository.findById(request.getDeparturePortId())
                .orElseThrow(() -> new ResourceNotFoundException("Departure Port", request.getDeparturePortId()));
        if (departurePort.getStatus() != PortStatus.ACTIVE) {
            throw new BusinessException("PORT_NOT_ACTIVE", "Departure port must be in ACTIVE status");
        }

        Port returnPort = portRepository.findById(request.getReturnPortId())
                .orElseThrow(() -> new ResourceNotFoundException("Return Port", request.getReturnPortId()));
        if (returnPort.getStatus() != PortStatus.ACTIVE) {
            throw new BusinessException("PORT_NOT_ACTIVE", "Return port must be in ACTIVE status");
        }

        Ship ship = null;
        if (request.getShipId() != null) {
            ship = shipRepository.findById(request.getShipId())
                    .orElseThrow(() -> new ResourceNotFoundException("Ship", request.getShipId()));

            if (ship.getStatus() != ShipStatus.ACTIVE) {
                throw new BusinessException("SHIP_NOT_ACTIVE", "Assigned ship must be in ACTIVE status");
            }

            if (cruiseRepository.existsOverlappingCruises(ship.getShipId(), request.getDepartureDatetime(), request.getReturnDatetime())) {
                throw new BusinessException("SHIP_SCHEDULE_CONFLICT", "Ship already has an overlapping active cruise in this schedule window");
            }
        }

        // D01 / C15: Link cruise to an existing published TripScheduleVersion (optional)
        TripScheduleVersion sourceVersion = null;
        if (request.getSourceVersionId() != null) {
            sourceVersion = tripScheduleVersionRepository.findById(request.getSourceVersionId())
                    .orElseThrow(() -> new ResourceNotFoundException("TripScheduleVersion", request.getSourceVersionId()));
            if (sourceVersion.getStatus() != TripScheduleVersionStatus.PUBLISHED) {
                throw new BusinessException("VERSION_NOT_PUBLISHED",
                        "Source TripScheduleVersion must be in PUBLISHED status (current: " + sourceVersion.getStatus() + ")");
            }
        }

        // BE-11: Validate departure + return port country scope when sourceVersion is present
        if (sourceVersion != null && sourceVersion.getTrip() != null) {
            com.casms.trip.service.CountryScopeValidator.validatePort(departurePort, sourceVersion.getTrip(), "Cảng khởi hành");
            com.casms.trip.service.CountryScopeValidator.validatePort(returnPort, sourceVersion.getTrip(), "Cảng về");
        }

        Cruise cruise = new Cruise();
        cruise.setCruiseCode(cruiseCode);
        cruise.setCruiseName(request.getCruiseName());
        cruise.setShip(ship);
        cruise.setCruiseType(cruiseType);
        cruise.setDepartureDatetime(request.getDepartureDatetime());
        cruise.setReturnDatetime(request.getReturnDatetime());
        cruise.setDeparturePort(departurePort);
        cruise.setReturnPort(returnPort);
        cruise.setStatus(CruiseStatus.DRAFT);
        if (sourceVersion != null) {
            cruise.setSourceVersion(sourceVersion);
            cruise.setSourceTrip(sourceVersion.getTrip());
            log.info("Cruise {} linked to Trip {} version {}",
                    cruiseCode,
                    sourceVersion.getTrip().getTripCode(),
                    sourceVersion.getVersionNumber());
        }

        cruise = cruiseRepository.save(cruise);
        if (sourceVersion != null) {
            scheduleMaterializationService.materializeSchedule(cruise, sourceVersion);
        }
        return mapToResponse(cruise);
    }

    @Transactional(readOnly = true)
    public Page<CruiseResponse> list(CruiseStatus status, UUID shipId, CruiseCruiseType cruiseType,
                                      UUID sourceTripId, UUID sourceVersionId, Pageable pageable) {
        org.springframework.data.jpa.domain.Specification<Cruise> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (shipId != null) {
                predicates.add(cb.equal(root.get("ship").get("shipId"), shipId));
            }
            if (cruiseType != null) {
                predicates.add(cb.equal(root.get("cruiseType"), cruiseType));
            }
            // GAP-3 / C15: filter by sourceTrip and/or sourceVersion
            if (sourceTripId != null) {
                predicates.add(cb.equal(root.get("sourceTrip").get("tripId"), sourceTripId));
            }
            if (sourceVersionId != null) {
                predicates.add(cb.equal(root.get("sourceVersion").get("versionId"), sourceVersionId));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return cruiseRepository.findAll(spec, pageable).map(this::mapToResponse);
    }

    // Overload for backward compat (no sourceTrip/Version filter)
    @Transactional(readOnly = true)
    public Page<CruiseResponse> list(CruiseStatus status, UUID shipId, CruiseCruiseType cruiseType, Pageable pageable) {
        return list(status, shipId, cruiseType, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public CruiseResponse detail(UUID cruiseId) {
        Cruise cruise = getCruiseById(cruiseId);
        return mapToResponse(cruise);
    }

    @Transactional
    public CruiseResponse updateDraft(UUID cruiseId, CruiseUpdateDraftRequest request) {
        // GAP-5 / D09: pessimistic lock to protect against concurrent edits
        Cruise cruise = cruiseRepository.findByIdForUpdate(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));

        if (cruise.getStatus() != CruiseStatus.DRAFT) {
            throw new BusinessException("CRUISE_NOT_DRAFT", "Only draft cruises can be modified directly");
        }

        if (request.getExpectedRevision() != null && !request.getExpectedRevision().equals(cruise.getRevision())) {
            throw new BusinessException(org.springframework.http.HttpStatus.CONFLICT, "STALE_REVISION",
                    "Revision của Cruise không khớp (expected: " + request.getExpectedRevision() + ", current: " + cruise.getRevision() + ")");
        }

        Instant effectiveDeparture = request.getDepartureDatetime() != null ? request.getDepartureDatetime() : cruise.getDepartureDatetime();
        Instant effectiveReturn = request.getReturnDatetime() != null ? request.getReturnDatetime() : cruise.getReturnDatetime();

        if (effectiveDeparture == null || effectiveReturn == null || !effectiveReturn.isAfter(effectiveDeparture)) {
            throw new BusinessException("INVALID_SCHEDULE", "Departure and return datetimes are required; return must be after departure");
        }

        if (cruise.getShip() != null && (request.getDepartureDatetime() != null || request.getReturnDatetime() != null)) {
            if (cruiseRepository.existsOverlappingCruisesExcluding(cruise.getShip().getShipId(), cruise.getCruiseId(), effectiveDeparture, effectiveReturn)) {
                throw new BusinessException("SHIP_SCHEDULE_CONFLICT", "Assigned ship has an overlapping active cruise in the updated schedule");
            }
        }

        if (request.getCruiseName() != null) cruise.setCruiseName(request.getCruiseName());
        if (request.getCruiseType() != null) cruise.setCruiseType(request.getCruiseType());
        cruise.setDepartureDatetime(effectiveDeparture);
        cruise.setReturnDatetime(effectiveReturn);

        com.casms.trip.entity.Trip sourceTrip = cruise.getSourceTrip();

        if (request.getDeparturePortId() != null) {
            Port departurePort = portRepository.findById(request.getDeparturePortId())
                    .orElseThrow(() -> new ResourceNotFoundException("Departure Port", request.getDeparturePortId()));
            if (departurePort.getStatus() != PortStatus.ACTIVE) {
                throw new BusinessException("PORT_NOT_ACTIVE", "Departure port must be in ACTIVE status");
            }
            if (sourceTrip != null) {
                com.casms.trip.service.CountryScopeValidator.validatePort(departurePort, sourceTrip, "Cảng khởi hành");
            }
            cruise.setDeparturePort(departurePort);
        }

        if (request.getReturnPortId() != null) {
            Port returnPort = portRepository.findById(request.getReturnPortId())
                    .orElseThrow(() -> new ResourceNotFoundException("Return Port", request.getReturnPortId()));
            if (returnPort.getStatus() != PortStatus.ACTIVE) {
                throw new BusinessException("PORT_NOT_ACTIVE", "Return port must be in ACTIVE status");
            }
            if (sourceTrip != null) {
                com.casms.trip.service.CountryScopeValidator.validatePort(returnPort, sourceTrip, "Cảng về");
            }
            cruise.setReturnPort(returnPort);
        }

        cruise = cruiseRepository.save(cruise);
        cruiseRepository.flush();
        return mapToResponse(cruise);
    }

    @Transactional
    public void deleteDraft(UUID cruiseId) {
        Cruise cruise = getCruiseById(cruiseId);

        if (cruise.getStatus() != CruiseStatus.DRAFT) {
            throw new BusinessException("CRUISE_NOT_DRAFT", "Only draft cruises can be deleted");
        }

        if (bookingRepository.existsByCruiseCruiseId(cruiseId)) {
            throw new BusinessException("CRUISE_HAS_BOOKINGS", "Cannot delete cruise because bookings already exist");
        }

        // 1. Delete ShoreExcursions & OnboardActivities
        if (shoreExcursionRepository != null) {
            shoreExcursionRepository.deleteByCruiseId(cruiseId);
        }
        if (onboardActivityRepository != null) {
            onboardActivityRepository.deleteByCruise_CruiseId(cruiseId);
        }

        // 2. Delete CruiseStops & Itinerary
        itineraryRepository.findByCruiseCruiseId(cruiseId).ifPresent(itinerary -> {
            cruiseStopRepository.deleteByItineraryItineraryId(itinerary.getItineraryId());
            cruiseStopRepository.flush();
            itineraryRepository.delete(itinerary);
            itineraryRepository.flush();
        });

        // 3. Delete CruiseTourPackages, CruiseStaff, CruiseRegulations, CruiseImpactTasks
        if (cruiseTourPackageRepository != null) {
            cruiseTourPackageRepository.deleteByCruise_CruiseId(cruiseId);
        }
        if (cruiseStaffRepository != null) {
            cruiseStaffRepository.deleteByCruise_CruiseId(cruiseId);
        }
        if (cruiseRegulationRepository != null) {
            cruiseRegulationRepository.deleteByCruise_CruiseId(cruiseId);
        }
        if (cruiseImpactTaskRepository != null) {
            cruiseImpactTaskRepository.deleteByCruiseCruiseId(cruiseId);
        }

        cruiseRepository.delete(cruise);
        log.info("Deleted draft cruise: {}", cruiseId);
    }

    @Transactional
    public CruiseResponse assignShip(UUID cruiseId, UUID shipId) {
        Cruise cruise = cruiseRepository.findByIdForUpdate(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));

        if (cruise.getStatus() != CruiseStatus.DRAFT && cruise.getStatus() != CruiseStatus.PUBLISHED) {
            throw new BusinessException("INVALID_CRUISE_STATUS", "Cannot assign ship when cruise status is " + cruise.getStatus());
        }

        if (cruise.getShip() != null) {
            if (cruise.getShip().getShipId().equals(shipId)) {
                return mapToResponse(cruise);
            }
            throw new BusinessException("SHIP_ALREADY_ASSIGNED", "Cruise already has an assigned ship. Use change-assigned-ship instead.");
        }

        Ship ship = shipRepository.findByIdForUpdate(shipId)
                .orElseThrow(() -> new ResourceNotFoundException("Ship", shipId));

        if (ship.getStatus() != ShipStatus.ACTIVE) {
            throw new BusinessException("SHIP_NOT_ACTIVE", "Cannot assign ship because ship status is " + ship.getStatus());
        }

        long decks = deckRepository.countByShipShipId(shipId);
        long cabins = cabinRepository.countByShipShipId(shipId);
        if (decks == 0 || cabins == 0) {
            throw new BusinessException("SHIP_CONFIGURATION_INVALID",
                    "Ship is not fully configured with decks (found: " + decks + ") and cabins (found: " + cabins + ")");
        }

        if (cruiseRepository.existsOverlappingCruisesExcluding(shipId, cruiseId, cruise.getDepartureDatetime(), cruise.getReturnDatetime())) {
            throw new BusinessException("SHIP_SCHEDULE_CONFLICT", "Ship has an overlapping active cruise during this period");
        }

        cruise.setShip(ship);
        cruise = cruiseRepository.save(cruise);
        cruiseRepository.flush();
        return mapToResponse(cruise);
    }

    @Transactional
    public CruiseResponse changeAssignedShip(UUID cruiseId, UUID newShipId) {
        Cruise cruise = cruiseRepository.findByIdForUpdate(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));

        if (cruise.getStatus() != CruiseStatus.DRAFT && cruise.getStatus() != CruiseStatus.PUBLISHED) {
            throw new BusinessException("INVALID_CRUISE_STATUS", "Cannot change assigned ship when cruise status is " + cruise.getStatus());
        }

        if (cruise.getShip() != null && cruise.getShip().getShipId().equals(newShipId)) {
            return mapToResponse(cruise);
        }

        Ship newShip = shipRepository.findByIdForUpdate(newShipId)
                .orElseThrow(() -> new ResourceNotFoundException("Ship", newShipId));

        if (newShip.getStatus() != ShipStatus.ACTIVE) {
            throw new BusinessException("SHIP_NOT_ACTIVE", "New ship must be in ACTIVE status");
        }

        long decks = deckRepository.countByShipShipId(newShipId);
        long cabins = cabinRepository.countByShipShipId(newShipId);
        if (decks == 0 || cabins == 0) {
            throw new BusinessException("SHIP_CONFIGURATION_INVALID",
                    "New ship is not fully configured with decks (found: " + decks + ") and cabins (found: " + cabins + ")");
        }

        if (cruiseRepository.existsOverlappingCruisesExcluding(newShipId, cruiseId, cruise.getDepartureDatetime(), cruise.getReturnDatetime())) {
            throw new BusinessException("SHIP_SCHEDULE_CONFLICT", "New ship has an overlapping active cruise during this period");
        }

        long bookedPassengers = bookingPassengerRepository.countActivePassengersByCruiseId(cruiseId);
        if (newShip.getTotalCapacity() < bookedPassengers) {
            throw new BusinessException("INSUFFICIENT_SHIP_CAPACITY",
                    "New ship capacity (" + newShip.getTotalCapacity() + ") is less than current booked passengers (" + bookedPassengers + ")");
        }

        cruise.setShip(newShip);
        cruise = cruiseRepository.save(cruise);
        cruiseRepository.flush();
        return mapToResponse(cruise);
    }

    @Transactional
    public CruiseResponse unassignShip(UUID cruiseId) {
        Cruise cruise = cruiseRepository.findByIdForUpdate(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));

        if (cruise.getStatus() != CruiseStatus.DRAFT) {
            throw new BusinessException("CRUISE_NOT_DRAFT", "Cannot unassign ship unless cruise is in DRAFT status");
        }

        if (bookingRepository.existsByCruiseCruiseId(cruiseId)) {
            throw new BusinessException("CRUISE_HAS_BOOKINGS", "Cannot unassign ship because bookings already exist");
        }

        cruise.setShip(null);
        cruise = cruiseRepository.save(cruise);
        cruiseRepository.flush();
        return mapToResponse(cruise);
    }

    @Transactional(readOnly = true)
    public ShipCompatibilityResponse checkShipCompatibility(UUID cruiseId, UUID shipId) {
        Cruise cruise = getCruiseById(cruiseId);
        Ship ship = shipRepository.findById(shipId)
                .orElseThrow(() -> new ResourceNotFoundException("Ship", shipId));

        List<String> issues = new ArrayList<>();
        boolean active = ship.getStatus() == ShipStatus.ACTIVE;
        if (!active) {
            issues.add("Tàu không ở trạng thái ACTIVE (hiện tại: " + ship.getStatus() + ")");
        }

        long decks = deckRepository.countByShipShipId(shipId);
        long cabins = cabinRepository.countByShipShipId(shipId);
        boolean configured = decks > 0 && cabins > 0;
        if (!configured) {
            issues.add("Tàu chưa được cấu hình đầy đủ tầng (decks=" + decks + ") hoặc phòng (cabins=" + cabins + ")");
        }

        boolean scheduleAvailable = !cruiseRepository.existsOverlappingCruisesExcluding(shipId, cruiseId, cruise.getDepartureDatetime(), cruise.getReturnDatetime());
        if (!scheduleAvailable) {
            issues.add("Tàu bị trùng lịch với chuyến đi khác trong khoảng thời gian này");
        }

        long bookedPassengers = bookingPassengerRepository.countActivePassengersByCruiseId(cruiseId);
        boolean capacitySufficient = ship.getTotalCapacity() >= bookedPassengers;
        if (!capacitySufficient) {
            issues.add("Sức chứa của tàu (" + ship.getTotalCapacity() + ") không đủ cho số khách đã đặt (" + bookedPassengers + ")");
        }

        boolean compatible = issues.isEmpty();

        return ShipCompatibilityResponse.builder()
                .cruiseId(cruiseId)
                .shipId(shipId)
                .compatible(compatible)
                .shipActive(active)
                .scheduleAvailable(scheduleAvailable)
                .capacitySufficient(capacitySufficient)
                .shipConfigured(configured)
                .decksCount(decks)
                .cabinsCount(cabins)
                .totalCapacity(ship.getTotalCapacity())
                .bookedPassengersCount(bookedPassengers)
                .issues(issues)
                .build();
    }

    @Transactional(readOnly = true)
    public ShipChangeImpactResponse previewShipChangeImpact(UUID cruiseId, UUID newShipId) {
        Cruise cruise = getCruiseById(cruiseId);
        Ship currentShip = cruise.getShip();
        Ship newShip = shipRepository.findById(newShipId)
                .orElseThrow(() -> new ResourceNotFoundException("Ship", newShipId));

        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (newShip.getStatus() != ShipStatus.ACTIVE) {
            blockers.add("Tàu mới không ở trạng thái ACTIVE (hiện tại: " + newShip.getStatus() + ")");
        }

        boolean scheduleConflict = cruiseRepository.existsOverlappingCruisesExcluding(newShipId, cruiseId, cruise.getDepartureDatetime(), cruise.getReturnDatetime());
        if (scheduleConflict) {
            blockers.add("Tàu mới bị trùng lịch vận hành với chuyến đi khác");
        }

        long bookedPassengers = bookingPassengerRepository.countActivePassengersByCruiseId(cruiseId);
        boolean capacitySufficient = newShip.getTotalCapacity() >= bookedPassengers;
        if (!capacitySufficient) {
            blockers.add("Sức chứa của tàu mới (" + newShip.getTotalCapacity() + ") nhỏ hơn số khách đã đặt (" + bookedPassengers + ")");
        }

        long decks = deckRepository.countByShipShipId(newShipId);
        long cabins = cabinRepository.countByShipShipId(newShipId);
        if (decks == 0 || cabins == 0) {
            blockers.add("Tàu mới chưa được cấu hình đầy đủ tầng hoặc phòng");
        }

        long affectedCabins = bookedPassengers;
        if (currentShip != null && !currentShip.getShipId().equals(newShipId) && affectedCabins > 0) {
            warnings.add("Cần phân bổ lại phòng cho " + affectedCabins + " khách đã đặt sau khi đổi tàu");
        }

        long affectedActivities = 0;
        if (currentShip != null && !currentShip.getShipId().equals(newShipId)) {
            affectedActivities = onboardActivityRepository.countByCruise_CruiseIdAndDeckIsNotNull(cruiseId);
            if (affectedActivities > 0) {
                warnings.add("Có " + affectedActivities + " hoạt động trên tàu cần kiểm tra lại vị trí tầng/phòng trên tàu mới");
            }
        }

        boolean canProceed = blockers.isEmpty();

        return ShipChangeImpactResponse.builder()
                .cruiseId(cruiseId)
                .currentShipId(currentShip != null ? currentShip.getShipId() : null)
                .newShipId(newShipId)
                .bookedPassengersCount(bookedPassengers)
                .capacitySufficient(capacitySufficient)
                .affectedCabinAssignmentsCount(affectedCabins)
                .affectedOnboardActivitiesCount(affectedActivities)
                .blockers(blockers)
                .warnings(warnings)
                .canProceed(canProceed)
                .build();
    }

    @Transactional(readOnly = true)
    public boolean validateShipAvailability(UUID cruiseId, UUID shipId) {
        Cruise cruise = getCruiseById(cruiseId);
        Ship ship = shipRepository.findById(shipId)
                .orElseThrow(() -> new ResourceNotFoundException("Ship", shipId));

        if (ship.getStatus() != ShipStatus.ACTIVE) {
            return false;
        }

        return !cruiseRepository.existsOverlappingCruisesExcluding(shipId, cruiseId, cruise.getDepartureDatetime(), cruise.getReturnDatetime());
    }

    @Transactional(readOnly = true)
    public boolean validateSchedule(UUID cruiseId) {
        Cruise cruise = getCruiseById(cruiseId);
        return cruise.getReturnDatetime().isAfter(cruise.getDepartureDatetime());
    }

    @Transactional(readOnly = true)
    public CruiseCapacityValidationResponse validateCapacity(UUID cruiseId) {
        Cruise cruise = getCruiseById(cruiseId);

        if (cruise.getShip() == null) {
            throw new BusinessException("NO_SHIP_ASSIGNED", "No ship is assigned to this cruise");
        }

        Ship ship = cruise.getShip();
        long bookedPassengers = bookingPassengerRepository.countActivePassengersByCruiseId(cruiseId);
        long available = Math.max(0, ship.getTotalCapacity() - bookedPassengers);
        boolean sufficient = ship.getTotalCapacity() >= bookedPassengers;

        CruiseCapacityValidationResponse res = new CruiseCapacityValidationResponse();
        res.setCruiseId(cruise.getCruiseId());
        res.setShipId(ship.getShipId());
        res.setShipName(ship.getShipName());
        res.setShipTotalCapacity(ship.getTotalCapacity());
        res.setBookedPassengersCount(bookedPassengers);
        res.setAvailableCapacity(available);
        res.setCapacitySufficient(sufficient);

        return res;
    }

    @Transactional(readOnly = true)
    public CruiseReadinessResponse validateOperationalReadiness(UUID cruiseId) {
        Cruise cruise = getCruiseById(cruiseId);
        List<String> issues = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        boolean shipAssigned = cruise.getShip() != null;
        boolean shipActive = false;
        boolean shipConfigValid = false;

        if (!shipAssigned) {
            issues.add("Chưa gán tàu cho chuyến đi");
            blockers.add("NO_SHIP_ASSIGNED");
        } else {
            Ship ship = cruise.getShip();
            shipActive = ship.getStatus() == ShipStatus.ACTIVE;
            if (!shipActive) {
                issues.add("Tàu được gán đang không ở trạng thái ACTIVE (hiện tại: " + ship.getStatus() + ")");
                blockers.add("SHIP_NOT_ACTIVE");
            }

            long decks = deckRepository.countByShipShipId(ship.getShipId());
            long cabins = cabinRepository.countByShipShipId(ship.getShipId());
            shipConfigValid = decks > 0 && cabins > 0;
            if (!shipConfigValid) {
                issues.add("Tàu chưa được cấu hình đầy đủ tầng (decks=" + decks + ") hoặc phòng (cabins=" + cabins + ")");
                blockers.add("SHIP_NOT_CONFIGURED");
            }
        }

        boolean scheduleValid = cruise.getReturnDatetime().isAfter(cruise.getDepartureDatetime());
        if (!scheduleValid) {
            issues.add("Lịch trình không hợp lệ: Thời điểm kết thúc phải sau thời điểm khởi hành");
            blockers.add("INVALID_SCHEDULE");
        }

        boolean departurePortActive = cruise.getDeparturePort() != null && cruise.getDeparturePort().getStatus() == PortStatus.ACTIVE;
        boolean returnPortActive = cruise.getReturnPort() != null && cruise.getReturnPort().getStatus() == PortStatus.ACTIVE;
        boolean portsActive = departurePortActive && returnPortActive;
        if (!portsActive) {
            issues.add("Cảng đi hoặc cảng về đang không ở trạng thái hoạt động (ACTIVE)");
            blockers.add("PORTS_NOT_ACTIVE");
        }

        Optional<com.casms.ship.entity.Itinerary> itineraryOpt = itineraryRepository.findByCruiseCruiseId(cruiseId);
        boolean itineraryConfigured = itineraryOpt.isPresent();
        boolean itineraryApproved = itineraryConfigured && (
                itineraryOpt.get().getStatus() == com.casms.ship.enums.ItineraryStatus.APPROVED ||
                itineraryOpt.get().getStatus() == com.casms.ship.enums.ItineraryStatus.OPEN_FOR_SALE ||
                itineraryOpt.get().getStatus() == com.casms.ship.enums.ItineraryStatus.ACTIVE ||
                itineraryOpt.get().getStatus() == com.casms.ship.enums.ItineraryStatus.SUSPENDED
        );
        if (!itineraryConfigured) {
            issues.add("Chuyến đi chưa được liên kết hải trình (Itinerary)");
            blockers.add("ITINERARY_MISSING");
        } else if (!itineraryApproved) {
            issues.add("Hải trình của chuyến đi chưa được phê duyệt hoặc mở bán (Trạng thái hiện tại: " + itineraryOpt.get().getStatus() + ")");
            blockers.add("ITINERARY_NOT_APPROVED");
        }

        // BE-06: Staff readiness verification
        List<com.casms.ship.entity.CruiseStaff> staffList = cruiseStaffRepository != null
                ? cruiseStaffRepository.findByCruise_CruiseId(cruiseId)
                : List.of();
        List<com.casms.ship.entity.CruiseStaff> activeStaff = staffList.stream()
                .filter(s -> "ACTIVE".equalsIgnoreCase(s.getStatus()))
                .toList();

        boolean staffAssigned = !activeStaff.isEmpty();
        boolean captainAssigned = activeStaff.stream()
                .anyMatch(s -> "CAPTAIN".equalsIgnoreCase(s.getRoleInCruise()));

        List<String> staffConflicts = new ArrayList<>();
        if (!captainAssigned) {
            issues.add("Chuyến đi chưa được phân công Thuyền trưởng (CAPTAIN)");
            blockers.add("MISSING_CAPTAIN");
        }

        for (com.casms.ship.entity.CruiseStaff s : activeStaff) {
            List<com.casms.ship.entity.CruiseStaff> conflicts = cruiseStaffRepository.findConflictingAssignments(
                    s.getUser().getUserId(),
                    cruise.getDepartureDatetime(),
                    cruise.getReturnDatetime(),
                    cruiseId
            );
            if (!conflicts.isEmpty()) {
                for (com.casms.ship.entity.CruiseStaff cs : conflicts) {
                    String msg = String.format("Nhân sự %s (%s) bị trùng lịch với chuyến %s (%s - %s)",
                            s.getUser().getUsername(), s.getRoleInCruise(),
                            cs.getCruise().getCruiseCode(),
                            cs.getCruise().getDepartureDatetime(), cs.getCruise().getReturnDatetime());
                    staffConflicts.add(msg);
                    issues.add(msg);
                    blockers.add("STAFF_SCHEDULE_CONFLICT");
                }
            }
        }

        // BE-06: Regulations readiness verification
        boolean regulationsCompliant = true;
        List<String> regulationConflicts = new ArrayList<>();
        if (cruiseRegulationService != null) {
            try {
                var snapshot = cruiseRegulationService.getEffectiveSnapshot(cruiseId, Instant.now());
                if (snapshot != null) {
                    boolean directEmpty = snapshot.getDirect() == null || snapshot.getDirect().isEmpty();
                    boolean inheritedEmpty = snapshot.getInherited() == null || snapshot.getInherited().isEmpty();
                    if (directEmpty && inheritedEmpty) {
                        warnings.add("Chưa có quy định an toàn/vận hành nào được gán hoặc kế thừa cho chuyến đi");
                    }
                }
            } catch (Exception ex) {
                log.warn("Failed to check regulation snapshot for cruise {}: {}", cruiseId, ex.getMessage());
            }
        }

        boolean ready = issues.isEmpty();

        CruiseReadinessResponse response = new CruiseReadinessResponse();
        response.setCruiseId(cruiseId);
        response.setReady(ready);
        response.setShipAssigned(shipAssigned);
        response.setShipActive(shipActive);
        response.setShipConfigurationValid(shipConfigValid);
        response.setScheduleValid(scheduleValid);
        response.setPortsActive(portsActive);
        response.setItineraryConfigured(itineraryApproved);
        response.setStaffAssigned(staffAssigned);
        response.setCaptainAssigned(captainAssigned);
        response.setStaffConflicts(staffConflicts);
        response.setRegulationsCompliant(regulationsCompliant);
        response.setRegulationConflicts(regulationConflicts);
        response.setBlockers(blockers);
        response.setWarnings(warnings);
        response.setIssues(issues);
        // GAP-4 / BE-08: fingerprint - evaluatedAt + cruiseRevision for staleness detection
        response.setEvaluatedAt(Instant.now());
        response.setCruiseRevision(cruise.getRevision());

        return response;
    }

    @Transactional
    public CruiseResponse publish(UUID cruiseId) {
        Cruise cruise = getCruiseById(cruiseId);

        if (cruise.getStatus() != CruiseStatus.DRAFT) {
            throw new BusinessException("CRUISE_NOT_DRAFT", "Only draft cruises can be published");
        }

        CruiseReadinessResponse readiness = validateOperationalReadiness(cruiseId);
        if (!readiness.isReady()) {
            throw new BusinessException("CRUISE_NOT_READY", "Cruise is not operationally ready to publish: " + String.join("; ", readiness.getIssues()));
        }

        cruise.setStatus(CruiseStatus.PUBLISHED);
        cruise = cruiseRepository.save(cruise);
        log.info("Published cruise: {}", cruiseId);
        return mapToResponse(cruise);
    }

    @Transactional
    public CruiseResponse start(UUID cruiseId) {
        Cruise cruise = getCruiseById(cruiseId);

        if (cruise.getStatus() != CruiseStatus.PUBLISHED) {
            throw new BusinessException("CRUISE_NOT_PUBLISHED", "Only published cruises can be started");
        }

        if (cruise.getShip() == null || cruise.getShip().getStatus() != ShipStatus.ACTIVE) {
            throw new BusinessException("SHIP_NOT_ACTIVE", "Cannot start cruise: assigned ship is missing or not active");
        }

        // Rule 1: Không bấm start quá sớm trước giờ khởi hành (tối đa 2 giờ trước departureDatetime)
        if (cruise.getDepartureDatetime() != null && Instant.now().isBefore(cruise.getDepartureDatetime().minus(java.time.Duration.ofHours(2)))) {
            throw new BusinessException("CRUISE_START_TOO_EARLY",
                    "Cannot start cruise too early before scheduled departure (minimum 2 hours prior to " + cruise.getDepartureDatetime() + ")");
        }

        cruise.setStatus(CruiseStatus.IN_PROGRESS);
        cruise = cruiseRepository.save(cruise);

        // Đồng bộ hải trình sang ACTIVE nếu đang APPROVED hoặc OPEN_FOR_SALE
        itineraryRepository.findByCruiseCruiseId(cruiseId).ifPresent(it -> {
            if (it.getStatus() == com.casms.ship.enums.ItineraryStatus.APPROVED || it.getStatus() == com.casms.ship.enums.ItineraryStatus.OPEN_FOR_SALE) {
                it.setStatus(com.casms.ship.enums.ItineraryStatus.ACTIVE);
                itineraryRepository.save(it);
            }
        });

        log.info("Started cruise: {}", cruiseId);
        return mapToResponse(cruise);
    }

    @Transactional
    public CruiseResponse complete(UUID cruiseId) {
        Cruise cruise = getCruiseById(cruiseId);

        if (cruise.getStatus() != CruiseStatus.IN_PROGRESS) {
            throw new BusinessException("CRUISE_NOT_IN_PROGRESS", "Only in-progress cruises can be completed");
        }

        // Rule: Không complete trước khi kết thúc hành trình returnDatetime
        if (cruise.getReturnDatetime() != null && Instant.now().isBefore(cruise.getReturnDatetime())) {
            throw new BusinessException("CRUISE_RETURN_NOT_REACHED",
                    "Cannot complete cruise before scheduled return datetime: " + cruise.getReturnDatetime());
        }

        cruise.setStatus(CruiseStatus.COMPLETED);
        cruise = cruiseRepository.save(cruise);

        // Đồng bộ hải trình sang COMPLETED
        itineraryRepository.findByCruiseCruiseId(cruiseId).ifPresent(it -> {
            it.setStatus(com.casms.ship.enums.ItineraryStatus.COMPLETED);
            itineraryRepository.save(it);
        });

        // Giải phóng phòng đã gán
        List<com.casms.booking.entity.BookingPassenger> passengers = bookingPassengerRepository.findActivePassengersByCruiseId(cruiseId);
        for (com.casms.booking.entity.BookingPassenger bp : passengers) {
            cabinAssignmentRepository.findByBookingPassengerBookingPassengerIdAndValidToIsNull(bp.getBookingPassengerId())
                    .ifPresent(assignment -> {
                        assignment.setValidTo(Instant.now());
                        cabinAssignmentRepository.save(assignment);
                    });
        }

        log.info("Completed cruise: {}", cruiseId);
        return mapToResponse(cruise);
    }

    @Transactional
    public CruiseResponse cancel(UUID cruiseId, String reason) {
        Cruise cruise = getCruiseById(cruiseId);

        if (cruise.getStatus() == CruiseStatus.COMPLETED) {
            throw new BusinessException("CANNOT_CANCEL_COMPLETED", "Completed cruises cannot be cancelled");
        }

        if (cruise.getStatus() == CruiseStatus.CANCELLED) {
            return mapToResponse(cruise);
        }

        if (reason == null || reason.isBlank()) {
            throw new BusinessException("REASON_REQUIRED", "Cancellation reason is required");
        }

        cruise.setStatus(CruiseStatus.CANCELLED);
        cruise = cruiseRepository.save(cruise);
        log.info("Cancelled cruise: {} with reason: {}", cruiseId, reason);
        return mapToResponse(cruise);
    }

    private Cruise getCruiseById(UUID cruiseId) {
        return cruiseRepository.findById(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));
    }

    private CruiseResponse mapToResponse(Cruise cruise) {
        CruiseResponse res = new CruiseResponse();
        res.setCruiseId(cruise.getCruiseId());
        res.setCruiseCode(cruise.getCruiseCode());
        res.setCruiseName(cruise.getCruiseName());
        if (cruise.getShip() != null) {
            res.setShipId(cruise.getShip().getShipId());
            res.setShipName(cruise.getShip().getShipName());
            res.setShipCode(cruise.getShip().getShipCode());
        }
        res.setCruiseType(cruise.getCruiseType());
        res.setDepartureDatetime(cruise.getDepartureDatetime());
        res.setReturnDatetime(cruise.getReturnDatetime());
        if (cruise.getDeparturePort() != null) {
            res.setDeparturePortId(cruise.getDeparturePort().getPortId());
            res.setDeparturePortName(cruise.getDeparturePort().getPortName());
        }
        if (cruise.getReturnPort() != null) {
            res.setReturnPortId(cruise.getReturnPort().getPortId());
            res.setReturnPortName(cruise.getReturnPort().getPortName());
        }
        res.setStatus(cruise.getStatus());
        // D01 / C15: Source trip/version traceability
        if (cruise.getSourceTrip() != null) {
            res.setSourceTripId(cruise.getSourceTrip().getTripId());
            res.setSourceTripCode(cruise.getSourceTrip().getTripCode());
        }
        if (cruise.getSourceVersion() != null) {
            res.setSourceVersionId(cruise.getSourceVersion().getVersionId());
            res.setSourceVersionNumber(cruise.getSourceVersion().getVersionNumber());
        }
        res.setRevision(cruise.getRevision());
        res.setCreatedAt(cruise.getCreatedAt());
        res.setUpdatedAt(cruise.getUpdatedAt());
        return res;
    }

    @Transactional(readOnly = true)
    public com.casms.ship.dto.response.CruiseManifestResponse getManifest(UUID cruiseId) {
        Cruise cruise = cruiseRepository.findById(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise not found with id: " + cruiseId));

        Ship ship = cruise.getShip();
        String shipName = ship != null ? ship.getShipName() : "UNASSIGNED";
        Integer capacity = ship != null ? ship.getTotalCapacity() : 0;
        long bookedCount = bookingPassengerRepository.countActivePassengersByCruiseId(cruiseId);

        List<com.casms.booking.entity.BookingPassenger> activePassengers = bookingPassengerRepository.findActivePassengersByCruiseId(cruiseId);
        List<com.casms.booking.entity.CabinAssignment> activeAssignments = cabinAssignmentRepository.findActiveAssignmentsByCruiseId(cruiseId);
        Map<UUID, com.casms.booking.entity.CabinAssignment> assignmentByPassenger = activeAssignments.stream()
                .filter(a -> a.getBookingPassenger() != null)
                .collect(Collectors.toMap(a -> a.getBookingPassenger().getBookingPassengerId(), a -> a, (first, second) -> first));

        List<com.casms.checkin.entity.CruiseCheckin> checkins = checkinRepository.findByCruiseId(cruiseId);
        Map<UUID, com.casms.checkin.entity.CruiseCheckin> checkinByPassenger = checkins.stream()
                .filter(c -> c.getBookingPassenger() != null)
                .collect(Collectors.toMap(c -> c.getBookingPassenger().getBookingPassengerId(), c -> c, (first, second) -> first));

        List<com.casms.ship.dto.response.CruiseManifestResponse.PassengerManifestItem> passengerItems = activePassengers.stream()
                .map(bp -> {
                    var pass = bp.getPassenger();
                    String fullName = pass != null ? pass.getFullName() : "N/A";
                    String idNum = pass != null ? pass.getDocumentNumberProtected() : "N/A";
                    String bookingCode = bp.getBooking() != null ? bp.getBooking().getBookingReference() : "N/A";
                    String bookingStatus = bp.getBooking() != null ? bp.getBooking().getBookingStatus().name() : "Draft";

                    var assignment = assignmentByPassenger.get(bp.getBookingPassengerId());
                    String cabinNumber = (assignment != null && assignment.getCabin() != null)
                            ? assignment.getCabin().getCabinNumber() : "UNASSIGNED";
                    String deckName = (assignment != null && assignment.getCabin() != null && assignment.getCabin().getDeck() != null)
                            ? assignment.getCabin().getDeck().getDeckName() : "N/A";

                    var checkin = checkinByPassenger.get(bp.getBookingPassengerId());
                    String checkinStatus = (checkin != null && checkin.getCheckinStatus() != null)
                            ? checkin.getCheckinStatus().name() : "NOT_CHECKED_IN";

                    return com.casms.ship.dto.response.CruiseManifestResponse.PassengerManifestItem.builder()
                            .bookingPassengerId(bp.getBookingPassengerId())
                            .bookingCode(bookingCode)
                            .fullName(fullName)
                            .identificationNumber(idNum)
                            .cabinNumber(cabinNumber)
                            .deckName(deckName)
                            .bookingStatus(bookingStatus)
                            .checkinStatus(checkinStatus)
                            .build();
                })
                .toList();

        return com.casms.ship.dto.response.CruiseManifestResponse.builder()
                .cruiseId(cruise.getCruiseId())
                .cruiseCode(cruise.getCruiseCode())
                .cruiseName(cruise.getCruiseName())
                .shipName(shipName)
                .totalCapacity(capacity)
                .bookedPassengers(bookedCount)
                .departureTime(cruise.getDepartureDatetime())
                .returnTime(cruise.getReturnDatetime())
                .originPort(cruise.getDeparturePort() != null ? cruise.getDeparturePort().getPortName() : null)
                .destinationPort(cruise.getReturnPort() != null ? cruise.getReturnPort().getPortName() : null)
                .staffList(new ArrayList<>())
                .passengerList(passengerItems)
                .build();
    }

    public static BigDecimal getBaseCabinFare(CabinType cabinType) {
        if (cabinType == null) return BigDecimal.valueOf(300);
        return switch (cabinType) {
            case INTERIOR -> BigDecimal.valueOf(300);
            case OCEAN_VIEW -> BigDecimal.valueOf(450);
            case BALCONY -> BigDecimal.valueOf(650);
            case SUITE -> BigDecimal.valueOf(1100);
        };
    }

    @Transactional(readOnly = true)
    public CruiseInventorySummaryResponse getInventorySummary(UUID cruiseId) {
        Cruise cruise = getCruiseById(cruiseId);
        Ship ship = cruise.getShip();
        UUID shipId = ship != null ? ship.getShipId() : null;
        String shipName = ship != null ? ship.getShipName() : "UNASSIGNED";
        int physicalCapacity = ship != null ? ship.getTotalCapacity() : 0;

        long totalCabins = shipId != null ? cabinRepository.countByShipShipId(shipId) : 0;
        long operationalCabins = shipId != null ? cabinRepository.countByShipShipIdAndStatus(shipId, CabinStatus.AVAILABLE) : 0;

        long bookedPassengers = bookingPassengerRepository.countActivePassengersByCruiseId(cruiseId);
        long heldPassengers = bookingPassengerRepository.countHeldPassengersByCruiseId(cruiseId);
        long remainingCapacity = Math.max(0, physicalCapacity - bookedPassengers - heldPassengers);
        long activePackages = cruiseTourPackageRepository.countByCruise_CruiseIdAndActiveTrue(cruiseId);

        List<CabinTypeInventoryItem> cabinTypes = new ArrayList<>();
        if (shipId != null) {
            for (CabinType type : CabinType.values()) {
                long totalCabinsForType = cabinRepository.countByShipShipIdAndCabinType(shipId, type);
                long availableCabinsForType = cabinRepository.countByShipShipIdAndCabinTypeAndStatus(shipId, type, CabinStatus.AVAILABLE);
                long totalSlotsForType = cabinRepository.sumCapacityByShipIdAndCabinType(shipId, type);
                long occupiedSlotsForType = cabinAssignmentRepository.countActiveAssignmentsByCabinTypeAndCruise(type, cruiseId);
                long remainingSlotsForType = Math.max(0, totalSlotsForType - occupiedSlotsForType);
                BigDecimal baseFare = getBaseCabinFare(type);

                cabinTypes.add(CabinTypeInventoryItem.builder()
                        .cabinType(type)
                        .totalCabins(totalCabinsForType)
                        .operationalCabins(availableCabinsForType)
                        .totalSlots(totalSlotsForType)
                        .occupiedSlots(occupiedSlotsForType)
                        .remainingSlots(remainingSlotsForType)
                        .baseFare(baseFare)
                        .build());
            }
        }

        return CruiseInventorySummaryResponse.builder()
                .cruiseId(cruiseId)
                .cruiseCode(cruise.getCruiseCode())
                .shipId(shipId)
                .shipName(shipName)
                .physicalCapacity(physicalCapacity)
                .totalCabins(totalCabins)
                .operationalCabins(operationalCabins)
                .bookedPassengersCount(bookedPassengers)
                .heldPassengersCount(heldPassengers)
                .remainingCapacity(remainingCapacity)
                .activePackagesCount(activePackages)
                .cabinTypeInventory(cabinTypes)
                .build();
    }

    @Transactional(readOnly = true)
    public CruiseSaleReadinessResponse validateSaleReadiness(UUID cruiseId) {
        Cruise cruise = getCruiseById(cruiseId);
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> issues = new ArrayList<>();

        // 1. Evaluate operational readiness
        CruiseReadinessResponse opReadiness = validateOperationalReadiness(cruiseId);
        boolean operationalReady = opReadiness.isReady();
        if (!operationalReady) {
            blockers.addAll(opReadiness.getBlockers());
            issues.addAll(opReadiness.getIssues());
        }

        // 2. Check departure time in the future (at least 24 hours)
        boolean departureInFuture = cruise.getDepartureDatetime() != null
                && cruise.getDepartureDatetime().isAfter(Instant.now().plus(24, ChronoUnit.HOURS));
        if (!departureInFuture) {
            if (cruise.getDepartureDatetime() == null || cruise.getDepartureDatetime().isBefore(Instant.now())) {
                blockers.add("CRUISE_ALREADY_DEPARTED");
                issues.add("Thời điểm khởi hành đã qua hoặc không xác định");
            } else {
                warnings.add("Thời điểm khởi hành còn dưới 24 giờ kể từ thời điểm kiểm tra");
            }
        }

        // 3. Check tour packages assigned and active
        long activePackages = cruiseTourPackageRepository.countByCruise_CruiseIdAndActiveTrue(cruiseId);
        boolean packagesConfigured = activePackages > 0;
        if (!packagesConfigured) {
            blockers.add("NO_ACTIVE_PACKAGES");
            issues.add("Chưa có gói tour/sản phẩm dịch vụ nào được gán và kích hoạt cho chuyến đi");
        }

        // 4. Check inventory
        int physicalCapacity = cruise.getShip() != null ? cruise.getShip().getTotalCapacity() : 0;
        long booked = bookingPassengerRepository.countActivePassengersByCruiseId(cruiseId);
        long held = bookingPassengerRepository.countHeldPassengersByCruiseId(cruiseId);
        long remaining = Math.max(0, physicalCapacity - booked - held);
        boolean inventoryAvailable = remaining > 0 && physicalCapacity > 0;
        if (!inventoryAvailable) {
            blockers.add("INVENTORY_EXHAUSTED");
            issues.add("Không còn chỗ trống trên tàu để mở bán");
        }

        // 5. Pricing configuration
        boolean pricingConfigured = true;

        boolean ready = blockers.isEmpty();

        return CruiseSaleReadinessResponse.builder()
                .cruiseId(cruiseId)
                .ready(ready)
                .operationalReady(operationalReady)
                .packagesConfigured(packagesConfigured)
                .activePackagesCount(activePackages)
                .inventoryAvailable(inventoryAvailable)
                .remainingCapacity(remaining)
                .physicalCapacity(physicalCapacity)
                .departureInFuture(departureInFuture)
                .pricingConfigured(pricingConfigured)
                .blockers(blockers)
                .warnings(warnings)
                .issues(issues)
                .evaluatedAt(Instant.now())
                .cruiseRevision(cruise.getRevision())
                .build();
    }

    @Transactional
    public CruiseResponse openSale(UUID cruiseId, CruiseOpenSaleRequest request) {
        Cruise cruise = cruiseRepository.findByIdForUpdate(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));

        if (request != null && request.getExpectedRevision() != null && !request.getExpectedRevision().equals(cruise.getRevision())) {
            throw new BusinessException(HttpStatus.CONFLICT, "STALE_REVISION",
                    "Revision của Cruise không khớp (expected: " + request.getExpectedRevision() + ", current: " + cruise.getRevision() + ")");
        }

        // Idempotency: if already PUBLISHED and Itinerary is OPEN_FOR_SALE, return existing
        Optional<Itinerary> itineraryOpt = itineraryRepository.findByCruiseCruiseId(cruiseId);
        if (cruise.getStatus() == CruiseStatus.PUBLISHED
                && itineraryOpt.isPresent() && itineraryOpt.get().getStatus() == ItineraryStatus.OPEN_FOR_SALE) {
            return mapToResponse(cruise);
        }

        // Validation gate: validateSaleReadiness
        CruiseSaleReadinessResponse saleReadiness = validateSaleReadiness(cruiseId);
        if (!saleReadiness.isReady()) {
            throw new BusinessException("CRUISE_NOT_SALE_READY",
                    "Chuyến đi chưa đủ điều kiện mở bán: " + String.join("; ", saleReadiness.getIssues()));
        }

        // Activate specific packages if requested
        if (request != null && request.getPackageIds() != null && !request.getPackageIds().isEmpty()) {
            for (UUID pkgId : request.getPackageIds()) {
                cruiseTourPackageRepository.findByCruise_CruiseIdAndTourPackage_TourPackageId(cruiseId, pkgId)
                        .ifPresent(ctp -> {
                            ctp.setActive(true);
                            cruiseTourPackageRepository.save(ctp);
                        });
            }
        }

        cruise.setStatus(CruiseStatus.PUBLISHED);
        cruise = cruiseRepository.save(cruise);

        // Update Itinerary status to OPEN_FOR_SALE
        if (itineraryOpt.isPresent()) {
            Itinerary itinerary = itineraryOpt.get();
            itinerary.setStatus(ItineraryStatus.OPEN_FOR_SALE);
            itineraryRepository.save(itinerary);
        }

        cruiseRepository.flush();
        log.info("Opened sale for cruise {} (revision {})", cruise.getCruiseCode(), cruise.getRevision());
        return mapToResponse(cruise);
    }

    @Transactional
    public CruiseResponse stopSale(UUID cruiseId, CruiseStopSaleRequest request) {
        Cruise cruise = cruiseRepository.findByIdForUpdate(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));

        if (request != null && request.getExpectedRevision() != null && !request.getExpectedRevision().equals(cruise.getRevision())) {
            throw new BusinessException(HttpStatus.CONFLICT, "STALE_REVISION",
                    "Revision của Cruise không khớp (expected: " + request.getExpectedRevision() + ", current: " + cruise.getRevision() + ")");
        }

        if (request != null && request.getPackageId() != null) {
            // Targeted stop-sale: deactivate only specific package
            CruiseTourPackage ctp = cruiseTourPackageRepository
                    .findByCruise_CruiseIdAndTourPackage_TourPackageId(cruiseId, request.getPackageId())
                    .orElseThrow(() -> new ResourceNotFoundException("CruiseTourPackage", request.getPackageId()));
            ctp.setActive(false);
            cruiseTourPackageRepository.save(ctp);
            log.info("Stopped sale for package {} on cruise {}", request.getPackageId(), cruise.getCruiseCode());
        } else {
            // Cruise-wide stop-sale: set Itinerary status to SUSPENDED without cancelling existing bookings
            itineraryRepository.findByCruiseCruiseId(cruiseId).ifPresent(itinerary -> {
                itinerary.setStatus(ItineraryStatus.SUSPENDED);
                itineraryRepository.save(itinerary);
            });
            log.info("Stopped sale (SUSPENDED) for entire cruise {}. Reason: {}", cruise.getCruiseCode(), request != null ? request.getReason() : "N/A");
        }

        cruise = cruiseRepository.save(cruise);
        cruiseRepository.flush();
        return mapToResponse(cruise);
    }

    @Transactional
    public CruiseResponse resumeSale(UUID cruiseId, CruiseResumeSaleRequest request) {
        Cruise cruise = cruiseRepository.findByIdForUpdate(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));

        if (request != null && request.getExpectedRevision() != null && !request.getExpectedRevision().equals(cruise.getRevision())) {
            throw new BusinessException(HttpStatus.CONFLICT, "STALE_REVISION",
                    "Revision của Cruise không khớp (expected: " + request.getExpectedRevision() + ", current: " + cruise.getRevision() + ")");
        }

        if (request != null && request.getPackageId() != null) {
            // Targeted resume: activate package
            CruiseTourPackage ctp = cruiseTourPackageRepository
                    .findByCruise_CruiseIdAndTourPackage_TourPackageId(cruiseId, request.getPackageId())
                    .orElseThrow(() -> new ResourceNotFoundException("CruiseTourPackage", request.getPackageId()));
            ctp.setActive(true);
            cruiseTourPackageRepository.save(ctp);
            log.info("Resumed sale for package {} on cruise {}", request.getPackageId(), cruise.getCruiseCode());
        } else {
            // Re-validate sale readiness before resuming whole cruise
            CruiseSaleReadinessResponse saleReadiness = validateSaleReadiness(cruiseId);
            if (!saleReadiness.isReady()) {
                throw new BusinessException("CRUISE_NOT_SALE_READY",
                        "Không thể mở bán lại do chưa đủ điều kiện: " + String.join("; ", saleReadiness.getIssues()));
            }

            itineraryRepository.findByCruiseCruiseId(cruiseId).ifPresent(itinerary -> {
                itinerary.setStatus(ItineraryStatus.OPEN_FOR_SALE);
                itineraryRepository.save(itinerary);
            });
            log.info("Resumed sale for entire cruise {}", cruise.getCruiseCode());
        }

        cruise = cruiseRepository.save(cruise);
        cruiseRepository.flush();
        return mapToResponse(cruise);
    }

    @Transactional(readOnly = true)
    public CruiseDownstreamHandoffResponse getDownstreamHandoff(UUID cruiseId) {
        Cruise cruise = getCruiseById(cruiseId);
        Ship ship = cruise.getShip();
        String shipName = ship != null ? ship.getShipName() : "UNASSIGNED";
        Integer capacity = ship != null ? ship.getTotalCapacity() : 0;

        // Differentiated metrics
        long totalBookings = bookingRepository.countByCruiseCruiseId(cruiseId);
        long confirmedBookings = bookingRepository.countByCruiseCruiseIdAndBookingStatus(cruiseId, com.casms.booking.enums.BookingStatus.Confirmed);
        long draftBookings = bookingRepository.countByCruiseCruiseIdAndBookingStatus(cruiseId, com.casms.booking.enums.BookingStatus.Draft);
        long cancelledBookings = bookingRepository.countByCruiseCruiseIdAndBookingStatus(cruiseId, com.casms.booking.enums.BookingStatus.Cancelled);

        long activePassengers = bookingPassengerRepository.countActivePassengersByCruiseId(cruiseId);
        long checkedInPassengers = checkinRepository.countCompletedCheckinsByCruiseId(cruiseId);
        long boardedPassengers = checkinRepository.countBoardedPassengersByCruiseId(cruiseId);

        // Staff & Captain
        boolean captainAssigned = cruiseStaffRepository.existsByCruise_CruiseIdAndRoleInCruiseAndStatus(
                cruiseId, "CAPTAIN", "ACTIVE");
        List<CruiseStaffResponse> staffList = cruiseStaffRepository.findByCruise_CruiseId(cruiseId).stream()
                .map(s -> CruiseStaffResponse.builder()
                        .cruiseStaffId(s.getCruiseStaffId())
                        .cruiseId(cruiseId)
                        .userId(s.getUser().getUserId())
                        .username(s.getUser().getUsername())
                        .roleInCruise(s.getRoleInCruise())
                        .assignedAt(s.getAssignedAt())
                        .status(s.getStatus())
                        .notes(s.getNotes())
                        .build())
                .toList();

        // Passenger manifest
        CruiseManifestResponse manifest = getManifest(cruiseId);

        boolean departureClearanceReady = captainAssigned && ship != null && ship.getStatus() == ShipStatus.ACTIVE
                && (cruise.getStatus() == CruiseStatus.PUBLISHED || cruise.getStatus() == CruiseStatus.IN_PROGRESS);

        return CruiseDownstreamHandoffResponse.builder()
                .cruiseId(cruise.getCruiseId())
                .cruiseCode(cruise.getCruiseCode())
                .cruiseName(cruise.getCruiseName())
                .departureDatetime(cruise.getDepartureDatetime())
                .returnDatetime(cruise.getReturnDatetime())
                .cruiseStatus(cruise.getStatus().name())
                .sourceTripId(cruise.getSourceTrip() != null ? cruise.getSourceTrip().getTripId() : null)
                .sourceTripCode(cruise.getSourceTrip() != null ? cruise.getSourceTrip().getTripCode() : null)
                .sourceVersionId(cruise.getSourceVersion() != null ? cruise.getSourceVersion().getVersionId() : null)
                .sourceVersionNumber(cruise.getSourceVersion() != null ? cruise.getSourceVersion().getVersionNumber() : null)
                .shipId(ship != null ? ship.getShipId() : null)
                .shipName(shipName)
                .totalCapacity(capacity)
                .departurePortName(cruise.getDeparturePort() != null ? cruise.getDeparturePort().getPortName() : null)
                .returnPortName(cruise.getReturnPort() != null ? cruise.getReturnPort().getPortName() : null)
                .totalBookingsCount(totalBookings)
                .confirmedBookingsCount(confirmedBookings)
                .draftBookingsCount(draftBookings)
                .cancelledBookingsCount(cancelledBookings)
                .totalPassengersCount(activePassengers)
                .checkedInPassengersCount(checkedInPassengers)
                .boardedPassengersCount(boardedPassengers)
                .captainAssigned(captainAssigned)
                .departureClearanceReady(departureClearanceReady)
                .manifestLocked(cruise.getStatus() == CruiseStatus.IN_PROGRESS || cruise.getStatus() == CruiseStatus.COMPLETED)
                .passengerManifest(manifest.getPassengerList())
                .staffAssignments(staffList)
                .build();
    }
}
