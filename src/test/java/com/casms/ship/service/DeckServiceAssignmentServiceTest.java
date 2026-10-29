package com.casms.ship.service;

import com.casms.common.exception.ResourceNotFoundException;
import com.casms.servicepackage.repository.ServiceRepository;
import com.casms.ship.entity.Deck;
import com.casms.ship.entity.Ship;
import com.casms.ship.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeckServiceAssignmentServiceTest {
    @Mock DeckRepository decks;
    @Mock ServiceRepository services;
    @Mock ShipRepository ships;
    @Mock DeckServiceAssignmentRepository assignments;
    @InjectMocks DeckServiceAssignmentService service;
    Deck deck;
    UUID deckId = UUID.randomUUID(), shipId = UUID.randomUUID(), serviceId = UUID.randomUUID();

    @BeforeEach void setup() {
        Ship ship = new Ship(); ship.setShipId(shipId);
        deck = new Deck(); deck.setDeckId(deckId); deck.setShip(ship);
    }

    @Test void wrongShipCannotWriteOrResolveCatalogue() {
        UUID other = UUID.randomUUID();
        when(decks.findByIdForUpdate(deckId)).thenReturn(Optional.of(deck));
        when(ships.existsById(other)).thenReturn(true);
        assertThrows(ResourceNotFoundException.class, () -> service.assign(deckId, serviceId, other));
        verifyNoInteractions(assignments, services);
    }

    @Test void unknownShipCannotReadAssignments() {
        when(decks.findById(deckId)).thenReturn(Optional.of(deck));
        when(ships.existsById(shipId)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> service.list(deckId, shipId));
        verifyNoInteractions(assignments);
    }

    @Test void unscopedCallsKeepDeckAsAuthoritativeShip() {
        when(decks.findById(deckId)).thenReturn(Optional.of(deck));
        when(assignments.findByDeckDeckIdOrderByCreatedAtAscAssignmentIdAsc(deckId)).thenReturn(List.of());
        assertTrue(service.list(deckId, null).isEmpty());
        verifyNoInteractions(ships);
    }

    @Test void wrongShipCannotUnassign() {
        UUID other = UUID.randomUUID();
        when(decks.findByIdForUpdate(deckId)).thenReturn(Optional.of(deck));
        when(ships.existsById(other)).thenReturn(true);
        assertThrows(ResourceNotFoundException.class, () -> service.unassign(deckId, serviceId, other));
        verifyNoInteractions(assignments, services);
    }

    @Test void missingDeckStopsBeforeShipAndServiceLookups() {
        when(decks.findByIdForUpdate(deckId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.assign(deckId, serviceId, shipId));
        verifyNoInteractions(ships, services, assignments);
    }
}
