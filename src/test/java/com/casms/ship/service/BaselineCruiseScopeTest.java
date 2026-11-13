package com.casms.ship.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.casms.common.exception.ResourceNotFoundException;
import com.casms.identity.repository.UserRepository;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.CruiseStaff;
import com.casms.ship.repository.CruiseRepository;
import com.casms.ship.repository.CruiseStaffRepository;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

class BaselineCruiseScopeTest {
    @Test
    void assignmentInCruiseACannotBeRemovedThroughCruiseB() {
        CruiseStaffRepository assignments = mock(CruiseStaffRepository.class);
        UserRepository users = mock(UserRepository.class);
        CruisePreparationService preparation = mock(CruisePreparationService.class);
        CruiseRepository cruises = mock(CruiseRepository.class);
        CruiseStaffService service =
                new CruiseStaffService(
                        assignments, cruises, users, preparation,
                        mock(com.casms.notification.service.StaffAssignmentEmailService.class),
                        mock(com.casms.notification.repository.NotificationRepository.class),
                        mock(com.casms.audit.AuditRepository.class));
        UUID cruiseA = UUID.randomUUID(), cruiseB = UUID.randomUUID(), user = UUID.randomUUID();
        Cruise original = new Cruise();
        original.setCruiseId(cruiseA);
        Cruise other = new Cruise();
        other.setCruiseId(cruiseB);
        when(cruises.findByIdForUpdate(cruiseB)).thenReturn(Optional.of(other));
        CruiseStaff assignment = new CruiseStaff();
        assignment.setCruise(original);
        assignment.setStatus("ACTIVE");
        when(assignments.findFirstByCruise_CruiseIdAndUser_UserIdOrderByAssignedAtDesc(
                        any(), eq(user)))
                .thenAnswer(
                        i ->
                                cruiseA.equals(i.getArgument(0))
                                        ? Optional.of(assignment)
                                        : Optional.empty());
        UUID removedBy = UUID.randomUUID();
        assertThrows(
                ResourceNotFoundException.class,
                () -> service.removeStaff(cruiseB, user, null, null, removedBy));
        verify(assignments)
                .findFirstByCruise_CruiseIdAndUser_UserIdOrderByAssignedAtDesc(cruiseB, user);
        verify(assignments, never())
                .findFirstByCruise_CruiseIdAndUser_UserIdOrderByAssignedAtDesc(cruiseA, user);
        verify(assignments, never()).saveAndFlush(any());
        org.junit.jupiter.api.Assertions.assertEquals("ACTIVE", assignment.getStatus());
        verify(users).lockForStaffAssignment(user);
        verifyNoMoreInteractions(users);
        verifyNoInteractions(preparation);
    }
}
