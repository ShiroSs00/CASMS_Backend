package com.casms.ship.service;

import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.identity.entity.User;
import com.casms.identity.repository.UserRepository;
import com.casms.ship.dto.request.ItineraryCreateRequest;
import com.casms.ship.dto.request.ItineraryUpdateDraftRequest;
import com.casms.ship.dto.response.ItineraryResponse;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.Itinerary;
import com.casms.ship.entity.ItineraryDay;
import com.casms.ship.enums.ItineraryStatus;
import com.casms.ship.enums.ItineraryType;
import com.casms.ship.repository.CruiseRepository;
import com.casms.ship.repository.ItineraryDayRepository;
import com.casms.ship.repository.ItineraryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ItineraryServiceTest {

    @Mock
    private ItineraryRepository itineraryRepository;

    @Mock
    private ItineraryDayRepository itineraryDayRepository;

    @Mock
    private CruiseRepository cruiseRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ItineraryService itineraryService;

    private UUID cruiseId;
    private UUID creatorUserId;
    private UUID approverUserId;
    private Cruise sampleCruise;
    private User creatorUser;
    private User approverUser;
    private ItineraryCreateRequest createRequest;

    @BeforeEach
    void setUp() {
        cruiseId = UUID.randomUUID();
        creatorUserId = UUID.randomUUID();
        approverUserId = UUID.randomUUID();

        sampleCruise = new Cruise();
        sampleCruise.setCruiseId(cruiseId);
        sampleCruise.setCruiseCode("CR-2026-001");

        creatorUser = new User();
        creatorUser.setUserId(creatorUserId);
        creatorUser.setUsername("creator_staff");

        approverUser = new User();
        approverUser.setUserId(approverUserId);
        approverUser.setUsername("cruise_manager");

        createRequest = new ItineraryCreateRequest();
        createRequest.setCruiseId(cruiseId);
        createRequest.setItineraryName("Ha Long - Da Nang 7 Days");
        createRequest.setStartDate(LocalDate.of(2026, 11, 1));
        createRequest.setEndDate(LocalDate.of(2026, 11, 8));
        createRequest.setItineraryType(ItineraryType.ROUND_TRIP);
    }

    @Test
    void create_nullCreatorUserId_throwsUnauthorized() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> itineraryService.create(createRequest, null));
        assertEquals("AUTH_REQUIRED", ex.getCode());
    }

    @Test
    void create_cruiseNotFound_throwsResourceNotFound() {
        when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> itineraryService.create(createRequest, creatorUserId));
    }

    @Test
    void create_itineraryAlreadyExists_throwsException() {
        when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(sampleCruise));
        when(itineraryRepository.existsByCruiseCruiseId(cruiseId)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> itineraryService.create(createRequest, creatorUserId));
        assertEquals("ITINERARY_ALREADY_EXISTS", ex.getCode());
    }

    @Test
    void create_creatorNotFound_throwsResourceNotFound() {
        when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(sampleCruise));
        when(itineraryRepository.existsByCruiseCruiseId(cruiseId)).thenReturn(false);
        when(userRepository.findById(creatorUserId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> itineraryService.create(createRequest, creatorUserId));
    }

    @Test
    void create_invalidDateRange_throwsException() {
        when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(sampleCruise));
        when(itineraryRepository.existsByCruiseCruiseId(cruiseId)).thenReturn(false);
        when(userRepository.findById(creatorUserId)).thenReturn(Optional.of(creatorUser));

        createRequest.setEndDate(createRequest.getStartDate()); // same day or before

        BusinessException ex = assertThrows(BusinessException.class,
                () -> itineraryService.create(createRequest, creatorUserId));
        assertEquals("INVALID_DATE_RANGE", ex.getCode());
    }

    @Test
    void create_validRequest_createsItineraryWithCreatorFromPrincipal() {
        when(cruiseRepository.findById(cruiseId)).thenReturn(Optional.of(sampleCruise));
        when(itineraryRepository.existsByCruiseCruiseId(cruiseId)).thenReturn(false);
        when(userRepository.findById(creatorUserId)).thenReturn(Optional.of(creatorUser));
        when(itineraryRepository.save(any(Itinerary.class))).thenAnswer(invocation -> {
            Itinerary it = invocation.getArgument(0);
            it.setItineraryId(UUID.randomUUID());
            return it;
        });

        ItineraryResponse response = itineraryService.create(createRequest, creatorUserId);

        assertNotNull(response);
        assertEquals(creatorUserId, response.getCreatedByUserId());
        assertEquals("Ha Long - Da Nang 7 Days", response.getItineraryName());
        assertEquals(ItineraryStatus.DRAFT, response.getStatus());
        assertNull(response.getApprovedByUserId());
        assertNull(response.getApprovedAt());
    }

    @Test
    void updateDraft_nonDraftStatus_throwsException() {
        UUID itineraryId = UUID.randomUUID();
        Itinerary itinerary = new Itinerary();
        itinerary.setItineraryId(itineraryId);
        itinerary.setStatus(ItineraryStatus.APPROVED);

        when(itineraryRepository.findById(itineraryId)).thenReturn(Optional.of(itinerary));

        ItineraryUpdateDraftRequest updateReq = new ItineraryUpdateDraftRequest();
        updateReq.setItineraryName("Updated Name");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> itineraryService.updateDraft(itineraryId, updateReq));
        assertEquals("ITINERARY_NOT_DRAFT", ex.getCode());
    }

    @Test
    void updateDraft_validDraft_updatesFieldsWithoutSettingApproval() {
        UUID itineraryId = UUID.randomUUID();
        Itinerary itinerary = new Itinerary();
        itinerary.setItineraryId(itineraryId);
        itinerary.setStatus(ItineraryStatus.DRAFT);
        itinerary.setItineraryName("Old Name");
        itinerary.setStartDate(LocalDate.of(2026, 11, 1));
        itinerary.setEndDate(LocalDate.of(2026, 11, 8));

        when(itineraryRepository.findById(itineraryId)).thenReturn(Optional.of(itinerary));
        when(itineraryRepository.save(any(Itinerary.class))).thenAnswer(i -> i.getArgument(0));

        ItineraryUpdateDraftRequest updateReq = new ItineraryUpdateDraftRequest();
        updateReq.setItineraryName("New Itinerary Name");

        ItineraryResponse response = itineraryService.updateDraft(itineraryId, updateReq);

        assertNotNull(response);
        assertEquals("New Itinerary Name", response.getItineraryName());
        assertEquals(ItineraryStatus.DRAFT, response.getStatus());
        assertNull(response.getApprovedByUserId(), "Draft update must not set approver");
        assertNull(response.getApprovedAt(), "Draft update must not set approvedAt timestamp");
    }

    @Test
    void approve_nonPendingStatus_throwsException() {
        UUID itineraryId = UUID.randomUUID();
        Itinerary itinerary = new Itinerary();
        itinerary.setItineraryId(itineraryId);
        itinerary.setStatus(ItineraryStatus.DRAFT);

        when(itineraryRepository.findById(itineraryId)).thenReturn(Optional.of(itinerary));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> itineraryService.approve(itineraryId, approverUserId));
        assertEquals("INVALID_STATUS", ex.getCode());
    }

    @Test
    void approve_pendingApproval_setsApprovedAndApproverUser() {
        UUID itineraryId = UUID.randomUUID();
        Itinerary itinerary = new Itinerary();
        itinerary.setItineraryId(itineraryId);
        itinerary.setStatus(ItineraryStatus.PENDING_APPROVAL);

        when(itineraryRepository.findById(itineraryId)).thenReturn(Optional.of(itinerary));
        when(userRepository.findById(approverUserId)).thenReturn(Optional.of(approverUser));
        when(itineraryRepository.save(any(Itinerary.class))).thenAnswer(i -> i.getArgument(0));

        ItineraryResponse response = itineraryService.approve(itineraryId, approverUserId);

        assertNotNull(response);
        assertEquals(ItineraryStatus.APPROVED, response.getStatus());
        assertEquals(approverUserId, response.getApprovedByUserId());
        assertNotNull(response.getApprovedAt());
    }

    @Test
    void reject_nonPendingStatus_throwsException() {
        UUID itineraryId = UUID.randomUUID();
        Itinerary itinerary = new Itinerary();
        itinerary.setItineraryId(itineraryId);
        itinerary.setStatus(ItineraryStatus.DRAFT);

        when(itineraryRepository.findById(itineraryId)).thenReturn(Optional.of(itinerary));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> itineraryService.reject(itineraryId, approverUserId, "Budget unfeasible"));
        assertEquals("INVALID_STATUS", ex.getCode());
    }

    @Test
    void reject_pendingApproval_setsRejectedAndApproverUser() {
        UUID itineraryId = UUID.randomUUID();
        Itinerary itinerary = new Itinerary();
        itinerary.setItineraryId(itineraryId);
        itinerary.setStatus(ItineraryStatus.PENDING_APPROVAL);

        when(itineraryRepository.findById(itineraryId)).thenReturn(Optional.of(itinerary));
        when(userRepository.findById(approverUserId)).thenReturn(Optional.of(approverUser));
        when(itineraryRepository.save(any(Itinerary.class))).thenAnswer(i -> i.getArgument(0));

        ItineraryResponse response = itineraryService.reject(itineraryId, approverUserId, "Budget unfeasible");

        assertNotNull(response);
        assertEquals(ItineraryStatus.REJECTED, response.getStatus());
        assertEquals(approverUserId, response.getApprovedByUserId());
        assertNotNull(response.getApprovedAt());
    }

    @Test
    void approve_sameUserAsCreator_throwsSelfApprovalForbidden() {
        UUID itineraryId = UUID.randomUUID();
        Itinerary itinerary = new Itinerary();
        itinerary.setItineraryId(itineraryId);
        itinerary.setStatus(ItineraryStatus.PENDING_APPROVAL);
        itinerary.setCreatedByUser(approverUser); // Người duyệt cũng chính là người tạo!

        when(itineraryRepository.findById(itineraryId)).thenReturn(Optional.of(itinerary));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> itineraryService.approve(itineraryId, approverUserId));
        assertEquals("SELF_APPROVAL_FORBIDDEN", ex.getCode());
    }

    @Test
    void returnToDraft_rejectedStatus_success() {
        UUID itineraryId = UUID.randomUUID();
        Itinerary itinerary = new Itinerary();
        itinerary.setItineraryId(itineraryId);
        itinerary.setStatus(ItineraryStatus.REJECTED);

        when(itineraryRepository.findById(itineraryId)).thenReturn(Optional.of(itinerary));
        when(itineraryRepository.save(any(Itinerary.class))).thenAnswer(i -> i.getArgument(0));

        ItineraryResponse response = itineraryService.returnToDraft(itineraryId, creatorUserId);
        assertNotNull(response);
        assertEquals(ItineraryStatus.DRAFT, response.getStatus());
    }

    @Test
    void validateFeasibility_emptyDays_throwsEmptyDaysException() {
        UUID itineraryId = UUID.randomUUID();
        Itinerary itinerary = new Itinerary();
        itinerary.setItineraryId(itineraryId);
        itinerary.setStartDate(LocalDate.of(2026, 11, 1));
        itinerary.setEndDate(LocalDate.of(2026, 11, 8));
        itinerary.setDays(List.of());

        when(itineraryRepository.findById(itineraryId)).thenReturn(Optional.of(itinerary));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> itineraryService.validateFeasibility(itineraryId));
        assertEquals("EMPTY_ITINERARY_DAYS", ex.getCode());
    }

    @Test
    void validateFeasibility_dayCountMismatch_throwsInvalidDayCount() {
        UUID itineraryId = UUID.randomUUID();
        Itinerary itinerary = new Itinerary();
        itinerary.setItineraryId(itineraryId);
        itinerary.setStartDate(LocalDate.of(2026, 11, 1));
        itinerary.setEndDate(LocalDate.of(2026, 11, 3)); // Khoảng cách 3 ngày (1, 2, 3)

        ItineraryDay day1 = new ItineraryDay();
        day1.setDayNumber(1);
        day1.setDate(LocalDate.of(2026, 11, 1));
        itinerary.setDays(new java.util.ArrayList<>(List.of(day1))); // Chỉ có 1 ngày thay vì 3

        when(itineraryRepository.findById(itineraryId)).thenReturn(Optional.of(itinerary));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> itineraryService.validateFeasibility(itineraryId));
        assertEquals("INVALID_DAY_COUNT", ex.getCode());
    }
}
