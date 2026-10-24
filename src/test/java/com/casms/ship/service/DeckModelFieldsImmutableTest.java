package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.ship.dto.request.DeckUpdateRequest;
import com.casms.ship.entity.Deck;
import com.casms.ship.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeckModelFieldsImmutableTest {
    @Mock DeckRepository decks;
    @Mock ShipRepository ships;
    @Mock CabinRepository cabins;
    @Mock ShipModelObjectRepository modelObjects;
    @Mock LocationDeviceRepository devices;
    @InjectMocks DeckService service;

    @Test
    void updateRejectsModelTransformOutsideImportWorkflow() {
        UUID id = UUID.randomUUID();
        Deck deck = new Deck();
        deck.setDeckId(id);
        when(decks.findById(id)).thenReturn(Optional.of(deck));
        DeckUpdateRequest request = new DeckUpdateRequest();
        request.setModelPositionX(2.0);

        BusinessException error = assertThrows(BusinessException.class, () -> service.update(id, request));

        assertEquals("DECK_MODEL_FIELDS_IMMUTABLE", error.getCode());
        verify(decks, never()).save(deck);
    }
}
