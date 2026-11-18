package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.identity.enums.Role;
import com.casms.security.principal.CasmsPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class CruiseWorkHandoffServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ItineraryProgramPolicy policy = mock(ItineraryProgramPolicy.class);
    private final CruiseWorkHandoffService service = new CruiseWorkHandoffService(jdbc, policy);
    private final UUID cruise = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();

    @Test
    void linkedAsmCompletionPromotesStaffingAndRecordsDecision() {
        var asm = linkedAsm();
        when(policy.issues(cruise)).thenReturn(List.of());
        var task = mock(CruiseWorkHandoffService.Task.class);
        stubCompletedTask(task);

        var completed = service.respond(cruise, CruiseWorkHandoffService.Group.ASM,
                CruiseWorkHandoffService.Action.COMPLETE, 1L, "Program ready", asm);

        assertEquals(task, completed);
        verify(jdbc).update(contains("WITH promoted AS"), eq(cruise), eq(user));
    }

    @Test
    void incompleteLinkedAsmProgramCannotPromoteStaffing() {
        var asm = linkedAsm();
        when(policy.issues(cruise)).thenReturn(List.of(
                new ItineraryProgramPolicy.Issue("ACTIVITY_AREA_REQUIRED", "ACTIVITY", UUID.randomUUID(), "Area required")));

        var error = assertThrows(BusinessException.class, () -> service.respond(cruise,
                CruiseWorkHandoffService.Group.ASM, CruiseWorkHandoffService.Action.COMPLETE, 1L, "Review", asm));

        assertEquals("ITINERARY_PROGRAMS_INCOMPLETE", error.getCode());
        verify(jdbc, never()).update(contains("WITH promoted AS"), eq(cruise), eq(user));
    }

    @Test
    void directShipHandoffCannotBypassLinkedStaffing() {
        var ship = actor(Role.SHIP_MANAGER, "CRUISE_PREPARATION_READ");
        when(jdbc.queryForObject(contains("asm_program_workflow"), eq(Boolean.class), eq(cruise))).thenReturn(true);

        var error = assertThrows(BusinessException.class, () -> service.respond(cruise,
                CruiseWorkHandoffService.Group.SHIP, CruiseWorkHandoffService.Action.COMPLETE, 1L, "Review", ship));

        assertEquals("HANDOFF_MANAGED_BY_STAFFING", error.getCode());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    private CasmsPrincipal linkedAsm() {
        var asm = actor(Role.ACTIVITY_SERVICE_MANAGER, "TOUR_PACKAGE_MANAGE");
        when(jdbc.queryForObject(contains("coalesce(p.status"), eq(Boolean.class), eq(cruise))).thenReturn(true);
        when(jdbc.update(contains("UPDATE"), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForObject(contains("status='PENDING_ASM'"), eq(Boolean.class), eq(cruise))).thenReturn(true);
        return asm;
    }

    private CasmsPrincipal actor(Role role, String permission) {
        var actor = mock(CasmsPrincipal.class);
        when(actor.getUserId()).thenReturn(user);
        when(actor.hasRole(role)).thenReturn(true);
        when(actor.hasAuthority(permission)).thenReturn(true);
        return actor;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubCompletedTask(CruiseWorkHandoffService.Task task) {
        when(jdbc.query(contains("SELECT c.cruise_id,c.cruise_name"), any(RowMapper.class),
                eq(cruise), eq("ASM"))).thenReturn(List.of(task));
    }
}
