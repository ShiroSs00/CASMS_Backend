package com.casms.ship.service;

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
import com.casms.ship.repository.ShipModelObjectRepository;
import com.casms.ship.repository.LocationDeviceRepository;
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
public class DeckService {

    private final DeckRepository deckRepository;
    private final ShipRepository shipRepository;
    private final CabinRepository cabinRepository;
    private final ShipModelObjectRepository shipModelObjectRepository;
    private final LocationDeviceRepository locationDeviceRepository;

    @Transactional
    public DeckResponse create(DeckCreateRequest request) {
        Ship ship = shipRepository.findById(request.getShipId())
                .orElseThrow(() -> new ResourceNotFoundException("Ship", request.getShipId()));

        Integer deckNumber = request.getDeckNumber();
        if (deckNumber == null) {
            Integer maxDeck = deckRepository.findMaxDeckNumberByShipId(ship.getShipId());
            deckNumber = (maxDeck != null ? maxDeck : 0) + 1;
        } else if (deckNumber < 1) {
            throw new BusinessException("INVALID_DECK_NUMBER", "Deck number must be at least 1");
        }
        if (deckNumber > ship.getTotalDecks()) {
            throw new BusinessException("INVALID_DECK_NUMBER", "Deck number cannot exceed ship's total decks limit");
        }

        if (deckRepository.existsByShipShipIdAndDeckNumber(ship.getShipId(), deckNumber)) {
            throw new BusinessException("DUPLICATE_DECK", "Deck number already exists on this ship");
        }

        String deckName = (request.getDeckName() != null && !request.getDeckName().isBlank())
                ? request.getDeckName().trim()
                : "Deck " + deckNumber;

        Deck deck = new Deck();
        deck.setShip(ship);
        deck.setDeckNumber(deckNumber);
        deck.setDeckName(deckName);
        deck.setPriceMultiplier(request.getPriceMultiplier() != null ? request.getPriceMultiplier() : java.math.BigDecimal.valueOf(1.00));
        applyModelTransform(deck, request.getModelPositionX(), request.getModelPositionY(), request.getModelPositionZ(),
                request.getModelRotationX(), request.getModelRotationY(), request.getModelRotationZ());

        deck = deckRepository.save(deck);
        return mapToResponse(deck);
    }

    @Transactional(readOnly = true)
    public Page<DeckResponse> list(Pageable pageable) {
        return deckRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<DeckResponse> listByShip(UUID shipId, Pageable pageable) {
        if (!shipRepository.existsById(shipId)) throw new ResourceNotFoundException("Ship", shipId);
        return deckRepository.findByShipShipId(shipId, pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public DeckResponse detail(UUID deckId) {
        Deck deck = getDeckById(deckId);
        return mapToResponse(deck);
    }

    @Transactional
    public DeckResponse update(UUID deckId, DeckUpdateRequest request) {
        Deck deck = getDeckById(deckId);

        if (request.getDeckNumber() != null && !request.getDeckNumber().equals(deck.getDeckNumber())) {
            if (request.getDeckNumber() < 1) {
                throw new BusinessException("INVALID_DECK_NUMBER", "Deck number must be at least 1");
            }
            if (request.getDeckNumber() > deck.getShip().getTotalDecks()) {
                throw new BusinessException("INVALID_DECK_NUMBER", "Deck number cannot exceed ship's total decks limit");
            }
            if (deckRepository.existsByShipShipIdAndDeckNumber(deck.getShip().getShipId(), request.getDeckNumber())) {
                throw new BusinessException("DUPLICATE_DECK", "Deck number already exists on this ship");
            }
            deck.setDeckNumber(request.getDeckNumber());
        }

        if (request.getDeckName() != null) {
            deck.setDeckName(request.getDeckName());
        }
        if (request.getPriceMultiplier() != null) {
            deck.setPriceMultiplier(request.getPriceMultiplier());
        }
        Double px = request.getModelPositionX() == null ? deck.getModelPositionX() : request.getModelPositionX();
        Double py = request.getModelPositionY() == null ? deck.getModelPositionY() : request.getModelPositionY();
        Double pz = request.getModelPositionZ() == null ? deck.getModelPositionZ() : request.getModelPositionZ();
        Double rx = request.getModelRotationX() == null ? deck.getModelRotationX() : request.getModelRotationX();
        Double ry = request.getModelRotationY() == null ? deck.getModelRotationY() : request.getModelRotationY();
        Double rz = request.getModelRotationZ() == null ? deck.getModelRotationZ() : request.getModelRotationZ();
        validateTriplet(px, py, pz, "position");
        validateTriplet(rx, ry, rz, "rotation");
        if (px != null) deck.setModelPositionX(coordinate(px));
        if (py != null) deck.setModelPositionY(coordinate(py));
        if (pz != null) deck.setModelPositionZ(coordinate(pz));
        if (rx != null) deck.setModelRotationX(coordinate(rx));
        if (ry != null) deck.setModelRotationY(coordinate(ry));
        if (rz != null) deck.setModelRotationZ(coordinate(rz));

        deck = deckRepository.save(deck);
        return mapToResponse(deck);
    }

    @Transactional
    public void deleteUnused(UUID deckId) {
        Deck deck = getDeckById(deckId);
        
        if (cabinRepository.existsByDeckDeckId(deckId)) {
            throw new BusinessException("DECK_IN_USE", "Cannot delete deck because it contains cabins");
        }
        if (shipModelObjectRepository.existsByDeckDeckId(deckId) || locationDeviceRepository.existsByDeckDeckId(deckId)) {
            throw new BusinessException("DECK_IN_USE", "Cannot delete deck while a model mapping or BLE anchor references it");
        }

        deckRepository.delete(deck);
        log.info("Deleted unused deck: {}", deckId);
    }

    @Transactional(readOnly = true)
    public List<CabinSummaryResponse> listCabinsByDeck(UUID deckId) {
        getDeckById(deckId); // validate existence
        return cabinRepository.findByDeckDeckId(deckId).stream()
                .map(this::mapToCabinResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public boolean validateBelongsToShip(UUID deckId, UUID shipId) {
        Deck deck = getDeckById(deckId);
        return deck.getShip().getShipId().equals(shipId);
    }

    @Transactional(readOnly = true)
    public void validateDeck(UUID deckId) {
        Deck deck = getDeckById(deckId);
        if (deck.getDeckNumber() > deck.getShip().getTotalDecks()) {
            throw new BusinessException("INVALID_DECK_NUMBER", "Deck number violates ship limit constraint");
        }
    }

    @Transactional(readOnly = true)
    public DeckCapacityResponse getDeckCapacity(UUID deckId) {
        Deck deck = getDeckById(deckId);

        long totalCabins = cabinRepository.countByDeckDeckId(deckId);
        long availableCabins = cabinRepository.countByDeckDeckIdAndStatus(deckId, CabinStatus.AVAILABLE);
        Long totalPassengerCapacity = cabinRepository.sumCapacityByDeckId(deckId);

        DeckCapacityResponse res = new DeckCapacityResponse();
        res.setDeckId(deck.getDeckId());
        res.setDeckName(deck.getDeckName());
        res.setDeckNumber(deck.getDeckNumber());
        res.setTotalCabins(totalCabins);
        res.setAvailableCabins(availableCabins);
        res.setTotalPassengerCapacity(totalPassengerCapacity == null ? 0L : totalPassengerCapacity);

        return res;
    }

    private Deck getDeckById(UUID deckId) {
        return deckRepository.findById(deckId)
                .orElseThrow(() -> new ResourceNotFoundException("Deck", deckId));
    }

    private DeckResponse mapToResponse(Deck deck) {
        DeckResponse res = new DeckResponse();
        res.setDeckId(deck.getDeckId());
        res.setShipId(deck.getShip().getShipId());
        res.setDeckNumber(deck.getDeckNumber());
        res.setDeckName(deck.getDeckName());
        res.setPriceMultiplier(deck.getPriceMultiplier() != null ? deck.getPriceMultiplier() : java.math.BigDecimal.valueOf(1.00));
        res.setModelPositionX(deck.getModelPositionX());
        res.setModelPositionY(deck.getModelPositionY());
        res.setModelPositionZ(deck.getModelPositionZ());
        res.setModelRotationX(deck.getModelRotationX());
        res.setModelRotationY(deck.getModelRotationY());
        res.setModelRotationZ(deck.getModelRotationZ());
        res.setCreatedAt(deck.getCreatedAt());
        res.setUpdatedAt(deck.getUpdatedAt());
        return res;
    }

    private void applyModelTransform(Deck deck, Double x, Double y, Double z, Double rx, Double ry, Double rz) {
        validateTriplet(x, y, z, "position");
        validateTriplet(rx, ry, rz, "rotation");
        deck.setModelPositionX(x == null ? null : coordinate(x));
        deck.setModelPositionY(y == null ? null : coordinate(y));
        deck.setModelPositionZ(z == null ? null : coordinate(z));
        deck.setModelRotationX(rx == null ? null : coordinate(rx));
        deck.setModelRotationY(ry == null ? null : coordinate(ry));
        deck.setModelRotationZ(rz == null ? null : coordinate(rz));
    }

    private void validateTriplet(Double x, Double y, Double z, String name) {
        if ((x == null || y == null || z == null) && (x != null || y != null || z != null)) {
            throw new BusinessException("INCOMPLETE_MODEL_TRANSFORM", "Model " + name + " requires x, y and z values together");
        }
    }

    private Double coordinate(Double value) {
        if (!Double.isFinite(value) || Math.abs(value) > 1000) {
            throw new BusinessException("INVALID_MODEL_TRANSFORM", "Model coordinates and rotations must be finite and within ±1000 model units");
        }
        return value;
    }

    private CabinSummaryResponse mapToCabinResponse(Cabin cabin) {
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
}
