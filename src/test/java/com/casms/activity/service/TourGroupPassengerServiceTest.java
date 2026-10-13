package com.casms.activity.service;

import com.casms.activity.entity.ShoreExcursion;
import com.casms.activity.entity.TourGroup;
import com.casms.activity.entity.TourGroupPassenger;
import com.casms.activity.enums.TourGroupPassengerAssignmentStatus;
import com.casms.activity.repository.RegistrationRepository;
import com.casms.activity.repository.TourGroupPassengerRepository;
import com.casms.activity.repository.TourGroupRepository;
import com.casms.identity.entity.Passenger;
import com.casms.identity.repository.PassengerRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TourGroupPassengerServiceTest {

    @Mock
    private TourGroupPassengerRepository assignmentRepository;

    @Mock
    private TourGroupRepository tourGroupRepository;

    @Mock
    private PassengerRepository passengerRepository;

    @Mock
    private RegistrationRepository registrationRepository;

    @InjectMocks
    private TourGroupPassengerService service;

    @Test
    void assign_nullRequest_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.assign(null)
        );
    }

    @Test
    void detail_nullId_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.detail(null)
        );
    }

    @Test
    void detail_missingAssignment_throwsException() {
        UUID id = UUID.randomUUID();

        when(assignmentRepository.findDetailById(id))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> service.detail(id)
        );
    }

    @Test
    void move_nullTargetGroupId_throwsException() {
        UUID assignmentId = UUID.randomUUID();

        when(assignmentRepository.findByIdForUpdate(assignmentId))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> service.move(assignmentId, null)
        );
    }

    @Test
    void remove_missingAssignment_throwsException() {
        UUID id = UUID.randomUUID();

        when(assignmentRepository.findByIdForUpdate(id))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> service.remove(id)
        );
    }

    @Test
    void recordDeparture_missingAssignment_throwsException() {
        UUID id = UUID.randomUUID();

        when(assignmentRepository.findByIdForUpdate(id))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> service.recordDeparture(id)
        );
    }

    @Test
    void recordReturn_missingAssignment_throwsException() {
        UUID id = UUID.randomUUID();

        when(assignmentRepository.findByIdForUpdate(id))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> service.recordReturn(id)
        );
    }

    @Test
    void remove_cancelledAssignment_doesNotSaveAgain() {
        UUID assignmentId = UUID.randomUUID();
        UUID tourGroupId = UUID.randomUUID();
        UUID excursionId = UUID.randomUUID();
        UUID passengerId = UUID.randomUUID();

        ShoreExcursion excursion = mock(ShoreExcursion.class);
        when(excursion.getShoreExcursionId()).thenReturn(excursionId);

        TourGroup tourGroup = mock(TourGroup.class);
        when(tourGroup.getTourGroupId()).thenReturn(tourGroupId);
        when(tourGroup.getShoreExcursion()).thenReturn(excursion);

        Passenger passenger = mock(Passenger.class);
        when(passenger.getPassengerId()).thenReturn(passengerId);

        TourGroupPassenger assignment = mock(TourGroupPassenger.class);
        when(assignment.getAssignmentStatus())
                .thenReturn(TourGroupPassengerAssignmentStatus.CANCELLED);
        when(assignment.getTourGroup()).thenReturn(tourGroup);
        when(assignment.getPassenger()).thenReturn(passenger);

        when(assignmentRepository.findByIdForUpdate(assignmentId))
                .thenReturn(Optional.of(assignment));

        assertNotNull(service.remove(assignmentId));

        verify(assignmentRepository, never()).save(assignment);
    }
}