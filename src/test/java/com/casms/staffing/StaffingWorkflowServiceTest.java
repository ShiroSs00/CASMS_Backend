package com.casms.staffing;

import com.casms.common.exception.BusinessException;
import com.casms.identity.enums.Role;
import com.casms.pos.PosAccessService;
import com.casms.pos.PosService;
import com.casms.ship.service.CruiseStaffService;
import com.casms.ship.service.CruiseWorkHandoffService;
import com.casms.ship.service.DeckServiceAssignmentService;
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
    private final PosAccessService posAccess = mock(PosAccessService.class);
    private final PosService pos = mock(PosService.class);
    private final CruiseWorkHandoffService handoffs = mock(CruiseWorkHandoffService.class);
    private final DeckServiceAssignmentService decks = mock(DeckServiceAssignmentService.class);
    private final CruiseStaffService cruiseStaff = mock(CruiseStaffService.class);
    private final StaffingWorkflowService subject =
            new StaffingWorkflowService(jdbc, handoffs, posAccess, pos, decks, cruiseStaff);

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
    void rejectMissingScheduleRevisionWithoutTouchingDatabase() {
        CasmsPrincipal planner = mock(CasmsPrincipal.class);
        when(planner.isEnabled()).thenReturn(true);
        when(planner.hasRole(Role.CRUISE_MANAGER)).thenReturn(true);
        when(planner.isAccountNonLocked()).thenReturn(true);
        when(planner.hasAuthority("CRUISE_PREPARATION_SUBMIT")).thenReturn(true);
        var error = assertThrows(BusinessException.class, () -> subject.submit(UUID.randomUUID(),
                new StaffingWorkflowService.CreateRequest(null, List.of()), planner));
        assertEquals("STAFFING_SCHEDULE_REVISION_REQUIRED", error.getCode());
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

    @Test
    void missingPosPreventsShipHandoffAndNotification() throws Exception {
        var review = newReview("PENDING_SHIP_REVIEW", Role.SHIP_MANAGER, "CRUISE_PREPARATION_READ");
        inspectShip(review.cruise(), UUID.randomUUID(), false, "initial");

        var error = assertThrows(BusinessException.class, () -> subject.transition(review.request(),
                "SHIP_CHECKED", new StaffingWorkflowService.ReviewRequest(4L, null), review.actor()));

        assertEquals("STAFFING_POS_COVERAGE_REQUIRED", error.getCode());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void validatedShipMovesToHrAndNotifiesCoordinator() throws Exception {
        var review = newReview("PENDING_SHIP_REVIEW", Role.SHIP_MANAGER, "CRUISE_PREPARATION_READ");
        inspectShip(review.cruise(), UUID.randomUUID(), true, "checked");
        stubSnapshot(review, "SENT_TO_HR");

        var result = subject.transition(review.request(), "SHIP_CHECKED",
                new StaffingWorkflowService.ReviewRequest(4L, null), review.actor());

        assertEquals("SENT_TO_HR", result.status());
        verify(jdbc).update(contains("ship_review_fingerprint=?"), eq("SENT_TO_HR"), eq("checked"),
                isNull(), eq(review.request()));
        verify(jdbc).update(contains("INSERT INTO \"Notification\""), eq(review.coordinator()),
                eq("STAFFING_SENT_TO_HR"), anyString(), contains("xác nhận phương án tàu"));
    }

    @Test
    void noReadyModelPreventsShipHandoff() throws Exception {
        var review = newReview("PENDING_SHIP_REVIEW", Role.SHIP_MANAGER, "CRUISE_PREPARATION_READ");
        inspectShip(review.cruise(), null, true, "no-model");

        var error = assertThrows(BusinessException.class, () -> subject.transition(review.request(),
                "SHIP_CHECKED", new StaffingWorkflowService.ReviewRequest(4L, null), review.actor()));

        assertEquals("STAFFING_SHIP_MODEL_REQUIRED", error.getCode());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void changedReadyModelOrPosLinksRequireAnotherShipReview() throws Exception {
        var review = newReview("SENT_TO_HR", Role.HR_MANAGER, "CRUISE_STAFF_ASSIGN");
        inspectShip(review.cruise(), UUID.randomUUID(), true, "changed");

        var error = assertThrows(BusinessException.class, () -> subject.transition(review.request(),
                "HR_SUBMITTED", new StaffingWorkflowService.ReviewRequest(4L, null), review.actor()));

        assertEquals("STAFFING_SHIP_REVIEW_STALE", error.getCode());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void hrCannotHandOffBeforeShipHasReviewedRoster() throws Exception {
        var review = newReview("HR_SUBMITTED", Role.HR_MANAGER, "CRUISE_STAFF_ASSIGN");

        var error = assertThrows(BusinessException.class, () -> subject.transition(review.request(),
                "HR_APPROVED", new StaffingWorkflowService.ReviewRequest(4L, null), review.actor()));

        assertEquals("STAFFING_INVALID_TRANSITION", error.getCode());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void hrMustGiveReasonToReturnShipReview() throws Exception {
        var review = newReview("SHIP_CONFIRMED", Role.HR_MANAGER, "CRUISE_STAFF_ASSIGN");

        var error = assertThrows(BusinessException.class, () -> subject.transition(review.request(),
                "HR_RETURN_SHIP", new StaffingWorkflowService.ReviewRequest(4L, " "), review.actor()));

        assertEquals("STAFFING_RETURN_REASON_REQUIRED", error.getCode());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void shipCannotCheckWhileAsmIsPending() throws Exception {
        var review = newReview("PENDING_ASM", Role.SHIP_MANAGER, "CRUISE_PREPARATION_READ", true);

        var error = assertThrows(BusinessException.class, () -> subject.transition(review.request(),
                "SHIP_CHECKED", new StaffingWorkflowService.ReviewRequest(4L, null), review.actor()));

        assertEquals("STAFFING_INVALID_TRANSITION", error.getCode());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void missingPlannedOnboardServicePlacementBlocksShipHandoff() throws Exception {
        var review = newReview("PENDING_SHIP_REVIEW", Role.SHIP_MANAGER, "CRUISE_PREPARATION_READ", true);
        inspectShip(review.cruise(), UUID.randomUUID(), true, "checked", true, false);

        var error = assertThrows(BusinessException.class, () -> subject.transition(review.request(),
                "SHIP_CHECKED", new StaffingWorkflowService.ReviewRequest(4L, null), review.actor()));

        assertEquals("STAFFING_SERVICE_POS_COVERAGE_REQUIRED", error.getCode());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void rejectsCrossShipTerminalBeforeAnyPosWrite() throws Exception {
        var review = newReview("PENDING_SHIP_REVIEW", Role.SHIP_MANAGER, "CRUISE_PREPARATION_READ", true);
        UUID areaId = UUID.randomUUID();
        UUID terminalId = UUID.randomUUID();
        var error = assertThrows(BusinessException.class, () -> subject.placeAreaPos(review.request(),
                areaId, new StaffingWorkflowService.AreaPosInput(terminalId, 4L), review.actor()));

        assertEquals("STAFFING_POS_PLACEMENT_OUT_OF_SCOPE", error.getCode());
        verifyNoInteractions(pos, posAccess);
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void plannedServicePlacementLinksPosDeckAndAreaAndAuditsRevision() throws Exception {
        var review = newReview("PENDING_SHIP_REVIEW", Role.SHIP_MANAGER, "CRUISE_PREPARATION_READ", true);
        UUID ship = UUID.randomUUID(), deck = UUID.randomUUID(), area = UUID.randomUUID();
        UUID terminal = UUID.randomUUID(), service = UUID.randomUUID();
        ResultSet target = mock(ResultSet.class);
        when(target.getObject(1, UUID.class)).thenReturn(ship);
        when(target.getObject(2, UUID.class)).thenReturn(deck);
        when(jdbc.query(contains("terminal.ship_id=area.ship_id"), any(RowMapper.class),
                eq(review.cruise()), eq(area), eq(terminal)))
                .thenAnswer(call -> List.of(call.<RowMapper<?>>getArgument(1).mapRow(target, 0)));
        when(jdbc.queryForObject(contains("StaffingPlannedOnboardService"), eq(Boolean.class),
                eq(review.cruise()), eq(service))).thenReturn(true);
        when(jdbc.queryForObject(contains("StaffingPlannedServicePlacement"), eq(Boolean.class),
                eq(review.cruise()), eq(service), eq(area), eq(terminal))).thenReturn(true);
        stubSnapshot(review, "PENDING_SHIP_REVIEW");

        var result = subject.placeService(review.request(), service,
                new StaffingWorkflowService.ServicePlacementInput(area, terminal, 4L), review.actor());

        assertEquals(5L, result.request().revision());
        UUID actorId = review.actor().getUserId();
        var order = inOrder(posAccess, decks, pos, jdbc);
        order.verify(posAccess).ensureAreaLinkForPlacement(terminal, area);
        order.verify(decks).assign(deck, service, ship);
        order.verify(pos).enableAreaServiceForPlacement(ship, area, service);
        order.verify(jdbc).update(contains("StaffingRequest"), eq(review.request()));
        order.verify(jdbc).update(contains("StaffingDecision"), eq(review.request()), eq(5L),
                eq("SERVICE_PLACED"), eq(actorId), contains("service=" + service));
    }

    private record ReviewFixture(UUID request, UUID cruise, UUID coordinator, CasmsPrincipal actor) {}

    private ReviewFixture newReview(String status, Role role, String authority) throws Exception {
        return newReview(status, role, authority, false);
    }

    @SuppressWarnings("unchecked")
    private ReviewFixture newReview(String status, Role role, String authority, boolean asmProgram) throws Exception {
        UUID request = UUID.randomUUID();
        UUID cruise = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID coordinator = UUID.randomUUID();
        CasmsPrincipal actor = mock(CasmsPrincipal.class);
        when(actor.getUserId()).thenReturn(actorId);
        when(actor.isEnabled()).thenReturn(true);
        when(actor.isAccountNonLocked()).thenReturn(true);
        when(actor.hasRole(role)).thenReturn(true);
        when(actor.hasAuthority(authority)).thenReturn(true);
        when(jdbc.queryForList(contains("SELECT cruise_id"), eq(UUID.class), eq(request)))
                .thenReturn(List.of(cruise));
        ResultSet cruiseRow = mock(ResultSet.class);
        when(cruiseRow.getString(1)).thenReturn("DRAFT");
        when(cruiseRow.getObject(3, UUID.class)).thenReturn(coordinator);
        when(jdbc.query(contains("FROM \"Cruise\""), any(RowMapper.class), eq(cruise)))
                .thenAnswer(call -> List.of(call.<RowMapper<?>>getArgument(1).mapRow(cruiseRow, 0)));
        ResultSet requestRow = mock(ResultSet.class);
        when(requestRow.getString(1)).thenReturn(status);
        when(requestRow.getLong(2)).thenReturn(4L);
        when(requestRow.getBoolean(7)).thenReturn(true);
        when(requestRow.getString(9)).thenReturn("checked");
        when(requestRow.getBoolean(10)).thenReturn(asmProgram);
        when(jdbc.query(contains("FOR UPDATE"), any(RowMapper.class), eq(request)))
                .thenAnswer(call -> List.of(call.<RowMapper<?>>getArgument(1).mapRow(requestRow, 0)));
        if (role == Role.SHIP_MANAGER) {
            when(jdbc.queryForObject(contains("role_in_cruise='SHIP_MANAGER'"), eq(Boolean.class),
                    eq(cruise), eq(actorId))).thenReturn(true);
        }
        return new ReviewFixture(request, cruise, coordinator, actor);
    }

    private void inspectShip(UUID cruiseId, UUID modelId, boolean posCovered, String fingerprint) throws Exception {
        inspectShip(cruiseId, modelId, posCovered, fingerprint, false, false);
    }

    @SuppressWarnings("unchecked")
    private void inspectShip(UUID cruiseId, UUID modelId, boolean posCovered, String fingerprint,
                             boolean asmCompleted, boolean serviceCovered) throws Exception {
        ResultSet inspection = mock(ResultSet.class);
        when(inspection.getObject(1, UUID.class)).thenReturn(modelId);
        when(inspection.getBoolean(2)).thenReturn(true);
        when(inspection.getBoolean(3)).thenReturn(posCovered);
        when(inspection.getString(4)).thenReturn(fingerprint);
        when(inspection.getBoolean(5)).thenReturn(asmCompleted);
        when(inspection.getBoolean(6)).thenReturn(serviceCovered);
        when(jdbc.queryForObject(contains("StaffingShipInspection"), any(RowMapper.class), eq(cruiseId)))
                .thenAnswer(call -> call.<RowMapper<?>>getArgument(1).mapRow(inspection, 0));
    }

    @SuppressWarnings("unchecked")
    private void stubSnapshot(ReviewFixture review, String status) throws Exception {
        ResultSet request = mock(ResultSet.class);
        when(request.getObject("request_id", UUID.class)).thenReturn(review.request());
        when(request.getObject("cruise_id", UUID.class)).thenReturn(review.cruise());
        when(request.getString("status")).thenReturn(status);
        when(request.getLong("revision")).thenReturn(5L);
        when(jdbc.query(contains("SELECT request_id,cruise_id"), any(RowMapper.class), eq(review.request())))
                .thenAnswer(call -> List.of(call.<RowMapper<?>>getArgument(1).mapRow(request, 0)));
        ResultSet cruise = mock(ResultSet.class);
        when(jdbc.queryForObject(contains("SELECT cruise_name"), any(RowMapper.class), eq(review.cruise())))
                .thenAnswer(call -> call.<RowMapper<?>>getArgument(1).mapRow(cruise, 0));
    }
}
