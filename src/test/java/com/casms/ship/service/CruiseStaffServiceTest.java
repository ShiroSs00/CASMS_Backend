package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.identity.entity.User;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.enums.Role;
import com.casms.identity.repository.UserRepository;
import com.casms.ship.dto.request.CruiseStaffRequest;
import com.casms.ship.dto.response.CruiseStaffResponse;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.CruiseStaff;
import com.casms.ship.repository.CruiseRepository;
import com.casms.ship.repository.CruiseStaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CruiseStaffServiceTest {
    @Mock
    private CruisePreparationService cruisePreparationService;

    @Mock
    private CruiseStaffRepository cruiseStaffRepository;

    @Mock
    private CruiseRepository cruiseRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CruiseStaffService cruiseStaffService;

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
        cruise.setDepartureDatetime(Instant.now().plusSeconds(3600));
        cruise.setReturnDatetime(Instant.now().plusSeconds(7200));

        user = new User();
        user.setUserId(userId);
        user.setUsername("captain_jack");
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(java.util.EnumSet.of(Role.ONBOARD_STAFF));
    }

    @Test
    @DisplayName("Gán nhân viên vào chuyến đi thành công khi thông tin hợp lệ")
    void assignStaff_Success() {
        CruiseStaffRequest req = new CruiseStaffRequest();
        req.setUserId(userId);
        req.setRoleInCruise("CAPTAIN");
        req.setNotes("Thuyền trưởng phụ trách chính");

        when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(cruise));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(cruiseStaffRepository.existsByCruise_CruiseIdAndUser_UserIdAndStatus(cruiseId, userId, "ACTIVE")).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(cruiseStaffRepository.findConflictingAssignments(any(), any(), any(), any())).thenReturn(List.of());

        CruiseStaff savedStaff = new CruiseStaff();
        savedStaff.setCruiseStaffId(UUID.randomUUID());
        savedStaff.setCruise(cruise);
        savedStaff.setUser(user);
        savedStaff.setRoleInCruise("CAPTAIN");
        savedStaff.setAssignedAt(Instant.now());
        savedStaff.setStatus("ACTIVE");
        savedStaff.setNotes("Thuyền trưởng phụ trách chính");

        when(cruiseStaffRepository.save(any(CruiseStaff.class))).thenReturn(savedStaff);

        CruiseStaffResponse resp = cruiseStaffService.assignStaff(cruiseId, req, userId);

        assertNotNull(resp);
        assertEquals("CAPTAIN", resp.getRoleInCruise());
        assertEquals("captain_jack", resp.getUsername());
        verify(cruiseStaffRepository).save(any(CruiseStaff.class));
    }

    @Test
    @DisplayName("Ném BusinessException khi nhân viên bị trùng lịch chuyến tàu khác")
    void assignStaff_ScheduleConflict_ThrowsException() {
        CruiseStaffRequest req = new CruiseStaffRequest();
        req.setUserId(userId);
        req.setRoleInCruise("CAPTAIN");

        when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(cruise));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(cruiseStaffRepository.existsByCruise_CruiseIdAndUser_UserIdAndStatus(cruiseId, userId, "ACTIVE")).thenReturn(false);

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

        BusinessException ex = assertThrows(BusinessException.class,
                () -> cruiseStaffService.assignStaff(cruiseId, req, userId));

        assertEquals("SHIP_STAFF_SCHEDULE_CONFLICT", ex.getCode());
        verify(cruiseStaffRepository, never()).save(any());
    }

    @Test
    @DisplayName("Ném BusinessException khi nhân viên đã được gán vào chuyến đi này")
    void assignStaff_DuplicateAssignment_ThrowsException() {
        CruiseStaffRequest req = new CruiseStaffRequest();
        req.setUserId(userId);
        req.setRoleInCruise("CAPTAIN");

        when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(cruise));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(cruiseStaffRepository.existsByCruise_CruiseIdAndUser_UserIdAndStatus(cruiseId, userId, "ACTIVE")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> cruiseStaffService.assignStaff(cruiseId, req, userId));

        assertEquals("DUPLICATE_STAFF_ASSIGNMENT", ex.getCode());
    }
}
