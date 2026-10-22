package com.casms.ship.service;

import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.booking.repository.BookingRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.common.service.BusinessCodeGenerator;
import com.casms.ship.dto.request.CruiseCreateRequest;
import com.casms.ship.dto.request.CruiseUpdateDraftRequest;
import com.casms.ship.dto.response.CruiseCapacityValidationResponse;
import com.casms.ship.dto.response.CruiseReadinessResponse;
import com.casms.ship.dto.response.CruiseResponse;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.Port;
import com.casms.ship.entity.Ship;
import com.casms.ship.enums.CruiseCruiseType;
import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.enums.PortStatus;
import com.casms.ship.enums.ShipStatus;
import com.casms.ship.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    private final BusinessCodeGenerator businessCodeGenerator;

    @Transactional
    public CruiseResponse create(CruiseCreateRequest request) {
        String cruiseCode = businessCodeGenerator.next("CR");
        log.info("Creating new cruise with code: {}", cruiseCode);

        if (request.getDepartureDatetime() == null || request.getReturnDatetime() == null
                || !request.getReturnDatetime().isAfter(request.getDepartureDatetime())) {
            throw new BusinessException("INVALID_SCHEDULE", "Departure and return datetimes are required; return must be after departure");
        }

        if (request.getCruiseType() == null) {
            long hours = java.time.Duration.between(request.getDepartureDatetime(), request.getReturnDatetime()).toHours();
            request.setCruiseType(hours <= 24 ? CruiseCruiseType.ONE_DAY : CruiseCruiseType.MULTI_DAYS);
        }

        Port departurePort = portRepository.findById(request.getDeparturePortId())
                .orElseThrow(() -> new ResourceNotFoundException("Departure Port", request.getDeparturePortId()));

        Port returnPort = portRepository.findById(request.getReturnPortId())
                .orElseThrow(() -> new ResourceNotFoundException("Return Port", request.getReturnPortId()));

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

        Cruise cruise = new Cruise();
        cruise.setCruiseCode(cruiseCode);
        cruise.setCruiseName(request.getCruiseName());
        cruise.setShip(ship);
        cruise.setCruiseType(request.getCruiseType());
        cruise.setDepartureDatetime(request.getDepartureDatetime());
        cruise.setReturnDatetime(request.getReturnDatetime());
        cruise.setDeparturePort(departurePort);
        cruise.setReturnPort(returnPort);
        cruise.setStatus(CruiseStatus.DRAFT);

        cruise = cruiseRepository.save(cruise);
        return mapToResponse(cruise);
    }

    @Transactional(readOnly = true)
    public Page<CruiseResponse> list(CruiseStatus status, UUID shipId, CruiseCruiseType cruiseType, Pageable pageable) {
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
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return cruiseRepository.findAll(spec, pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public CruiseResponse detail(UUID cruiseId) {
        Cruise cruise = getCruiseById(cruiseId);
        return mapToResponse(cruise);
    }

    @Transactional
    public CruiseResponse updateDraft(UUID cruiseId, CruiseUpdateDraftRequest request) {
        Cruise cruise = getCruiseById(cruiseId);

        if (cruise.getStatus() != CruiseStatus.DRAFT) {
            throw new BusinessException("CRUISE_NOT_DRAFT", "Only draft cruises can be modified directly");
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

        if (request.getDeparturePortId() != null) {
            Port departurePort = portRepository.findById(request.getDeparturePortId())
                    .orElseThrow(() -> new ResourceNotFoundException("Departure Port", request.getDeparturePortId()));
            cruise.setDeparturePort(departurePort);
        }

        if (request.getReturnPortId() != null) {
            Port returnPort = portRepository.findById(request.getReturnPortId())
                    .orElseThrow(() -> new ResourceNotFoundException("Return Port", request.getReturnPortId()));
            cruise.setReturnPort(returnPort);
        }

        cruise = cruiseRepository.save(cruise);
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

        cruiseRepository.delete(cruise);
        log.info("Deleted draft cruise: {}", cruiseId);
    }

    @Transactional
    public CruiseResponse assignShip(UUID cruiseId, UUID shipId) {
        Cruise cruise = getCruiseById(cruiseId);

        if (cruise.getStatus() != CruiseStatus.DRAFT && cruise.getStatus() != CruiseStatus.PUBLISHED) {
            throw new BusinessException("INVALID_CRUISE_STATUS", "Cannot assign ship when cruise status is " + cruise.getStatus());
        }

        if (cruise.getShip() != null) {
            if (cruise.getShip().getShipId().equals(shipId)) {
                return mapToResponse(cruise);
            }
            throw new BusinessException("SHIP_ALREADY_ASSIGNED", "Cruise already has an assigned ship. Use change-assigned-ship instead.");
        }

        Ship ship = shipRepository.findById(shipId)
                .orElseThrow(() -> new ResourceNotFoundException("Ship", shipId));

        if (ship.getStatus() != ShipStatus.ACTIVE) {
            throw new BusinessException("SHIP_NOT_ACTIVE", "Cannot assign ship because ship status is " + ship.getStatus());
        }

        if (cruiseRepository.existsOverlappingCruisesExcluding(shipId, cruiseId, cruise.getDepartureDatetime(), cruise.getReturnDatetime())) {
            throw new BusinessException("SHIP_SCHEDULE_CONFLICT", "Ship has an overlapping active cruise during this period");
        }

        cruise.setShip(ship);
        cruise = cruiseRepository.save(cruise);
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

        Ship newShip = shipRepository.findById(newShipId)
                .orElseThrow(() -> new ResourceNotFoundException("Ship", newShipId));

        if (newShip.getStatus() != ShipStatus.ACTIVE) {
            throw new BusinessException("SHIP_NOT_ACTIVE", "New ship must be in ACTIVE status");
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
        return mapToResponse(cruise);
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

        boolean shipAssigned = cruise.getShip() != null;
        boolean shipActive = false;
        boolean shipConfigValid = false;

        if (!shipAssigned) {
            issues.add("Chưa gán tàu cho chuyến đi");
        } else {
            Ship ship = cruise.getShip();
            shipActive = ship.getStatus() == ShipStatus.ACTIVE;
            if (!shipActive) {
                issues.add("Tàu được gán đang không ở trạng thái ACTIVE (hiện tại: " + ship.getStatus() + ")");
            }

            long decks = deckRepository.countByShipShipId(ship.getShipId());
            long cabins = cabinRepository.countByShipShipId(ship.getShipId());
            shipConfigValid = decks > 0 && cabins > 0;
            if (!shipConfigValid) {
                issues.add("Tàu chưa được cấu hình đầy đủ tầng (decks=" + decks + ") hoặc phòng (cabins=" + cabins + ")");
            }
        }

        boolean scheduleValid = cruise.getReturnDatetime().isAfter(cruise.getDepartureDatetime());
        if (!scheduleValid) {
            issues.add("Lịch trình không hợp lệ: Thời điểm kết thúc phải sau thời điểm khởi hành");
        }

        boolean departurePortActive = cruise.getDeparturePort() != null && cruise.getDeparturePort().getStatus() == PortStatus.ACTIVE;
        boolean returnPortActive = cruise.getReturnPort() != null && cruise.getReturnPort().getStatus() == PortStatus.ACTIVE;
        boolean portsActive = departurePortActive && returnPortActive;
        if (!portsActive) {
            issues.add("Cảng đi hoặc cảng về đang không ở trạng thái hoạt động (ACTIVE)");
        }

        Optional<com.casms.ship.entity.Itinerary> itineraryOpt = itineraryRepository.findByCruiseCruiseId(cruiseId);
        boolean itineraryConfigured = itineraryOpt.isPresent();
        if (!itineraryConfigured) {
            issues.add("Chuyến đi chưa được liên kết hải trình (Itinerary)");
        } else if (itineraryOpt.get().getStatus() != com.casms.ship.enums.ItineraryStatus.APPROVED) {
            issues.add("Hải trình của chuyến đi chưa được phê duyệt (Trạng thái hiện tại: " + itineraryOpt.get().getStatus() + ")");
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
        response.setItineraryConfigured(itineraryConfigured && itineraryOpt.get().getStatus() == com.casms.ship.enums.ItineraryStatus.APPROVED);
        response.setIssues(issues);

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

        cruise.setStatus(CruiseStatus.IN_PROGRESS);
        cruise = cruiseRepository.save(cruise);
        log.info("Started cruise: {}", cruiseId);
        return mapToResponse(cruise);
    }

    @Transactional
    public CruiseResponse complete(UUID cruiseId) {
        Cruise cruise = getCruiseById(cruiseId);

        if (cruise.getStatus() != CruiseStatus.IN_PROGRESS) {
            throw new BusinessException("CRUISE_NOT_IN_PROGRESS", "Only in-progress cruises can be completed");
        }

        cruise.setStatus(CruiseStatus.COMPLETED);
        cruise = cruiseRepository.save(cruise);
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
        List<com.casms.ship.dto.response.CruiseManifestResponse.PassengerManifestItem> passengerItems = activePassengers.stream()
                .map(bp -> {
                    var pass = bp.getPassenger();
                    String fullName = pass != null ? pass.getFullName() : "N/A";
                    String idNum = pass != null ? pass.getDocumentNumberProtected() : "N/A";
                    String bookingCode = bp.getBooking() != null ? bp.getBooking().getBookingReference() : "N/A";
                    return com.casms.ship.dto.response.CruiseManifestResponse.PassengerManifestItem.builder()
                            .bookingPassengerId(bp.getBookingPassengerId())
                            .bookingCode(bookingCode)
                            .fullName(fullName)
                            .identificationNumber(idNum)
                            .cabinNumber("ASSIGNED")
                            .deckName("MAIN")
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
}
