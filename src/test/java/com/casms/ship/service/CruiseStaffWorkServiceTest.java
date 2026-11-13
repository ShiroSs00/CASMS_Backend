package com.casms.ship.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.servicepackage.service.ShipServiceAvailability;
import com.casms.ship.service.CruiseStaffWorkService.ActivityKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class CruiseStaffWorkServiceTest {
    @Mock private JdbcTemplate jdbc;
    @Mock private ShipServiceAvailability shipServices;
    @InjectMocks private CruiseStaffWorkService work;

    private final UUID cruise = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();
    private final UUID staff = UUID.randomUUID();
    private final UUID occurrence = UUID.randomUUID();
    private final UUID definition = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();

    @BeforeEach
    void activeEmployeeOnCruise() {
        when(jdbc.queryForList(contains("FROM \"Cruise\""), eq(String.class), eq(cruise)))
                .thenReturn(List.of("PUBLISHED"));
        when(jdbc.queryForList(contains("FROM \"User\""), eq(String.class), eq(user)))
                .thenReturn(List.of("ACTIVE"));
        when(jdbc.queryForObject(contains("FROM \"UserRole\""), eq(Boolean.class), eq(user)))
                .thenReturn(true);
        when(jdbc.queryForList(contains("FROM \"CruiseStaff\""), eq(UUID.class), eq(cruise), eq(user)))
                .thenReturn(List.of(staff));
    }

    @Test
    void serviceReplacementClosesPreviousDutyBeforeCreatingNewDuty() {
        work.assignService(cruise, user, occurrence, actor);

        var order = inOrder(shipServices, jdbc);
        order.verify(shipServices).requireServiceForRequest(cruise, occurrence);
        order.verify(jdbc).queryForObject(contains("target.starts_at < existing.ends_at"),
                eq(Boolean.class), eq(user), eq(cruise), eq("SERVICE"), eq(occurrence), eq(true));
        order.verify(jdbc).update(contains("SET ended_at = now()"), eq(actor), eq(staff));
        order.verify(jdbc).update(contains("(cruise_staff_id, service_id, assigned_by)"),
                eq(staff), eq(occurrence), eq(actor));
    }

    @Test
    void shoreShiftCannotUseOccurrenceOutsideCruise() {
        when(jdbc.queryForList(contains("SELECT definition_id"), eq(UUID.class),
                eq(cruise), eq("SHORE"), eq(occurrence))).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class,
                () -> work.assignActivity(cruise, user, ActivityKind.SHORE, occurrence, actor));

        verify(jdbc, never()).update(contains("INSERT"), eq(staff), eq(occurrence), eq(actor));
    }

    @Test
    void activityRequiresQualificationForSourceDefinition() {
        when(jdbc.queryForList(contains("SELECT definition_id"), eq(UUID.class),
                eq(cruise), eq("ONBOARD"), eq(occurrence))).thenReturn(List.of(definition));

        var error = assertThrows(BusinessException.class,
                () -> work.assignActivity(cruise, user, ActivityKind.ONBOARD, occurrence, actor));

        assertEquals("STAFF_QUALIFICATION_REQUIRED", error.getCode());
        verify(jdbc).queryForObject(contains("FROM \"StaffActivityQualification\""),
                eq(Boolean.class), eq(user), eq(definition));
        verify(jdbc, never()).update(contains("INSERT"), eq(staff), eq(occurrence), eq(actor));
    }

    @Test
    void activityOverlappingAnExistingDutyIsRejectedBeforeInsert() {
        when(jdbc.queryForList(contains("SELECT definition_id"), eq(UUID.class),
                eq(cruise), eq("SHORE"), eq(occurrence))).thenReturn(List.of(definition));
        when(jdbc.queryForObject(contains("FROM \"CruiseStaffDuty\""), eq(Boolean.class),
                eq(staff), eq(occurrence))).thenReturn(false);
        when(jdbc.queryForObject(contains("FROM \"StaffActivityQualification\""),
                eq(Boolean.class), eq(user), eq(definition))).thenReturn(true);
        when(jdbc.queryForObject(contains("existing.starts_at < target.ends_at"),
                eq(Boolean.class), eq(user), eq(cruise), eq("SHORE"), eq(occurrence), eq(false)))
                .thenReturn(true);

        var error = assertThrows(BusinessException.class,
                () -> work.assignActivity(cruise, user, ActivityKind.SHORE, occurrence, actor));

        assertEquals("STAFF_WORK_TIME_CONFLICT", error.getCode());
        verify(jdbc, never()).update(contains("INSERT"), eq(staff), eq(occurrence), eq(actor));
    }
}
