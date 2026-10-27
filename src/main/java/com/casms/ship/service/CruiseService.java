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
import com.casms.ship.dto.response.CruiseSellabilityResponse;
import com.casms.ship.dto.response.CabinTypeSellabilityItem;
import com.casms.ship.dto.response.PackageSellabilityItem;
import com.casms.ship.dto.response.CruiseSchedulePreviewResponse;
import com.casms.ship.dto.response.CruiseStaffResponse;
import com.casms.ship.dto.response.ReadinessIssueDetail;
import com.casms.ship.dto.response.ShipCompatibilityResponse;
import com.casms.ship.dto.response.ShipChangeImpactResponse;
import com.casms.ship.dto.response.CruisePreparationResponse;
import com.casms.ship.dto.response.CruisePreparationSectionResponse;
import com.casms.identity.enums.Permission;
import com.casms.ship.enums.ReadinessSeverity;
import com.casms.ship.enums.ReadinessStatus;
import com.casms.activity.entity.OnboardActivity;
import com.casms.activity.entity.ShoreExcursion;
import com.casms.activity.entity.Supplier;
import com.casms.activity.enums.SupplierStatus;
import com.casms.activity.enums.SupplierVerificationStatus;
import com.casms.activity.repository.OnboardActivityRepository;
import com.casms.activity.repository.ShoreExcursionRepository;
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
import com.casms.ship.entity.CruisePricingPolicy;
import com.casms.ship.enums.PricingPolicyStatus;
import com.casms.ship.dto.response.CruiseOpenSaleResponse;
import com.casms.ship.repository.CruisePricingPolicyRepository;
import com.casms.ship.repository.ItineraryDayRepository;
import com.casms.ship.repository.*;
import com.casms.ship.util.PortTimeZoneHelper;
import com.casms.trip.service.CountryScopeValidator;
import java.time.Duration;
import java.time.LocalDate;
import com.casms.booking.entity.BookingPassenger;
import com.casms.booking.entity.CabinAssignment;
import com.casms.booking.enums.BookingStatus;
import com.casms.checkin.entity.CruiseCheckin;
import com.casms.ship.entity.CruiseStaff;
import com.casms.ship.enums.OperationalIssueStatus;
import com.casms.trip.entity.Trip;
import com.casms.trip.enums.TripStatus;
import com.casms.trip.entity.TripScheduleVersion;
import com.casms.trip.enums.TripScheduleVersionStatus;
import com.casms.trip.repository.TripScheduleVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final OnboardActivityRepository onboardActivityRepository;
    private final ShoreExcursionRepository shoreExcursionRepository;
    private final CruiseTourPackageRepository cruiseTourPackageRepository;
    private final CruiseStopRepository cruiseStopRepository;
    private final CruiseRegulationRepository cruiseRegulationRepository;
    private final CruiseImpactTaskRepository cruiseImpactTaskRepository;
    private final TourPackageRepository tourPackageRepository;
    private final UserRepository userRepository;
    private final ItineraryDayRepository itineraryDayRepository;
    private final CruiseImpactAssessmentService cruiseImpactAssessmentService;
    private final CruisePreparationService cruisePreparationService;
    private final CruisePricingPolicyRepository cruisePricingPolicyRepository;
    private final OperationalIssueRepository operationalIssueRepository;
    private final CruiseAuditService cruiseAuditService;
    private final CruisePackageService cruisePackageService;

    @Transactional
    public CruiseResponse create(CruiseCreateRequest request) {
        String cruiseCode = businessCodeGenerator.next("CR");
        log.info("Creating new cruise with code: {}", cruiseCode);

        if (request.getDepartureDatetime() == null) {
            throw new BusinessException("INVALID_SCHEDULE", "Departure datetime is required");
        }

        // D01 / C15 / G01: Link cruise to an existing published TripScheduleVersion (optional)
        TripScheduleVersion sourceVersion = null;
        if (request.getSourceVersionId() != null) {
            sourceVersion = tripScheduleVersionRepository.findById(request.getSourceVersionId())
                    .orElseThrow(() -> new ResourceNotFoundException("TripScheduleVersion", request.getSourceVersionId()));
            if (sourceVersion.getStatus() != TripScheduleVersionStatus.PUBLISHED) {
                throw new BusinessException("VERSION_NOT_PUBLISHED",
                        "Source TripScheduleVersion must be in PUBLISHED status (current: " + sourceVersion.getStatus() + ")");
            }
            if (sourceVersion.getTrip() != null && sourceVersion.getTrip().getStatus() == TripStatus.ARCHIVED) {
                throw new BusinessException("TRIP_ARCHIVED", "Trip nguồn đã bị ARCHIVED hoặc không tồn tại.");
            }
            if (request.getExpectedSourceVersionRevision() != null && !Objects.equals(request.getExpectedSourceVersionRevision(), sourceVersion.getRevision())) {
                throw new BusinessException("STALE_PREVIEW",
                        String.format("Phiên bản nguồn đã thay đổi (revision hiện tại: %d, revision preview: %d). Vui lòng tải lại preview trước khi tạo.",
                                sourceVersion.getRevision(), request.getExpectedSourceVersionRevision()));
            }

            // G04 & BE-03: Khóa thời lượng và cảng từ lịch nguồn; tự tính toán returnDatetime từ departureDatetime và schedule projection
            CruiseSchedulePreviewResponse projection = scheduleMaterializationService.previewSchedule(
                    sourceVersion.getVersionId(), request.getDepartureDatetime());
            if (projection.getReturnDatetime() != null) {
                request.setReturnDatetime(projection.getReturnDatetime());
            }
            if (request.getDeparturePortId() == null && projection.getDeparturePortId() != null) {
                request.setDeparturePortId(projection.getDeparturePortId());
            }
            if (request.getReturnPortId() == null && projection.getReturnPortId() != null) {
                request.setReturnPortId(projection.getReturnPortId());
            }
        }

        if (request.getReturnDatetime() == null || !request.getReturnDatetime().isAfter(request.getDepartureDatetime())) {
            throw new BusinessException("INVALID_SCHEDULE", "Departure and return datetimes are required; return must be after departure");
        }

        if (request.getDeparturePortId() == null) {
            throw new BusinessException("DEPARTURE_PORT_REQUIRED", "Departure port ID is required");
        }

        if (request.getReturnPortId() == null) {
            throw new BusinessException("RETURN_PORT_REQUIRED", "Return port ID is required");
        }

        CruiseCruiseType cruiseType = request.getCruiseType();
        if (cruiseType == null) {
            long hours = Duration.between(request.getDepartureDatetime(), request.getReturnDatetime()).toHours();
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

        // BE-11: Validate departure + return port country scope when sourceVersion is present
        if (sourceVersion != null && sourceVersion.getTrip() != null) {
            CountryScopeValidator.validatePort(departurePort, sourceVersion.getTrip(), "Cảng khởi hành");
            CountryScopeValidator.validatePort(returnPort, sourceVersion.getTrip(), "Cảng về");
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
            cruise.setSourceVersionRevision(sourceVersion.getRevision());
            log.info("Cruise {} linked to Trip {} version {} (revision {})",
                    cruiseCode,
                    sourceVersion.getTrip().getTripCode(),
                    sourceVersion.getVersionNumber(),
                    sourceVersion.getRevision());
        }

        cruise = cruiseRepository.save(cruise);
        if (sourceVersion != null) {
            scheduleMaterializationService.materializeSchedule(cruise, sourceVersion);
        }
        return mapToResponse(cruise);
    }

    @Transactional(readOnly = true)
    public Page<CruiseResponse> list(CruiseStatus status, UUID shipId, Boolean unassigned, CruiseCruiseType cruiseType,
                                      UUID sourceTripId, UUID sourceVersionId, Pageable pageable) {
        Specification<Cruise> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (Boolean.TRUE.equals(unassigned)) {
                predicates.add(cb.isNull(root.get("ship")));
            } else if (Boolean.FALSE.equals(unassigned)) {
                predicates.add(cb.isNotNull(root.get("ship")));
            } else if (shipId != null) {
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
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return cruiseRepository.findAll(spec, pageable).map(this::mapToResponse);
    }

    // Overloads for backward compat
    @Transactional(readOnly = true)
    public Page<CruiseResponse> list(CruiseStatus status, UUID shipId, CruiseCruiseType cruiseType,
                                      UUID sourceTripId, UUID sourceVersionId, Pageable pageable) {
        return list(status, shipId, null, cruiseType, sourceTripId, sourceVersionId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<CruiseResponse> list(CruiseStatus status, UUID shipId, CruiseCruiseType cruiseType, Pageable pageable) {
        return list(status, shipId, null, cruiseType, null, null, pageable);
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
            throw new BusinessException(HttpStatus.CONFLICT, "CRUISE_NOT_DRAFT",
                    "Chuyến đi không ở trạng thái DRAFT (hiện tại: " + cruise.getStatus() + "). Các thay đổi sau khi mở bán bắt buộc phải thực hiện thông qua quy trình đánh giá tác động /apply-change");
        }

        // BE-18: Khóa mọi direct writes khi đã có booking; bắt buộc đi qua impact workflow /apply-change
        long bookedCount = bookingRepository.countByCruiseCruiseId(cruiseId);
        if (bookedCount > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "CANNOT_MODIFY_COMMITTED_CRUISE_DIRECTLY",
                    "Chuyến đi đã có " + bookedCount + " đặt chỗ. Bắt buộc phải thực hiện thay đổi thông qua quy trình đánh giá tác động /apply-change");
        }

        if (request.getExpectedRevision() != null && !request.getExpectedRevision().equals(cruise.getRevision())) {
            throw new BusinessException(HttpStatus.CONFLICT, "STALE_REVISION",
                    "Revision của Cruise không khớp (expected: " + request.getExpectedRevision() + ", current: " + cruise.getRevision() + ")");
        }

        Instant oldDep = cruise.getDepartureDatetime();
        Instant effectiveDeparture = request.getDepartureDatetime() != null ? request.getDepartureDatetime() : cruise.getDepartureDatetime();
        Instant effectiveReturn = request.getReturnDatetime() != null ? request.getReturnDatetime() : cruise.getReturnDatetime();

        // G04 & BE-03: Khóa thời lượng chuyến đi từ nguồn khi dời lịch khởi hành
        if (cruise.getSourceVersion() != null && oldDep != null && cruise.getReturnDatetime() != null) {
            long durationMillis = cruise.getReturnDatetime().toEpochMilli() - oldDep.toEpochMilli();
            effectiveReturn = effectiveDeparture.plusMillis(durationMillis);
        }

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

        Trip sourceTrip = cruise.getSourceTrip();

        if (request.getDeparturePortId() != null) {
            Port departurePort = portRepository.findById(request.getDeparturePortId())
                    .orElseThrow(() -> new ResourceNotFoundException("Departure Port", request.getDeparturePortId()));
            if (departurePort.getStatus() != PortStatus.ACTIVE) {
                throw new BusinessException("PORT_NOT_ACTIVE", "Departure port must be in ACTIVE status");
            }
            if (sourceTrip != null) {
                CountryScopeValidator.validatePort(departurePort, sourceTrip, "Cảng khởi hành");
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
                CountryScopeValidator.validatePort(returnPort, sourceTrip, "Cảng về");
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
        return assignShip(cruiseId, shipId, null);
    }

    @Transactional
    public CruiseResponse assignShip(UUID cruiseId, UUID shipId, UUID actorId) {
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

        if (ship.getStatus() == ShipStatus.MAINTENANCE) {
            throw new BusinessException("SHIP_IN_MAINTENANCE", "Không thể gán tàu: Tàu đang trong trạng thái bảo trì (MAINTENANCE)");
        } else if (ship.getStatus() == ShipStatus.INACTIVE) {
            throw new BusinessException("SHIP_INACTIVE", "Không thể gán tàu: Tàu đang trong trạng thái không hoạt động (INACTIVE)");
        } else if (ship.getStatus() != ShipStatus.ACTIVE) {
            throw new BusinessException("SHIP_NOT_ACTIVE", "Cannot assign ship because ship status is " + ship.getStatus());
        }

        if (cruise.getDepartureDatetime() == null || cruise.getReturnDatetime() == null) {
            throw new BusinessException("CRUISE_SCHEDULE_INCOMPLETE", "Chuyến đi chưa có đầy đủ thời gian khởi hành và kết thúc");
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

        cruiseAuditService.recordEvent(
                cruise,
                "SHIP_ASSIGNMENT",
                "EVT-ASSIGN-" + cruise.getCruiseCode() + "-" + shipId,
                actorId,
                "CRM",
                "CRUISE_ASSIGN_SHIP",
                "WHOLE_CRUISE",
                "Ship=UNASSIGNED",
                "Ship=" + ship.getShipName() + " (" + shipId + ")",
                "Gán tàu phục vụ chuyến đi",
                "Decks=" + decks + ", Cabins=" + cabins
        );

        return mapToResponse(cruise);
    }

    @Transactional
    public CruiseResponse changeAssignedShip(UUID cruiseId, UUID newShipId) {
        return changeAssignedShip(cruiseId, newShipId, null);
    }

    @Transactional
    public CruiseResponse changeAssignedShip(UUID cruiseId, UUID newShipId, UUID actorId) {
        Cruise cruise = cruiseRepository.findByIdForUpdate(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));

        if (cruise.getStatus() != CruiseStatus.DRAFT && cruise.getStatus() != CruiseStatus.PUBLISHED) {
            throw new BusinessException("INVALID_CRUISE_STATUS", "Cannot change assigned ship when cruise status is " + cruise.getStatus());
        }

        if (cruise.getShip() != null && cruise.getShip().getShipId().equals(newShipId)) {
            return mapToResponse(cruise);
        }

        // BE-18: Chặn đi tắt qua PUT /change-assigned-ship khi đã mở bán hoặc đã có booking; bắt buộc đi qua impact path
        long bookedCount = bookingRepository.countByCruiseCruiseId(cruiseId);
        if (cruise.getStatus() == CruiseStatus.PUBLISHED || bookedCount > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "CHANGE_SHIP_REQUIRES_IMPACT_WORKFLOW",
                    "Chuyến đi đã mở bán hoặc đã có đặt chỗ. Thay đổi tàu bắt buộc phải thực hiện thông qua quy trình đánh giá tác động (/preview-change-impact và /apply-change)");
        }

        Ship newShip = shipRepository.findByIdForUpdate(newShipId)
                .orElseThrow(() -> new ResourceNotFoundException("Ship", newShipId));

        if (newShip.getStatus() == ShipStatus.MAINTENANCE) {
            throw new BusinessException("SHIP_IN_MAINTENANCE", "Không thể đổi tàu: Tàu mới đang trong trạng thái bảo trì (MAINTENANCE)");
        } else if (newShip.getStatus() == ShipStatus.INACTIVE) {
            throw new BusinessException("SHIP_INACTIVE", "Không thể đổi tàu: Tàu mới đang trong trạng thái không hoạt động (INACTIVE)");
        } else if (newShip.getStatus() != ShipStatus.ACTIVE) {
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

        Ship oldShip = cruise.getShip();
        String oldShipDesc = oldShip != null ? "Ship=" + oldShip.getShipName() + " (" + oldShip.getShipId() + ")" : "Ship=UNASSIGNED";

        cruise.setShip(newShip);
        cruise = cruiseRepository.save(cruise);
        cruiseRepository.flush();
        cruisePreparationService.invalidateSignoffs(cruiseId, "Đổi tàu gán sang " + newShip.getShipName());

        cruiseAuditService.recordEvent(
                cruise,
                "SHIP_CHANGE",
                "EVT-CHANGE-SHIP-" + cruise.getCruiseCode() + "-" + newShipId,
                actorId,
                "CRM",
                "CRUISE_CHANGE_SHIP",
                "WHOLE_CRUISE",
                oldShipDesc,
                "Ship=" + newShip.getShipName() + " (" + newShipId + ")",
                "Thay đổi tàu phục vụ chuyến đi",
                "Decks=" + decks + ", Cabins=" + cabins + ", Capacity=" + newShip.getTotalCapacity()
        );

        return mapToResponse(cruise);
    }

    @Transactional
    public CruiseResponse unassignShip(UUID cruiseId) {
        return unassignShip(cruiseId, null);
    }

    @Transactional
    public CruiseResponse unassignShip(UUID cruiseId, UUID actorId) {
        Cruise cruise = cruiseRepository.findByIdForUpdate(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));

        if (cruise.getStatus() != CruiseStatus.DRAFT) {
            throw new BusinessException("CRUISE_NOT_DRAFT", "Cannot unassign ship unless cruise is in DRAFT status");
        }

        if (bookingRepository.existsByCruiseCruiseId(cruiseId)) {
            throw new BusinessException("CRUISE_HAS_BOOKINGS", "Cannot unassign ship because bookings already exist");
        }

        Ship oldShip = cruise.getShip();
        String oldShipDesc = oldShip != null ? "Ship=" + oldShip.getShipName() + " (" + oldShip.getShipId() + ")" : "Ship=UNASSIGNED";

        cruise.setShip(null);
        cruise = cruiseRepository.save(cruise);
        cruiseRepository.flush();
        cruisePreparationService.invalidateSignoffs(cruiseId, "Gỡ bỏ tàu được gán");
        log.info("Unassigned ship from cruise {} by user {}", cruiseId, actorId);

        cruiseAuditService.recordEvent(
                cruise,
                "SHIP_UNASSIGNMENT",
                "EVT-UNASSIGN-" + cruise.getCruiseCode() + "-" + System.currentTimeMillis(),
                actorId,
                "CRM",
                "CRUISE_UNASSIGN_SHIP",
                "WHOLE_CRUISE",
                oldShipDesc,
                "Ship=UNASSIGNED",
                "Hủy gán tàu khỏi chuyến đi",
                "Unassigned by " + actorId
        );

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
                    .ownerRole("CRUISE_MANAGER")
                    .allowedAction("Gán tàu cho chuyến đi qua /api/cruise/" + cruiseId + "/assign-ship")
                    .requiredPermission(Permission.CRUISE_ASSIGN_SHIP.name())
                    .remediationUrl("/api/cruise/" + cruiseId + "/assign-ship")
                    .remediationOperation("POST")
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
                        .ownerRole("SHIP_MANAGER")
                        .allowedAction("Kích hoạt tàu qua /api/ships/" + ship.getShipId() + "/status")
                        .requiredPermission(Permission.SHIP_UPDATE.name())
                        .remediationUrl("/api/ships/" + ship.getShipId() + "/status")
                        .remediationOperation("PUT")
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
                        .ownerRole("SHIP_MANAGER")
                        .allowedAction("Cấu hình boong và cabin cho con tàu trước khi vận hành")
                        .requiredPermission(Permission.DECK_CREATE.name())
                        .remediationUrl("/api/ships/" + ship.getShipId() + "/decks")
                        .remediationOperation("POST")
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
                            .ownerRole("CRUISE_MANAGER")
                            .allowedAction("Đổi sang tàu khác qua /api/cruise/" + cruiseId + "/change-assigned-ship")
                            .requiredPermission(Permission.CRUISE_CHANGE_SHIP.name())
                            .remediationUrl("/api/cruise/" + cruiseId + "/change-assigned-ship")
                            .remediationOperation("PUT")
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
                    .ownerRole("CRUISE_MANAGER")
                    .allowedAction("Cập nhật thời gian khởi hành/kết thúc qua /api/cruise/" + cruiseId + "/draft")
                    .requiredPermission(Permission.CRUISE_UPDATE_DRAFT.name())
                    .remediationUrl("/api/cruise/" + cruiseId + "/draft")
                    .remediationOperation("PATCH")
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
                    .ownerRole("OPERATIONS_MANAGER")
                    .allowedAction("Kích hoạt cảng khả dụng qua /api/ports")
                    .requiredPermission(Permission.PORT_ACTIVATE.name())
                    .remediationUrl("/api/ports")
                    .remediationOperation("PUT")
                    .build());
        }

        Optional<Itinerary> itineraryOpt = itineraryRepository.findByCruiseCruiseId(cruiseId);
        boolean itineraryConfigured = itineraryOpt.isPresent();
        boolean itineraryApproved = itineraryConfigured && (
                itineraryOpt.get().getStatus() == ItineraryStatus.APPROVED ||
                itineraryOpt.get().getStatus() == ItineraryStatus.OPEN_FOR_SALE ||
                itineraryOpt.get().getStatus() == ItineraryStatus.ACTIVE ||
                itineraryOpt.get().getStatus() == ItineraryStatus.SUSPENDED ||
                (Boolean.TRUE.equals(itineraryOpt.get().getApprovalInherited()) && !Boolean.TRUE.equals(itineraryOpt.get().getContentModifiedAfterMaterialization()))
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
                    .ownerRole("OPERATIONS_MANAGER")
                    .allowedAction("Tạo hoặc liên kết hải trình cho chuyến đi qua /api/itineraries")
                    .requiredPermission(Permission.ITINERARY_CREATE.name())
                    .remediationUrl("/api/itineraries")
                    .remediationOperation("POST")
                    .build());
        } else if (!itineraryApproved) {
            boolean modified = Boolean.TRUE.equals(itineraryOpt.get().getContentModifiedAfterMaterialization());
            String msg = modified
                    ? "Hải trình của chuyến đi đã bị chỉnh sửa nội dung sau khi khởi tạo từ nguồn và cần được thẩm định/phê duyệt lại"
                    : "Hải trình của chuyến đi chưa được phê duyệt hoặc mở bán (Trạng thái hiện tại: " + itineraryOpt.get().getStatus() + ")";
            issues.add(msg);
            blockers.add("ITINERARY_NOT_APPROVED");
            details.add(ReadinessIssueDetail.builder()
                    .code("ITINERARY_NOT_APPROVED")
                    .targetType("ITINERARY")
                    .targetId(itineraryOpt.get().getItineraryId())
                    .scope("CRUISE")
                    .severity(ReadinessSeverity.BLOCKER)
                    .message(msg)
                    .evidence("Itinerary.status=" + itineraryOpt.get().getStatus() + ", modified=" + modified)
                    .ownerRole("OPERATIONS_MANAGER")
                    .allowedAction("Phê duyệt hải trình sang APPROVED qua /api/itineraries/" + itineraryOpt.get().getItineraryId() + "/approve")
                    .requiredPermission(Permission.ITINERARY_APPROVE.name())
                    .remediationUrl("/api/itineraries/" + itineraryOpt.get().getItineraryId() + "/approve")
                    .remediationOperation("POST")
                    .build());
        }

        // BE-06 / BE-R03: Staff readiness verification
        List<CruiseStaff> staffList = cruiseStaffRepository != null
                ? cruiseStaffRepository.findByCruise_CruiseId(cruiseId)
                : List.of();
        List<CruiseStaff> activeStaff = staffList.stream()
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
                    .ownerRole("OPERATIONS_MANAGER")
                    .allowedAction("Phân công nhân sự có vai trò Thuyền trưởng qua /api/cruise/" + cruiseId + "/staff")
                    .requiredPermission(Permission.CRUISE_STAFF_ASSIGN.name())
                    .remediationUrl("/api/cruise/" + cruiseId + "/staff")
                    .remediationOperation("POST")
                    .build());
        }

        for (CruiseStaff s : activeStaff) {
            List<CruiseStaff> conflicts = cruiseStaffRepository.findConflictingAssignments(
                    s.getUser().getUserId(),
                    cruise.getDepartureDatetime(),
                    cruise.getReturnDatetime(),
                    cruiseId
            );
            if (!conflicts.isEmpty()) {
                for (CruiseStaff cs : conflicts) {
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
                            .ownerRole("OPERATIONS_MANAGER")
                            .allowedAction("Điều chuyển nhân sự sang ca khác hoặc phân công người thay thế qua /api/cruise/" + cruiseId + "/staff")
                            .requiredPermission(Permission.CRUISE_STAFF_ASSIGN.name())
                            .remediationUrl("/api/cruise/" + cruiseId + "/staff")
                            .remediationOperation("POST")
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
                                .ownerRole("CRUISE_MANAGER")
                                .allowedAction("Gán quy định vận hành cho chuyến đi qua /api/cruises/" + cruiseId + "/regulations")
                                .requiredPermission(Permission.CRUISE_REGULATION_ASSIGN.name())
                                .remediationUrl("/api/cruises/" + cruiseId + "/regulations")
                                .remediationOperation("POST")
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
        return start(cruiseId, null);
    }

    @Transactional
    public CruiseResponse start(UUID cruiseId, UUID actorId) {
        Cruise cruise = getCruiseById(cruiseId);

        if (cruise.getStatus() != CruiseStatus.PUBLISHED) {
            throw new BusinessException("CRUISE_NOT_PUBLISHED", "Only published cruises can be started");
        }

        if (cruise.getShip() == null || cruise.getShip().getStatus() != ShipStatus.ACTIVE) {
            throw new BusinessException("SHIP_NOT_ACTIVE", "Cannot start cruise: assigned ship is missing or not active");
        }

        // Rule 1: Không bấm start quá sớm trước giờ khởi hành (tối đa 2 giờ trước departureDatetime)
        if (cruise.getDepartureDatetime() != null && Instant.now().isBefore(cruise.getDepartureDatetime().minus(Duration.ofHours(2)))) {
            throw new BusinessException("CRUISE_START_TOO_EARLY",
                    "Cannot start cruise too early before scheduled departure (minimum 2 hours prior to " + cruise.getDepartureDatetime() + ")");
        }

        // BE-21: Tách gate trước Start khỏi open sale: Bắt buộc vượt qua Departure Gate
        CruiseReadinessResponse departureReadiness = validateDepartureReadiness(cruiseId);
        if (!departureReadiness.isReady()) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "CRUISE_NOT_DEPARTURE_READY",
                    "Chuyến đi chưa đủ điều kiện khởi hành thực tế (Departure Gate): " + String.join("; ", departureReadiness.getBlockers()));
        }

        Instant now = Instant.now();
        cruise.setStatus(CruiseStatus.IN_PROGRESS);
        cruise.setActualDepartureDatetime(now);
        if (actorId != null) {
            userRepository.findById(actorId).ifPresent(cruise::setDepartureActor);
        }
        cruise = cruiseRepository.save(cruise);

        // Ghi nhận actualDeparture cho điểm dừng đầu tiên nếu có
        itineraryRepository.findByCruiseCruiseId(cruiseId).ifPresent(it -> {
            List<CruiseStop> stops = cruiseStopRepository.findByItineraryItineraryIdOrderByStopOrderAsc(it.getItineraryId());
            if (!stops.isEmpty()) {
                CruiseStop firstStop = stops.get(0);
                if (firstStop.getActualDeparture() == null) {
                    firstStop.setActualDeparture(now);
                    cruiseStopRepository.save(firstStop);
                }
            }
            if (it.getStatus() == ItineraryStatus.APPROVED || it.getStatus() == ItineraryStatus.OPEN_FOR_SALE) {
                it.setStatus(ItineraryStatus.ACTIVE);
                itineraryRepository.save(it);
            }
        });

        log.info("Started cruise: {} by actor: {} at actual departure: {}", cruiseId, actorId, now);

        cruiseAuditService.recordEvent(
                cruise,
                "CRUISE_START",
                "EVT-START-" + cruise.getCruiseCode() + "-" + now.toEpochMilli(),
                actorId,
                "OPERATIONS_MANAGER",
                "CRUISE_START",
                "WHOLE_CRUISE",
                "Status=PUBLISHED",
                "Status=IN_PROGRESS, ActualDeparture=" + now,
                "Bắt đầu khởi hành chuyến đi sau khi kiểm tra Departure Gate thành công",
                "DepartureReadiness=READY"
        );

        return mapToResponse(cruise);
    }

    @Transactional
    public CruiseResponse complete(UUID cruiseId) {
        return complete(cruiseId, null, null);
    }

    @Transactional
    public CruiseResponse complete(UUID cruiseId, UUID actorId, String closureNotes) {
        Cruise cruise = getCruiseById(cruiseId);

        if (cruise.getStatus() != CruiseStatus.IN_PROGRESS) {
            throw new BusinessException("CRUISE_NOT_IN_PROGRESS", "Only in-progress cruises can be completed");
        }

        // Rule: Không complete trước khi kết thúc hành trình returnDatetime (trừ khi có lý do khẩn cấp)
        if (cruise.getReturnDatetime() != null && Instant.now().isBefore(cruise.getReturnDatetime()) && (closureNotes == null || closureNotes.isBlank())) {
            throw new BusinessException("CRUISE_RETURN_NOT_REACHED",
                    "Cannot complete cruise before scheduled return datetime: " + cruise.getReturnDatetime() + " without emergency closure notes");
        }

        // BE-21: Checklist đóng chuyến (Closure Checklist)
        long openIssues = operationalIssueRepository.countByCruise_CruiseIdAndStatus(cruiseId, OperationalIssueStatus.OPEN)
                + operationalIssueRepository.countByCruise_CruiseIdAndStatus(cruiseId, OperationalIssueStatus.IN_PROGRESS);
        if (openIssues > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "CRUISE_HAS_UNRESOLVED_ISSUES",
                    "Không thể hoàn tất đóng chuyến khi còn " + openIssues + " sự cố vận hành (OperationalIssue) chưa được giải quyết xong.");
        }

        Instant now = Instant.now();
        cruise.setStatus(CruiseStatus.COMPLETED);
        cruise.setActualReturnDatetime(now);
        if (actorId != null) {
            userRepository.findById(actorId).ifPresent(cruise::setCompletionActor);
        }
        if (closureNotes != null && !closureNotes.isBlank()) {
            cruise.setClosureNotes(closureNotes.trim());
        }
        cruise = cruiseRepository.save(cruise);

        // Đồng bộ hải trình sang COMPLETED
        itineraryRepository.findByCruiseCruiseId(cruiseId).ifPresent(it -> {
            it.setStatus(ItineraryStatus.COMPLETED);
            itineraryRepository.save(it);
        });

        // Giải phóng phòng đã gán
        List<BookingPassenger> passengers = bookingPassengerRepository.findActivePassengersByCruiseId(cruiseId);
        for (BookingPassenger bp : passengers) {
            cabinAssignmentRepository.findByBookingPassengerBookingPassengerIdAndValidToIsNull(bp.getBookingPassengerId())
                    .ifPresent(assignment -> {
                        assignment.setValidTo(now);
                        cabinAssignmentRepository.save(assignment);
                    });
        }

        log.info("Completed cruise: {} by actor: {} at actual return: {}", cruiseId, actorId, now);

        cruiseAuditService.recordEvent(
                cruise,
                "CRUISE_COMPLETE",
                "EVT-COMPLETE-" + cruise.getCruiseCode() + "-" + now.toEpochMilli(),
                actorId,
                "OPERATIONS_MANAGER",
                "CRUISE_COMPLETE",
                "WHOLE_CRUISE",
                "Status=IN_PROGRESS",
                "Status=COMPLETED, ActualReturn=" + now,
                closureNotes != null && !closureNotes.isBlank() ? closureNotes : "Hoàn tất chuyến đi và quyết toán đóng chuyến",
                "UnresolvedIssues=0"
        );

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
        res.setSourceVersionRevision(cruise.getSourceVersionRevision());
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
        res.setSaleScope(cruise.getSaleScope());
        res.setOpenedForSaleAt(cruise.getOpenedForSaleAt());
        if (cruise.getOpenedForSaleBy() != null) {
            res.setOpenedForSaleByUsername(cruise.getOpenedForSaleBy().getUsername());
        }
        res.setSaleEventIdentity(cruise.getSaleEventIdentity());
        res.setActualDepartureDatetime(cruise.getActualDepartureDatetime());
        res.setActualReturnDatetime(cruise.getActualReturnDatetime());
        if (cruise.getDepartureActor() != null) {
            res.setDepartureActorUsername(cruise.getDepartureActor().getUsername());
        }
        if (cruise.getCompletionActor() != null) {
            res.setCompletionActorUsername(cruise.getCompletionActor().getUsername());
        }
        res.setClosureNotes(cruise.getClosureNotes());
        res.setCreatedAt(cruise.getCreatedAt());
        res.setUpdatedAt(cruise.getUpdatedAt());
        return res;
    }

    @Transactional(readOnly = true)
    public CruiseManifestResponse getManifest(UUID cruiseId) {
        Cruise cruise = cruiseRepository.findById(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise not found with id: " + cruiseId));

        Ship ship = cruise.getShip();
        String shipName = ship != null ? ship.getShipName() : "UNASSIGNED";
        Integer capacity = ship != null ? ship.getTotalCapacity() : 0;
        long bookedCount = bookingPassengerRepository.countActivePassengersByCruiseId(cruiseId);

        List<BookingPassenger> activePassengers = bookingPassengerRepository.findActivePassengersByCruiseId(cruiseId);
        List<CabinAssignment> activeAssignments = cabinAssignmentRepository.findActiveAssignmentsByCruiseId(cruiseId);
        Map<UUID, CabinAssignment> assignmentByPassenger = activeAssignments.stream()
                .filter(a -> a.getBookingPassenger() != null)
                .collect(Collectors.toMap(a -> a.getBookingPassenger().getBookingPassengerId(), a -> a, (first, second) -> first));

        List<CruiseCheckin> checkins = checkinRepository.findByCruiseId(cruiseId);
        Map<UUID, CruiseCheckin> checkinByPassenger = checkins.stream()
                .filter(c -> c.getBookingPassenger() != null)
                .collect(Collectors.toMap(c -> c.getBookingPassenger().getBookingPassengerId(), c -> c, (first, second) -> first));

        List<CruiseManifestResponse.PassengerManifestItem> passengerItems = activePassengers.stream()
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

                    return CruiseManifestResponse.PassengerManifestItem.builder()
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

        return CruiseManifestResponse.builder()
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
        return null;
    }

    public BigDecimal getBaseCabinFareForCruise(UUID cruiseId, CabinType cabinType) {
        if (cruiseId != null) {
            Optional<CruisePricingPolicy> policyOpt = cruisePricingPolicyRepository.findByCruise_CruiseId(cruiseId);
            if (policyOpt.isPresent()) {
                return policyOpt.get().getBaseFareForCabinType(cabinType);
            }
        }
        return null;
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
        long blockedCabins = shipId != null
                ? (cabinRepository.countByShipShipIdAndStatus(shipId, CabinStatus.MAINTENANCE) + cabinRepository.countByShipShipIdAndStatus(shipId, CabinStatus.OUT_OF_SERVICE))
                : 0;

        long bookedPassengers = bookingPassengerRepository.countConfirmedPassengersByCruiseId(cruiseId);
        long heldPassengers = bookingPassengerRepository.countHeldPassengersByCruiseId(cruiseId);
        long remainingCapacity = Math.max(0, physicalCapacity - bookedPassengers - heldPassengers);
        long activePackages = cruiseTourPackageRepository.countByCruise_CruiseIdAndActiveTrue(cruiseId);

        List<CabinTypeInventoryItem> cabinTypes = new ArrayList<>();
        if (shipId != null) {
            for (CabinType type : CabinType.values()) {
                long totalCabinsForType = cabinRepository.countByShipShipIdAndCabinType(shipId, type);
                long availableCabinsForType = cabinRepository.countByShipShipIdAndCabinTypeAndStatus(shipId, type, CabinStatus.AVAILABLE);
                long blockedCabinsForType = cabinRepository.countByShipShipIdAndCabinTypeAndStatus(shipId, type, CabinStatus.MAINTENANCE)
                        + cabinRepository.countByShipShipIdAndCabinTypeAndStatus(shipId, type, CabinStatus.OUT_OF_SERVICE);
                long totalSlotsForType = cabinRepository.sumCapacityByShipIdAndCabinType(shipId, type);
                long bookedSlotsForType = cabinAssignmentRepository.countConfirmedAssignmentsByCabinTypeAndCruise(type, cruiseId);
                long heldSlotsForType = cabinAssignmentRepository.countHeldAssignmentsByCabinTypeAndCruise(type, cruiseId);
                long occupiedSlotsForType = bookedSlotsForType + heldSlotsForType;
                long remainingSlotsForType = Math.max(0, totalSlotsForType - occupiedSlotsForType);
                boolean canSell = remainingSlotsForType > 0 && availableCabinsForType > 0;
                long accessibleCount = cabinRepository.countByShipShipIdAndCabinTypeAndIsAccessibleTrue(shipId, type);
                long familyCount = cabinRepository.countByShipShipIdAndCabinTypeAndIsFamilyCabinTrue(shipId, type);
                long connectingCount = cabinRepository.countByShipShipIdAndCabinTypeAndHasConnectingDoorTrue(shipId, type);
                BigDecimal baseFare = getBaseCabinFareForCruise(cruiseId, type);

                cabinTypes.add(CabinTypeInventoryItem.builder()
                        .cabinType(type)
                        .totalCabins(totalCabinsForType)
                        .operationalCabins(availableCabinsForType)
                        .blockedCabins(blockedCabinsForType)
                        .totalSlots(totalSlotsForType)
                        .bookedSlots(bookedSlotsForType)
                        .heldSlots(heldSlotsForType)
                        .occupiedSlots(occupiedSlotsForType)
                        .remainingSlots(remainingSlotsForType)
                        .canSell(canSell)
                        .accessibleCabinsCount(accessibleCount)
                        .familyCabinsCount(familyCount)
                        .connectingCabinsCount(connectingCount)
                        .bedConfigurationSummary("Standard " + type.name())
                        .baseFare(baseFare)
                        .build());
            }
        }

        boolean canSellOverall = remainingCapacity > 0 && cabinTypes.stream().anyMatch(CabinTypeInventoryItem::isCanSell);

        return CruiseInventorySummaryResponse.builder()
                .cruiseId(cruiseId)
                .cruiseCode(cruise.getCruiseCode())
                .shipId(shipId)
                .shipName(shipName)
                .physicalCapacity(physicalCapacity)
                .totalCabins(totalCabins)
                .operationalCabins(operationalCabins)
                .blockedCabins(blockedCabins)
                .bookedPassengersCount(bookedPassengers)
                .heldPassengersCount(heldPassengers)
                .remainingCapacity(remainingCapacity)
                .canSell(canSellOverall)
                .activePackagesCount(activePackages)
                .policyVersion("v3.0-2026")
                .cabinTypeInventory(cabinTypes)
                .build();
    }

    @Transactional(readOnly = true, noRollbackFor = Exception.class)
    public CruiseSaleReadinessResponse validateSaleReadiness(UUID cruiseId) {
        return validateSaleReadiness(cruiseId, "WHOLE_CRUISE", null);
    }

    @Transactional(readOnly = true, noRollbackFor = Exception.class)
    public CruiseSaleReadinessResponse validateSaleReadiness(UUID cruiseId, String saleScope, Set<UUID> requestedPackageIds) {
        Cruise cruise = getCruiseById(cruiseId);
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        List<ReadinessIssueDetail> details = new ArrayList<>();
        boolean hasInfrastructureError = false;

        // 1. Evaluate operational readiness for visibility (BE-08: do NOT copy departure/operational blockers to sale gate)
        CruiseReadinessResponse opReadiness = validateOperationalReadiness(cruiseId);
        boolean operationalReady = opReadiness.isReady();
        warnings.addAll(opReadiness.getWarnings());
        if (opReadiness.getStatus() == ReadinessStatus.INDETERMINATE) {
            hasInfrastructureError = true;
        }

        // 2. Core Sale Gates: Ship assigned, active, and configured
        if (cruise.getShip() == null) {
            blockers.add("NO_SHIP_ASSIGNED");
            issues.add("Chưa gán tàu cho chuyến đi");
            details.add(ReadinessIssueDetail.builder()
                    .code("NO_SHIP_ASSIGNED")
                    .targetType("CRUISE")
                    .targetId(cruiseId)
                    .scope("WHOLE_CRUISE")
                    .severity(ReadinessSeverity.BLOCKER)
                    .message("Chưa gán tàu cho chuyến đi")
                    .evidence("Cruise.ship is null")
                    .ownerRole("CRUISE_MANAGER")
                    .allowedAction("Gán tàu cho chuyến đi qua /api/cruise/" + cruiseId + "/assign-ship")
                    .requiredPermission(Permission.CRUISE_ASSIGN_SHIP.name())
                    .remediationUrl("/api/cruise/" + cruiseId + "/assign-ship")
                    .remediationOperation("POST")
                    .build());
        } else {
            Ship ship = cruise.getShip();
            if (ship.getStatus() != ShipStatus.ACTIVE) {
                blockers.add("SHIP_NOT_ACTIVE");
                issues.add("Tàu được gán đang không ở trạng thái ACTIVE (hiện tại: " + ship.getStatus() + ")");
                details.add(ReadinessIssueDetail.builder()
                        .code("SHIP_NOT_ACTIVE")
                        .targetType("SHIP")
                        .targetId(ship.getShipId())
                        .scope("SHIP")
                        .severity(ReadinessSeverity.BLOCKER)
                        .message("Tàu được gán không ở trạng thái ACTIVE")
                        .evidence("Ship.status=" + ship.getStatus())
                        .ownerRole("SHIP_MANAGER")
                        .allowedAction("Kích hoạt tàu hoặc đổi tàu khác qua /cruises/{id}/preview-ship-change")
                        .requiredPermission(Permission.SHIP_UPDATE.name())
                        .remediationUrl("/api/ships/" + ship.getShipId() + "/status")
                        .remediationOperation("PUT")
                        .build());
            }

            long decks = deckRepository.countByShipShipId(ship.getShipId());
            long cabins = cabinRepository.countByShipShipId(ship.getShipId());
            if (decks == 0 || cabins == 0) {
                blockers.add("SHIP_NOT_CONFIGURED");
                issues.add("Tàu chưa được cấu hình boong hoặc phòng");
                details.add(ReadinessIssueDetail.builder()
                        .code("SHIP_NOT_CONFIGURED")
                        .targetType("SHIP")
                        .targetId(ship.getShipId())
                        .scope("SHIP")
                        .severity(ReadinessSeverity.BLOCKER)
                        .message("Tàu chưa được cấu hình boong hoặc phòng")
                        .evidence("Decks=" + decks + ", Cabins=" + cabins)
                        .ownerRole("SHIP_MANAGER")
                        .allowedAction("Cấu hình boong và cabin cho tàu")
                        .requiredPermission(Permission.DECK_CREATE.name())
                        .remediationUrl("/api/ships/" + ship.getShipId() + "/decks")
                        .remediationOperation("POST")
                        .build());
            }
        }

        // 3. Core Sale Gates: Ports active
        boolean portsActive = cruise.getDeparturePort() != null && cruise.getReturnPort() != null
                && cruise.getDeparturePort().getStatus() == PortStatus.ACTIVE
                && cruise.getReturnPort().getStatus() == PortStatus.ACTIVE;
        if (!portsActive) {
            blockers.add("PORTS_NOT_ACTIVE");
            issues.add("Cảng đi hoặc cảng đến chưa được thiết lập hoặc chưa kích hoạt");
            details.add(ReadinessIssueDetail.builder()
                    .code("PORTS_NOT_ACTIVE")
                    .targetType("CRUISE")
                    .targetId(cruiseId)
                    .scope("WHOLE_CRUISE")
                    .severity(ReadinessSeverity.BLOCKER)
                    .message("Cảng đi hoặc cảng về không ở trạng thái ACTIVE")
                    .evidence("DeparturePort=" + (cruise.getDeparturePort() != null ? cruise.getDeparturePort().getStatus() : "null")
                            + ", ReturnPort=" + (cruise.getReturnPort() != null ? cruise.getReturnPort().getStatus() : "null"))
                    .ownerRole("OPERATIONS_MANAGER")
                    .allowedAction("Kích hoạt cảng vận hành")
                    .requiredPermission(Permission.PORT_ACTIVATE.name())
                    .remediationUrl("/api/ports")
                    .remediationOperation("PUT")
                    .build());
        }

        // 4. Core Sale Gates: Approved Itinerary (hoặc kế thừa từ Trip published)
        Optional<Itinerary> itineraryOpt = itineraryRepository.findByCruiseCruiseId(cruiseId);
        boolean itineraryApproved = itineraryOpt.isPresent() && (
                itineraryOpt.get().getStatus() == ItineraryStatus.APPROVED
                || itineraryOpt.get().getStatus() == ItineraryStatus.OPEN_FOR_SALE
                || itineraryOpt.get().getStatus() == ItineraryStatus.ACTIVE
                || (Boolean.TRUE.equals(itineraryOpt.get().getApprovalInherited()) && itineraryOpt.get().getStatus() != ItineraryStatus.REJECTED)
        );
        if (!itineraryApproved) {
            blockers.add("ITINERARY_NOT_APPROVED");
            issues.add("Chuyến đi chưa có hải trình (Itinerary) được phê duyệt");
            details.add(ReadinessIssueDetail.builder()
                    .code("ITINERARY_NOT_APPROVED")
                    .targetType("CRUISE")
                    .targetId(cruiseId)
                    .scope("WHOLE_CRUISE")
                    .severity(ReadinessSeverity.BLOCKER)
                    .message("Hải trình chưa được phê duyệt hoặc kế thừa phê duyệt từ Trip")
                    .evidence("Itinerary status: " + (itineraryOpt.map(i -> i.getStatus().name()).orElse("NONE")))
                    .ownerRole("OPERATIONS_MANAGER")
                    .allowedAction("Phê duyệt hải trình chuyến đi")
                    .requiredPermission(Permission.ITINERARY_APPROVE.name())
                    .remediationUrl("/api/itineraries/" + (itineraryOpt.map(i -> i.getItineraryId().toString()).orElse(cruiseId.toString())) + "/approve")
                    .remediationOperation("POST")
                    .build());
        }

        // 5. Check departure time in the future (at least 24 hours)
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
                        .scope("WHOLE_CRUISE")
                        .severity(ReadinessSeverity.BLOCKER)
                        .message("Thời điểm khởi hành đã qua hoặc không xác định")
                        .evidence("Departure: " + cruise.getDepartureDatetime())
                        .ownerRole("CRUISE_MANAGER")
                        .allowedAction("Thiết lập lại lịch khởi hành trong tương lai qua /api/cruise/" + cruiseId + "/draft")
                        .requiredPermission(Permission.CRUISE_UPDATE_DRAFT.name())
                        .remediationUrl("/api/cruise/" + cruiseId + "/draft")
                        .remediationOperation("PATCH")
                        .build());
            } else {
                warnings.add("Thời điểm khởi hành còn dưới 24 giờ kể từ thời điểm kiểm tra");
                details.add(ReadinessIssueDetail.builder()
                        .code("DEPARTURE_NEAR")
                        .targetType("CRUISE")
                        .targetId(cruiseId)
                        .scope("WHOLE_CRUISE")
                        .severity(ReadinessSeverity.WARNING)
                        .message("Thời điểm khởi hành còn dưới 24 giờ")
                        .evidence("Departure: " + cruise.getDepartureDatetime())
                        .ownerRole("CRUISE_MANAGER")
                        .allowedAction("Đảm bảo mọi công tác chuẩn bị đã sẵn sàng")
                        .requiredPermission(Permission.CRUISE_READ.name())
                        .remediationUrl("/api/cruise/" + cruiseId)
                        .remediationOperation("GET")
                        .build());
            }
        }

        // 6. Check tour packages vs scope (BE-08 / BE-H04: scope-aware sale readiness)
        String effectiveScope = saleScope != null ? saleScope.trim().toUpperCase() : "WHOLE_CRUISE";
        List<CruiseTourPackage> assignedPackages = cruiseTourPackageRepository.findByCruise_CruiseId(cruiseId);
        List<CruiseTourPackage> targetPackages;
        boolean cabinOnlySalePermitted = false;

        if ("CABIN_ONLY".equals(effectiveScope)) {
            targetPackages = Collections.emptyList();
            cabinOnlySalePermitted = true;
            warnings.add("Áp dụng phạm vi mở bán CABIN_ONLY: Bán vé phòng lẻ, không yêu cầu gói dịch vụ");
        } else if ("SELECTED_PACKAGES".equals(effectiveScope)) {
            Set<UUID> targetIds = requestedPackageIds != null ? requestedPackageIds : Collections.emptySet();
            targetPackages = assignedPackages.stream()
                    .filter(ctp -> ctp.getTourPackage() != null && targetIds.contains(ctp.getTourPackage().getTourPackageId()))
                    .toList();
            Set<UUID> foundIds = targetPackages.stream()
                    .map(ctp -> ctp.getTourPackage().getTourPackageId())
                    .collect(Collectors.toSet());
            for (UUID reqId : targetIds) {
                if (!foundIds.contains(reqId)) {
                    blockers.add("PACKAGE_NOT_ASSIGNED_TO_CRUISE");
                    issues.add("Gói tour ID " + reqId + " chưa được gán cho chuyến đi này");
                }
            }
        } else {
            // WHOLE_CRUISE
            targetPackages = assignedPackages.stream()
                    .filter(CruiseTourPackage::isActive)
                    .toList();
            if (targetPackages.isEmpty()) {
                cabinOnlySalePermitted = true;
                warnings.add("Chưa có gói dịch vụ đính kèm, cho phép mở bán vé phòng đơn lẻ (cabin-only sale)");
            }
        }

        long activePackages = targetPackages.size();
        boolean packagesConfigured = activePackages > 0 || "CABIN_ONLY".equals(effectiveScope);

        // Pricing check on targetPackages (packages not in targetPackages are NOT evaluated and do not block)
        boolean packagesPricingConfigured = true;
        StringBuilder packageDigest = new StringBuilder();
        for (CruiseTourPackage ctp : targetPackages) {
            TourPackage pkg = ctp.getTourPackage();
            if (pkg == null) continue;
            packageDigest.append(pkg.getTourPackageId()).append(":")
                    .append(pkg.getPrice()).append(":")
                    .append(pkg.getCurrencyCode()).append(";");
            boolean isOptional = !ctp.isMandatory();
            boolean blockSale = !isOptional || !cabinOnlySalePermitted;

            if (pkg.getPrice() == null || pkg.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                String msg = "Gói dịch vụ " + pkg.getPackageName() + " (" + pkg.getPackageCode() + ") chưa được cấu hình giá bán hợp lệ";
                if (blockSale) {
                    packagesPricingConfigured = false;
                    blockers.add("PACKAGE_PRICE_MISSING");
                    issues.add(msg);
                } else {
                    warnings.add("Gói dịch vụ tự chọn " + pkg.getPackageName() + " (" + pkg.getPackageCode() + ") chưa được cấu hình giá bán hợp lệ (chỉ khóa bán gói này, không chặn bán cabin)");
                }
                details.add(ReadinessIssueDetail.builder()
                        .code("PACKAGE_PRICE_MISSING")
                        .targetType("PACKAGE")
                        .targetId(pkg.getTourPackageId())
                        .scope(isOptional ? "PACKAGE" : "WHOLE_CRUISE")
                        .severity(blockSale ? ReadinessSeverity.BLOCKER : ReadinessSeverity.WARNING)
                        .message(msg)
                        .evidence("TourPackage.price is " + pkg.getPrice())
                        .ownerRole("CRUISE_MANAGER")
                        .allowedAction("Cấu hình giá bán cho gói tour")
                        .requiredPermission(Permission.TOUR_PACKAGE_MANAGE.name())
                        .remediationUrl("/api/servicepackage/packages/" + pkg.getTourPackageId())
                        .remediationOperation("PUT")
                        .build());
            }
            if (pkg.getCurrencyCode() == null || pkg.getCurrencyCode().isBlank()) {
                String msg = "Gói dịch vụ " + pkg.getPackageName() + " (" + pkg.getPackageCode() + ") chưa được cấu hình đơn vị tiền tệ";
                if (blockSale) {
                    packagesPricingConfigured = false;
                    blockers.add("PACKAGE_CURRENCY_MISSING");
                    issues.add(msg);
                } else {
                    warnings.add("Gói dịch vụ tự chọn " + pkg.getPackageName() + " (" + pkg.getPackageCode() + ") chưa được cấu hình đơn vị tiền tệ (chỉ khóa bán gói này, không chặn bán cabin)");
                }
                details.add(ReadinessIssueDetail.builder()
                        .code("PACKAGE_CURRENCY_MISSING")
                        .targetType("PACKAGE")
                        .targetId(pkg.getTourPackageId())
                        .scope(isOptional ? "PACKAGE" : "WHOLE_CRUISE")
                        .severity(blockSale ? ReadinessSeverity.BLOCKER : ReadinessSeverity.WARNING)
                        .message(msg)
                        .evidence("TourPackage.currencyCode is null or empty")
                        .ownerRole("CRUISE_MANAGER")
                        .allowedAction("Thiết lập mã tiền tệ (USD/VND) cho gói dịch vụ")
                        .requiredPermission(Permission.TOUR_PACKAGE_MANAGE.name())
                        .remediationUrl("/api/servicepackage/packages/" + pkg.getTourPackageId())
                        .remediationOperation("PUT")
                        .build());
            }
        }

        // 6b. Core Sale Gate: Authoritative Approved Pricing Policy (BE-H01)
        Optional<CruisePricingPolicy> policyOpt = cruisePricingPolicyRepository.findByCruise_CruiseId(cruiseId);
        String pricingPolicyVersion = null;
        boolean policyConfiguredAndApproved = true;

        if (policyOpt.isEmpty()) {
            policyConfiguredAndApproved = false;
            blockers.add("PRICING_POLICY_MISSING");
            issues.add("Chuyến đi chưa được cấu hình chính sách giá (Pricing Policy)");
            details.add(ReadinessIssueDetail.builder()
                    .code("PRICING_POLICY_MISSING")
                    .targetType("CRUISE")
                    .targetId(cruiseId)
                    .scope("WHOLE_CRUISE")
                    .severity(ReadinessSeverity.BLOCKER)
                    .message("Chuyến đi chưa có chính sách giá thương mại được cấu hình")
                    .evidence("CruisePricingPolicy is absent")
                    .ownerRole("CRUISE_MANAGER")
                    .allowedAction("Thiết lập chính sách giá cho chuyến đi qua /api/cruises/" + cruiseId + "/pricing-policy")
                    .requiredPermission(Permission.CRUISE_UPDATE_DRAFT.name())
                    .remediationUrl("/api/cruises/" + cruiseId + "/pricing-policy")
                    .remediationOperation("PUT")
                    .build());
        } else {
            CruisePricingPolicy policy = policyOpt.get();
            pricingPolicyVersion = policy.getPolicyVersion();

            if (policy.getStatus() != PricingPolicyStatus.APPROVED) {
                policyConfiguredAndApproved = false;
                blockers.add("PRICING_POLICY_NOT_APPROVED");
                issues.add("Chính sách giá của chuyến đi chưa được phê duyệt (Trạng thái hiện tại: " + policy.getStatus() + ")");
                details.add(ReadinessIssueDetail.builder()
                        .code("PRICING_POLICY_NOT_APPROVED")
                        .targetType("PRICING_POLICY")
                        .targetId(policy.getPricingPolicyId())
                        .scope("WHOLE_CRUISE")
                        .severity(ReadinessSeverity.BLOCKER)
                        .message("Chính sách giá của chuyến đi chưa được phê duyệt để mở bán")
                        .evidence("PricingPolicy status=" + policy.getStatus() + ", version=" + policy.getPolicyVersion())
                        .ownerRole("OPERATIONS_MANAGER")
                        .allowedAction("Phê duyệt chính sách giá chuyến đi qua /api/cruises/" + cruiseId + "/pricing-policy/approve")
                        .requiredPermission(Permission.CRUISE_OPEN_SALE.name())
                        .remediationUrl("/api/cruises/" + cruiseId + "/pricing-policy/approve")
                        .remediationOperation("POST")
                        .build());
            } else {
                // Verify cabin base fares for all cabin types present on the assigned ship (0 is valid, null is blocker)
                if (cruise.getShip() != null) {
                    UUID shipId = cruise.getShip().getShipId();
                    for (CabinType type : CabinType.values()) {
                        long count = cabinRepository.countByShipShipIdAndCabinType(shipId, type);
                        if (count > 0 && policy.getBaseFareForCabinType(type) == null) {
                            policyConfiguredAndApproved = false;
                            blockers.add("PRICING_POLICY_FARE_MISSING");
                            issues.add("Chính sách giá chưa định giá cho hạng phòng " + type + " hiện có trên tàu");
                            details.add(ReadinessIssueDetail.builder()
                                    .code("PRICING_POLICY_FARE_MISSING")
                                    .targetType("PRICING_POLICY")
                                    .targetId(policy.getPricingPolicyId())
                                    .scope("WHOLE_CRUISE")
                                    .severity(ReadinessSeverity.BLOCKER)
                                    .message("Hạng phòng " + type + " trên tàu chưa được định giá trong chính sách giá")
                                    .evidence("CabinType=" + type + ", baseFare=null")
                                    .ownerRole("CRUISE_MANAGER")
                                    .allowedAction("Cấu hình giá cho hạng phòng qua /api/cruises/" + cruiseId + "/pricing-policy")
                                    .requiredPermission(Permission.CRUISE_UPDATE_DRAFT.name())
                                    .remediationUrl("/api/cruises/" + cruiseId + "/pricing-policy")
                                    .remediationOperation("PUT")
                                    .build());
                        }
                    }
                }
            }
        }

        boolean pricingConfigured = packagesPricingConfigured && policyConfiguredAndApproved;

        // 7. Check inventory (capacity availability)
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
                    .scope("WHOLE_CRUISE")
                    .severity(ReadinessSeverity.BLOCKER)
                    .message("Không còn chỗ trống khả dụng trên tàu")
                    .evidence("PhysicalCapacity=" + physicalCapacity + ", Booked=" + booked + ", Held=" + held + ", Remaining=" + remaining)
                    .ownerRole("CRUISE_MANAGER")
                    .allowedAction("Tạm dừng mở bán hoặc tăng tải trọng tàu nếu có thể")
                    .requiredPermission(Permission.CRUISE_STOP_SALE.name())
                    .remediationUrl("/api/cruise/" + cruiseId + "/stop-sale")
                    .remediationOperation("POST")
                    .build());
        }

        // 8. Shore Excursions: Phân định Bắt buộc vs Tự chọn (Optional tour không chặn bán cabin)
        List<ShoreExcursion> excursions = shoreExcursionRepository.findByCruiseStop_Itinerary_Cruise_CruiseId(cruiseId);
        StringBuilder supplierDigest = new StringBuilder();
        for (ShoreExcursion exc : excursions) {
            boolean isOptional = !exc.isMandatory();
            Supplier supplier = exc.getSupplier();
            supplierDigest.append(exc.getShoreExcursionId()).append(":");

            if (exc.getMaximumCapacity() == null || exc.getMaximumCapacity() <= 0) {
                if (!isOptional) {
                    blockers.add("EXCURSION_CAPACITY_INVALID");
                    issues.add("Tour bờ bắt buộc " + exc.getExcursionName() + " chưa cấu hình sức chứa hợp lệ");
                } else {
                    warnings.add("Tour bờ tự chọn " + exc.getExcursionName() + " chưa cấu hình sức chứa hợp lệ (chỉ khóa bán tour)");
                }
                details.add(ReadinessIssueDetail.builder()
                        .code("EXCURSION_CAPACITY_INVALID")
                        .targetType("EXCURSION")
                        .targetId(exc.getShoreExcursionId())
                        .scope(isOptional ? "EXCURSION" : "WHOLE_CRUISE")
                        .severity(isOptional ? ReadinessSeverity.WARNING : ReadinessSeverity.BLOCKER)
                        .message("Tour bờ " + exc.getExcursionName() + " chưa cấu hình sức chứa (maximumCapacity <= 0)")
                        .evidence("maximumCapacity=" + exc.getMaximumCapacity())
                        .ownerRole("CRUISE_MANAGER")
                        .allowedAction("Cấu hình sức chứa cho tour bờ")
                        .requiredPermission(Permission.EXCURSION_UPDATE.name())
                        .remediationUrl("/api/shore-excursions/" + exc.getShoreExcursionId())
                        .remediationOperation("PUT")
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
                        .scope(isOptional ? "EXCURSION" : "WHOLE_CRUISE")
                        .severity(isOptional ? ReadinessSeverity.WARNING : ReadinessSeverity.BLOCKER)
                        .message("Thời gian kết thúc tour bờ trước thời gian bắt đầu")
                        .evidence("StartTime=" + exc.getStartTime() + ", ReturnTime=" + exc.getPlannedReturnTime())
                        .ownerRole("CRUISE_MANAGER")
                        .allowedAction("Điều chỉnh khung giờ tour bờ")
                        .requiredPermission(Permission.EXCURSION_UPDATE.name())
                        .remediationUrl("/api/shore-excursions/" + exc.getShoreExcursionId())
                        .remediationOperation("PUT")
                        .build());
            }

            if (supplier == null) {
                supplierDigest.append("NOSUP;");
                if (!isOptional) {
                    blockers.add("MANDATORY_EXCURSION_SUPPLIER_MISSING");
                    issues.add("Tour bờ bắt buộc " + exc.getExcursionName() + " chưa được phân công nhà cung cấp");
                } else {
                    warnings.add("Tour bờ tự chọn " + exc.getExcursionName() + " chưa có nhà cung cấp (chỉ khóa bán tour này, không chặn bán cabin)");
                }
                details.add(ReadinessIssueDetail.builder()
                        .code("SUPPLIER_MISSING")
                        .targetType("EXCURSION")
                        .targetId(exc.getShoreExcursionId())
                        .scope(isOptional ? "EXCURSION" : "WHOLE_CRUISE")
                        .severity(isOptional ? ReadinessSeverity.WARNING : ReadinessSeverity.BLOCKER)
                        .message("Tour bờ " + exc.getExcursionName() + " chưa được phân công nhà cung cấp")
                        .evidence("Supplier is null on excursion " + exc.getExcursionCode())
                        .ownerRole("CRUISE_MANAGER")
                        .allowedAction("Phân công nhà cung cấp cho tour bờ")
                        .requiredPermission(Permission.EXCURSION_UPDATE.name())
                        .remediationUrl("/api/shore-excursions/" + exc.getShoreExcursionId())
                        .remediationOperation("PUT")
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
                            .scope(isOptional ? "EXCURSION" : "WHOLE_CRUISE")
                            .severity(isOptional ? ReadinessSeverity.WARNING : ReadinessSeverity.BLOCKER)
                            .message("Nhà cung cấp " + supplier.getSupplierName() + " chưa ở trạng thái ACTIVE và VERIFIED (hiện tại: " + statusLabel + ")")
                            .evidence("Supplier status=" + supplier.getStatus() + ", verification=" + supplier.getVerificationStatus())
                            .ownerRole("OPERATIONS_MANAGER")
                            .allowedAction("Xác minh hoặc kích hoạt nhà cung cấp")
                            .requiredPermission(Permission.SUPPLIER_VERIFY.name())
                            .remediationUrl("/api/suppliers/" + supplier.getSupplierId() + "/verify")
                            .remediationOperation("POST")
                            .build());
                }
            }

            // BE-17: Kiểm tra điểm tập trung (meeting point) của tour bờ
            if (exc.getMeetingPoint() == null || exc.getMeetingPoint().isBlank()) {
                if (!isOptional) {
                    blockers.add("MANDATORY_EXCURSION_MEETING_POINT_MISSING");
                    issues.add("Tour bờ bắt buộc " + exc.getExcursionName() + " chưa cấu hình điểm tập trung (meeting point)");
                } else {
                    warnings.add("Tour bờ tự chọn " + exc.getExcursionName() + " chưa cấu hình điểm tập trung (tạm khóa bán tour)");
                }
                details.add(ReadinessIssueDetail.builder()
                        .code("EXCURSION_MEETING_POINT_MISSING")
                        .targetType("EXCURSION")
                        .targetId(exc.getShoreExcursionId())
                        .scope(isOptional ? "EXCURSION" : "WHOLE_CRUISE")
                        .severity(isOptional ? ReadinessSeverity.WARNING : ReadinessSeverity.BLOCKER)
                        .message("Tour bờ " + exc.getExcursionName() + " thiếu điểm tập trung đón khách")
                        .evidence("meetingPoint is null or blank")
                        .ownerRole("CRUISE_MANAGER")
                        .allowedAction("Bổ sung điểm đón/tập trung cho tour bờ")
                        .requiredPermission(Permission.EXCURSION_UPDATE.name())
                        .remediationUrl("/api/shore-excursions/" + exc.getShoreExcursionId())
                        .remediationOperation("PUT")
                        .build());
            }

            // BE-17: Kiểm tra thời gian tour trong PortCall cộng tender/return buffer
            if (exc.getCruiseStop() != null) {
                CruiseStop stop = exc.getCruiseStop();
                Instant portArrival = stop.getPlannedArrival();
                Instant portDeparture = stop.getPlannedDeparture();
                boolean outsidePortCall = (portArrival != null && exc.getStartTime() != null && exc.getStartTime().isBefore(portArrival))
                        || (portDeparture != null && exc.getPlannedReturnTime() != null && exc.getPlannedReturnTime().isAfter(portDeparture));
                if (outsidePortCall) {
                    if (!isOptional) {
                        blockers.add("MANDATORY_EXCURSION_OUTSIDE_PORT_CALL");
                        issues.add("Tour bờ bắt buộc " + exc.getExcursionName() + " nằm ngoài khung giờ tàu cập cảng");
                    } else {
                        warnings.add("Tour bờ tự chọn " + exc.getExcursionName() + " nằm ngoài khung giờ tàu cập cảng (tạm khóa bán tour)");
                    }
                    details.add(ReadinessIssueDetail.builder()
                            .code("EXCURSION_OUTSIDE_PORT_CALL")
                            .targetType("EXCURSION")
                            .targetId(exc.getShoreExcursionId())
                            .scope(isOptional ? "EXCURSION" : "WHOLE_CRUISE")
                            .severity(isOptional ? ReadinessSeverity.WARNING : ReadinessSeverity.BLOCKER)
                            .message("Thời gian tour bờ nằm ngoài khung giờ tàu cập cảng (" + portArrival + " - " + portDeparture + ")")
                            .evidence("Tour: " + exc.getStartTime() + " to " + exc.getPlannedReturnTime() + "; Port: " + portArrival + " to " + portDeparture)
                            .ownerRole("CRUISE_MANAGER")
                            .allowedAction("Điều chỉnh khung giờ tour bờ phù hợp lịch cập cảng")
                            .requiredPermission(Permission.EXCURSION_UPDATE.name())
                            .remediationUrl("/api/shore-excursions/" + exc.getShoreExcursionId())
                            .remediationOperation("PUT")
                            .build());
                }
            }
        }

        // 9. BE-17: Onboard Activities - Kiểm tra deck/facility thuộc tàu được gán
        List<OnboardActivity> activities = onboardActivityRepository.findByCruise_CruiseId(cruiseId);
        for (OnboardActivity act : activities) {
            if (cruise.getShip() != null && act.getDeck() != null && act.getDeck().getShip() != null) {
                if (!act.getDeck().getShip().getShipId().equals(cruise.getShip().getShipId())) {
                    warnings.add("Hoạt động " + act.getActivityName() + " có tầng boong không thuộc về tàu được gán");
                    details.add(ReadinessIssueDetail.builder()
                            .code("ACTIVITY_DECK_MISMATCH")
                            .targetType("ACTIVITY")
                            .targetId(act.getOnboardActivityId())
                            .scope("ACTIVITY")
                            .severity(ReadinessSeverity.WARNING)
                            .message("Tầng boong nơi tổ chức hoạt động không thuộc về tàu " + cruise.getShip().getShipName())
                            .evidence("Activity deck ship=" + act.getDeck().getShip().getShipId() + ", Cruise ship=" + cruise.getShip().getShipId())
                            .ownerRole("CRUISE_MANAGER")
                            .allowedAction("Cập nhật lại vị trí tầng/phòng tổ chức hoạt động")
                            .requiredPermission(Permission.ACTIVITY_UPDATE.name())
                            .remediationUrl("/api/onboard-activities/" + act.getOnboardActivityId())
                            .remediationOperation("PUT")
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

        // Composite fingerprint including external dependencies (prices, pricing policy, supplier status, ports, capacity)
        String rawState = String.format("CRUISE:%s:REV:%s:STAT:%s:DEP:%s:RET:%s:PACKAGES:%s:PRICE_OK:%s:POLICY_VER:%s:INV:%d:SUP:%s:BLOCKERS:%d:WARNS:%d:INFRA:%s",
                cruiseId, cruise.getRevision(), cruise.getStatus(),
                cruise.getDeparturePort() != null ? cruise.getDeparturePort().getStatus() : "null",
                cruise.getReturnPort() != null ? cruise.getReturnPort().getStatus() : "null",
                packageDigest.toString(), pricingConfigured, pricingPolicyVersion != null ? pricingPolicyVersion : "NONE",
                remaining, supplierDigest.toString(),
                blockers.size(), warnings.size(), hasInfrastructureError);
        String fingerprint = generateFingerprint(rawState);

        return CruiseSaleReadinessResponse.builder()
                .cruiseId(cruiseId)
                .gateType("SALE_GATE")
                .policyVersion("v3.0-2026")
                .ready(ready)
                .status(status)
                .fingerprint(fingerprint)
                .validUntil(Instant.now().plus(15, ChronoUnit.MINUTES))
                .priceVersion(pricingPolicyVersion != null ? pricingPolicyVersion : generateFingerprint(packageDigest.toString()))
                .shipVersion(cruise.getShip() != null ? cruise.getShip().getShipId() + ":" + cruise.getShip().getStatus() : "NONE")
                .supplierVersion(generateFingerprint(supplierDigest.toString()))
                .details(details)
                .operationalReady(operationalReady)
                .packagesConfigured(packagesConfigured)
                .cabinOnlySalePermitted(cabinOnlySalePermitted)
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

    /**
     * BE-08: Đánh giá tính sẵn sàng khởi hành (Departure Readiness Gate).
     * Kiểm tra toàn diện bao gồm:
     * 1. Điều kiện vận hành & hàng hải (Operational Readiness).
     * 2. Phân công thuyền trưởng và tối thiểu nhân sự phục vụ.
     * 3. Tất cả 5 phân hệ chuẩn bị liên phòng ban (BE-07) phải được ký duyệt (CONFIRMED).
     */
    @Transactional(readOnly = true, noRollbackFor = Exception.class)
    public CruiseReadinessResponse validateDepartureReadiness(UUID cruiseId) {
        CruiseReadinessResponse response = validateOperationalReadiness(cruiseId);
        response.setGateType("DEPARTURE_GATE");
        response.setPolicyVersion("v3.0-2026");

        // BE-07 / BE-08: Kiểm tra ký duyệt phân hệ chuẩn bị liên phòng ban
        CruisePreparationResponse prep = cruisePreparationService.getPreparationDashboard(cruiseId);
        response.setPreparationSectionsConfirmed(prep.isAllConfirmed());
        long unconfirmedCount = prep.getSections().stream()
                .filter(s -> !"CONFIRMED".equalsIgnoreCase(s.getStatus()))
                .count();
        response.setUnconfirmedPreparationSectionsCount((int) unconfirmedCount);

        if (!prep.isAllConfirmed()) {
            response.getBlockers().add("PREPARATION_SECTIONS_NOT_CONFIRMED");
            response.getIssues().add("Còn " + unconfirmedCount + " phân hệ chuẩn bị liên phòng ban chưa được ký duyệt");
            for (CruisePreparationSectionResponse sec : prep.getSections()) {
                if (!"CONFIRMED".equalsIgnoreCase(sec.getStatus())) {
                    response.getDetails().add(ReadinessIssueDetail.builder()
                            .code("PREPARATION_SECTION_UNCONFIRMED")
                            .targetType("PREPARATION_SECTION")
                            .targetId(cruiseId)
                            .scope("WHOLE_CRUISE")
                            .severity(ReadinessSeverity.BLOCKER)
                            .message("Phân hệ chuẩn bị " + sec.getSectionName() + " (" + sec.getSectionCode() + ") chưa được ký duyệt (Trạng thái: " + sec.getStatus() + ")")
                            .evidence("Section " + sec.getSectionCode() + " status is " + sec.getStatus())
                            .ownerRole(sec.getOwnerRole())
                            .allowedAction("Hoàn thành công tác chuẩn bị và ký duyệt phân hệ")
                            .requiredPermission("CRUISE_PREPARATION_CONFIRM")
                            .remediationUrl("/api/cruises/" + cruiseId + "/preparation/" + sec.getSectionCode() + "/confirm")
                            .remediationOperation("POST")
                            .build());
                }
            }
            response.setReady(false);
            response.setStatus(ReadinessStatus.NOT_READY);
        }

        return response;
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
        return openSale(cruiseId, request, null);
    }

    @Transactional
    public CruiseResponse openSale(UUID cruiseId, CruiseOpenSaleRequest request, UUID actorId) {
        Cruise cruise = cruiseRepository.findByIdForUpdate(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));

        if (request == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "REQUEST_REQUIRED", "Yêu cầu mở bán không được để trống");
        }

        if (request.getExpectedRevision() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "EXPECTED_REVISION_REQUIRED", "expectedRevision là bắt buộc khi mở bán");
        }

        if (!request.getExpectedRevision().equals(cruise.getRevision())) {
            throw new BusinessException(HttpStatus.CONFLICT, "STALE_REVISION",
                    "Revision của Cruise không khớp (expected: " + request.getExpectedRevision() + ", current: " + cruise.getRevision() + ")");
        }

        if (request.getSaleScope() == null || request.getSaleScope().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SALE_SCOPE_REQUIRED", "saleScope là bắt buộc (CABIN_ONLY, SELECTED_PACKAGES, WHOLE_CRUISE)");
        }

        String scope = request.getSaleScope().trim().toUpperCase();
        if (!Set.of("CABIN_ONLY", "SELECTED_PACKAGES", "WHOLE_CRUISE").contains(scope)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_SALE_SCOPE",
                    "Phạm vi mở bán không hợp lệ (hỗ trợ CABIN_ONLY, SELECTED_PACKAGES, WHOLE_CRUISE): " + scope);
        }

        if (request.getFingerprint() == null || request.getFingerprint().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "FINGERPRINT_REQUIRED", "fingerprint là bắt buộc khi mở bán");
        }

        Set<UUID> requestedPackageIds = Collections.emptySet();
        if ("SELECTED_PACKAGES".equals(scope)) {
            if (request.getPackageIds() == null || request.getPackageIds().isEmpty()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "EMPTY_PACKAGE_SELECTION",
                        "Danh sách packageIds không được để trống khi scope là SELECTED_PACKAGES");
            }
            requestedPackageIds = new HashSet<>(request.getPackageIds());
        }

        Optional<Itinerary> itineraryOpt = itineraryRepository.findByCruiseCruiseId(cruiseId);
        boolean alreadyOpen = cruise.getStatus() == CruiseStatus.PUBLISHED
                && itineraryOpt.isPresent() && itineraryOpt.get().getStatus() == ItineraryStatus.OPEN_FOR_SALE;

        String eventIdentity = (request.getEventIdentity() != null && !request.getEventIdentity().isBlank())
                ? request.getEventIdentity()
                : "EVT-SALE-" + cruise.getCruiseCode() + "-" + System.currentTimeMillis();

        if (alreadyOpen && eventIdentity.equals(cruise.getSaleEventIdentity())) {
            if (scope.equalsIgnoreCase(cruise.getSaleScope())) {
                return mapToResponse(cruise); // Idempotent on same payload
            }
            throw new BusinessException(HttpStatus.CONFLICT, "EVENT_IDENTITY_CONFLICT",
                    "Event identity " + eventIdentity + " đã được xử lý với phạm vi mở bán khác (" + cruise.getSaleScope() + ")");
        }

        // Validation gate: validateSaleReadiness scope-aware (revalidate in transaction, do NOT trust client)
        CruiseSaleReadinessResponse saleReadiness = validateSaleReadiness(cruiseId, scope, requestedPackageIds);
        if (!saleReadiness.isReady()) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "CRUISE_NOT_SALE_READY",
                    "Chuyến đi chưa đủ điều kiện mở bán: " + String.join("; ", saleReadiness.getIssues()));
        }

        // Validate fingerprint against scope-evaluated fingerprint
        if (!request.getFingerprint().equals(saleReadiness.getFingerprint())) {
            throw new BusinessException(HttpStatus.CONFLICT, "READINESS_FINGERPRINT_STALE",
                    "Dấu vân tay dữ liệu (fingerprint) đã thay đổi kể từ lần đánh giá readiness, vui lòng kiểm tra lại");
        }

        List<CruiseTourPackage> allAssigned = cruiseTourPackageRepository.findByCruise_CruiseId(cruiseId);

        if ("CABIN_ONLY".equals(scope)) {
            // Cabin-only: deactivate all packages for this cruise
            for (CruiseTourPackage ctp : allAssigned) {
                if (ctp.isActive()) {
                    ctp.setActive(false);
                    cruiseTourPackageRepository.save(ctp);
                }
            }
        } else if ("SELECTED_PACKAGES".equals(scope)) {
            for (UUID pkgId : requestedPackageIds) {
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

            for (CruiseTourPackage ctp : allAssigned) {
                boolean shouldBeActive = requestedPackageIds.contains(ctp.getTourPackage().getTourPackageId());
                if (ctp.isActive() != shouldBeActive) {
                    ctp.setActive(shouldBeActive);
                    cruiseTourPackageRepository.save(ctp);
                }
            }
        } else {
            // WHOLE_CRUISE: ensure all assigned active tour packages are activated
            for (CruiseTourPackage ctp : allAssigned) {
                if (ctp.getTourPackage() != null && ctp.getTourPackage().getStatus() == TourPackageStatus.ACTIVE) {
                    if (!ctp.isActive()) {
                        ctp.setActive(true);
                        cruiseTourPackageRepository.save(ctp);
                    }
                }
            }
        }

        cruise.setStatus(CruiseStatus.PUBLISHED);
        cruise.setSaleScope(scope);
        cruise.setOpenedForSaleAt(Instant.now());
        if (actorId != null) {
            userRepository.findById(actorId).ifPresent(cruise::setOpenedForSaleBy);
        }
        cruise.setSaleEventIdentity(eventIdentity);
        cruise = cruiseRepository.save(cruise);

        // Update Itinerary status to OPEN_FOR_SALE
        if (itineraryOpt.isPresent()) {
            Itinerary itinerary = itineraryOpt.get();
            itinerary.setStatus(ItineraryStatus.OPEN_FOR_SALE);
            itineraryRepository.save(itinerary);
        }

        cruiseRepository.flush();
        log.info("Opened sale for cruise {} (scope: {}, revision: {})", cruise.getCruiseCode(), scope, cruise.getRevision());

        cruiseAuditService.recordEvent(
                cruise,
                "OPEN_SALE",
                eventIdentity,
                actorId,
                "OPERATIONS_MANAGER",
                "CRUISE_OPEN_SALE",
                scope,
                "Status=DRAFT, Itinerary=DRAFT",
                "Status=PUBLISHED, Itinerary=OPEN_FOR_SALE, Scope=" + scope,
                "Mở bán chuyến đi sau khi đạt kiểm tra Sale Readiness",
                "Fingerprint=" + saleReadiness.getFingerprint() + ", Revision=" + cruise.getRevision()
        );

        return mapToResponse(cruise);
    }

    @Transactional
    public CruiseOpenSaleResponse openSaleDetailed(UUID cruiseId, CruiseOpenSaleRequest request, UUID actorId) {
        CruiseResponse res = openSale(cruiseId, request, actorId);
        long activePkgs = cruiseTourPackageRepository.countByCruise_CruiseIdAndActiveTrue(cruiseId);
        Set<UUID> pkgIds = (request != null && request.getPackageIds() != null)
                ? new HashSet<>(request.getPackageIds()) : Collections.emptySet();
        CruiseSaleReadinessResponse readiness = validateSaleReadiness(cruiseId, res.getSaleScope(), pkgIds);
        return CruiseOpenSaleResponse.builder()
                .cruiseId(res.getCruiseId())
                .cruiseCode(res.getCruiseCode())
                .cruiseStatus(res.getStatus())
                .itineraryStatus(ItineraryStatus.OPEN_FOR_SALE)
                .saleScope(res.getSaleScope())
                .activePackagesCount(activePkgs)
                .openedBy(actorId)
                .openedAt(res.getOpenedForSaleAt())
                .fingerprint(readiness.getFingerprint())
                .revision(res.getRevision())
                .eventIdentity(res.getSaleEventIdentity())
                .idempotentRetry(false)
                .build();
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

        cruiseAuditService.recordEvent(
                cruise,
                "STOP_SALE",
                eventIdentity,
                actorId,
                "OPERATIONS_MANAGER",
                "CRUISE_STOP_SALE",
                scope,
                "Status=PUBLISHED, Itinerary=OPEN_FOR_SALE",
                "Status=PUBLISHED, Itinerary=SUSPENDED, StoppedScope=" + scope,
                request != null && request.getReason() != null ? request.getReason() : "Tạm dừng mở bán do vi phạm điều kiện vận hành",
                request != null ? request.getEvidence() : null
        );

        return mapToResponse(cruise);
    }

    // Overload for backward compatibility
    @Transactional
    public CruiseResponse stopSale(UUID cruiseId, CruiseStopSaleRequest request) {
        return stopSale(cruiseId, request, null);
    }

    @Transactional
    public CruiseResponse resumeSale(UUID cruiseId, CruiseResumeSaleRequest request) {
        return resumeSale(cruiseId, request, null);
    }

    @Transactional
    public CruiseResponse resumeSale(UUID cruiseId, CruiseResumeSaleRequest request, UUID actorId) {
        Cruise cruise = cruiseRepository.findByIdForUpdate(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));

        if (request != null && request.getExpectedRevision() != null && !request.getExpectedRevision().equals(cruise.getRevision())) {
            throw new BusinessException(HttpStatus.CONFLICT, "STALE_REVISION",
                    "Revision của Cruise không khớp (expected: " + request.getExpectedRevision() + ", current: " + cruise.getRevision() + ")");
        }

        if (request != null && request.getPackageId() != null) {
            // BE-H14: Không bật package nếu whole Cruise đang stop
            if (cruise.getStoppedAt() != null && (cruise.getStopScope() == null || "WHOLE_CRUISE".equalsIgnoreCase(cruise.getStopScope()) || "CRUISE".equalsIgnoreCase(cruise.getStopScope()))) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "CRUISE_STOPPED",
                        "Không thể mở bán lại gói tour khi toàn bộ chuyến đi đang bị tạm dừng bán (whole cruise is stopped)");
            }

            // Targeted resume: revalidate package sale readiness before activating (BE-14)
            CruiseTourPackage ctp = cruiseTourPackageRepository
                    .findByCruise_CruiseIdAndTourPackage_TourPackageId(cruiseId, request.getPackageId())
                    .orElseThrow(() -> new ResourceNotFoundException("CruiseTourPackage", request.getPackageId()));

            // Idempotent: nếu đã active thì không thực hiện lại thao tác phụ
            if (ctp.isActive()) {
                log.info("Package {} is already active on cruise {}, skipping duplicate resume", request.getPackageId(), cruise.getCruiseCode());
                return mapToResponse(cruise);
            }

            cruisePackageService.validatePackageSaleScopeGate(cruise, ctp);

            ctp.setActive(true);
            cruiseTourPackageRepository.save(ctp);
            log.info("Resumed sale for package {} on cruise {}", request.getPackageId(), cruise.getCruiseCode());
        } else {
            // Re-validate sale readiness before resuming whole cruise (BE-14)
            CruiseSaleReadinessResponse saleReadiness = validateSaleReadiness(cruiseId);
            if (!saleReadiness.isReady()) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "CRUISE_NOT_SALE_READY",
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
            if (actorId != null) {
                userRepository.findById(actorId).ifPresent(cruise::setOpenedForSaleBy);
            }
            cruise.setOpenedForSaleAt(Instant.now());
            log.info("Resumed sale for entire cruise {}", cruise.getCruiseCode());
        }

        cruise = cruiseRepository.save(cruise);
        cruiseRepository.flush();

        cruiseAuditService.recordEvent(
                cruise,
                "RESUME_SALE",
                "EVT-RESUME-" + cruise.getCruiseCode() + "-" + System.currentTimeMillis(),
                actorId,
                "OPERATIONS_MANAGER",
                "CRUISE_RESUME_SALE",
                (request != null && request.getPackageId() != null) ? "PACKAGE" : "WHOLE_CRUISE",
                "Status=PUBLISHED, Itinerary=SUSPENDED",
                "Status=PUBLISHED, Itinerary=OPEN_FOR_SALE",
                request != null && request.getReason() != null ? request.getReason() : "Tiếp tục mở bán sau khi kiểm tra lại sale readiness",
                request != null ? request.getEvidence() : null
        );

        return mapToResponse(cruise);
    }

    /**
     * BE-15: Public Sellability Read Model - Nguồn sự thật duy nhất (Single Source of Truth)
     * Đánh giá trạng thái mở bán authoritative cho Chuyến đi, từng hạng phòng và gói dịch vụ.
     * Hoàn toàn không để lộ dữ liệu nhân sự, manifest hành khách hay nguồn nhạy cảm.
     */
    @Transactional(readOnly = true)
    public CruiseSellabilityResponse getPublicSellability(UUID cruiseId) {
        Cruise cruise = cruiseRepository.findById(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));

        boolean isPublished = cruise.getStatus() == CruiseStatus.PUBLISHED;
        Itinerary itinerary = itineraryRepository.findByCruiseCruiseId(cruiseId).orElse(null);
        boolean isItineraryOpen = itinerary != null && itinerary.getStatus() == ItineraryStatus.OPEN_FOR_SALE;
        boolean isSuspended = itinerary != null && itinerary.getStatus() == ItineraryStatus.SUSPENDED;
        boolean isStopped = cruise.getStoppedAt() != null || isSuspended;
        boolean isDepartureFuture = cruise.getDepartureDatetime() != null && cruise.getDepartureDatetime().isAfter(Instant.now());

        int physicalCapacity = cruise.getShip() != null ? cruise.getShip().getTotalCapacity() : 0;
        UUID shipId = cruise.getShip() != null ? cruise.getShip().getShipId() : null;
        long totalAvailableCabinCapacity = (shipId != null)
                ? Optional.ofNullable(cabinRepository.sumAvailableCapacityByShipId(shipId)).orElse(0L)
                : 0L;
        long booked = bookingPassengerRepository.countConfirmedPassengersByCruiseId(cruiseId);
        long held = bookingPassengerRepository.countHeldPassengersByCruiseId(cruiseId);
        long sellableInventory = Math.max(0, totalAvailableCabinCapacity - booked - held);
        boolean hasInventory = sellableInventory > 0 && totalAvailableCabinCapacity > 0;

        Optional<CruisePricingPolicy> policyOpt = cruisePricingPolicyRepository.findByCruise_CruiseId(cruiseId)
                .filter(p -> p.getStatus() == PricingPolicyStatus.APPROVED);

        boolean sellable = isPublished && isItineraryOpen && !isStopped && isDepartureFuture && hasInventory && policyOpt.isPresent();

        String saleState;
        if (cruise.getStatus() == CruiseStatus.DRAFT) {
            saleState = "DRAFT";
        } else if (cruise.getStatus() == CruiseStatus.CANCELLED) {
            saleState = "CANCELLED";
        } else if (!isDepartureFuture) {
            saleState = "DEPARTED";
        } else if (isStopped) {
            saleState = "STOPPED";
        } else if (!isItineraryOpen || policyOpt.isEmpty()) {
            saleState = "NOT_YET_OPEN";
        } else if (!hasInventory || totalAvailableCabinCapacity == 0) {
            saleState = "SOLD_OUT";
        } else {
            saleState = "OPEN_FOR_SALE";
        }

        CruisePricingPolicy policy = policyOpt.orElse(null);
        String currency = policy != null ? policy.getCurrency() : "USD";
        String policyVersion = policy != null ? policy.getPolicyVersion() : null;

        BigDecimal depositPercentage = policy != null ? policy.getDepositPercentage() : BigDecimal.valueOf(0.30);
        if (depositPercentage != null && depositPercentage.compareTo(BigDecimal.ONE) > 0) {
            depositPercentage = depositPercentage.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        }

        List<CabinTypeSellabilityItem> cabinItems = new ArrayList<>();
        for (CabinType type : CabinType.values()) {
            long totalSlots = (shipId != null) ? Optional.ofNullable(cabinRepository.sumAvailableCapacityByShipIdAndCabinType(shipId, type)).orElse(0L) : 0L;
            long activeAssignments = cabinAssignmentRepository.countActiveAssignmentsByCabinTypeAndCruise(type, cruiseId);
            long availableSlots = Math.max(0, totalSlots - activeAssignments);
            BigDecimal baseFare = policy != null ? policy.getBaseFareForCabinType(type) : null;
            boolean cabinSellable = sellable && availableSlots > 0 && baseFare != null;
            cabinItems.add(CabinTypeSellabilityItem.builder()
                    .cabinType(type)
                    .baseFare(baseFare)
                    .currency(currency)
                    .totalSlots(totalSlots)
                    .availableSlots(availableSlots)
                    .sellable(cabinSellable)
                    .build());
        }

        List<PackageSellabilityItem> packageItems = new ArrayList<>();
        List<CruiseTourPackage> ctpList = cruiseTourPackageRepository.findByCruise_CruiseId(cruiseId);
        for (CruiseTourPackage ctp : ctpList) {
            TourPackage pkg = ctp.getTourPackage();
            if (pkg == null) continue;
            boolean packageActive = ctp.isActive() && pkg.getStatus() == TourPackageStatus.ACTIVE;
            packageItems.add(PackageSellabilityItem.builder()
                    .tourPackageId(pkg.getTourPackageId())
                    .packageCode(pkg.getPackageCode())
                    .packageName(pkg.getPackageName())
                    .price(pkg.getPrice())
                    .currency(pkg.getCurrencyCode())
                    .mandatory(ctp.isMandatory())
                    .entitlementType(ctp.getEntitlementType())
                    .sellable(sellable && packageActive)
                    .build());
        }

        return CruiseSellabilityResponse.builder()
                .cruiseId(cruise.getCruiseId())
                .cruiseCode(cruise.getCruiseCode())
                .cruiseName(cruise.getCruiseName())
                .departureDatetime(cruise.getDepartureDatetime())
                .returnDatetime(cruise.getReturnDatetime())
                .sellable(sellable)
                .saleState(saleState)
                .stopReason(cruise.getStopReason())
                .stoppedAt(cruise.getStoppedAt())
                .physicalCapacity(physicalCapacity)
                .totalCapacity(physicalCapacity)
                .sellableInventory(sellableInventory)
                .remainingCapacity(sellableInventory)
                .pricingPolicyVersion(policyVersion)
                .currency(currency)
                .depositPercentage(depositPercentage)
                .finalPaymentCutoffDays(policy != null ? policy.getFullPaymentCutoffDays() : 0)
                .cabinTypes(cabinItems)
                .availablePackages(packageItems)
                .build();
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

            // Xử lý CabinAssignments có cabin thuộc tàu cũ: đóng assignment để bảo đảm không để cabin/booking trỏ tàu cũ
            List<CabinAssignment> activeAssignments = cabinAssignmentRepository.findActiveAssignmentsByCruiseId(cruiseId);
            Instant now = Instant.now();
            for (CabinAssignment ca : activeAssignments) {
                if (ca.getCabin() != null && ca.getCabin().getDeck() != null && ca.getCabin().getDeck().getShip() != null
                        && !newShip.getShipId().equals(ca.getCabin().getDeck().getShip().getShipId())) {
                    ca.setValidTo(now);
                    ca.setChangeReason("Đổi tàu chuyến đi sang " + newShip.getShipName() + "; cần tái phân bổ cabin trên tàu mới");
                    cabinAssignmentRepository.save(ca);
                }
            }

            // Xử lý OnboardActivities có deck thuộc tàu cũ
            List<OnboardActivity> activities = onboardActivityRepository.findByCruise_CruiseId(cruiseId);
            for (OnboardActivity act : activities) {
                if (act.getDeck() != null && act.getDeck().getShip() != null
                        && !act.getDeck().getShip().getShipId().equals(newShip.getShipId())) {
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
        cruisePreparationService.invalidateSignoffs(cruiseId, "Áp dụng thay đổi hành trình/tàu/cảng qua /apply-change");

        // 5. Tự động sinh và lưu công việc C17 nếu có tác động cần xử lý
        List<CruiseImpactTaskResponse> generatedTasks = List.of();
        if (!impact.getGeneratedTasks().isEmpty()) {
            generatedTasks = cruiseImpactAssessmentService.generateAndPersistImpactTasks(cruiseId, impact);
        }

        cruiseAuditService.recordEvent(
                cruise,
                "CRUISE_APPLY_CHANGE",
                request.getEventIdentity() != null ? request.getEventIdentity() : "EVT-CHANGE-" + cruise.getCruiseCode() + "-" + System.currentTimeMillis(),
                actorId,
                "OPERATIONS_MANAGER",
                "CRUISE_UPDATE_DRAFT",
                "WHOLE_CRUISE",
                "Departure=" + beforeDeparture + ", Ship=" + beforeShipId,
                "Departure=" + cruise.getDepartureDatetime() + ", Ship=" + (cruise.getShip() != null ? cruise.getShip().getShipId() : null),
                request.getReason() != null ? request.getReason() : "Áp dụng thay đổi cấu hình/hành trình/tàu",
                "AffectedBookings=" + impact.getAffectedBookingsCount() + ", Tasks=" + (generatedTasks != null ? generatedTasks.size() : 0)
        );

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
                LocalDate newStartDate = newDep.atZone(PortTimeZoneHelper.SHIP_TIME_ZONE).toLocalDate();
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
        List<OnboardActivity> activities = onboardActivityRepository.findByCruise_CruiseId(cruiseId);
        for (OnboardActivity act : activities) {
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
        long confirmedBookings = bookingRepository.countByCruiseCruiseIdAndBookingStatus(cruiseId, BookingStatus.Confirmed);
        long draftBookings = bookingRepository.countByCruiseCruiseIdAndBookingStatus(cruiseId, BookingStatus.Draft);
        long cancelledBookings = bookingRepository.countByCruiseCruiseIdAndBookingStatus(cruiseId, BookingStatus.Cancelled);

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
