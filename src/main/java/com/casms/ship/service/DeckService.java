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

    @Transactional
    public DeckResponse create(DeckCreateRequest request) {
        Ship ship = shipRepository.findById(request.getShipId())
                .orElseThrow(() -> new ResourceNotFoundException("Ship", request.getShipId()));

        if (request.getDeckNumber() > ship.getTotalDecks()) {
            throw new BusinessException("INVALID_DECK_NUMBER", "Deck number cannot exceed ship's total decks limit");
        }

        if (deckRepository.existsByShipShipIdAndDeckNumber(ship.getShipId(), request.getDeckNumber())) {
            throw new BusinessException("DUPLICATE_DECK", "Deck number already exists on this ship");
        }

        Deck deck = new Deck();
        deck.setShip(ship);
        deck.setDeckNumber(request.getDeckNumber());
        deck.setDeckName(request.getDeckName());

        deck = deckRepository.save(deck);
        return mapToResponse(deck);
    }

    @Transactional(readOnly = true)
    public Page<DeckResponse> list(Pageable pageable) {
        return deckRepository.findAll(pageable).map(this::mapToResponse);
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

        deck = deckRepository.save(deck);
        return mapToResponse(deck);
    }

    @Transactional
    public void deleteUnused(UUID deckId) {
        Deck deck = getDeckById(deckId);
        
        if (cabinRepository.existsByDeckDeckId(deckId)) {
            throw new BusinessException("DECK_IN_USE", "Cannot delete deck because it contains cabins");
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
        res.setCreatedAt(deck.getCreatedAt());
        res.setUpdatedAt(deck.getUpdatedAt());
        return res;
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