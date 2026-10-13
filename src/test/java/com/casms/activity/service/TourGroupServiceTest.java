package com.casms.activity.service;

import com.casms.activity.entity.ShoreExcursion;
import com.casms.activity.entity.TourGroup;
import com.casms.activity.enums.TourGroupStatus;
import com.casms.activity.repository.RegistrationRepository;
import com.casms.activity.repository.ShoreExcursionRepository;
import com.casms.activity.repository.TourGroupPassengerRepository;
import com.casms.activity.repository.TourGroupRepository;
import com.casms.identity.repository.PassengerRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TourGroupServiceTest {

    @Mock
    private TourGroupRepository tourGroupRepository;

    @Mock
    private TourGroupPassengerRepository passengerAssignmentRepository;

    @Mock
    private ShoreExcursionRepository shoreExcursionRepository;

    @Mock
    private RegistrationRepository registrationRepository;

    @Mock
    private PassengerRepository passengerRepository;

    @InjectMocks
    private TourGroupService service;

    @Test
    void list_withoutFilters_returnsGroups() {
        UUID groupId = UUID.randomUUID();
        UUID excursionId = UUID.randomUUID();

        ShoreExcursion excursion = mock(ShoreExcursion.class);
        when(excursion.getShoreExcursionId()).thenReturn(excursionId);

        TourGroup group = mock(TourGroup.class);
        when(group.getTourGroupId()).thenReturn(groupId);
        when(group.getShoreExcursion()).thenReturn(excursion);
        when(group.getStatus()).thenReturn(TourGroupStatus.PLANNED);
        when(group.getGuideId()).thenReturn(null);

        when(tourGroupRepository.findAll()).thenReturn(List.of(group));

        assertEquals(1, service.list(null, null, null).size());
    }

    @Test
    void detail_missingGroup_throwsException() {
        UUID id = UUID.randomUUID();

        when(tourGroupRepository.findById(id))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> service.detail(id)
        );
    }

    @Test
    void update_nonDraftGroup_throwsException() {
        UUID id = UUID.randomUUID();

        TourGroup group = new TourGroup();
        group.setStatus(TourGroupStatus.ASSEMBLING);

        when(tourGroupRepository.findById(id))
                .thenReturn(Optional.of(group));

        assertThrows(
                IllegalStateException.class,
                () -> service.update(id, null)
        );

        verify(tourGroupRepository, never()).save(any());
    }

    @Test
    void assignGuide_nullGuideId_throwsException() {
        UUID id = UUID.randomUUID();

        TourGroup group = new TourGroup();
        group.setStatus(TourGroupStatus.PLANNED);

        when(tourGroupRepository.findById(id))
                .thenReturn(Optional.of(group));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.assignGuide(id, null)
        );
    }

    @Test
    void confirm_nonDraftGroup_throwsException() {
        UUID id = UUID.randomUUID();

        TourGroup group = new TourGroup();
        group.setStatus(TourGroupStatus.CANCELLED);

        when(tourGroupRepository.findById(id))
                .thenReturn(Optional.of(group));

        assertThrows(
                IllegalStateException.class,
                () -> service.confirm(id)
        );
    }

    @Test
    void cancel_blankReason_throwsException() {
        UUID id = UUID.randomUUID();

        TourGroup group = new TourGroup();
        group.setStatus(TourGroupStatus.PLANNED);

        when(tourGroupRepository.findById(id))
                .thenReturn(Optional.of(group));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.cancel(id, " ")
        );
    }
}