package com.casms.ship.service;

import com.casms.common.exception.ResourceNotFoundException;
import com.casms.identity.repository.UserRepository;
import com.casms.ship.repository.CruiseRepository;
import com.casms.ship.repository.CruiseStaffRepository;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.CruiseStaff;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class BaselineCruiseScopeTest {
    @Test
    void assignmentInCruiseACannotBeRemovedThroughCruiseB() {
        CruiseStaffRepository assignments = mock(CruiseStaffRepository.class);
        UserRepository users = mock(UserRepository.class);
        CruisePreparationService preparation = mock(CruisePreparationService.class);
        CruiseStaffService service = new CruiseStaffService(assignments, mock(CruiseRepository.class), users, preparation);
        UUID cruiseA = UUID.randomUUID(), cruiseB = UUID.randomUUID(), user = UUID.randomUUID();
        Cruise original = new Cruise();
        original.setCruiseId(cruiseA);
        CruiseStaff assignment = new CruiseStaff();
        assignment.setCruise(original);
        assignment.setStatus("ACTIVE");
        when(assignments.findFirstByCruise_CruiseIdAndUser_UserIdAndStatusOrderByAssignedAtDesc(any(), eq(user), eq("ACTIVE")))
                .thenAnswer(i -> cruiseA.equals(i.getArgument(0)) ? Optional.of(assignment) : Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.removeStaff(cruiseB, user, UUID.randomUUID()));
        verify(assignments).findFirstByCruise_CruiseIdAndUser_UserIdAndStatusOrderByAssignedAtDesc(cruiseB, user, "ACTIVE");
        verify(assignments, never()).findFirstByCruise_CruiseIdAndUser_UserIdAndStatusOrderByAssignedAtDesc(cruiseA, user, "ACTIVE");
        verify(assignments, never()).save(any());
        org.junit.jupiter.api.Assertions.assertEquals("ACTIVE", assignment.getStatus());
        verifyNoInteractions(users, preparation);
    }
}
