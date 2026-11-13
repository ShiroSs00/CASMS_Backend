package com.casms.activity.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.casms.activity.entity.ShoreExcursion;
import com.casms.activity.enums.ShoreExcursionStatus;
import com.casms.activity.repository.RegistrationRepository;
import com.casms.activity.repository.ShoreExcursionRepository;
import com.casms.activity.repository.SupplierRepository;
import com.casms.ship.repository.CruiseStopRepository;
import com.casms.ship.repository.DestinationRepository;
import com.casms.ship.repository.PortRepository;

import jakarta.persistence.EntityNotFoundException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class ShoreExcursionServiceTest {

    @Mock private ShoreExcursionRepository excursionRepository;

    @Mock private RegistrationRepository registrationRepository;

    @Mock private SupplierRepository supplierRepository;

    @Mock private CruiseStopRepository cruiseStopRepository;

    @Mock private DestinationRepository destinationRepository;

    @Mock private PortRepository portRepository;

    @InjectMocks private ShoreExcursionService service;

    @Test
    void list_withoutFilters_excludesUnscheduledDefinitions() {
        ShoreExcursion excursion = new ShoreExcursion();
        excursion.setStatus(ShoreExcursionStatus.PLANNED);

        when(excursionRepository.findAll()).thenReturn(List.of(excursion));

        // Cần supplier và cruise stop hợp lệ để map response.
        // Nếu entity chưa có quan hệ, hãy bổ sung fixture theo entity thực tế.
        assertTrue(service.list(null, null, null).isEmpty());
    }

    @Test
    void detail_missingExcursion_throwsException() {
        UUID id = UUID.randomUUID();

        when(excursionRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.detail(id));
    }

    @Test
    void deleteDraft_nonDraftExcursion_throwsException() {
        UUID id = UUID.randomUUID();

        ShoreExcursion excursion = new ShoreExcursion();
        excursion.setStatus(ShoreExcursionStatus.OPEN);

        when(excursionRepository.findById(id)).thenReturn(Optional.of(excursion));

        assertThrows(IllegalStateException.class, () -> service.deleteDraft(id));

        verify(excursionRepository, never()).delete(any());
    }

    @Test
    void deleteDraft_excursionWithRegistrations_throwsException() {
        UUID id = UUID.randomUUID();

        ShoreExcursion excursion = new ShoreExcursion();
        excursion.setStatus(ShoreExcursionStatus.PLANNED);

        when(excursionRepository.findById(id)).thenReturn(Optional.of(excursion));
        when(registrationRepository.existsByShoreExcursion_ShoreExcursionId(id)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> service.deleteDraft(id));

        verify(excursionRepository, never()).delete(any());
    }

    @Test
    void deleteDraft_unusedDraft_deletesExcursion() {
        UUID id = UUID.randomUUID();

        ShoreExcursion excursion = mock(ShoreExcursion.class);
        when(excursion.getStatus()).thenReturn(ShoreExcursionStatus.PLANNED);
        when(excursionRepository.findById(id)).thenReturn(Optional.of(excursion));
        when(registrationRepository.existsByShoreExcursion_ShoreExcursionId(id)).thenReturn(false);

        service.deleteDraft(id);

        verify(excursionRepository).delete(excursion);
    }

    @Test
    void checkAvailability_nonPositiveRequestedParticipants_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.checkAvailability(UUID.randomUUID(), 0));
    }

    @Test
    void checkAvailability_incompleteDraftIsUnavailableWithoutNullPointer() {
        UUID id = UUID.randomUUID();
        ShoreExcursion excursion = new ShoreExcursion();
        excursion.setStatus(ShoreExcursionStatus.PLANNED);
        when(excursionRepository.findById(id)).thenReturn(Optional.of(excursion));
        assertThrows(IllegalStateException.class, () -> service.checkAvailability(id, 1));
    }

    @Test
    void cancel_blankReason_throwsException() {
        UUID id = UUID.randomUUID();

        ShoreExcursion excursion = new ShoreExcursion();
        excursion.setStatus(ShoreExcursionStatus.OPEN);

        when(excursionRepository.findById(id)).thenReturn(Optional.of(excursion));

        assertThrows(IllegalArgumentException.class, () -> service.cancel(id, " "));
    }

    @Test
    void complete_nonInProgressExcursion_throwsException() {
        UUID id = UUID.randomUUID();

        ShoreExcursion excursion = new ShoreExcursion();
        excursion.setStatus(ShoreExcursionStatus.PLANNED);

        when(excursionRepository.findById(id)).thenReturn(Optional.of(excursion));

        assertThrows(IllegalStateException.class, () -> service.complete(id));
    }
}
