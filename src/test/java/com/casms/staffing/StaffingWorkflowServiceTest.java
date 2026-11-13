package com.casms.staffing;

import com.casms.common.exception.BusinessException;
import com.casms.identity.enums.Role;
import com.casms.security.principal.CasmsPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class StaffingWorkflowServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final StaffingWorkflowService subject = new StaffingWorkflowService(jdbc);

    @Test
    void rejectNonPlannerWithoutTouchingDatabase() {
        CasmsPrincipal hr = mock(CasmsPrincipal.class);
        when(hr.isEnabled()).thenReturn(true);
        when(hr.hasRole(Role.CRUISE_MANAGER)).thenReturn(false);
        assertThrows(BusinessException.class, () -> subject.submit(UUID.randomUUID(),
                new StaffingWorkflowService.CreateRequest(1L,List.of()),hr));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectEmptyDemandWithoutTouchingDatabase() {
        CasmsPrincipal planner = mock(CasmsPrincipal.class);
        when(planner.isEnabled()).thenReturn(true);
        when(planner.hasRole(Role.CRUISE_MANAGER)).thenReturn(true);
        when(planner.isAccountNonLocked()).thenReturn(true);
        when(planner.hasAuthority("CRUISE_PREPARATION_SUBMIT")).thenReturn(true);
        var error = assertThrows(BusinessException.class, () -> subject.submit(UUID.randomUUID(),
                new StaffingWorkflowService.CreateRequest(1L, List.of()), planner));
        assertEquals("STAFFING_REQUIREMENTS_REQUIRED", error.getCode());
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectUnrecognizedActionWithoutTouchingDatabase() {
        CasmsPrincipal planner = mock(CasmsPrincipal.class);
        var error = assertThrows(BusinessException.class, () -> subject.transition(UUID.randomUUID(),
                "DELETE_ALL", new StaffingWorkflowService.ReviewRequest(0L, ""), planner));
        assertEquals("STAFFING_INVALID_ACTION", error.getCode());
        verifyNoInteractions(jdbc);
    }

    @Test
    void lockedOperationsManagerCannotApprove() {
        CasmsPrincipal actor = mock(CasmsPrincipal.class);
        when(actor.isEnabled()).thenReturn(true);
        when(actor.hasRole(Role.OPERATIONS_MANAGER)).thenReturn(true);
        when(actor.hasAuthority("CRUISE_VALIDATE")).thenReturn(true);

        var error = assertThrows(BusinessException.class, () -> subject.transition(UUID.randomUUID(),
                "OPS_APPROVED", new StaffingWorkflowService.ReviewRequest(3L, ""), actor));

        assertEquals("STAFFING_ROLE_REQUIRED", error.getCode());
        verifyNoInteractions(jdbc);
    }

    @Test
    @SuppressWarnings("unchecked")
    void staleRequestRevisionPreventsReview() throws Exception {
        UUID request = UUID.randomUUID();
        UUID cruise = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        CasmsPrincipal actor = mock(CasmsPrincipal.class);
        when(actor.getUserId()).thenReturn(actorId);
        when(actor.isEnabled()).thenReturn(true);
        when(actor.isAccountNonLocked()).thenReturn(true);
        when(actor.hasRole(Role.OPERATIONS_MANAGER)).thenReturn(true);
        when(actor.hasAuthority("CRUISE_VALIDATE")).thenReturn(true);
        when(jdbc.queryForList(contains("SELECT cruise_id"), eq(UUID.class), eq(request)))
                .thenReturn(List.of(cruise));
        ResultSet cruiseRow = mock(ResultSet.class);
        when(cruiseRow.getString(1)).thenReturn("DRAFT");
        when(jdbc.query(contains("FROM \"Cruise\""), any(RowMapper.class), eq(cruise)))
                .thenAnswer(call -> List.of(call.<RowMapper<?>>getArgument(1).mapRow(cruiseRow, 0)));
        ResultSet requestRow = mock(ResultSet.class);
        when(requestRow.getString(1)).thenReturn("PLANNER_CONFIRMED");
        when(requestRow.getLong(2)).thenReturn(4L);
        when(jdbc.query(contains("FOR UPDATE"), any(RowMapper.class), eq(request)))
                .thenAnswer(call -> List.of(call.<RowMapper<?>>getArgument(1).mapRow(requestRow, 0)));

        var error = assertThrows(BusinessException.class, () -> subject.transition(request,
                "OPS_APPROVED", new StaffingWorkflowService.ReviewRequest(3L, ""), actor));

        assertEquals("STAFFING_REVISION_CONFLICT", error.getCode());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
