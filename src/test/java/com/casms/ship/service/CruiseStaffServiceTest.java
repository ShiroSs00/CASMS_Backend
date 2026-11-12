package com.casms.ship.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.casms.common.exception.BusinessException;
import com.casms.audit.AuditRepository;
import com.casms.identity.entity.User;
import com.casms.identity.enums.Role;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.repository.UserRepository;
import com.casms.notification.service.StaffAssignmentEmailService;
import com.casms.notification.entity.Notification;
import com.casms.notification.repository.NotificationRepository;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.dto.request.CruiseStaffRequest;
import com.casms.ship.dto.response.CruiseStaffResponse;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.CruiseStaff;
import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.repository.CruiseRepository;
import com.casms.ship.repository.CruiseStaffRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class CruiseStaffServiceTest {
    @Mock private CruisePreparationService cruisePreparationService;

    @Mock private CruiseStaffRepository cruiseStaffRepository;

    @Mock private CruiseRepository cruiseRepository;

    @Mock private UserRepository userRepository;
    @Mock private StaffAssignmentEmailService assignmentEmail;
    @Mock private NotificationRepository notifications;
    @Mock private AuditRepository audit;

    @InjectMocks private CruiseStaffService cruiseStaffService;

    private UUID cruiseId;
    private UUID userId;
    private Cruise cruise;
    private User user;

    @BeforeEach
    void setUp() {
        cruiseId = UUID.randomUUID();
        userId = UUID.randomUUID();

        cruise = new Cruise();
        cruise.setCruiseId(cruiseId);
        cruise.setCruiseCode("CR-0001");
        cruise.setStatus(CruiseStatus.DRAFT);
        cruise.setDepartureDatetime(Instant.now().plusSeconds(3600));
        cruise.setReturnDatetime(Instant.now().plusSeconds(7200));

        user = new User();
        user.setUserId(userId);
        user.setUsername("captain_jack");
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(java.util.EnumSet.of(Role.ONBOARD_STAFF));
    }

    @Test
    void shipboardOptionsIncludeOnlyEligibleUsersAndTheirRoles() {
        User manager = new User();
        manager.setUserId(UUID.randomUUID());
        manager.setUsername("manager");
        manager.addRole(Role.SHIP_MANAGER);
        User finance = new User();
        finance.setUserId(UUID.randomUUID());
        finance.setUsername("finance");
        finance.addRole(Role.FINANCE_OFFICER);
        User agent = new User();
        agent.setUserId(UUID.randomUUID());
        agent.setUsername("agent");
        agent.addRole(Role.BOOKING_AGENT);
        when(cruiseRepository.existsById(cruiseId)).thenReturn(true);
        when(userRepository.findByStatus(UserStatus.ACTIVE)).thenReturn(List.of(user, manager, finance, agent));

        var options = cruiseStaffService.staffOptions(cruiseId, true);

        assertEquals(List.of("SHIP_MANAGER", "ONBOARD_STAFF", "FINANCE_OFFICER"), options.roles());
        assertEquals(List.of(
                new CruiseStaffService.StaffCandidate(userId, user.getUsername(), List.of("ONBOARD_STAFF")),
                new CruiseStaffService.StaffCandidate(finance.getUserId(), "finance", List.of("FINANCE_OFFICER")),
                new CruiseStaffService.StaffCandidate(manager.getUserId(), "manager", List.of("SHIP_MANAGER"))),
                options.users());
    }

    @Test
    void assignStaffRejectsShipboardRoleThatTheAccountDoesNotHave() {
        CruiseStaffRequest request = new CruiseStaffRequest();
        request.setUserId(userId);
        request.setRoleInCruise("SHIP_MANAGER");
        when(cruiseRepository.findByIdForUpdate(cruiseId)).thenReturn(Optional.of(cruise));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        var error = assertThrows(BusinessException.class,
                () -> cruiseStaffService.assignStaff(cruiseId, request, userId));

        assertEquals("INVALID_CRUISE_STAFF_ROLE", error.getCode());
        verify(cruiseStaffRepository, never()).saveAndFlush(any());
        verifyNoInteractions(assignmentEmail);
    }

    @Test
    @DisplayName("Gán nhân viên vào chuyến đi thành công khi thông tin hợp lệ")
    void assignStaff_Success() {
        CruiseStaffRequest req = new CruiseStaffRequest();
        req.setUserId(userId);
        req.setRoleInCruise("CAPTAIN");
        req.setNotes("Thuyền trưởng phụ trách chính");

        when(cruiseRepository.findByIdForUpdate(cruiseId)).thenReturn(Optional.of(cruise));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(cruiseStaffRepository.existsByCruise_CruiseIdAndUser_UserIdAndStatus(
                        cruiseId, userId, "ACTIVE"))
                .thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(cruiseStaffRepository.findConflictingAssignments(any(), any(), any(), any()))
                .thenReturn(List.of());

        CruiseStaff savedStaff = new CruiseStaff();
        savedStaff.setCruiseStaffId(UUID.randomUUID());
        savedStaff.setCruise(cruise);
        savedStaff.setUser(user);
        savedStaff.setRoleInCruise("CAPTAIN");
        savedStaff.setAssignedAt(Instant.now());
        savedStaff.setStatus("ACTIVE");
        savedStaff.setNotes("Thuyền trưởng phụ trách chính");

        when(cruiseStaffRepository.saveAndFlush(any(CruiseStaff.class))).thenReturn(savedStaff);
        when(assignmentEmail.enqueue(savedStaff)).thenReturn(StaffAssignmentEmailService.Status.QUEUED);

        CruiseStaffResponse resp = cruiseStaffService.assignStaff(cruiseId, req, userId);

        assertNotNull(resp);
        assertEquals("CAPTAIN", resp.getRoleInCruise());
        assertEquals("captain_jack", resp.getUsername());
        assertEquals(StaffAssignmentEmailService.Status.QUEUED, resp.getAssignmentEmailStatus());
        var order = inOrder(cruiseStaffRepository, assignmentEmail);
        order.verify(cruiseStaffRepository).saveAndFlush(any(CruiseStaff.class));
        order.verify(assignmentEmail).enqueue(savedStaff);
    }

    @Test
    @DisplayName("Ném BusinessException khi nhân viên bị trùng lịch chuyến tàu khác")
    void assignStaff_ScheduleConflict_ThrowsException() {
        CruiseStaffRequest req = new CruiseStaffRequest();
        req.setUserId(userId);
        req.setRoleInCruise("CAPTAIN");

        when(cruiseRepository.findByIdForUpdate(cruiseId)).thenReturn(Optional.of(cruise));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(cruiseStaffRepository.existsByCruise_CruiseIdAndUser_UserIdAndStatus(
                        cruiseId, userId, "ACTIVE"))
                .thenReturn(false);

        Cruise conflictingCruise = new Cruise();
        conflictingCruise.setCruiseId(UUID.randomUUID());
        conflictingCruise.setCruiseCode("CR-0099");
        conflictingCruise.setDepartureDatetime(Instant.now().plusSeconds(1800));
        conflictingCruise.setReturnDatetime(Instant.now().plusSeconds(9000));

        CruiseStaff conflict = new CruiseStaff();
        conflict.setCruise(conflictingCruise);
        conflict.setUser(user);

        when(cruiseStaffRepository.findConflictingAssignments(any(), any(), any(), any()))
                .thenReturn(List.of(conflict));

        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () -> cruiseStaffService.assignStaff(cruiseId, req, userId));

        assertEquals("SHIP_STAFF_SCHEDULE_CONFLICT", ex.getCode());
        verify(cruiseStaffRepository, never()).saveAndFlush(any());
        verifyNoInteractions(assignmentEmail);
    }

    @Test
    @DisplayName("Ném BusinessException khi nhân viên đã được gán vào chuyến đi này")
    void assignStaff_DuplicateAssignment_ThrowsException() {
        CruiseStaffRequest req = new CruiseStaffRequest();
        req.setUserId(userId);
        req.setRoleInCruise("CAPTAIN");

        when(cruiseRepository.findByIdForUpdate(cruiseId)).thenReturn(Optional.of(cruise));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(cruiseStaffRepository.existsByCruise_CruiseIdAndUser_UserIdAndStatus(
                        cruiseId, userId, "ACTIVE"))
                .thenReturn(true);

        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () -> cruiseStaffService.assignStaff(cruiseId, req, userId));

        assertEquals("DUPLICATE_STAFF_ASSIGNMENT", ex.getCode());
    }

    @Test
    void nonOverlappingOpenCruiseStillBlocksSecondAssignment() {
        var request = new CruiseStaffRequest();
        request.setUserId(userId);
        request.setRoleInCruise("ONBOARD_STAFF");
        when(cruiseRepository.findByIdForUpdate(cruiseId)).thenReturn(Optional.of(cruise));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(cruiseStaffRepository.findActiveAssignment(userId)).thenReturn(Optional.of(new CruiseStaff()));

        var error = assertThrows(BusinessException.class,
                () -> cruiseStaffService.assignStaff(cruiseId, request, userId));
        assertEquals("STAFF_ALREADY_ASSIGNED_TO_OPEN_CRUISE", error.getCode());
        verify(cruiseStaffRepository, never()).saveAndFlush(any());
    }

    @Test
    void inactiveAccountCannotBeAssigned() {
        var request = new CruiseStaffRequest();
        request.setUserId(userId);
        request.setRoleInCruise("ONBOARD_STAFF");
        user.setStatus(UserStatus.DISABLED);
        when(cruiseRepository.findByIdForUpdate(cruiseId)).thenReturn(Optional.of(cruise));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertEquals("INACTIVE_USER", assertThrows(BusinessException.class,
                () -> cruiseStaffService.assignStaff(cruiseId, request, userId)).getCode());
        verifyNoInteractions(assignmentEmail);
    }

    @Test
    void removalPreservesAuditAndNotifiesHrOnceOnRetry() {
        CruiseStaff staff = existingAssignment();
        User hr = new User();
        hr.setUserId(UUID.randomUUID());
        hr.setUsername("hr_operator");
        hr.addRole(Role.HR_MANAGER);
        when(userRepository.findById(hr.getUserId())).thenReturn(Optional.of(hr));

        cruiseStaffService.removeStaff(cruiseId, userId, staff.getCruiseStaffId(), "Đổi nhiệm vụ", hr.getUserId());
        cruiseStaffService.removeStaff(cruiseId, userId, staff.getCruiseStaffId(), "Đổi nhiệm vụ", hr.getUserId());

        assertEquals("REMOVED", staff.getStatus());
        assertEquals(hr, staff.getEndedBy());
        assertNotNull(staff.getEndedAt());
        assertEquals("Đổi nhiệm vụ", staff.getRemovalReason());
        var message = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).saveAndFlush(message.capture());
        assertEquals(hr, message.getValue().getRecipientUser());
        assertTrue(message.getValue().getContent().contains("CR-0001"));
        assertTrue(message.getValue().getContent().contains("Đổi nhiệm vụ"));
        verify(audit).save(any());
    }

    @Test
    void activeCruiseRemovalIsRejectedWithoutMutation() {
        CruiseStaff staff = existingAssignment();
        cruise.setStatus(CruiseStatus.IN_PROGRESS);
        assertEquals("CRUISE_STAFF_REMOVAL_NOT_ALLOWED", assertThrows(BusinessException.class,
                () -> cruiseStaffService.removeStaff(cruiseId, userId, staff.getCruiseStaffId(), null, userId)).getCode());
        assertEquals("ACTIVE", staff.getStatus());
        verifyNoInteractions(audit, notifications);
    }

    @Test
    void adminAccountCannotBeRemovedThroughHrAssignment() {
        CruiseStaff staff = existingAssignment();
        user.addRole(Role.ADMIN);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> cruiseStaffService.removeStaff(cruiseId, userId, staff.getCruiseStaffId(), null, userId));
        assertEquals("ACTIVE", staff.getStatus());
        verifyNoInteractions(audit, notifications);
    }

    @Test
    void serviceRejectsActorWithoutAssignmentPermission() {
        var actor = mock(CasmsPrincipal.class);
        when(actor.isEnabled()).thenReturn(true);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> cruiseStaffService.assignStaff(cruiseId, new CruiseStaffRequest(), actor));
        verifyNoInteractions(cruiseRepository, userRepository);
    }

    private CruiseStaff existingAssignment() {
        var staff = new CruiseStaff();
        staff.setCruiseStaffId(UUID.randomUUID());
        staff.setCruise(cruise);
        staff.setUser(user);
        staff.setRoleInCruise("ONBOARD_STAFF");
        when(cruiseRepository.findByIdForUpdate(cruiseId)).thenReturn(Optional.of(cruise));
        when(cruiseStaffRepository.findById(staff.getCruiseStaffId())).thenReturn(Optional.of(staff));
        return staff;
    }
}
