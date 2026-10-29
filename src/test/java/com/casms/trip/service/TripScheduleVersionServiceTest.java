package com.casms.trip.service;

import com.casms.common.exception.BusinessException;
import com.casms.identity.entity.User;
import com.casms.identity.repository.UserRepository;
import com.casms.ship.entity.Port;
import com.casms.ship.enums.CruiseStopArrivalMethod;
import com.casms.ship.enums.CruiseStopCallType;
import com.casms.ship.enums.ItineraryDayType;
import com.casms.ship.enums.PortLocationType;
import com.casms.ship.enums.PortStatus;
import com.casms.ship.repository.DestinationRepository;
import com.casms.ship.repository.PortRepository;
import com.casms.audit.AuditLog;
import com.casms.audit.AuditRepository;
import com.casms.audit.AuditService;
import com.casms.trip.dto.request.*;
import com.casms.trip.dto.response.TripAuditResponse;
import com.casms.trip.dto.response.TripScheduleVersionDetailResponse;
import com.casms.trip.dto.response.ValidationReport;
import com.casms.trip.entity.*;
import com.casms.trip.enums.TripPlanEntryKind;
import com.casms.trip.enums.TripScheduleVersionStatus;
import com.casms.trip.enums.TripStatus;
import com.casms.trip.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripScheduleVersionServiceTest {

    @Mock
    private TripRepository tripRepository;

    @Mock
    private TripScheduleVersionRepository versionRepository;

    @Mock
    private TripPlanDayRepository dayRepository;

    @Mock
    private TripPlanStopRepository stopRepository;

    @Mock
    private TripPlanEntryRepository entryRepository;

    @Mock
    private PortRepository portRepository;

    @Mock
    private DestinationRepository destinationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditRepository auditRepository;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private TripScheduleVersionService versionService;

    private UUID tripId;
    private UUID creatorId;
    private UUID submitterId;
    private UUID approverId;
    private User creator;
    private User submitter;
    private User approver;
    private Trip trip;
    private TripScheduleVersion version;
    private Port samplePort;

    @BeforeEach
    void setUp() {
        tripId = UUID.randomUUID();
        creatorId = UUID.randomUUID();
        submitterId = UUID.randomUUID();
        approverId = UUID.randomUUID();

        creator = new User();
        creator.setUserId(creatorId);
        creator.setUsername("creator");

        submitter = new User();
        submitter.setUserId(submitterId);
        submitter.setUsername("submitter");

        approver = new User();
        approver.setUserId(approverId);
        approver.setUsername("approver");

        trip = new Trip();
        trip.setTripId(tripId);
        trip.setTripCode("TR-1001");
        trip.setName("Vịnh Hạ Long 2N1Đ");
        trip.setDurationDays(2);
        trip.setDurationNights(1);
        trip.setCreatedByUser(creator);
        trip.setStatus(TripStatus.DRAFT);

        version = new TripScheduleVersion();
        version.setVersionId(UUID.randomUUID());
        version.setTrip(trip);
        version.setVersionNumber(1);
        version.setStatus(TripScheduleVersionStatus.DRAFT);
        version.setRevision(1L);

        List<TripPlanDay> days = new ArrayList<>();
        TripPlanDay d1 = new TripPlanDay();
        d1.setDayId(UUID.randomUUID());
        d1.setVersion(version);
        d1.setDayNumber(1);
        d1.setShipTimeZoneId("Asia/Ho_Chi_Minh");
        days.add(d1);

        TripPlanDay d2 = new TripPlanDay();
        d2.setDayId(UUID.randomUUID());
        d2.setVersion(version);
        d2.setDayNumber(2);
        d2.setShipTimeZoneId("Asia/Ho_Chi_Minh");
        days.add(d2);

        version.setDays(days);

        samplePort = new Port();
        samplePort.setPortId(UUID.randomUUID());
        samplePort.setPortCode("VN-HPH");
        samplePort.setPortName("Cảng Hải Phòng");
        samplePort.setCountryCode("VN");
        samplePort.setTimeZoneId("Asia/Ho_Chi_Minh");
        samplePort.setLocationType(PortLocationType.PORT);
        samplePort.setStatus(PortStatus.ACTIVE);
    }

    @Test
    void testSaveDraftFailsIfVersionFrozen() {
        version.setStatus(TripScheduleVersionStatus.IN_REVIEW);
        when(versionRepository.findByTrip_TripIdAndVersionNumber(tripId, 1)).thenReturn(Optional.of(version));

        ScheduleDraftSaveRequest request = new ScheduleDraftSaveRequest();
        request.setExpectedRevision(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> versionService.saveDraft(tripId, 1, request, creatorId));
        assertEquals("VERSION_FROZEN", ex.getCode());
    }

    @Test
    void testSaveDraftOptimisticLockConflict() {
        when(versionRepository.findByTrip_TripIdAndVersionNumber(tripId, 1)).thenReturn(Optional.of(version));

        ScheduleDraftSaveRequest request = new ScheduleDraftSaveRequest();
        request.setExpectedRevision(999L); // Mismatch

        BusinessException ex = assertThrows(BusinessException.class,
                () -> versionService.saveDraft(tripId, 1, request, creatorId));
        assertEquals("OPTIMISTIC_LOCK_CONFLICT", ex.getCode());
    }

    @Test
    void testSaveDraftShoreExcursionRequiresStopClientKey() {
        when(versionRepository.findByTrip_TripIdAndVersionNumber(tripId, 1)).thenReturn(Optional.of(version));

        ScheduleDraftSaveRequest request = new ScheduleDraftSaveRequest();
        request.setExpectedRevision(1L);

        TripPlanDayDraftDto d1 = new TripPlanDayDraftDto();
        d1.setDayNumber(1);
        TripPlanDayDraftDto d2 = new TripPlanDayDraftDto();
        d2.setDayNumber(2);
        request.setDays(List.of(d1, d2));

        TripPlanEntryDraftDto shoreEntry = new TripPlanEntryDraftDto();
        shoreEntry.setClientKey("shore-01");
        shoreEntry.setDayNumber(1);
        shoreEntry.setKind(TripPlanEntryKind.SHORE_EXCURSION);
        shoreEntry.setStopClientKey("invalid-stop-key"); // Not in stops!
        shoreEntry.setTitle("Tour Hang Sửng Sốt");
        shoreEntry.setStartDayNumber(1);
        shoreEntry.setPlannedStartTime(LocalTime.of(9, 0));
        shoreEntry.setEndDayNumber(1);
        shoreEntry.setPlannedEndTime(LocalTime.of(15, 0));

        request.setEntries(List.of(shoreEntry));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> versionService.saveDraft(tripId, 1, request, creatorId));
        assertEquals("SHORE_REQUIRES_STOP", ex.getCode());
    }

    @Test
    void testSaveDraftSuccessWithStopsAndEntries() {
        when(versionRepository.findByTrip_TripIdAndVersionNumber(tripId, 1)).thenReturn(Optional.of(version));
        when(portRepository.findById(samplePort.getPortId())).thenReturn(Optional.of(samplePort));
        when(versionRepository.save(any(TripScheduleVersion.class))).thenAnswer(inv -> {
            TripScheduleVersion v = inv.getArgument(0);
            v.setRevision((v.getRevision() != null ? v.getRevision() : 0L) + 1);
            return v;
        });

        ScheduleDraftSaveRequest request = new ScheduleDraftSaveRequest();
        request.setExpectedRevision(1L);

        TripPlanDayDraftDto d1 = new TripPlanDayDraftDto();
        d1.setDayNumber(1);
        d1.setDayType(ItineraryDayType.PORT_DAY);
        d1.setShipTimeZoneId("Asia/Ho_Chi_Minh");

        TripPlanDayDraftDto d2 = new TripPlanDayDraftDto();
        d2.setDayNumber(2);
        d2.setDayType(ItineraryDayType.PORT_DAY);
        d2.setShipTimeZoneId("Asia/Ho_Chi_Minh");
        request.setDays(List.of(d1, d2));

        TripPlanStopDraftDto stop1 = new TripPlanStopDraftDto();
        stop1.setClientKey("stop-01");
        stop1.setPortId(samplePort.getPortId());
        stop1.setStopOrder(1);
        stop1.setArrivalDayNumber(1);
        stop1.setPlannedArrivalTime(LocalTime.of(8, 0));
        stop1.setDepartureDayNumber(1);
        stop1.setPlannedDepartureTime(LocalTime.of(18, 0));
        stop1.setAllAboardDayNumber(1);
        stop1.setAllAboardTime(LocalTime.of(17, 30));
        stop1.setIsEmbarkation(true);
        request.setStops(List.of(stop1));

        TripPlanEntryDraftDto entry1 = new TripPlanEntryDraftDto();
        entry1.setClientKey("entry-01");
        entry1.setDayNumber(1);
        entry1.setKind(TripPlanEntryKind.SHORE_EXCURSION);
        entry1.setStopClientKey("stop-01");
        entry1.setTitle("Tour Hang Sửng Sốt");
        entry1.setStartDayNumber(1);
        entry1.setPlannedStartTime(LocalTime.of(9, 0));
        entry1.setEndDayNumber(1);
        entry1.setPlannedEndTime(LocalTime.of(15, 0));
        request.setEntries(List.of(entry1));

        TripScheduleVersionDetailResponse res = versionService.saveDraft(tripId, 1, request, creatorId);

        assertNotNull(res);
        assertEquals(2L, res.getRevision());
        assertNotNull(res.getContentHash());
        assertEquals(2, res.getDays().size());
        assertEquals(1, res.getStops().size());
        assertEquals("stop-01", res.getStops().get(0).getClientKey());
        assertEquals(1, res.getEntries().size());
        assertEquals("entry-01", res.getEntries().get(0).getClientKey());
        assertEquals("stop-01", res.getEntries().get(0).getStopClientKey());
    }

    @Test
    void testSubmitForReviewRequiresDayType() {
        when(versionRepository.findByTrip_TripIdAndVersionNumber(tripId, 1)).thenReturn(Optional.of(version));
        // day1 and day2 have dayType = null

        BusinessException ex = assertThrows(BusinessException.class,
                () -> versionService.submitForReview(tripId, 1, submitterId));
        assertEquals("DAY_TYPE_REQUIRED", ex.getCode());
    }

    @Test
    void testSubmitForReviewRequiresEmbarkationOnDay1() {
        when(versionRepository.findByTrip_TripIdAndVersionNumber(tripId, 1)).thenReturn(Optional.of(version));

        version.getDays().get(0).setDayType(ItineraryDayType.PORT_DAY);
        version.getDays().get(1).setDayType(ItineraryDayType.PORT_DAY);

        TripPlanStop s1 = new TripPlanStop();
        s1.setClientKey("s1");
        s1.setPort(samplePort);
        s1.setStopOrder(1);
        s1.setArrivalDayNumber(1);
        s1.setDepartureDayNumber(1);
        s1.setIsEmbarkation(false); // NOT embarkation!
        version.getStops().add(s1);

        TripPlanStop s2 = new TripPlanStop();
        s2.setClientKey("s2");
        s2.setPort(samplePort);
        s2.setStopOrder(2);
        s2.setArrivalDayNumber(2);
        s2.setDepartureDayNumber(2);
        s2.setIsDisembarkation(true);
        version.getStops().add(s2);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> versionService.submitForReview(tripId, 1, submitterId));
        assertEquals("MISSING_EMBARKATION", ex.getCode());
    }

    @Test
    void testSubmitForReviewShoreExcursionExceedsAllAboard() {
        when(versionRepository.findByTrip_TripIdAndVersionNumber(tripId, 1)).thenReturn(Optional.of(version));

        version.getDays().get(0).setDayType(ItineraryDayType.PORT_DAY);
        version.getDays().get(1).setDayType(ItineraryDayType.PORT_DAY);

        TripPlanStop s1 = new TripPlanStop();
        s1.setClientKey("s1");
        s1.setPort(samplePort);
        s1.setStopOrder(1);
        s1.setArrivalDayNumber(1);
        s1.setPlannedArrivalTime(LocalTime.of(8, 0));
        s1.setDepartureDayNumber(1);
        s1.setPlannedDepartureTime(LocalTime.of(18, 0));
        s1.setAllAboardDayNumber(1);
        s1.setAllAboardTime(LocalTime.of(17, 30));
        s1.setIsEmbarkation(true);
        version.getStops().add(s1);

        TripPlanStop s2 = new TripPlanStop();
        s2.setClientKey("s2");
        s2.setPort(samplePort);
        s2.setStopOrder(2);
        s2.setArrivalDayNumber(2);
        s2.setDepartureDayNumber(2);
        s2.setIsDisembarkation(true);
        version.getStops().add(s2);

        TripPlanEntry shore = new TripPlanEntry();
        shore.setClientKey("e1");
        shore.setKind(TripPlanEntryKind.SHORE_EXCURSION);
        shore.setDay(version.getDays().get(0));
        shore.setStop(s1);
        shore.setTitle("Hạ Long Tour");
        shore.setStartDayNumber(1);
        shore.setPlannedStartTime(LocalTime.of(9, 0));
        shore.setEndDayNumber(1);
        shore.setPlannedEndTime(LocalTime.of(17, 45)); // Exceeds 17:30 allAboardTime!
        shore.setMeetingPoint("Cổng cảng số 1");
        version.getEntries().add(shore);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> versionService.submitForReview(tripId, 1, submitterId));
        assertEquals("SHORE_AFTER_DEPARTURE", ex.getCode());
    }

    @Test
    void testReturnToDraftRequiresReason() {
        version.setStatus(TripScheduleVersionStatus.IN_REVIEW);
        when(versionRepository.findByTrip_TripIdAndVersionNumber(tripId, 1)).thenReturn(Optional.of(version));

        VersionReturnRequest emptyReq = new VersionReturnRequest();
        emptyReq.setRejectionReason("");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> versionService.returnToDraft(tripId, 1, emptyReq, approverId));
        assertEquals("REJECTION_REASON_REQUIRED", ex.getCode());
    }

    @Test
    void testPublishMakerCheckerForbiddenForCreator() {
        version.setStatus(TripScheduleVersionStatus.IN_REVIEW);
        version.setSubmitter(submitter);
        when(versionRepository.findByTrip_TripIdAndVersionNumber(tripId, 1)).thenReturn(Optional.of(version));

        // Attempt approval by creator
        BusinessException ex = assertThrows(BusinessException.class,
                () -> versionService.publishVersion(tripId, 1, creatorId));
        assertEquals("SELF_APPROVAL_FORBIDDEN", ex.getCode());
    }

    @Test
    void testPublishMakerCheckerForbiddenForSubmitter() {
        version.setStatus(TripScheduleVersionStatus.IN_REVIEW);
        version.setSubmitter(submitter);
        when(versionRepository.findByTrip_TripIdAndVersionNumber(tripId, 1)).thenReturn(Optional.of(version));

        // Attempt approval by submitter
        BusinessException ex = assertThrows(BusinessException.class,
                () -> versionService.publishVersion(tripId, 1, submitterId));
        assertEquals("SELF_APPROVAL_FORBIDDEN", ex.getCode());
    }

    @Test
    void testPublishSuccessForIndependentApprover() {
        version.setStatus(TripScheduleVersionStatus.IN_REVIEW);
        version.setSubmitter(submitter);
        version.getDays().get(0).setDayType(ItineraryDayType.PORT_DAY);
        version.getDays().get(1).setDayType(ItineraryDayType.PORT_DAY);

        TripPlanStop s1 = new TripPlanStop();
        s1.setClientKey("stop-01");
        s1.setPort(samplePort);
        s1.setStopOrder(1);
        s1.setArrivalDayNumber(1);
        s1.setDepartureDayNumber(1);
        s1.setPlannedArrivalTime(LocalTime.of(8, 0));
        s1.setPlannedDepartureTime(LocalTime.of(18, 0));
        s1.setIsEmbarkation(true);
        version.getStops().add(s1);

        TripPlanStop s2 = new TripPlanStop();
        s2.setClientKey("stop-02");
        s2.setPort(samplePort);
        s2.setStopOrder(2);
        s2.setArrivalDayNumber(2);
        s2.setDepartureDayNumber(2);
        s2.setPlannedArrivalTime(LocalTime.of(8, 0));
        s2.setPlannedDepartureTime(LocalTime.of(18, 0));
        s2.setIsDisembarkation(true);
        version.getStops().add(s2);

        when(versionRepository.findByTrip_TripIdAndVersionNumber(tripId, 1)).thenReturn(Optional.of(version));
        when(userRepository.findById(approverId)).thenReturn(Optional.of(approver));
        when(versionRepository.save(any(TripScheduleVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        TripScheduleVersionDetailResponse res = versionService.publishVersion(tripId, 1, approverId);

        assertNotNull(res);
        assertEquals(TripScheduleVersionStatus.PUBLISHED, res.getStatus());
        assertEquals(approverId, res.getPublishedBy());
        assertNotNull(res.getPublishedAt());
        assertEquals(TripStatus.READY, trip.getStatus());
        assertNotNull(trip.getStructuralLockedAt());
    }

    @Test
    void testCreateNextDraftVersionClonesPublishedVersion() {
        version.setStatus(TripScheduleVersionStatus.PUBLISHED);

        when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
        when(versionRepository.findByTrip_TripIdOrderByVersionNumberAsc(tripId))
                .thenReturn(List.of(version));
        when(versionRepository.existsByTrip_TripIdAndStatusIn(eq(tripId), anyList()))
                .thenReturn(false);
        when(versionRepository.save(any(TripScheduleVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        TripScheduleVersionDetailResponse res = versionService.createNextDraftVersion(tripId, creatorId);

        assertNotNull(res);
        assertEquals(2, res.getVersionNumber());
        assertEquals(TripScheduleVersionStatus.DRAFT, res.getStatus());
        assertEquals(1L, res.getRevision());
        assertEquals(2, res.getDays().size());
    }

    @Test
    void testValidateVersionReturnsReportWithoutChangingStatus() {
        when(versionRepository.findById(version.getVersionId())).thenReturn(Optional.of(version));

        // When validating draft with unset day types
        ValidationReport report = versionService.validateVersion(version.getVersionId());

        assertNotNull(report);
        assertEquals(version.getVersionId(), report.getVersionId());
        assertEquals(version.getRevision(), report.getRevision());
        assertFalse(report.getValidForSubmit());
        assertFalse(report.getViolations().isEmpty());
        // Status remains DRAFT
        assertEquals(TripScheduleVersionStatus.DRAFT, version.getStatus());
    }

    @Test
    void testGetVersionDetailByIdSuccess() {
        when(versionRepository.findById(version.getVersionId())).thenReturn(Optional.of(version));

        TripScheduleVersionDetailResponse res = versionService.getVersionDetailById(version.getVersionId());

        assertNotNull(res);
        assertEquals(version.getVersionId(), res.getVersionId());
        assertEquals(1, res.getVersionNumber());
    }

    @Test
    void testSaveDraftByIdOptimisticLockStaleRevision() {
        when(versionRepository.findById(version.getVersionId())).thenReturn(Optional.of(version));

        ScheduleDraftSaveRequest req = new ScheduleDraftSaveRequest();
        req.setExpectedRevision(999L); // Mismatch

        BusinessException ex = assertThrows(BusinessException.class,
                () -> versionService.saveDraftById(version.getVersionId(), req, creatorId));
        assertEquals("STALE_REVISION", ex.getCode());
    }

    @Test
    void testSubmitForReviewByIdStaleRevision() {
        when(versionRepository.findById(version.getVersionId())).thenReturn(Optional.of(version));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> versionService.submitForReviewById(version.getVersionId(), 999L, submitterId));
        assertEquals("STALE_REVISION", ex.getCode());
    }

    @Test
    void testReturnToDraftByIdStaleRevision() {
        version.setStatus(TripScheduleVersionStatus.IN_REVIEW);
        when(versionRepository.findById(version.getVersionId())).thenReturn(Optional.of(version));

        VersionReturnRequest req = new VersionReturnRequest();
        req.setExpectedRevision(999L);
        req.setRejectionReason("Cần sửa đổi");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> versionService.returnToDraftById(version.getVersionId(), req, approverId));
        assertEquals("STALE_REVISION", ex.getCode());
    }

    @Test
    void testReturnToDraftByIdBlankReason() {
        version.setStatus(TripScheduleVersionStatus.IN_REVIEW);
        when(versionRepository.findById(version.getVersionId())).thenReturn(Optional.of(version));

        VersionReturnRequest req = new VersionReturnRequest();
        req.setExpectedRevision(1L);
        req.setRejectionReason("");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> versionService.returnToDraftById(version.getVersionId(), req, approverId));
        assertEquals("REJECTION_REASON_REQUIRED", ex.getCode());
    }

    @Test
    void testPublishVersionByIdMakerCheckerForbidden() {
        version.setStatus(TripScheduleVersionStatus.IN_REVIEW);
        version.setSubmitter(submitter);
        when(versionRepository.findById(version.getVersionId())).thenReturn(Optional.of(version));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> versionService.publishVersionById(version.getVersionId(), 1L, creatorId));
        assertEquals("SELF_APPROVAL_FORBIDDEN", ex.getCode());
    }

    @Test
    void testGetVersionHistorySuccess() {
        when(versionRepository.findById(version.getVersionId())).thenReturn(Optional.of(version));

        AuditLog log1 = new AuditLog();
        log1.setAuditId(UUID.randomUUID());
        log1.setActorUserId(creatorId);
        log1.setAction("SAVE_DRAFT");
        log1.setEntityType("TripScheduleVersion");
        log1.setEntityId(version.getVersionId());
        log1.setResult("SUCCESS");
        log1.setDetail("Saved draft content");

        when(auditRepository.findByEntityIdOrderByOccurredAtDesc(version.getVersionId()))
                .thenReturn(List.of(log1));

        List<TripAuditResponse> history = versionService.getVersionHistory(version.getVersionId());

        assertNotNull(history);
        assertEquals(1, history.size());
        assertEquals("SAVE_DRAFT", history.get(0).getAction());
        assertEquals(creatorId, history.get(0).getActorUserId());
    }
}
