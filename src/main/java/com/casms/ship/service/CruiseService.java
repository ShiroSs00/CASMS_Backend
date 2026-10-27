package com.casms.ship.service;

import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.booking.repository.BookingRepository;
import com.casms.booking.repository.CabinAssignmentRepository;
import com.casms.checkin.repository.CruiseCheckinRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.common.service.BusinessCodeGenerator;
import java.util.Objects;
import com.casms.identity.repository.UserRepository;
import com.casms.ship.dto.request.CruiseChangeImpactRequest;
import com.casms.ship.dto.request.CruiseCreateRequest;
import com.casms.ship.dto.request.CruiseOpenSaleRequest;
import com.casms.ship.dto.request.CruiseResumeSaleRequest;
import com.casms.ship.dto.request.CruiseStopSaleRequest;
import com.casms.ship.dto.request.CruiseUpdateDraftRequest;
import com.casms.ship.dto.response.CabinTypeInventoryItem;
import com.casms.ship.dto.response.CruiseApplyChangeResponse;
import com.casms.ship.dto.response.CruiseCapacityValidationResponse;
import com.casms.ship.dto.response.CruiseChangeImpactResponse;
import com.casms.ship.dto.response.CruiseDownstreamHandoffResponse;
import com.casms.ship.dto.response.CruiseImpactTaskResponse;
import com.casms.ship.dto.response.CruiseInventorySummaryResponse;
import com.casms.ship.dto.response.CruiseManifestResponse;
import com.casms.ship.dto.response.CruiseReadinessResponse;
import com.casms.ship.dto.response.CruiseResponse;
import com.casms.ship.dto.response.CruiseSaleReadinessResponse;
import com.casms.ship.dto.response.CruiseStaffResponse;
import com.casms.ship.dto.response.ReadinessIssueDetail;
import com.casms.ship.dto.response.ShipCompatibilityResponse;
import com.casms.ship.dto.response.ShipChangeImpactResponse;
import com.casms.ship.enums.ReadinessSeverity;
import com.casms.ship.enums.ReadinessStatus;
import com.casms.activity.entity.ShoreExcursion;
import com.casms.activity.entity.Supplier;
import com.casms.activity.enums.SupplierStatus;
import com.casms.activity.enums.SupplierVerificationStatus;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.CruiseImpactTask;
import com.casms.ship.entity.CruiseStop;
import com.casms.ship.entity.CruiseTourPackage;
import com.casms.ship.entity.Itinerary;
import com.casms.ship.entity.ItineraryDay;
import com.casms.ship.entity.Port;
import com.casms.ship.entity.Ship;
import com.casms.ship.enums.CabinStatus;
import com.casms.ship.enums.CabinType;
import com.casms.ship.enums.CruiseCruiseType;
import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.enums.ItineraryStatus;
import com.casms.ship.enums.PortStatus;
import com.casms.ship.enums.ShipStatus;
import com.casms.ship.repository.ItineraryDayRepository;
import com.casms.ship.util.PortTimeZoneHelper;
import com.casms.trip.service.CountryScopeValidator;
import java.time.Duration;
import java.time.LocalDate;
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
import com.casms.servicepackage.entity.TourPackage;
import com.casms.servicepackage.enums.TourPackageStatus;
import com.casms.servicepackage.repository.TourPackageRepository;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
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
    private final TourPackageRepository tourPackageRepository;
    private final UserRepository userRepository;
    private final ItineraryDayRepository itineraryDayRepository;
    private final CruiseImpactAssessmentService cruiseImpactAssessmentService;

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
            if (sourceVersion.getTrip() != null && sourceVersion.getTrip().getStatus() == com.casms.trip.enums.TripStatus.ARCHIVED) {
                throw new BusinessException("TRIP_ARCHIVED", "Trip nguồn đã bị ARCHIVED hoặc không tồn tại.");
            }
            if (request.getExpectedSourceVersionRevision() != null && !Objects.equals(request.getExpectedSourceVersionRevision(), sourceVersion.getRevision())) {
                throw new BusinessException("STALE_PREVIEW",
                        String.format("Phiên bản nguồn đã thay đổi (revision hiện tại: %d, revision preview: %d). Vui lòng tải lại preview trước khi tạo.",
                                sourceVersion.getRevision(), request.getExpectedSourceVersionRevision()));
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

        // BE-R05b: Nội dung không được phép thay khi đã có booking phải từ chối hoặc dùng workflow đã duyệt (/apply-change)
        long bookedCount = bookingRepository.countByCruiseCruiseId(cruiseId);
        if (bookedCount > 0) {
            boolean scheduleChanged = (request.getDepartureDatetime() != null && !request.getDepartureDatetime().equals(cruise.getDepartureDatetime()))
                    || (request.getReturnDatetime() != null && !request.getReturnDatetime().equals(cruise.getReturnDatetime()));
            boolean portsChanged = (request.getDeparturePortId() != null && (cruise.getDeparturePort() == null || !request.getDeparturePortId().equals(cruise.getDeparturePort().getPortId())))
                    || (request.getReturnPortId() != null && (cruise.getReturnPort() == null || !request.getReturnPortId().equals(cruise.getReturnPort().getPortId())));
            if (scheduleChanged || portsChanged) {
                throw new BusinessException("CANNOT_MODIFY_COMMITTED_CRUISE_DIRECTLY",
                        "Chuyến đi đã có " + bookedCount + " đặt chỗ. Bắt buộc phải thực hiện thay đổi thông qua quy trình đánh giá tác động /apply-change");
            }
        }

        if (request.getExpectedRevision() != null && !request.getExpectedRevision().equals(cruise.getRevision())) {
            throw new BusinessException(org.springframework.http.HttpStatus.CONFLICT, "STALE_REVISION",
                    "Revision của Cruise không khớp (expected: " + request.getExpectedRevision() + ", current: " + cruise.getRevision() + ")");
        }

        Instant oldDep = cruise.getDepartureDatetime();
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

        // BE-R05b: Cập nhật ngày của Itinerary/CruiseStop/OnboardActivity/ShoreExcursion đồng bộ khi dời ngày
        shiftScheduleAndPrograms(cruise, oldDep, effectiveDeparture);

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

    @Transactional(readOnly = true, noRollbackFor = Exception.class)
    public CruiseReadinessResponse validateOperationalReadiness(UUID cruiseId) {
        Cruise cruise = getCruiseById(cruiseId);
        List<String> issues = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<ReadinessIssueDetail> details = new ArrayList<>();
        boolean hasInfrastructureError = false;

        boolean shipAssigned = cruise.getShip() != null;
        boolean shipActive = false;
        boolean shipConfigValid = false;

        if (!shipAssigned) {
            issues.add("Chưa gán tàu cho chuyến đi");
            blockers.add("NO_SHIP_ASSIGNED");
            details.add(ReadinessIssueDetail.builder()
                    .code("NO_SHIP_ASSIGNED")
                    .targetType("CRUISE")
                    .targetId(cruiseId)
                    .scope("CRUISE")
                    .severity(ReadinessSeverity.BLOCKER)
                    .message("Chưa gán tàu cho chuyến đi")
                    .evidence("Cruise.ship is null")
                    .allowedAction("Gán tàu cho chuyến đi qua /cruises/{id}/assign-ship")
                    .requiredPermission("CRUISE_ASSIGN_SHIP")
                    .build());
        } else {
            Ship ship = cruise.getShip();
            shipActive = ship.getStatus() == ShipStatus.ACTIVE;
            if (!shipActive) {
                issues.add("Tàu được gán đang không ở trạng thái ACTIVE (hiện tại: " + ship.getStatus() + ")");
                blockers.add("SHIP_NOT_ACTIVE");
                details.add(ReadinessIssueDetail.builder()
                        .code("SHIP_NOT_ACTIVE")
                        .targetType("SHIP")
                        .targetId(ship.getShipId())
                        .scope("SHIP")
                        .severity(ReadinessSeverity.BLOCKER)
                        .message("Tàu được gán đang không ở trạng thái ACTIVE (hiện tại: " + ship.getStatus() + ")")
                        .evidence("Ship.status=" + ship.getStatus())
                        .allowedAction("Kích hoạt tàu hoặc đổi tàu khác qua /cruises/{id}/change-ship")
                        .requiredPermission("SHIP_UPDATE")
                        .build());
            }

            long decks = deckRepository.countByShipShipId(ship.getShipId());
            long cabins = cabinRepository.countByShipShipId(ship.getShipId());
            shipConfigValid = decks > 0 && cabins > 0;
            if (!shipConfigValid) {
                issues.add("Tàu chưa được cấu hình đầy đủ tầng (decks=" + decks + ") hoặc phòng (cabins=" + cabins + ")");
                blockers.add("SHIP_NOT_CONFIGURED");
                details.add(ReadinessIssueDetail.builder()
                        .code("SHIP_NOT_CONFIGURED")
                        .targetType("SHIP")
                        .targetId(ship.getShipId())
                        .scope("SHIP")
                        .severity(ReadinessSeverity.BLOCKER)
                        .message("Tàu chưa được cấu hình đầy đủ tầng hoặc phòng")
                        .evidence("Decks count=" + decks + ", Cabins count=" + cabins)
                        .allowedAction("Cấu hình boong và cabin cho con tàu trước khi vận hành")
                        .requiredPermission("SHIP_UPDATE")
                        .build());
            }

            if (cruise.getDepartureDatetime() != null && cruise.getReturnDatetime() != null) {
                boolean shipOverlapped = cruiseRepository.existsOverlappingCruisesExcluding(
                        ship.getShipId(),
                        cruiseId,
                        cruise.getDepartureDatetime(),
                        cruise.getReturnDatetime()
                );
                if (shipOverlapped) {
                    issues.add("Tàu được gán bị trùng lịch với chuyến đi khác");
                    blockers.add("SHIP_SCHEDULE_CONFLICT");
                    details.add(ReadinessIssueDetail.builder()
                            .code("SHIP_SCHEDULE_CONFLICT")
                            .targetType("SHIP")
                            .targetId(ship.getShipId())
                            .scope("SHIP")
                            .severity(ReadinessSeverity.BLOCKER)
                            .message("Tàu được gán đang bị trùng lịch độc quyền với chuyến đi khác trong cùng khoảng thời gian")
                            .evidence("existsOverlappingCruisesExcluding=true")
                            .allowedAction("Đổi lịch trình hoặc đổi tàu khác qua /cruises/{id}/change-ship")
                            .requiredPermission("CRUISE_CHANGE_SHIP")
                            .build());
                }
            }
        }

        boolean scheduleValid = cruise.getReturnDatetime() != null && cruise.getDepartureDatetime() != null
                && cruise.getReturnDatetime().isAfter(cruise.getDepartureDatetime());
        if (!scheduleValid) {
            issues.add("Lịch trình không hợp lệ: Thời điểm kết thúc phải sau thời điểm khởi hành");
            blockers.add("INVALID_SCHEDULE");
            details.add(ReadinessIssueDetail.builder()
                    .code("INVALID_SCHEDULE")
                    .targetType("CRUISE")
                    .targetId(cruiseId)
                    .scope("CRUISE")
                    .severity(ReadinessSeverity.BLOCKER)
                    .message("Thời điểm kết thúc phải sau thời điểm khởi hành")
                    .evidence("Departure: " + cruise.getDepartureDatetime() + ", Return: " + cruise.getReturnDatetime())
                    .allowedAction("Cập nhật thời gian khởi hành/kết thúc qua /cruises/{id}")
                    .requiredPermission("CRUISE_UPDATE")
                    .build());
        }

        boolean departurePortActive = cruise.getDeparturePort() != null && cruise.getDeparturePort().getStatus() == PortStatus.ACTIVE;
        boolean returnPortActive = cruise.getReturnPort() != null && cruise.getReturnPort().getStatus() == PortStatus.ACTIVE;
        boolean portsActive = departurePortActive && returnPortActive;
        if (!portsActive) {
            issues.add("Cảng đi hoặc cảng về đang không ở trạng thái hoạt động (ACTIVE)");
            blockers.add("PORTS_NOT_ACTIVE");
            details.add(ReadinessIssueDetail.builder()
                    .code("PORTS_NOT_ACTIVE")
                    .targetType("PORT")
                    .targetId(cruise.getDeparturePort() != null ? cruise.getDeparturePort().getPortId() : null)
                    .scope("GLOBAL")
                    .severity(ReadinessSeverity.BLOCKER)
                    .message("Cảng đi hoặc cảng về đang không ở trạng thái hoạt động (ACTIVE)")
                    .evidence("DeparturePort: " + (cruise.getDeparturePort() != null ? cruise.getDeparturePort().getStatus() : "null")
                            + ", ReturnPort: " + (cruise.getReturnPort() != null ? cruise.getReturnPort().getStatus() : "null"))
                    .allowedAction("Kích hoạt cảng khả dụng qua /ports/{id}")
                    .requiredPermission("PORT_UPDATE")
                    .build());
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
            details.add(ReadinessIssueDetail.builder()
                    .code("ITINERARY_MISSING")
                    .targetType("ITINERARY")
                    .targetId(null)
                    .scope("CRUISE")
                    .severity(ReadinessSeverity.BLOCKER)
                    .message("Chuyến đi chưa được liên kết hải trình (Itinerary)")
                    .evidence("Itinerary reference is null")
                    .allowedAction("Tạo hoặc liên kết hải trình cho chuyến đi")
                    .requiredPermission("ITINERARY_MANAGE")
                    .build());
        } else if (!itineraryApproved) {
            issues.add("Hải trình của chuyến đi chưa được phê duyệt hoặc mở bán (Trạng thái hiện tại: " + itineraryOpt.get().getStatus() + ")");
            blockers.add("ITINERARY_NOT_APPROVED");
            details.add(ReadinessIssueDetail.builder()
                    .code("ITINERARY_NOT_APPROVED")
                    .targetType("ITINERARY")
                    .targetId(itineraryOpt.get().getItineraryId())
                    .scope("CRUISE")
                    .severity(ReadinessSeverity.BLOCKER)
                    .message("Hải trình chưa được phê duyệt (hiện tại: " + itineraryOpt.get().getStatus() + ")")
                    .evidence("Itinerary.status=" + itineraryOpt.get().getStatus())
                    .allowedAction("Phê duyệt hải trình sang APPROVED qua /itinerary/{id}/approve")
                    .requiredPermission("ITINERARY_APPROVE")
                    .build());
        }

        // BE-06 / BE-R03: Staff readiness verification
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
            details.add(ReadinessIssueDetail.builder()
                    .code("MISSING_CAPTAIN")
                    .targetType("CRUISE")
                    .targetId(cruiseId)
                    .scope("CRUISE")
                    .severity(ReadinessSeverity.BLOCKER)
                    .message("Chuyến đi chưa được phân công Thuyền trưởng (CAPTAIN)")
                    .evidence("Active staff with CAPTAIN role = 0")
                    .allowedAction("Phân công nhân sự có vai trò Thuyền trưởng qua /cruises/{id}/staff")
                    .requiredPermission("CRUISE_ASSIGN_STAFF")
                    .build());
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
                    details.add(ReadinessIssueDetail.builder()
                            .code("STAFF_SCHEDULE_CONFLICT")
                            .targetType("STAFF")
                            .targetId(s.getUser().getUserId())
                            .scope("CRUISE")
                            .severity(ReadinessSeverity.BLOCKER)
                            .message(msg)
                            .evidence("Conflicting cruise: " + cs.getCruise().getCruiseCode())
                            .allowedAction("Điều chuyển nhân sự sang ca khác hoặc phân công người thay thế")
                            .requiredPermission("CRUISE_ASSIGN_STAFF")
                            .build());
                }
            }
        }

        // BE-06 / BE-R03: Regulations readiness verification at milestone D07
        boolean regulationsCompliant = true;
        List<String> regulationConflicts = new ArrayList<>();
        Instant regulationEvaluationTime = cruise.getDepartureDatetime() != null ? cruise.getDepartureDatetime() : Instant.now();
        int regCount = 0;
        if (cruiseRegulationService != null) {
            try {
                var snapshot = cruiseRegulationService.getEffectiveSnapshot(cruiseId, regulationEvaluationTime);
                if (snapshot != null) {
                    int directCount = snapshot.getDirect() != null ? snapshot.getDirect().size() : 0;
                    int inheritedCount = snapshot.getInherited() != null ? snapshot.getInherited().size() : 0;
                    regCount = directCount + inheritedCount;
                    if (directCount == 0 && inheritedCount == 0) {
                        warnings.add("Chưa có quy định an toàn/vận hành nào được gán hoặc kế thừa cho chuyến đi");
                        details.add(ReadinessIssueDetail.builder()
                                .code("NO_EFFECTIVE_REGULATIONS")
                                .targetType("CRUISE")
                                .targetId(cruiseId)
                                .scope("CRUISE")
                                .severity(ReadinessSeverity.WARNING)
                                .message("Chưa có quy định an toàn/vận hành nào có hiệu lực cho chuyến đi tại mốc khởi hành")
                                .evidence("Direct=0, Inherited=0 at " + regulationEvaluationTime)
                                .allowedAction("Gán quy định vận hành cho chuyến đi hoặc tàu qua /cruiseregulations")
                                .requiredPermission("REGULATION_ASSIGN")
                                .build());
                    }
                }
            } catch (Exception ex) {
                log.warn("Failed to check regulation snapshot for cruise {}: {}", cruiseId, ex.getMessage());
                regulationsCompliant = false;
                hasInfrastructureError = true;
                issues.add("Lỗi hệ thống phụ thuộc khi kiểm tra quy định vận hành: " + ex.getMessage());
                details.add(ReadinessIssueDetail.builder()
                        .code("DEPENDENCY_TIMEOUT")
                        .targetType("REGULATION")
                        .targetId(cruiseId)
                        .scope("GLOBAL")
                        .severity(ReadinessSeverity.INFRASTRUCTURE_ERROR)
                        .message("Không thể kiểm tra quy định do lỗi hạ tầng hoặc timeout hệ thống phụ thuộc")
                        .evidence("Exception in CruiseRegulationService: " + ex.getClass().getSimpleName() + " - " + ex.getMessage())
                        .allowedAction("Thử lại đánh giá sau khi kiểm tra kết nối dịch vụ quy định")
                        .requiredPermission("CRUISE_READ")
                        .build());
            }
        }

        ReadinessStatus status;
        if (hasInfrastructureError) {
            status = ReadinessStatus.INDETERMINATE;
        } else if (!blockers.isEmpty()) {
            status = ReadinessStatus.NOT_READY;
        } else if (!warnings.isEmpty()) {
            status = ReadinessStatus.WARNING;
        } else {
            status = ReadinessStatus.READY;
        }
        boolean ready = (status == ReadinessStatus.READY || status == ReadinessStatus.WARNING) && blockers.isEmpty() && !hasInfrastructureError;

        String rawState = String.format("CRUISE:%s:REV:%s:SHIP:%s:ACT:%s:CONFIG:%s:PORTS:%s:CAPTAIN:%s:REGS:%d:BLOCKERS:%d:WARNS:%d:INFRA:%s",
                cruiseId, cruise.getRevision(), shipAssigned, shipActive, shipConfigValid, portsActive, captainAssigned,
                regCount, blockers.size(), warnings.size(), hasInfrastructureError);
        String fingerprint = generateFingerprint(rawState);

        CruiseReadinessResponse response = new CruiseReadinessResponse();
        response.setCruiseId(cruiseId);
        response.setReady(ready);
        response.setStatus(status);
        response.setFingerprint(fingerprint);
        response.setValidUntil(Instant.now().plus(15, ChronoUnit.MINUTES));
        response.setDetails(details);
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
        res.setStoppedAt(cruise.getStoppedAt());
        if (cruise.getStoppedBy() != null) {
            res.setStoppedByUsername(cruise.getStoppedBy().getUsername());
        }
        res.setStopReason(cruise.getStopReason());
        res.setStopEvidence(cruise.getStopEvidence());
        res.setStopScope(cruise.getStopScope());
        res.setStopEventIdentity(cruise.getStopEventIdentity());
        res.setSaleStopped(cruise.getStoppedAt() != null);
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

        long bookedPassengers = bookingPassengerRepository.countConfirmedPassengersByCruiseId(cruiseId);
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

    @Transactional(readOnly = true, noRollbackFor = Exception.class)
    public CruiseSaleReadinessResponse validateSaleReadiness(UUID cruiseId) {
        Cruise cruise = getCruiseById(cruiseId);
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        List<ReadinessIssueDetail> details = new ArrayList<>();
        boolean hasInfrastructureError = false;

        // 1. Evaluate operational readiness
        CruiseReadinessResponse opReadiness = validateOperationalReadiness(cruiseId);
        boolean operationalReady = opReadiness.isReady();
        if (!operationalReady) {
            blockers.addAll(opReadiness.getBlockers());
            issues.addAll(opReadiness.getIssues());
        }
        warnings.addAll(opReadiness.getWarnings());
        if (opReadiness.getDetails() != null) {
            details.addAll(opReadiness.getDetails());
        }
        if (opReadiness.getStatus() == ReadinessStatus.INDETERMINATE) {
            hasInfrastructureError = true;
        }

        // 2. Check departure time in the future (at least 24 hours)
        boolean departureInFuture = cruise.getDepartureDatetime() != null
                && cruise.getDepartureDatetime().isAfter(Instant.now().plus(24, ChronoUnit.HOURS));
        if (!departureInFuture) {
            if (cruise.getDepartureDatetime() == null || cruise.getDepartureDatetime().isBefore(Instant.now())) {
                blockers.add("CRUISE_ALREADY_DEPARTED");
                issues.add("Thời điểm khởi hành đã qua hoặc không xác định");
                details.add(ReadinessIssueDetail.builder()
                        .code("CRUISE_ALREADY_DEPARTED")
                        .targetType("CRUISE")
                        .targetId(cruiseId)
                        .scope("CRUISE")
                        .severity(ReadinessSeverity.BLOCKER)
                        .message("Thời điểm khởi hành đã qua hoặc không xác định")
                        .evidence("Departure: " + cruise.getDepartureDatetime())
                        .allowedAction("Thiết lập lại lịch khởi hành trong tương lai")
                        .requiredPermission("CRUISE_UPDATE")
                        .build());
            } else {
                warnings.add("Thời điểm khởi hành còn dưới 24 giờ kể từ thời điểm kiểm tra");
                details.add(ReadinessIssueDetail.builder()
                        .code("DEPARTURE_NEAR")
                        .targetType("CRUISE")
                        .targetId(cruiseId)
                        .scope("CRUISE")
                        .severity(ReadinessSeverity.WARNING)
                        .message("Thời điểm khởi hành còn dưới 24 giờ")
                        .evidence("Departure: " + cruise.getDepartureDatetime())
                        .allowedAction("Đảm bảo mọi công tác chuẩn bị đã sẵn sàng")
                        .requiredPermission("CRUISE_READ")
                        .build());
            }
        }

        // 3. Check tour packages assigned, active, and priced
        List<CruiseTourPackage> assignedPackages = cruiseTourPackageRepository.findByCruise_CruiseId(cruiseId);
        List<CruiseTourPackage> activeCtpList = assignedPackages.stream()
                .filter(CruiseTourPackage::isActive)
                .toList();

        long activePackages = activeCtpList.size();
        boolean packagesConfigured = activePackages > 0;
        if (!packagesConfigured) {
            blockers.add("NO_ACTIVE_PACKAGES");
            issues.add("Chưa có gói tour/sản phẩm dịch vụ nào được gán và kích hoạt cho chuyến đi");
            details.add(ReadinessIssueDetail.builder()
                    .code("NO_ACTIVE_PACKAGES")
                    .targetType("CRUISE")
                    .targetId(cruiseId)
                    .scope("CRUISE")
                    .severity(ReadinessSeverity.BLOCKER)
                    .message("Chưa có gói tour/sản phẩm dịch vụ nào được kích hoạt mở bán")
                    .evidence("Active packages count = 0")
                    .allowedAction("Mở bán các gói dịch vụ qua /cruises/{id}/open-sale")
                    .requiredPermission("CRUISE_OPEN_SALE")
                    .build());
        }

        // BE-R03: Bỏ kết quả đạt gán cố định cho giá. Kiểm tra cụ thể giá và tiền tệ từng gói
        boolean pricingConfigured = true;
        StringBuilder packageDigest = new StringBuilder();
        for (CruiseTourPackage ctp : activeCtpList) {
            TourPackage pkg = ctp.getTourPackage();
            if (pkg == null) continue;
            packageDigest.append(pkg.getTourPackageId()).append(":")
                    .append(pkg.getPrice()).append(":")
                    .append(pkg.getCurrencyCode()).append(";");
            if (pkg.getPrice() == null || pkg.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                pricingConfigured = false;
                blockers.add("PACKAGE_PRICE_MISSING");
                String msg = "Gói dịch vụ " + pkg.getPackageName() + " (" + pkg.getPackageCode() + ") chưa được cấu hình giá bán hợp lệ";
                issues.add(msg);
                details.add(ReadinessIssueDetail.builder()
                        .code("PACKAGE_PRICE_MISSING")
                        .targetType("PACKAGE")
                        .targetId(pkg.getTourPackageId())
                        .scope("PACKAGE")
                        .severity(ReadinessSeverity.BLOCKER)
                        .message(msg)
                        .evidence("TourPackage.price is " + pkg.getPrice())
                        .allowedAction("Cấu hình giá bán cho gói tour qua /servicepackage/packages/{id}")
                        .requiredPermission("TOUR_PACKAGE_MANAGE")
                        .build());
            }
            if (pkg.getCurrencyCode() == null || pkg.getCurrencyCode().isBlank()) {
                pricingConfigured = false;
                blockers.add("PACKAGE_CURRENCY_MISSING");
                String msg = "Gói dịch vụ " + pkg.getPackageName() + " (" + pkg.getPackageCode() + ") chưa được cấu hình đơn vị tiền tệ";
                issues.add(msg);
                details.add(ReadinessIssueDetail.builder()
                        .code("PACKAGE_CURRENCY_MISSING")
                        .targetType("PACKAGE")
                        .targetId(pkg.getTourPackageId())
                        .scope("PACKAGE")
                        .severity(ReadinessSeverity.BLOCKER)
                        .message(msg)
                        .evidence("TourPackage.currencyCode is null or empty")
                        .allowedAction("Thiết lập mã tiền tệ (USD/VND) cho gói dịch vụ qua /servicepackage/packages/{id}")
                        .requiredPermission("TOUR_PACKAGE_MANAGE")
                        .build());
            }
        }

        // 4. Check inventory
        int physicalCapacity = cruise.getShip() != null ? cruise.getShip().getTotalCapacity() : 0;
        long booked = bookingPassengerRepository.countConfirmedPassengersByCruiseId(cruiseId);
        long held = bookingPassengerRepository.countHeldPassengersByCruiseId(cruiseId);
        long remaining = Math.max(0, physicalCapacity - booked - held);
        boolean inventoryAvailable = remaining > 0 && physicalCapacity > 0;
        if (!inventoryAvailable) {
            blockers.add("INVENTORY_EXHAUSTED");
            issues.add("Không còn chỗ trống trên tàu để mở bán");
            details.add(ReadinessIssueDetail.builder()
                    .code("INVENTORY_EXHAUSTED")
                    .targetType("CRUISE")
                    .targetId(cruiseId)
                    .scope("CRUISE")
                    .severity(ReadinessSeverity.BLOCKER)
                    .message("Không còn chỗ trống khả dụng trên tàu")
                    .evidence("PhysicalCapacity=" + physicalCapacity + ", Booked=" + booked + ", Held=" + held + ", Remaining=" + remaining)
                    .allowedAction("Tạm dừng mở bán hoặc tăng tải trọng tàu nếu có thể")
                    .requiredPermission("CRUISE_STOP_SALE")
                    .build());
        }

        // 5. BE-R03: Supplier & Programs (Bắt buộc vs Optional phân lập đúng phạm vi)
        List<ShoreExcursion> excursions = shoreExcursionRepository.findByCruiseStop_Itinerary_Cruise_CruiseId(cruiseId);
        StringBuilder supplierDigest = new StringBuilder();
        for (ShoreExcursion exc : excursions) {
            boolean isOptional = exc.getPrice() != null && exc.getPrice().compareTo(BigDecimal.ZERO) > 0;
            Supplier supplier = exc.getSupplier();
            supplierDigest.append(exc.getShoreExcursionId()).append(":");

            if (exc.getMaximumCapacity() == null || exc.getMaximumCapacity() <= 0) {
                if (!isOptional) {
                    blockers.add("EXCURSION_CAPACITY_INVALID");
                    issues.add("Tour bờ bắt buộc " + exc.getExcursionName() + " chưa cấu hình sức chứa hợp lệ");
                } else {
                    warnings.add("Tour bờ tự chọn " + exc.getExcursionName() + " chưa cấu hình sức chứa hợp lệ");
                }
                details.add(ReadinessIssueDetail.builder()
                        .code("EXCURSION_CAPACITY_INVALID")
                        .targetType("EXCURSION")
                        .targetId(exc.getShoreExcursionId())
                        .scope(isOptional ? "EXCURSION" : "CRUISE")
                        .severity(isOptional ? ReadinessSeverity.WARNING : ReadinessSeverity.BLOCKER)
                        .message("Tour bờ " + exc.getExcursionName() + " chưa cấu hình sức chứa (maximumCapacity <= 0)")
                        .evidence("maximumCapacity=" + exc.getMaximumCapacity())
                        .allowedAction("Cấu hình sức chứa cho tour bờ qua /shore-excursions/{id}")
                        .requiredPermission("EXCURSION_MANAGE")
                        .build());
            }

            if (exc.getStartTime() != null && exc.getPlannedReturnTime() != null && exc.getPlannedReturnTime().isBefore(exc.getStartTime())) {
                if (!isOptional) {
                    blockers.add("EXCURSION_SCHEDULE_INVALID");
                    issues.add("Tour bờ bắt buộc " + exc.getExcursionName() + " có thời gian kết thúc trước thời gian bắt đầu");
                } else {
                    warnings.add("Tour bờ tự chọn " + exc.getExcursionName() + " có thời gian kết thúc trước thời gian bắt đầu");
                }
                details.add(ReadinessIssueDetail.builder()
                        .code("EXCURSION_SCHEDULE_INVALID")
                        .targetType("EXCURSION")
                        .targetId(exc.getShoreExcursionId())
                        .scope(isOptional ? "EXCURSION" : "CRUISE")
                        .severity(isOptional ? ReadinessSeverity.WARNING : ReadinessSeverity.BLOCKER)
                        .message("Thời gian kết thúc tour bờ trước thời gian bắt đầu")
                        .evidence("StartTime=" + exc.getStartTime() + ", ReturnTime=" + exc.getPlannedReturnTime())
                        .allowedAction("Điều chỉnh khung giờ tour bờ qua /shore-excursions/{id}")
                        .requiredPermission("EXCURSION_MANAGE")
                        .build());
            }

            if (supplier == null) {
                supplierDigest.append("NOSUP;");
                if (!isOptional) {
                    blockers.add("MANDATORY_EXCURSION_SUPPLIER_MISSING");
                    issues.add("Tour bờ bắt buộc " + exc.getExcursionName() + " chưa được phân công nhà cung cấp");
                } else {
                    warnings.add("Tour bờ tự chọn " + exc.getExcursionName() + " chưa có nhà cung cấp (chỉ khóa bán tour này)");
                }
                details.add(ReadinessIssueDetail.builder()
                        .code("SUPPLIER_MISSING")
                        .targetType("EXCURSION")
                        .targetId(exc.getShoreExcursionId())
                        .scope(isOptional ? "EXCURSION" : "CRUISE")
                        .severity(isOptional ? ReadinessSeverity.WARNING : ReadinessSeverity.BLOCKER)
                        .message("Tour bờ " + exc.getExcursionName() + " chưa được phân công nhà cung cấp")
                        .evidence("Supplier is null on excursion " + exc.getExcursionCode())
                        .allowedAction("Phân công nhà cung cấp cho tour bờ qua /shore-excursions/{id}")
                        .requiredPermission("EXCURSION_MANAGE")
                        .build());
            } else {
                supplierDigest.append(supplier.getSupplierId()).append(":").append(supplier.getStatus()).append(":").append(supplier.getVerificationStatus()).append(";");
                boolean isActive = supplier.getStatus() == SupplierStatus.ACTIVE;
                boolean isVerified = supplier.getVerificationStatus() == SupplierVerificationStatus.VERIFIED;
                if (!isActive || !isVerified) {
                    boolean isRejected = supplier.getVerificationStatus() == SupplierVerificationStatus.REJECTED;
                    String statusLabel = !isActive ? "INACTIVE" : supplier.getVerificationStatus().name();
                    String code = isRejected ? "SUPPLIER_REJECTED" : "SUPPLIER_NOT_VERIFIED";
                    if (!isOptional) {
                        blockers.add(isRejected ? "MANDATORY_EXCURSION_SUPPLIER_REJECTED" : "MANDATORY_EXCURSION_SUPPLIER_UNVERIFIED");
                        issues.add("Nhà cung cấp " + supplier.getSupplierName() + " cho tour bắt buộc " + exc.getExcursionName() + " chưa sẵn sàng (" + statusLabel + ")");
                    } else {
                        warnings.add("Nhà cung cấp " + supplier.getSupplierName() + " cho tour tự chọn " + exc.getExcursionName() + " chưa sẵn sàng (" + statusLabel + "), tạm khóa bán tour này");
                    }
                    details.add(ReadinessIssueDetail.builder()
                            .code(code)
                            .targetType("SUPPLIER")
                            .targetId(supplier.getSupplierId())
                            .scope(isOptional ? "EXCURSION" : "CRUISE")
                            .severity(isOptional ? ReadinessSeverity.WARNING : ReadinessSeverity.BLOCKER)
                            .message("Nhà cung cấp " + supplier.getSupplierName() + " chưa ở trạng thái ACTIVE và VERIFIED (hiện tại: " + statusLabel + ")")
                            .evidence("Supplier status=" + supplier.getStatus() + ", verification=" + supplier.getVerificationStatus())
                            .allowedAction("Xác minh hoặc kích hoạt nhà cung cấp qua /supplier/{id}/verify")
                            .requiredPermission("SUPPLIER_VERIFY")
                            .build());
                }
            }
        }

        // Determine Overall Readiness Status
        ReadinessStatus status;
        if (hasInfrastructureError) {
            status = ReadinessStatus.INDETERMINATE;
        } else if (!blockers.isEmpty()) {
            status = ReadinessStatus.NOT_READY;
        } else if (!warnings.isEmpty()) {
            status = ReadinessStatus.WARNING;
        } else {
            status = ReadinessStatus.READY;
        }
        boolean ready = (status == ReadinessStatus.READY || status == ReadinessStatus.WARNING) && blockers.isEmpty() && !hasInfrastructureError;

        // Composite fingerprint including external dependencies (prices, supplier status, ports, capacity)
        String rawState = String.format("CRUISE:%s:REV:%s:STAT:%s:DEP:%s:RET:%s:PACKAGES:%s:PRICE_OK:%s:INV:%d:SUP:%s:BLOCKERS:%d:WARNS:%d:INFRA:%s",
                cruiseId, cruise.getRevision(), cruise.getStatus(),
                cruise.getDeparturePort() != null ? cruise.getDeparturePort().getStatus() : "null",
                cruise.getReturnPort() != null ? cruise.getReturnPort().getStatus() : "null",
                packageDigest.toString(), pricingConfigured, remaining, supplierDigest.toString(),
                blockers.size(), warnings.size(), hasInfrastructureError);
        String fingerprint = generateFingerprint(rawState);

        return CruiseSaleReadinessResponse.builder()
                .cruiseId(cruiseId)
                .ready(ready)
                .status(status)
                .fingerprint(fingerprint)
                .validUntil(Instant.now().plus(15, ChronoUnit.MINUTES))
                .details(details)
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

    private String generateFingerprint(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    @Transactional
    public CruiseResponse openSale(UUID cruiseId, CruiseOpenSaleRequest request) {
        Cruise cruise = cruiseRepository.findByIdForUpdate(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));

        if (request != null && request.getExpectedRevision() != null && !request.getExpectedRevision().equals(cruise.getRevision())) {
            throw new BusinessException(HttpStatus.CONFLICT, "STALE_REVISION",
                    "Revision của Cruise không khớp (expected: " + request.getExpectedRevision() + ", current: " + cruise.getRevision() + ")");
        }

        Optional<Itinerary> itineraryOpt = itineraryRepository.findByCruiseCruiseId(cruiseId);
        boolean alreadyOpen = cruise.getStatus() == CruiseStatus.PUBLISHED
                && itineraryOpt.isPresent() && itineraryOpt.get().getStatus() == ItineraryStatus.OPEN_FOR_SALE;

        List<CruiseTourPackage> allAssigned = cruiseTourPackageRepository.findByCruise_CruiseId(cruiseId);

        // BE-R01: Validate requested scope and check idempotency
        if (request != null && request.getPackageIds() != null) {
            Set<UUID> requestedPackageIds = new HashSet<>(request.getPackageIds());

            // Validate every package ID in the requested scope: must exist, be ACTIVE, and be assigned to this cruise
            for (UUID pkgId : request.getPackageIds()) {
                TourPackage tourPackage = tourPackageRepository.findById(pkgId)
                        .orElseThrow(() -> new ResourceNotFoundException("TourPackage", pkgId));
                if (tourPackage.getStatus() != TourPackageStatus.ACTIVE) {
                    throw new BusinessException("PACKAGE_INACTIVE",
                            "Không thể mở bán gói tour đang không hoạt động: " + tourPackage.getPackageName());
                }
                boolean assignedToCruise = allAssigned.stream()
                        .anyMatch(ctp -> ctp.getTourPackage().getTourPackageId().equals(pkgId));
                if (!assignedToCruise) {
                    throw new BusinessException("PACKAGE_NOT_ASSIGNED_TO_CRUISE",
                            "Gói tour " + pkgId + " chưa được gán cho chuyến đi này");
                }
            }

            // Check if already open and current active packages already match requested scope exactly
            if (alreadyOpen) {
                Set<UUID> currentActivePackageIds = allAssigned.stream()
                        .filter(CruiseTourPackage::isActive)
                        .map(ctp -> ctp.getTourPackage().getTourPackageId())
                        .collect(Collectors.toSet());
                if (currentActivePackageIds.equals(requestedPackageIds)) {
                    return mapToResponse(cruise); // Truly idempotent retry
                }
            }

            // Apply scope: requested packages -> active = true, other assigned packages -> active = false (Scope A không mở B)
            for (CruiseTourPackage ctp : allAssigned) {
                boolean shouldBeActive = requestedPackageIds.contains(ctp.getTourPackage().getTourPackageId());
                if (ctp.isActive() != shouldBeActive) {
                    ctp.setActive(shouldBeActive);
                    cruiseTourPackageRepository.save(ctp);
                }
            }
        } else {
            // Omitted list (request == null || request.getPackageIds() == null)
            if (alreadyOpen) {
                return mapToResponse(cruise); // Already open, no scope change requested -> idempotent
            }
            // Cruise-wide open sale: ensure assigned active packages are active
            for (CruiseTourPackage ctp : allAssigned) {
                if (ctp.getTourPackage() != null && ctp.getTourPackage().getStatus() == TourPackageStatus.ACTIVE) {
                    if (!ctp.isActive()) {
                        ctp.setActive(true);
                        cruiseTourPackageRepository.save(ctp);
                    }
                }
            }
        }

        // Validation gate: validateSaleReadiness
        CruiseSaleReadinessResponse saleReadiness = validateSaleReadiness(cruiseId);
        if (!saleReadiness.isReady()) {
            throw new BusinessException("CRUISE_NOT_SALE_READY",
                    "Chuyến đi chưa đủ điều kiện mở bán: " + String.join("; ", saleReadiness.getIssues()));
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
    public CruiseResponse stopSale(UUID cruiseId, CruiseStopSaleRequest request, UUID actorId) {
        Cruise cruise = cruiseRepository.findByIdForUpdate(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));

        if (request != null && request.getExpectedRevision() != null && !request.getExpectedRevision().equals(cruise.getRevision())) {
            throw new BusinessException(HttpStatus.CONFLICT, "STALE_REVISION",
                    "Revision của Cruise không khớp (expected: " + request.getExpectedRevision() + ", current: " + cruise.getRevision() + ")");
        }

        String scope = (request != null && request.getPackageId() != null) ? "PACKAGE" : "CRUISE";
        String eventIdentity = (request != null && request.getEventIdentity() != null && !request.getEventIdentity().isBlank())
                ? request.getEventIdentity()
                : "EVT-" + cruise.getCruiseCode() + "-STOP-" + scope + "-" + System.currentTimeMillis();

        if ("PACKAGE".equals(scope)) {
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
            cruise.setStoppedAt(Instant.now());
            if (actorId != null) {
                userRepository.findById(actorId).ifPresent(cruise::setStoppedBy);
            }
            cruise.setStopReason(request != null ? request.getReason() : "Stop sale triggered");
            cruise.setStopEvidence(request != null ? request.getEvidence() : null);
            cruise.setStopScope("CRUISE");
            cruise.setStopEventIdentity(eventIdentity);
            log.info("Stopped sale (SUSPENDED) for entire cruise {}. Reason: {}", cruise.getCruiseCode(), cruise.getStopReason());
        }

        // BE-R06: Có booking/registration bị ảnh hưởng thì tạo việc phù hợp theo C17; retry cùng sự kiện không tạo lặp.
        // Không bắt mọi manual stop tạo một task giống nhau nếu không có tác động cần xử lý.
        long affectedBookings = bookingRepository.countByCruiseCruiseId(cruiseId);
        if (affectedBookings > 0) {
            String taskCode = "TASK-" + cruise.getCruiseCode() + "-STOP-" + Math.abs(eventIdentity.hashCode() % 10000);
            Optional<CruiseImpactTask> existingTask = cruiseImpactTaskRepository.findByTaskCode(taskCode);
            if (existingTask.isEmpty()) {
                CruiseImpactTask task = new CruiseImpactTask();
                task.setCruise(cruise);
                task.setTaskCode(taskCode);
                task.setEventIdentity(eventIdentity);
                task.setCategory("SALE_STOPPED");
                task.setTargetType("CRUISE");
                task.setTargetId(cruiseId.toString());
                task.setTitle("Xử lý tác động dừng bán chuyến đi " + cruise.getCruiseCode());
                task.setDescription("Chuyến đi bị tạm dừng mở bán do: " + (request != null ? request.getReason() : "N/A") + ". Có " + affectedBookings + " booking cần rà soát.");
                task.setStatus("PENDING");
                task.setSeverity("BLOCKING");
                task.setEvidence("Số booking bị ảnh hưởng: " + affectedBookings + "; Lý do: " + (request != null ? request.getReason() : "N/A"));
                cruiseImpactTaskRepository.save(task);
            }
        }

        cruise = cruiseRepository.save(cruise);
        cruiseRepository.flush();
        return mapToResponse(cruise);
    }

    // Overload for backward compatibility
    @Transactional
    public CruiseResponse stopSale(UUID cruiseId, CruiseStopSaleRequest request) {
        return stopSale(cruiseId, request, null);
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
            if (ctp.getTourPackage() != null && ctp.getTourPackage().getStatus() != TourPackageStatus.ACTIVE) {
                throw new BusinessException("PACKAGE_INACTIVE",
                        "Không thể mở bán lại gói tour đang không hoạt động: " + ctp.getTourPackage().getPackageName());
            }
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
            cruise.setStoppedAt(null);
            cruise.setStoppedBy(null);
            cruise.setStopReason(null);
            cruise.setStopEvidence(null);
            cruise.setStopScope(null);
            cruise.setStopEventIdentity(null);
            log.info("Resumed sale for entire cruise {}", cruise.getCruiseCode());
        }

        cruise = cruiseRepository.save(cruise);
        cruiseRepository.flush();
        return mapToResponse(cruise);
    }

    // BE-R05b / C11, C17: Áp dụng thay đổi toàn diện với kiểm tra dữ liệu mới nhất
    @Transactional
    public CruiseApplyChangeResponse applyChange(UUID cruiseId, CruiseChangeImpactRequest request, UUID actorId) {
        Cruise cruise = cruiseRepository.findByIdForUpdate(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));

        if (request.getExpectedRevision() != null && !request.getExpectedRevision().equals(cruise.getRevision())) {
            throw new BusinessException(HttpStatus.CONFLICT, "STALE_REVISION",
                    String.format("Cruise revision đã thay đổi (hiện tại: %d, mong đợi: %d). Vui lòng tải lại trang trước khi áp dụng.",
                            cruise.getRevision(), request.getExpectedRevision()));
        }

        // 1. Kiểm tra lại dữ liệu mới nhất (không tin preview cũ)
        CruiseChangeImpactResponse impact = cruiseImpactAssessmentService.previewChangeImpact(cruiseId, request);
        if (!impact.isCanProceed()) {
            throw new BusinessException("CHANGE_BLOCKED",
                    "Không thể áp dụng thay đổi do có điều kiện bị vi phạm: " + String.join("; ", impact.getBlockers()));
        }

        long activeBookings = bookingRepository.countByCruiseCruiseId(cruiseId);
        if (activeBookings > 0) {
            if (request.getReason() == null || request.getReason().trim().length() < 5) {
                throw new BusinessException("REASON_REQUIRED_FOR_COMMITTED_CRUISE",
                        "Chuyến đi đã có khách đặt chỗ, bắt buộc phải cung cấp lý do thay đổi cụ thể");
            }
        }

        Instant beforeDeparture = cruise.getDepartureDatetime();
        UUID beforeShipId = cruise.getShip() != null ? cruise.getShip().getShipId() : null;

        // 2. Thay đổi tàu
        if (request.getNewShipId() != null && (cruise.getShip() == null || !cruise.getShip().getShipId().equals(request.getNewShipId()))) {
            Ship newShip = shipRepository.findById(request.getNewShipId())
                    .orElseThrow(() -> new ResourceNotFoundException("Ship", request.getNewShipId()));
            cruise.setShip(newShip);

            // Xử lý OnboardActivities có deck thuộc tàu cũ
            List<com.casms.activity.entity.OnboardActivity> activities = onboardActivityRepository.findByCruise_CruiseId(cruiseId);
            for (com.casms.activity.entity.OnboardActivity act : activities) {
                if (act.getDeck() != null && !act.getDeck().getShip().getShipId().equals(newShip.getShipId())) {
                    act.setDeck(null);
                    act.setCabin(null);
                    onboardActivityRepository.save(act);
                }
            }
        }

        // 3. Thay đổi cảng
        if (request.getNewDeparturePortId() != null) {
            Port newDepPort = portRepository.findById(request.getNewDeparturePortId())
                    .orElseThrow(() -> new ResourceNotFoundException("Departure Port", request.getNewDeparturePortId()));
            if (newDepPort.getStatus() != PortStatus.ACTIVE) {
                throw new BusinessException("PORT_NOT_ACTIVE", "Cảng khởi hành mới phải ở trạng thái ACTIVE");
            }
            if (cruise.getSourceTrip() != null) {
                CountryScopeValidator.validatePort(newDepPort, cruise.getSourceTrip(), "Cảng khởi hành mới");
            }
            cruise.setDeparturePort(newDepPort);
        }
        if (request.getNewReturnPortId() != null) {
            Port newRetPort = portRepository.findById(request.getNewReturnPortId())
                    .orElseThrow(() -> new ResourceNotFoundException("Return Port", request.getNewReturnPortId()));
            if (newRetPort.getStatus() != PortStatus.ACTIVE) {
                throw new BusinessException("PORT_NOT_ACTIVE", "Cảng về mới phải ở trạng thái ACTIVE");
            }
            if (cruise.getSourceTrip() != null) {
                CountryScopeValidator.validatePort(newRetPort, cruise.getSourceTrip(), "Cảng về mới");
            }
            cruise.setReturnPort(newRetPort);
        }

        // 4. Thay đổi thời gian và dời lịch
        if (request.getNewDepartureDatetime() != null) {
            Instant oldDep = cruise.getDepartureDatetime();
            Instant newDep = request.getNewDepartureDatetime();
            Instant newRet = request.getNewReturnDatetime() != null ? request.getNewReturnDatetime() :
                    (oldDep != null && cruise.getReturnDatetime() != null
                            ? newDep.plus(Duration.between(oldDep, cruise.getReturnDatetime()))
                            : newDep.plusSeconds(86400 * 3));

            cruise.setDepartureDatetime(newDep);
            cruise.setReturnDatetime(newRet);
            shiftScheduleAndPrograms(cruise, oldDep, newDep);
        }

        cruise = cruiseRepository.save(cruise);
        cruiseRepository.flush();

        // 5. Tự động sinh và lưu công việc C17 nếu có tác động cần xử lý
        List<CruiseImpactTaskResponse> generatedTasks = List.of();
        if (!impact.getGeneratedTasks().isEmpty()) {
            generatedTasks = cruiseImpactAssessmentService.generateAndPersistImpactTasks(cruiseId, impact);
        }

        return CruiseApplyChangeResponse.builder()
                .cruiseId(cruiseId)
                .cruiseCode(cruise.getCruiseCode())
                .revision(cruise.getRevision())
                .appliedAt(Instant.now())
                .beforeDepartureDatetime(beforeDeparture)
                .afterDepartureDatetime(cruise.getDepartureDatetime())
                .beforeShipId(beforeShipId)
                .afterShipId(cruise.getShip() != null ? cruise.getShip().getShipId() : null)
                .affectedBookingsCount(impact.getAffectedBookingsCount())
                .affectedPassengersCount(impact.getBookedPassengersCount())
                .affectedActivitiesCount(impact.getAffectedActivitiesCount())
                .affectedExcursionsCount(impact.getAffectedExcursionsCount())
                .generatedTasks(generatedTasks)
                .message("Áp dụng thay đổi hành trình và cập nhật các đối tượng phụ thuộc thành công")
                .build();
    }

    // Helper: Đồng bộ dời thời gian của ItineraryDay, CruiseStop, OnboardActivity, ShoreExcursion
    private void shiftScheduleAndPrograms(Cruise cruise, Instant oldDep, Instant newDep) {
        if (oldDep == null || newDep == null || oldDep.equals(newDep)) {
            return;
        }
        Duration shift = Duration.between(oldDep, newDep);
        UUID cruiseId = cruise.getCruiseId();

        Optional<Itinerary> itOpt = itineraryRepository.findByCruiseCruiseId(cruiseId);
        if (itOpt.isPresent()) {
            Itinerary it = itOpt.get();
            List<ItineraryDay> days = itineraryDayRepository.findByItineraryItineraryIdOrderByDayNumberAsc(it.getItineraryId());
            if (cruise.getDeparturePort() != null && cruise.getDeparturePort().getTimeZoneId() != null) {
                LocalDate newStartDate = newDep.atZone(PortTimeZoneHelper.resolveZone(cruise.getDeparturePort().getTimeZoneId())).toLocalDate();
                // Pass 1: Temporarily shift dates far into future to free up dates and avoid unique constraint conflict
                for (ItineraryDay day : days) {
                    day.setDate(day.getDate().plusYears(100));
                }
                itineraryDayRepository.saveAllAndFlush(days);

                // Pass 2: Set final shifted dates
                for (ItineraryDay day : days) {
                    day.setDate(newStartDate.plusDays(day.getDayNumber() - 1));
                }
                itineraryDayRepository.saveAllAndFlush(days);
            }

            List<CruiseStop> stops = cruiseStopRepository.findByItineraryItineraryIdOrderByStopOrderAsc(it.getItineraryId());
            for (CruiseStop stop : stops) {
                if (stop.getPlannedArrival() != null) {
                    stop.setPlannedArrival(stop.getPlannedArrival().plus(shift));
                }
                if (stop.getPlannedDeparture() != null) {
                    stop.setPlannedDeparture(stop.getPlannedDeparture().plus(shift));
                }
                cruiseStopRepository.save(stop);
            }
        }

        // Dời thời gian của OnboardActivity
        List<com.casms.activity.entity.OnboardActivity> activities = onboardActivityRepository.findByCruise_CruiseId(cruiseId);
        for (com.casms.activity.entity.OnboardActivity act : activities) {
            if (act.getStartTime() != null) act.setStartTime(act.getStartTime().plus(shift));
            if (act.getEndTime() != null) act.setEndTime(act.getEndTime().plus(shift));
            onboardActivityRepository.save(act);
        }

        // Dời thời gian của ShoreExcursion
        List<ShoreExcursion> excursions = shoreExcursionRepository.findByCruiseStop_Itinerary_Cruise_CruiseId(cruiseId);
        for (ShoreExcursion exc : excursions) {
            if (exc.getStartTime() != null) exc.setStartTime(exc.getStartTime().plus(shift));
            if (exc.getPlannedReturnTime() != null) exc.setPlannedReturnTime(exc.getPlannedReturnTime().plus(shift));
            shoreExcursionRepository.save(exc);
        }
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
