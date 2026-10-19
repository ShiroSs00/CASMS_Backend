package com.casms.ship.service;

import com.casms.booking.entity.CabinAssignment;
import com.casms.booking.repository.CabinAssignmentRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.ship.dto.request.*;
import com.casms.ship.dto.response.*;
import com.casms.ship.entity.Cabin;
import com.casms.ship.enums.CabinStatus;
import com.casms.ship.entity.Deck;
import com.casms.ship.entity.Ship;
import com.casms.ship.repository.CabinRepository;
import com.casms.ship.repository.DeckRepository;
import com.casms.ship.repository.ShipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CabinService {

    private final CabinRepository cabinRepository;
    private final ShipRepository shipRepository;
    private final DeckRepository deckRepository;
    private final CabinAssignmentRepository cabinAssignmentRepository;

    @Transactional
    public CabinSummaryResponse create(CabinCreateRequest request) {
        Ship ship = shipRepository.findById(request.getShipId())
                .orElseThrow(() -> new ResourceNotFoundException("Ship", request.getShipId()));

        Deck deck = deckRepository.findById(request.getDeckId())
                .orElseThrow(() -> new ResourceNotFoundException("Deck", request.getDeckId()));

        if (!deck.getShip().getShipId().equals(ship.getShipId())) {
            throw new BusinessException("DECK_SHIP_MISMATCH", "The deck does not belong to the specified ship");
        }

        if (cabinRepository.existsByShipShipIdAndCabinNumber(ship.getShipId(), request.getCabinNumber())) {
            throw new BusinessException("DUPLICATE_CABIN", "Cabin number already exists on this ship");
        }

        if (request.getCapacity() == null || request.getCapacity() < 1) {
            throw new BusinessException("INVALID_CABIN_CAPACITY", "Cabin capacity must be greater than zero");
        }
        if (request.getCabinNumber() == null || request.getCabinNumber().trim().isEmpty()) {
            throw new BusinessException("INVALID_CABIN_NUMBER", "Cabin number is required");
        }

        Cabin cabin = new Cabin();
        cabin.setShip(ship);
        cabin.setDeck(deck);
        cabin.setCabinNumber(request.getCabinNumber().trim());
        cabin.setCabinType(request.getCabinType());
        cabin.setCapacity(request.getCapacity());
        cabin.setStatus(request.getStatus());
        cabin.setIsAccessible(request.getIsAccessible());

        cabin = cabinRepository.save(cabin);
        return mapToResponse(cabin);
    }

    @Transactional(readOnly = true)
    public Page<CabinSummaryResponse> list(Pageable pageable) {
        return cabinRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public CabinSummaryResponse detail(UUID cabinId) {
        return mapToResponse(getCabinById(cabinId));
    }

    @Transactional
    public CabinSummaryResponse update(UUID cabinId, CabinUpdateRequest request) {
        Cabin cabin = getCabinById(cabinId);

        if (request.getCabinType() != null) cabin.setCabinType(request.getCabinType());
        if (request.getCapacity() != null) {
            if (request.getCapacity() < 1) {
                throw new BusinessException("INVALID_CABIN_CAPACITY", "Cabin capacity must be greater than zero");
            }
            cabin.setCapacity(request.getCapacity());
        }
        if (request.getIsAccessible() != null) cabin.setIsAccessible(request.getIsAccessible());

        cabin = cabinRepository.save(cabin);
        return mapToResponse(cabin);
    }

    @Transactional
    public void deleteUnused(UUID cabinId) {
        Cabin cabin = getCabinById(cabinId);

        if (cabinAssignmentRepository.existsByCabinCabinId(cabinId)) {
            throw new BusinessException("CABIN_IN_USE", "Cannot delete cabin because it has been assigned to bookings");
        }

        cabinRepository.delete(cabin);
        log.info("Deleted unused cabin: {}", cabinId);
    }

    @Transactional(readOnly = true)
    public List<CabinSummaryResponse> searchAvailable(CabinSearchRequest request) {
        return cabinRepository.searchAvailable(
                request.getShipId(),
                request.getDeckId(),
                request.getCabinType(),
                request.getMinCapacity(),
                request.getIsAccessible()
        ).stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public boolean checkCapacity(UUID cabinId, Integer requiredCapacity) {
        Cabin cabin = getCabinById(cabinId);
        return cabin.getCapacity() >= requiredCapacity;
    }

    @Transactional(readOnly = true)
    public boolean checkCompatibility(UUID cabinId, Boolean requireAccessible) {
        Cabin cabin = getCabinById(cabinId);
        if (requireAccessible != null && requireAccessible && !cabin.getIsAccessible()) {
            return false;
        }
        return true;
    }

    @Transactional
    public CabinSummaryResponse changeOperationalStatus(UUID cabinId, CabinStatus newStatus) {
        Cabin cabin = getCabinById(cabinId);

        if (newStatus == null) {
            throw new BusinessException("INVALID_CABIN_STATUS", "Cabin status is required");
        }
        if (newStatus == CabinStatus.OCCUPIED && cabin.getStatus() != CabinStatus.OCCUPIED) {
            throw new BusinessException("CABIN_OCCUPANCY_REQUIRES_ASSIGNMENT",
                    "A cabin can only become OCCUPIED through the passenger assignment/booking workflow");
        }
        if (cabin.getStatus() == CabinStatus.OCCUPIED && newStatus != CabinStatus.OCCUPIED) {
            boolean hasActiveAssignment = cabinAssignmentRepository
                    .findByCabinCabinIdOrderByValidFromDesc(cabinId).stream()
                    .anyMatch(assignment -> assignment.getValidTo() == null);
            if (hasActiveAssignment) {
                throw new BusinessException("CABIN_HAS_ACTIVE_ASSIGNMENT",
                        "Cannot change status while an active passenger assignment exists");
            }
        }
        cabin.setStatus(newStatus);
        cabin = cabinRepository.save(cabin);
        return mapToResponse(cabin);
    }

    @Transactional(readOnly = true)
    public List<CabinAssignmentHistoryResponse> getAssignmentHistory(UUID cabinId) {
        getCabinById(cabinId);
        return cabinAssignmentRepository.findByCabinCabinIdOrderByValidFromDesc(cabinId).stream()
                .map(this::mapToAssignmentResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public void validateCabin(UUID cabinId) {
        Cabin cabin = getCabinById(cabinId);
        if (!cabin.getDeck().getShip().getShipId().equals(cabin.getShip().getShipId())) {
            throw new BusinessException("DATA_INTEGRITY_ERROR", "Cabin's deck does not belong to the cabin's assigned ship");
        }
    }

    @Transactional(readOnly = true)
    public boolean checkAvailability(UUID cabinId) {
        Cabin cabin = getCabinById(cabinId);
        return cabin.getStatus() == CabinStatus.AVAILABLE;
    }

    @Transactional
    public CabinSummaryResponse blockCabin(UUID cabinId, CabinStatus reasonStatus) {
        if (reasonStatus != CabinStatus.MAINTENANCE && reasonStatus != CabinStatus.OUT_OF_SERVICE) {
            throw new BusinessException("INVALID_BLOCK_REASON", "Cabin can only be blocked as MAINTENANCE or OUT_OF_SERVICE");
        }
        Cabin cabin = getCabinById(cabinId);
        if (cabin.getStatus() != CabinStatus.AVAILABLE) {
            throw new BusinessException("CABIN_NOT_AVAILABLE", "Cabin is not available to be blocked");
        }
        cabin.setStatus(reasonStatus);
        return mapToResponse(cabinRepository.save(cabin));
    }

    @Transactional
    public CabinSummaryResponse releaseCabin(UUID cabinId) {
        Cabin cabin = getCabinById(cabinId);
        if (cabin.getStatus() == CabinStatus.AVAILABLE) {
            return mapToResponse(cabin);
        }
        if (cabin.getStatus() == CabinStatus.OCCUPIED) {
            boolean hasActiveAssignment = cabinAssignmentRepository
                    .findByCabinCabinIdOrderByValidFromDesc(cabinId).stream()
                    .anyMatch(assignment -> assignment.getValidTo() == null);
            if (hasActiveAssignment) {
                throw new BusinessException("CABIN_HAS_ACTIVE_ASSIGNMENT",
                        "Cannot release an occupied cabin while an active passenger assignment exists");
            }
        } else if (cabin.getStatus() != CabinStatus.MAINTENANCE
                && cabin.getStatus() != CabinStatus.OUT_OF_SERVICE) {
            throw new BusinessException("INVALID_CABIN_RELEASE", "Cabin cannot be released from its current status");
        }
        cabin.setStatus(CabinStatus.AVAILABLE);
        return mapToResponse(cabinRepository.save(cabin));
    }

    private Cabin getCabinById(UUID cabinId) {
        return cabinRepository.findById(cabinId)
                .orElseThrow(() -> new ResourceNotFoundException("Cabin", cabinId));
    }

    private CabinSummaryResponse mapToResponse(Cabin cabin) {
        CabinSummaryResponse res = new CabinSummaryResponse();
        res.setCabinId(cabin.getCabinId());
        res.setShipId(cabin.getShip().getShipId());
        res.setDeckId(cabin.getDeck().getDeckId());
        res.setCabinNumber(cabin.getCabinNumber());
        res.setCabinType(cabin.getCabinType());
        res.setCapacity(cabin.getCapacity());
        res.setStatus(cabin.getStatus());
        res.setIsAccessible(cabin.getIsAccessible());
        return res;
    }

    private CabinAssignmentHistoryResponse mapToAssignmentResponse(CabinAssignment assign) {
        CabinAssignmentHistoryResponse res = new CabinAssignmentHistoryResponse();
        res.setCabinAssignmentId(assign.getCabinAssignmentId());
        res.setBookingPassengerId(assign.getBookingPassenger().getBookingPassengerId());
        res.setValidFrom(assign.getValidFrom());
        res.setValidTo(assign.getValidTo());
        res.setAssignedByUserId(assign.getAssignedByUser().getUserId());
        res.setChangeReason(assign.getChangeReason());
        return res;
    }
}