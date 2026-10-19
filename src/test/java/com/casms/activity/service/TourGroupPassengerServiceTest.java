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

    @Test
    void listEligiblePassengers_groupNotFound_throwsException() {
        UUID groupId = UUID.randomUUID();
        when(tourGroupRepository.findById(groupId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> service.listEligiblePassengers(groupId));
    }

    @Test
    void listEligiblePassengers_returnsConfirmedRegistrationsExcludingAssigned() {
        UUID groupId = UUID.randomUUID();
        UUID excursionId = UUID.randomUUID();

        ShoreExcursion excursion = mock(ShoreExcursion.class);
        when(excursion.getShoreExcursionId()).thenReturn(excursionId);

        TourGroup tourGroup = mock(TourGroup.class);
        when(tourGroup.getShoreExcursion()).thenReturn(excursion);

        when(tourGroupRepository.findById(groupId)).thenReturn(Optional.of(tourGroup));

        UUID passenger1Id = UUID.randomUUID();
        UUID passenger2Id = UUID.randomUUID();

        Passenger p1 = new Passenger();
        p1.setPassengerId(passenger1Id);
        p1.setFullName("Nguyen Van A");

        Passenger p2 = new Passenger();
        p2.setPassengerId(passenger2Id);
        p2.setFullName("Tran Thi B");

        com.casms.activity.entity.Registration reg1 = new com.casms.activity.entity.Registration();
        reg1.setRegistrationId(UUID.randomUUID());
        reg1.setPassenger(p1);
        reg1.setStatus(com.casms.activity.enums.RegistrationStatus.CONFIRMED);

        com.casms.activity.entity.Registration reg2 = new com.casms.activity.entity.Registration();
        reg2.setRegistrationId(UUID.randomUUID());
        reg2.setPassenger(p2);
        reg2.setStatus(com.casms.activity.enums.RegistrationStatus.CONFIRMED);

        when(registrationRepository.findConfirmedRegistrationsByExcursion(eq(excursionId), any()))
                .thenReturn(java.util.List.of(reg1, reg2));

        // Passenger 1 is already assigned in an active status
        when(assignmentRepository.findAssignedPassengerIdsByExcursion(eq(excursionId), any()))
                .thenReturn(java.util.List.of(passenger1Id));

        var result = service.listEligiblePassengers(groupId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(passenger2Id, result.get(0).passengerId());
        assertEquals("Tran Thi B", result.get(0).fullName());
    }
}