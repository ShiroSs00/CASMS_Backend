package com.casms.ship.service;

import com.casms.common.exception.ResourceNotFoundException;
import com.casms.servicepackage.repository.ServiceRepository;
import com.casms.ship.dto.response.DeckServiceAssignmentResponse;
import com.casms.ship.entity.Deck;
import com.casms.ship.entity.DeckServiceAssignment;
import com.casms.ship.repository.DeckRepository;
import com.casms.ship.repository.ShipRepository;
import com.casms.ship.repository.DeckServiceAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class DeckServiceAssignmentService {
    private final DeckRepository decks;
    private final ServiceRepository services;
    private final DeckServiceAssignmentRepository assignments;
    private final ShipRepository ships;

    @Transactional(readOnly = true)
    public List<DeckServiceAssignmentResponse> list(UUID deckId, UUID shipId) {
        Deck deck = decks.findById(deckId).orElseThrow(() -> new ResourceNotFoundException("Deck", deckId));
        validateShip(deck, shipId);
        return assignments.findByDeckDeckIdOrderByCreatedAtAscAssignmentIdAsc(deckId).stream().map(this::response).toList();
    }

    @Transactional
    public DeckServiceAssignmentResponse assign(UUID deckId, UUID serviceId, UUID shipId) {
        // Serialize per deck, including retries and assignments from concurrent callers.
        Deck deck = decks.findByIdForUpdate(deckId).orElseThrow(() -> new ResourceNotFoundException("Deck", deckId));
        validateShip(deck, shipId);
        var service = services.findByIdForUpdate(serviceId).orElseThrow(() -> new ResourceNotFoundException("Service", serviceId));
        var link = assignments.findByDeckDeckIdAndServiceServiceId(deckId, serviceId).orElseGet(() -> {
            var created = new DeckServiceAssignment(); created.setDeck(deck); created.setService(service);
            return assignments.saveAndFlush(created);
        });
        return response(link);
    }

    @Transactional
    public void unassign(UUID deckId, UUID serviceId, UUID shipId) {
        Deck deck = decks.findByIdForUpdate(deckId).orElseThrow(() -> new ResourceNotFoundException("Deck", deckId));
        validateShip(deck, shipId);
        services.findByIdForUpdate(serviceId).orElseThrow(() -> new ResourceNotFoundException("Service", serviceId));
        assignments.findByDeckDeckIdAndServiceServiceId(deckId, serviceId).ifPresent(assignments::delete);
    }

    private void validateShip(Deck deck, UUID shipId) {
        // Without an explicit client scope, the deck's mandatory FK determines its owning ship.
        if (shipId == null) return;
        if (!ships.existsById(shipId)) throw new ResourceNotFoundException("Ship", shipId);
        if (!deck.getShip().getShipId().equals(shipId)) throw new ResourceNotFoundException("Deck in requested ship", deck.getDeckId());
    }

    private DeckServiceAssignmentResponse response(DeckServiceAssignment link) {
        var service = link.getService();
        return new DeckServiceAssignmentResponse(link.getAssignmentId(), link.getDeck().getShip().getShipId(),
                link.getDeck().getDeckId(), service.getServiceId(), service.getServiceCode(), service.getServiceName(),
                service.getServiceType(), service.getStatus(), link.getCreatedAt());
    }
}
