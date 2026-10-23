package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.common.service.BusinessCodeGenerator;
import com.casms.ship.dto.request.*;
import com.casms.ship.dto.response.*;
import com.casms.ship.enums.CabinStatus;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.Ship;
import com.casms.ship.enums.ShipStatus;
import com.casms.ship.repository.CabinRepository;
import com.casms.ship.repository.CruiseRepository;
import com.casms.ship.repository.DeckRepository;
import com.casms.ship.repository.ShipRepository;
import com.casms.ship.repository.ShipModelRepository;
import com.casms.ship.repository.LocationDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShipService {

    private final ShipRepository shipRepository;
    private final CruiseRepository cruiseRepository;
    private final DeckRepository deckRepository;
    private final CabinRepository cabinRepository;
    private final ShipModelRepository shipModelRepository;
    private final LocationDeviceRepository locationDeviceRepository;
    private final BusinessCodeGenerator businessCodeGenerator;

    @Transactional
    public ShipResponse create(ShipCreateRequest request) {
        String shipName = request.getShipName().trim();
        String shipCode = businessCodeGenerator.next("SH");
        ShipStatus status = request.getStatus() == null ? ShipStatus.ACTIVE : request.getStatus();
        validateShipDimensions(request.getTotalCapacity(), request.getTotalDecks());
        validateHullDimensions(request.getLengthMeters(), request.getBeamMeters());
        validateCommissionDate(request.getCommissionDate());

        log.info("Creating new ship with code: {}", shipCode);
        Ship ship = new Ship();
        ship.setShipCode(shipCode);
        ship.setShipName(shipName);
        ship.setStatus(status);
        ship.setTotalCapacity(request.getTotalCapacity());
        ship.setTotalDecks(request.getTotalDecks());
        ship.setDeckNumberLimit(request.getTotalDecks());
        ship.setLengthMeters(request.getLengthMeters());
        ship.setBeamMeters(request.getBeamMeters());
        ship.setCommissionDate(request.getCommissionDate());
        
        ship = shipRepository.save(ship);
        return mapToResponse(ship);
    }

    @Transactional(readOnly = true)
    public Page<ShipResponse> list(Pageable pageable) {
        return shipRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public ShipResponse detail(UUID shipId) {
        Ship ship = getShipById(shipId);
        return mapToResponse(ship);
    }

    @Transactional
    public ShipResponse update(UUID shipId, ShipUpdateRequest request) {
        Ship ship = getShipById(shipId);
        Double proposedLength = request.getLengthMeters() == null ? ship.getLengthMeters() : request.getLengthMeters();
        Double proposedBeam = request.getBeamMeters() == null ? ship.getBeamMeters() : request.getBeamMeters();
        validateHullDimensions(proposedLength, proposedBeam);
        if (request.getShipName() != null) {
            String shipName = request.getShipName().trim();
            if (shipName.isEmpty()) {
                throw new BusinessException("INVALID_SHIP_NAME", "Ship name must not be blank");
            }
            ship.setShipName(shipName);
        }
        if (request.getTotalCapacity() != null) {
            if (request.getTotalCapacity() < 1) {
                throw new BusinessException("INVALID_SHIP_CAPACITY", "Total capacity must be greater than zero");
            }
            Long configuredCabinCapacity = cabinRepository.sumCapacityByShipId(shipId);
            if (configuredCabinCapacity != null && request.getTotalCapacity() < configuredCabinCapacity) {
                throw new BusinessException("SHIP_CAPACITY_BELOW_CABIN_CAPACITY",
                        "Total ship capacity cannot be lower than the combined capacity of its cabins");
            }
            ship.setTotalCapacity(request.getTotalCapacity());
        }
        if (request.getTotalDecks() != null) {
            if (request.getTotalDecks() < 1) {
                throw new BusinessException("INVALID_TOTAL_DECKS", "Total decks must be greater than zero");
            }
            long configuredDeckCount = deckRepository.countByShipShipId(shipId);
            if (request.getTotalDecks() < configuredDeckCount) {
                throw new BusinessException("TOTAL_DECKS_BELOW_CONFIGURED_DECK",
                        "Total decks cannot be lower than the number of configured business decks");
            }
            ship.setTotalDecks(request.getTotalDecks());
        }
        if (request.getCommissionDate() != null) {
            validateCommissionDate(request.getCommissionDate());
            ship.setCommissionDate(request.getCommissionDate());
        }
        if (request.getLengthMeters() != null) ship.setLengthMeters(request.getLengthMeters());
        if (request.getBeamMeters() != null) ship.setBeamMeters(request.getBeamMeters());

        ship = shipRepository.save(ship);
        return mapToResponse(ship);
    }

    @Transactional
    public void deleteUnused(UUID shipId) {
        Ship ship = getShipById(shipId);
        
        // Check if used in any Cruise
        if (cruiseRepository.existsByShip(ship)) {
            throw new BusinessException("SHIP_IN_USE", "Cannot delete ship because it is assigned to one or more cruises");
        }
        if (deckRepository.countByShipShipId(shipId) > 0 || cabinRepository.countByShipShipId(shipId) > 0) {
            throw new BusinessException("SHIP_HAS_CONFIGURATION", "Cannot delete a ship while decks or cabins are configured");
        }
        if (!shipModelRepository.findByShipShipIdOrderByCreatedAtDesc(shipId).isEmpty()
                || !locationDeviceRepository.findByShipShipId(shipId).isEmpty()) {
            throw new BusinessException("SHIP_HAS_CONFIGURATION", "Cannot delete a ship while 3D models or BLE devices are configured");
        }

        shipRepository.delete(ship);
        log.info("Deleted unused ship: {}", shipId);
    }

    @Transactional
    public ShipResponse changeStatus(UUID shipId, ShipStatus newStatus) {
        Ship ship = getShipById(shipId);
        if (newStatus == null) {
            throw new BusinessException("INVALID_SHIP_STATUS", "Ship status is required");
        }
        if (ship.getStatus() == newStatus) {
            return mapToResponse(ship);
        }
        
        // If changing to INACTIVE or MAINTENANCE, we should check if there are active cruises right now
        if ((newStatus == ShipStatus.INACTIVE || newStatus == ShipStatus.MAINTENANCE)) {
            Optional<Cruise> currentCruise = cruiseRepository.findCurrentActiveCruise(shipId, Instant.now());
            if (currentCruise.isPresent()) {
                throw new BusinessException("SHIP_ACTIVE_CRUISE", "Cannot change status to " + newStatus + " while ship is on an active cruise");
            }
        }
        
        ship.setStatus(newStatus);
        ship = shipRepository.save(ship);
        return mapToResponse(ship);
    }

    @Transactional(readOnly = true)
    public boolean checkScheduleAvailability(UUID shipId, Instant start, Instant end) {
        getShipById(shipId); // Ensure ship exists
        
        validateScheduleRange(start, end);
        return !cruiseRepository.existsOverlappingCruises(shipId, start, end);
    }

    @Transactional(readOnly = true)
    public ShipCapacitySummaryResponse getCapacitySummary(UUID shipId) {
        Ship ship = getShipById(shipId);
        
        long actualDecks = deckRepository.countByShipShipId(shipId);
        long actualCabins = cabinRepository.countByShipShipId(shipId);
        long availableCabins = cabinRepository.countByShipShipIdAndStatus(shipId, CabinStatus.AVAILABLE);
        
        ShipCapacitySummaryResponse response = new ShipCapacitySummaryResponse();
        response.setShipId(ship.getShipId());
        response.setShipName(ship.getShipName());
        response.setConfiguredTotalCapacity(ship.getTotalCapacity());
        response.setConfiguredTotalDecks(ship.getTotalDecks());
        response.setActualDeckCount(actualDecks);
        response.setActualCabinCount(actualCabins);
        response.setAvailableCabinCount(availableCabins);
        
        return response;
    }

    @Transactional(readOnly = true)
    public List<CruiseSummaryResponse> getAffectedCruises(UUID shipId, Instant start, Instant end) {
        getShipById(shipId);
        validateScheduleRange(start, end);
        return cruiseRepository.findAffectedCruises(shipId, start, end).stream()
                .map(this::mapToCruiseResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CruiseSummaryResponse getCurrentCruise(UUID shipId) {
        getShipById(shipId);
        return cruiseRepository.findCurrentActiveCruise(shipId, Instant.now())
                .map(this::mapToCruiseResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No active cruise found for ship currently"));
    }

    @Transactional(readOnly = true)
    public void validateShip(UUID shipId) {
        Ship ship = getShipById(shipId);
        // Basic entity validation is handled by DB constraints.
        // Example check: totalDecks vs actual registered decks in DB
        long actualDecks = deckRepository.countByShipShipId(shipId);
        if (actualDecks > ship.getTotalDecks()) {
            throw new BusinessException("SHIP_VALIDATION_FAILED", "Actual configured decks exceed ship's total deck limit");
        }
    }

    @Transactional(readOnly = true)
    public boolean validateOperationalReadiness(UUID shipId) {
        Ship ship = getShipById(shipId);
        
        // A ship is operationally ready if:
        // 1. Status is ACTIVE
        if (ship.getStatus() != ShipStatus.ACTIVE) {
            return false;
        }
        
        // 2. Has at least 1 deck and 1 cabin configured
        long deckCount = deckRepository.countByShipShipId(shipId);
        long cabinCount = cabinRepository.countByShipShipId(shipId);
        
        if (deckCount == 0 || cabinCount == 0) {
            return false;
        }
        
        // Additional operational regulation checks can be added here
        return true;
    }

    private void validateShipDimensions(Integer totalCapacity, Integer totalDecks) {
        if (totalCapacity == null || totalCapacity < 1) {
            throw new BusinessException("INVALID_SHIP_CAPACITY", "Total capacity must be greater than zero");
        }
        if (totalDecks == null || totalDecks < 1) {
            throw new BusinessException("INVALID_TOTAL_DECKS", "Total decks must be greater than zero");
        }
    }

    private void validateHullDimensions(Double lengthMeters, Double beamMeters) {
        if ((lengthMeters == null) != (beamMeters == null) ||
                (lengthMeters != null && (!Double.isFinite(lengthMeters) || lengthMeters <= 0 ||
                        !Double.isFinite(beamMeters) || beamMeters <= 0))) {
            throw new BusinessException("INVALID_SHIP_HULL_DIMENSIONS",
                    "Ship length and beam must either both be omitted or both be finite positive values");
        }
    }

    private void validateCommissionDate(java.time.LocalDate commissionDate) {
        if (commissionDate != null && commissionDate.isAfter(java.time.LocalDate.now())) {
            throw new BusinessException("INVALID_COMMISSION_DATE", "Commission date cannot be in the future");
        }
    }

    private void validateScheduleRange(Instant start, Instant end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new BusinessException("INVALID_DATE_RANGE", "Start time and end time are required, and end must be after start");
        }
    }

    private Ship getShipById(UUID shipId) {
        return shipRepository.findById(shipId)
                .orElseThrow(() -> new ResourceNotFoundException("Ship", shipId));
    }

    private ShipResponse mapToResponse(Ship ship) {
        ShipResponse res = new ShipResponse();
        res.setShipId(ship.getShipId());
        res.setShipCode(ship.getShipCode());
        res.setShipName(ship.getShipName());
        res.setStatus(ship.getStatus());
        res.setTotalCapacity(ship.getTotalCapacity());
        res.setTotalDecks(ship.getTotalDecks());
        res.setDeckNumberLimit(ship.getDeckNumberLimit() == null ? ship.getTotalDecks() : ship.getDeckNumberLimit());
        res.setManifestModelId(ship.getManifestModelId());
        res.setLengthMeters(ship.getLengthMeters());
        res.setBeamMeters(ship.getBeamMeters());
        res.setCommissionDate(ship.getCommissionDate());
        res.setCreatedAt(ship.getCreatedAt());
        res.setUpdatedAt(ship.getUpdatedAt());
        return res;
    }
    
    private CruiseSummaryResponse mapToCruiseResponse(Cruise cruise) {
        CruiseSummaryResponse res = new CruiseSummaryResponse();
        res.setCruiseId(cruise.getCruiseId());
        res.setCruiseCode(cruise.getCruiseCode());
        res.setCruiseName(cruise.getCruiseName());
        res.setStatus(cruise.getStatus());
        res.setDepartureDatetime(cruise.getDepartureDatetime());
        res.setReturnDatetime(cruise.getReturnDatetime());
        return res;
    }
}
