package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
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

    @Transactional
    public ShipResponse create(ShipCreateRequest request) {
        log.info("Creating new ship with code: {}", request.getShipCode());
        
        if (shipRepository.existsByShipCode(request.getShipCode().trim())) {
            throw new BusinessException("DUPLICATE_SHIP_CODE", "Ship code already exists: " + request.getShipCode());
        }
        
        Ship ship = new Ship();
        ship.setShipCode(request.getShipCode().trim());
        ship.setShipName(request.getShipName().trim());
        ship.setStatus(request.getStatus());
        ship.setTotalCapacity(request.getTotalCapacity());
        ship.setTotalDecks(request.getTotalDecks());
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
        
        if (request.getShipName() != null) ship.setShipName(request.getShipName().trim());
        if (request.getTotalCapacity() != null) ship.setTotalCapacity(request.getTotalCapacity());
        if (request.getTotalDecks() != null) {
            long currentDeckCount = deckRepository.countByShipShipId(shipId);
            if (request.getTotalDecks() < currentDeckCount) {
                throw new BusinessException("INVALID_TOTAL_DECKS",
                        "Total decks cannot be less than existing configured decks (" + currentDeckCount + ")");
            }
            ship.setTotalDecks(request.getTotalDecks());
        }
        if (request.getCommissionDate() != null) ship.setCommissionDate(request.getCommissionDate());
        
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
        
        shipRepository.delete(ship);
        log.info("Deleted unused ship: {}", shipId);
    }

    @Transactional
    public ShipResponse changeStatus(UUID shipId, ShipStatus newStatus) {
        Ship ship = getShipById(shipId);
        
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
        
        if (start.isAfter(end)) {
            throw new BusinessException("INVALID_DATE_RANGE", "Start time must be before end time");
        }
        
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
