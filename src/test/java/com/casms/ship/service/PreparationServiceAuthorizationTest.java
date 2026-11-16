package com.casms.ship.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.casms.activity.repository.OnboardActivityRepository;
import com.casms.activity.repository.ShoreExcursionRepository;
import com.casms.common.exception.BusinessException;
import com.casms.identity.enums.Role;
import com.casms.identity.repository.UserRepository;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.servicepackage.repository.ServiceScheduleRepository;
import com.casms.ship.dto.request.*;
import com.casms.ship.entity.*;
import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;
import java.math.BigDecimal;
import java.sql.ResultSet;

@ExtendWith(MockitoExtension.class)
class PreparationServiceAuthorizationTest {
    @Mock CruiseRepository cruises;
    @Mock CruisePreparationSectionRepository sections;
    @Mock CruiseStaffRepository staff;
    @Mock DeckRepository decks;
    @Mock CabinRepository cabins;
    @Mock ShoreExcursionRepository tours;
    @Mock OnboardActivityRepository activities;
    @Mock ServiceScheduleRepository schedules;
    @Mock UserRepository users;
    @Mock JdbcTemplate jdbc;
    @Mock ItineraryProgramPolicy programPolicy;

    @Spy
    ObjectMapper mapper =
            new ObjectMapper().findAndRegisterModules();

    @InjectMocks CruisePreparationService service;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private CasmsPrincipal actor(UUID id, boolean crm, boolean ops, boolean grant) {
        var p = mock(CasmsPrincipal.class);
        lenient().when(p.getUserId()).thenReturn(id);
        lenient().when(p.hasRole(Role.CRUISE_MANAGER)).thenReturn(crm);
        lenient().when(p.hasRole(Role.OPERATIONS_MANAGER)).thenReturn(ops);
        lenient().when(p.hasAuthority(anyString())).thenReturn(grant);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(p, null, List.of()));
        return p;
    }

    @Test
    void callerSuppliedRolesAndActorCannotBypassActualPrincipal() {
        UUID id = UUID.randomUUID();
        actor(id, false, true, false);
        var r = new CruisePreparationSubmitRequest();
        r.setExpectedRevision(0L);
        r.setEvidence("Caller evidence");
        assertEquals(
                "PREPARATION_ACTION_FORBIDDEN",
                assertThrows(
                                BusinessException.class,
                                () ->
                                        service.submitSection(
                                                UUID.randomUUID(),
                                                "SAFETY_CREW",
                                                r,
                                                id,
                                                Set.of("CRUISE_MANAGER", "ADMIN")))
                        .getCode());
        assertEquals(
                "AUTH_REQUIRED",
                assertThrows(
                                BusinessException.class,
                                () ->
                                        service.submitSection(
                                                UUID.randomUUID(),
                                                "SAFETY_CREW",
                                                r,
                                                UUID.randomUUID(),
                                                Set.of()))
                        .getCode());
        verifyNoInteractions(cruises, sections, jdbc);
    }

    @Test
    void departmentalSubmissionDoesNotRequireCompletePrograms() {
        UUID id = UUID.randomUUID(), cruiseId = UUID.randomUUID();
        var assignee = actor(id, false, false, true);
        when(assignee.hasRole(Role.SHIP_MANAGER)).thenReturn(true);
        var cruise = new Cruise(); cruise.setCruiseId(cruiseId);
        var assignment = new CruiseStaff(); assignment.setRoleInCruise("SHIP_MANAGER");
        when(staff.findFirstByCruise_CruiseIdAndUser_UserIdAndStatusOrderByAssignedAtDesc(
                cruiseId, id, "ACTIVE")).thenReturn(Optional.of(assignment));
        var section = new CruisePreparationSection(); section.setCruise(cruise);
        section.setSectionCode("TECHNICAL_NAUTICAL"); section.setStatus("NOT_STARTED"); section.setRevision(1L);
        when(cruises.findByIdForUpdate(cruiseId)).thenReturn(Optional.of(cruise));
        when(sections.findByCruiseCruiseIdOrderBySectionCodeAsc(cruiseId)).thenReturn(List.of(section));
        when(sections.findByCruiseCruiseIdAndSectionCodeForUpdate(cruiseId,"TECHNICAL_NAUTICAL"))
                .thenReturn(Optional.of(section));
        when(sections.saveAndFlush(section)).thenReturn(section);
        var input = new CruisePreparationSubmitRequest(); input.setExpectedRevision(1L); input.setEvidence("Technical dossier");
        service.submitSection(cruiseId,"TECHNICAL_NAUTICAL",input,id,Set.of());
        assertEquals("SUBMITTED",section.getStatus());
        verifyNoInteractions(programPolicy);
    }

    @Test
    void unassignedShipManagerCannotSubmitTechnicalSection() {
        UUID id = UUID.randomUUID(), cruiseId = UUID.randomUUID();
        var assignee = actor(id, false, false, true);
        when(assignee.hasRole(Role.SHIP_MANAGER)).thenReturn(true);
        var cruise = new Cruise(); cruise.setCruiseId(cruiseId);
        when(cruises.findByIdForUpdate(cruiseId)).thenReturn(Optional.of(cruise));

        assertEquals("PREPARATION_CRUISE_SCOPE_FORBIDDEN", assertThrows(BusinessException.class, () ->
                service.submitSection(cruiseId, "TECHNICAL_NAUTICAL",
                        new CruisePreparationSubmitRequest(), id, Set.of())).getCode());
        verifyNoInteractions(sections);
    }

    @Test
    void rejectionStoresReviewedRevisionAndLeavesOtherSectionsAlone() {
        UUID ops = UUID.randomUUID(), cruiseId = UUID.randomUUID();
        actor(ops, false, true, true);
        var cruise = new Cruise(); cruise.setCruiseId(cruiseId);
        var section = new CruisePreparationSection(); section.setCruise(cruise);
        section.setSectionCode("HOTEL_CABIN"); section.setStatus("SUBMITTED"); section.setRevision(3L);
        section.setSubmittedByUserId(UUID.randomUUID());
        when(cruises.findByIdForUpdate(cruiseId)).thenReturn(Optional.of(cruise));
        when(sections.findByCruiseCruiseIdAndSectionCodeForUpdate(cruiseId, "HOTEL_CABIN"))
                .thenReturn(Optional.of(section));
        when(sections.saveAndFlush(section)).thenAnswer(call -> {
            section.setRevision(4L);
            return section;
        });
        var rejection = new CruisePreparationReturnRequest();
        rejection.setExpectedRevision(3L);
        rejection.setReason("Thiếu số liệu");

        service.rejectSection(cruiseId, "HOTEL_CABIN", rejection, ops);

        assertEquals("REJECTED", section.getStatus());
        verify(sections).saveAndFlush(section);
        verify(jdbc).update(anyString(), any(), eq(cruiseId), eq("HOTEL_CABIN"),
                eq(4L), eq("REJECT"), eq(ops), eq("Thiếu số liệu"), anyString(), eq("SUBMITTED"), eq(3L));
        assertEquals("STALE_REVISION", assertThrows(BusinessException.class, () ->
                service.rejectSection(cruiseId, "HOTEL_CABIN", rejection, ops)).getCode());
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void rejectedBudgetCanBeEditedAndSubmittedAgain() throws Exception {
        UUID coordinatorId = UUID.randomUUID(), cruiseId = UUID.randomUUID();
        var principal = actor(coordinatorId, true, false, true);
        var coordinator = new com.casms.identity.entity.User(); coordinator.setUserId(coordinatorId);
        var cruise = new Cruise(); cruise.setCruiseId(cruiseId); cruise.setPreparationCoordinator(coordinator);
        cruise.setStatus(CruiseStatus.DRAFT); cruise.setRevision(5L);
        cruise.setOperatingBudgetVnd(new BigDecimal("100000"));
        when(cruises.findByIdForUpdate(cruiseId)).thenReturn(Optional.of(cruise));
        when(jdbc.query(contains("CruiseOperatingBudgetReview"), ArgumentMatchers.<RowMapper<Object>>any(),
                eq(cruiseId))).thenAnswer(call -> {
            var row = mock(ResultSet.class);
            when(row.getLong(1)).thenReturn(2L);
            when(row.getString(2)).thenReturn("REJECTED");
            when(row.getObject(3, UUID.class)).thenReturn(coordinatorId);
            when(row.getString(4)).thenReturn("Điều chỉnh số tiền");
            return List.of(call.<RowMapper<Object>>getArgument(1).mapRow(row, 0));
        });
        when(cruises.saveAndFlush(cruise)).thenAnswer(call -> {
            cruise.setRevision(6L);
            return cruise;
        });

        var edited = service.saveOperatingBudget(cruiseId, 5L, new BigDecimal("125000"), principal);
        var submitted = service.submitOperatingBudget(cruiseId, 6L, 2L, coordinatorId);

        assertTrue(edited.canEdit());
        assertEquals("125000", submitted.amountVnd());
        assertEquals("SUBMITTED", submitted.status());
        assertEquals(3L, submitted.reviewRevision());
        assertFalse(submitted.canEdit());
        verify(jdbc).update(contains("ON CONFLICT"), eq(cruiseId), eq(3L), eq("SUBMITTED"),
                eq(coordinatorId), isNull());
    }

    @Test
    void crmRoleWithoutEffectiveGrantCannotSubmit() {
        UUID id = UUID.randomUUID();
        actor(id, true, false, false);
        assertEquals(
                "PREPARATION_ACTION_FORBIDDEN",
                assertThrows(
                                BusinessException.class,
                                () ->
                                        service.submitSection(
                                                UUID.randomUUID(),
                                                "TECHNICAL_NAUTICAL",
                                                new CruisePreparationSubmitRequest(),
                                                id,
                                                Set.of()))
                        .getCode());
        verifyNoInteractions(cruises, sections);
    }

    @Test
    void dualRoleSubmitterStillCannotConfirmOwnEvidence() {
        UUID id = UUID.randomUUID(), cruiseId = UUID.randomUUID();
        actor(id, true, true, true);
        var cruise = new Cruise();
        cruise.setCruiseId(cruiseId);
        var section = new CruisePreparationSection();
        section.setCruise(cruise);
        section.setStatus("SUBMITTED");
        section.setRevision(1L);
        section.setSubmittedByUserId(id);
        when(cruises.findByIdForUpdate(cruiseId)).thenReturn(Optional.of(cruise));
        when(sections.findByCruiseCruiseIdAndSectionCodeForUpdate(cruiseId, "HOTEL_CABIN"))
                .thenReturn(Optional.of(section));
        var r = new CruisePreparationConfirmRequest();
        r.setExpectedRevision(1L);
        assertEquals(
                "SELF_APPROVAL_FORBIDDEN",
                assertThrows(
                                BusinessException.class,
                                () ->
                                        service.confirmSection(
                                                cruiseId, "HOTEL_CABIN", r, id, "spoofed"))
                        .getCode());
        verify(sections, never()).saveAndFlush(any());
        verifyNoInteractions(jdbc);
    }

    @Test
    void missingAssignmentNeverFallsBackToAnyCrm() {
        UUID id = UUID.randomUUID(), cruiseId = UUID.randomUUID();
        actor(id, true, false, true);
        var cruise = new Cruise();
        cruise.setCruiseId(cruiseId);
        when(cruises.findByIdForUpdate(cruiseId)).thenReturn(Optional.of(cruise));
        assertEquals(
                "PREPARATION_CRUISE_SCOPE_FORBIDDEN",
                assertThrows(
                                BusinessException.class,
                                () ->
                                        service.submitSection(
                                                cruiseId,
                                                "HOTEL_CABIN",
                                                new CruisePreparationSubmitRequest(),
                                                id,
                                                Set.of()))
                        .getCode());
        verifyNoInteractions(sections, jdbc);
    }

    @Test
    void crmCannotBypassReadyControllerByCallingServiceWithOpsPermission() {
        UUID id = UUID.randomUUID();
        actor(id, true, false, true);
        var plans =
                new CruiseMainflowService(
                        jdbc,
                        mapper,
                        mock(PortWindowEvaluator.class),
                        mock(org.springframework.beans.factory.ObjectProvider.class), programPolicy);
        assertEquals(
                "READY_ACTION_FORBIDDEN",
                assertThrows(
                                BusinessException.class,
                                () ->
                                        plans.setReady(
                                                UUID.randomUUID(),
                                                new CruiseMainflowService.Action(1L, null, null),
                                                id))
                        .getCode());
        verifyNoInteractions(jdbc);
    }

    @Test
    void materialChangeInvalidatesConfirmedEvidenceWithActorTimeRevisionAndAudit() {
        UUID actorId = UUID.randomUUID(), cruiseId = UUID.randomUUID();
        actor(actorId, false, true, true);
        var cruise = new Cruise();
        cruise.setCruiseId(cruiseId);
        var section = new CruisePreparationSection();
        section.setCruise(cruise);
        section.setSectionCode("SAFETY_CREW");
        section.setStatus("CONFIRMED");
        section.setRevision(3L);
        when(sections.findByCruiseCruiseIdOrderBySectionCodeAsc(cruiseId))
                .thenReturn(List.of(section));
        when(sections.saveAndFlush(section))
                .thenAnswer(
                        i -> {
                            section.setRevision(4L);
                            return section;
                        });
        service.invalidateSignoffs(cruiseId, "Crew assignment removed");
        assertEquals("STALE", section.getStatus());
        assertEquals(actorId, section.getReturnedByUserId());
        assertNotNull(section.getReturnedAt());
        assertEquals(4L, section.getRevision());
        assertTrue(section.getReturnReason().contains("Crew assignment removed"));
        verify(jdbc)
                .update(
                        anyString(),
                        any(),
                        eq(cruiseId),
                        eq("SAFETY_CREW"),
                        eq(4L),
                        eq("INVALIDATE"),
                        eq(actorId),
                        eq("Crew assignment removed"),
                        anyString(),
                        eq("CONFIRMED"),
                        eq(3L));
    }
}
