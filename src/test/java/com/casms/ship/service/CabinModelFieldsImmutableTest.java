package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.ship.dto.request.CabinUpdateRequest;
import com.casms.ship.entity.Cabin;
import com.casms.ship.repository.CabinRepository;
import com.casms.ship.repository.DeckRepository;
import com.casms.ship.repository.ShipModelObjectRepository;
import com.casms.ship.repository.ShipRepository;
import com.casms.booking.repository.CabinAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CabinModelFieldsImmutableTest {
    @Mock CabinRepository cabins;
    @Mock ShipRepository ships;
    @Mock DeckRepository decks;
    @Mock CabinAssignmentRepository assignments;
    @Mock ShipModelObjectRepository modelObjects;
    @InjectMocks CabinService service;

    @Test
    void updateRejectsModelFixedCabinTypeAndTransform() {
        UUID cabinId = UUID.randomUUID();
        Cabin cabin = new Cabin();
        cabin.setCabinId(cabinId);
        when(cabins.findById(cabinId)).thenReturn(Optional.of(cabin));
        CabinUpdateRequest request = new CabinUpdateRequest();
        request.setModelPositionX(3.0);

        BusinessException error = assertThrows(BusinessException.class, () -> service.update(cabinId, request));

        assertEquals("CABIN_MODEL_FIELDS_IMMUTABLE", error.getCode());
        verify(cabins, never()).save(cabin);
    }
}
