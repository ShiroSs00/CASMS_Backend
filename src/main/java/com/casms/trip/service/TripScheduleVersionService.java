package com.casms.trip.service;

import com.casms.audit.AuditLog;
import com.casms.audit.AuditRepository;
import com.casms.audit.AuditService;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.identity.entity.User;
import com.casms.identity.repository.UserRepository;
import com.casms.ship.entity.Destination;
import com.casms.ship.entity.Port;
import com.casms.ship.enums.CruiseStopArrivalMethod;
import com.casms.ship.enums.CruiseStopCallType;
import com.casms.ship.enums.ItineraryDayType;
import com.casms.ship.enums.ItineraryType;
import com.casms.ship.enums.PortStatus;
import com.casms.ship.repository.DestinationRepository;
import com.casms.ship.repository.PortRepository;
import com.casms.trip.dto.request.*;
import com.casms.trip.dto.response.*;
import com.casms.trip.entity.*;
import com.casms.trip.enums.TripPlanEntryKind;
import com.casms.trip.enums.TripScheduleVersionStatus;
import com.casms.trip.enums.TripStatus;
import com.casms.trip.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripScheduleVersionService {

    private final TripRepository tripRepository;
    private final TripScheduleVersionRepository versionRepository;
    private final TripPlanDayRepository dayRepository;
    private final TripPlanStopRepository stopRepository;
    private final TripPlanEntryRepository entryRepository;
    private final PortRepository portRepository;
    private final DestinationRepository destinationRepository;
    private final UserRepository userRepository;
    private final AuditRepository auditRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public TripScheduleVersionDetailResponse getVersionDetail(UUID tripId, Integer versionNumber) {
        TripScheduleVersion version = getVersion(tripId, versionNumber);
        return mapToDetailResponse(version);
    }

    @Transactional(readOnly = true)
    public TripScheduleVersionDetailResponse getLatestVersionDetail(UUID tripId) {
        TripScheduleVersion version = versionRepository.findTopByTrip_TripIdOrderByVersionNumberDesc(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("TripScheduleVersion", tripId));
        return mapToDetailResponse(version);
    }

    @Transactional(readOnly = true)
    public TripScheduleVersionDetailResponse getVersionDetailById(UUID versionId) {
        TripScheduleVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("TripScheduleVersion", versionId));
        return mapToDetailResponse(version);
    }

    @Transactional
    public TripScheduleVersionDetailResponse saveDraft(UUID tripId, Integer versionNumber, ScheduleDraftSaveRequest request, UUID actorId) {
        TripScheduleVersion version = getVersion(tripId, versionNumber);
        return executeSaveDraft(version, request, actorId, "OPTIMISTIC_LOCK_CONFLICT");
    }

    @Transactional
    public TripScheduleVersionDetailResponse saveDraftById(UUID versionId, ScheduleDraftSaveRequest request, UUID actorId) {
        TripScheduleVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("TripScheduleVersion", versionId));
        return executeSaveDraft(version, request, actorId, "STALE_REVISION");
    }

    private TripScheduleVersionDetailResponse executeSaveDraft(TripScheduleVersion version, ScheduleDraftSaveRequest request, UUID actorId, String lockConflictCode) {
        if (version.getTrip().getStatus() == TripStatus.ARCHIVED) {
            throw new BusinessException(HttpStatus.CONFLICT, "TRIP_ARCHIVED",
                    "Không thể chỉnh sửa phiên bản của Trip đã được lưu trữ (ARCHIVED)");
        }

        if (version.getStatus() != TripScheduleVersionStatus.DRAFT) {
            throw new BusinessException(HttpStatus.CONFLICT, "VERSION_FROZEN",
                    "Không thể chỉnh sửa phiên bản đang ở trạng thái: " + version.getStatus());
        }

        if (request.getExpectedRevision() == null || !request.getExpectedRevision().equals(version.getRevision())) {
            throw new BusinessException(HttpStatus.CONFLICT, lockConflictCode,
                    "Dữ liệu phiên bản đã bị thay đổi bởi phiên làm việc khác. Expected revision: "
                            + request.getExpectedRevision() + ", current: " + version.getRevision());
        }

        Trip trip = version.getTrip();
        if (request.getDays() == null || request.getDays().size() != trip.getDurationDays()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_DAY_COUNT",
                    "Số lượng ngày trong bản dự thảo (" + (request.getDays() != null ? request.getDays().size() : 0)
                            + ") phải khớp chính xác với số ngày của Trip (" + trip.getDurationDays() + ")");
        }

        // Validate distinct day numbers
        Set<Integer> dayNumbers = request.getDays().stream()
                .map(TripPlanDayDraftDto::getDayNumber)
                .collect(Collectors.toSet());
        for (int i = 1; i <= trip.getDurationDays(); i++) {
            if (!dayNumbers.contains(i)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_DAY_SEQUENCE",
                        "Các ngày phải tuần tự từ 1 đến " + trip.getDurationDays() + ". Thiếu ngày: " + i);
            }
        }

        // 1. Reconcile Days
        Map<Integer, TripPlanDay> dayMap = version.getDays().stream()
                .collect(Collectors.toMap(TripPlanDay::getDayNumber, d -> d));

        for (TripPlanDayDraftDto dayDto : request.getDays()) {
            TripPlanDay day = dayMap.get(dayDto.getDayNumber());
            if (day == null) {
                day = new TripPlanDay();
                day.setVersion(version);
                day.setDayNumber(dayDto.getDayNumber());
                version.getDays().add(day);
                dayMap.put(day.getDayNumber(), day);
            }
            day.setDayType(dayDto.getDayType());
            if (dayDto.getShipTimeZoneId() != null && !dayDto.getShipTimeZoneId().isBlank()) {
                day.setShipTimeZoneId(dayDto.getShipTimeZoneId().trim());
            }
            day.setTitle(dayDto.getTitle());
            day.setDescription(dayDto.getDescription());
        }

        // 2. Reconcile Stops
        List<TripPlanStopDraftDto> stopDtos = request.getStops() != null ? request.getStops() : List.of();
        Set<String> stopKeys = new HashSet<>();
        Set<Integer> stopOrders = new HashSet<>();
        Map<UUID, Port> portCache = new HashMap<>();

        for (TripPlanStopDraftDto stopDto : stopDtos) {
            if (stopDto.getClientKey() == null || stopDto.getClientKey().isBlank()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "CLIENT_KEY_REQUIRED", "clientKey của điểm dừng cảng không được để trống.");
            }
            if (!stopKeys.add(stopDto.getClientKey())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "DUPLICATE_CLIENT_KEY", "clientKey bị trùng lặp trong danh sách điểm dừng: " + stopDto.getClientKey());
            }
            if (!stopOrders.add(stopDto.getStopOrder())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "DUPLICATE_STOP_ORDER", "stopOrder bị trùng lặp: " + stopDto.getStopOrder());
            }

            if (!portCache.containsKey(stopDto.getPortId())) {
                Port port = portRepository.findById(stopDto.getPortId())
                        .orElseThrow(() -> new ResourceNotFoundException("Port", stopDto.getPortId()));
                if (port.getStatus() != PortStatus.ACTIVE) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "INACTIVE_PORT", "Cảng " + port.getPortCode() + " hiện không ở trạng thái ACTIVE.");
                }
                portCache.put(stopDto.getPortId(), port);
            }
        }

        Map<String, TripPlanStop> existingStopMap = version.getStops().stream()
                .collect(Collectors.toMap(TripPlanStop::getClientKey, s -> s));

        List<TripPlanStop> updatedStops = new ArrayList<>();
        Map<String, TripPlanStop> activeStopKeyMap = new HashMap<>();

        for (TripPlanStopDraftDto stopDto : stopDtos) {
            TripPlanStop stop = existingStopMap.get(stopDto.getClientKey());
            if (stop == null) {
                stop = new TripPlanStop();
                stop.setVersion(version);
                stop.setClientKey(stopDto.getClientKey());
            }
            stop.setPort(portCache.get(stopDto.getPortId()));
            if (stopDto.getDestinationId() != null) {
                Destination dest = destinationRepository.findById(stopDto.getDestinationId()).orElse(null);
                stop.setDestination(dest);
            } else {
                stop.setDestination(null);
            }
            stop.setStopOrder(stopDto.getStopOrder());
            stop.setArrivalDayNumber(stopDto.getArrivalDayNumber());
            stop.setPlannedArrivalTime(stopDto.getPlannedArrivalTime());
            stop.setDepartureDayNumber(stopDto.getDepartureDayNumber());
            stop.setPlannedDepartureTime(stopDto.getPlannedDepartureTime());
            stop.setCallType(stopDto.getCallType() != null ? stopDto.getCallType() : CruiseStopCallType.NORMAL);
            stop.setArrivalMethod(stopDto.getArrivalMethod() != null ? stopDto.getArrivalMethod() : CruiseStopArrivalMethod.BERTH);
            stop.setAllAboardDayNumber(stopDto.getAllAboardDayNumber());
            stop.setAllAboardTime(stopDto.getAllAboardTime());
            stop.setIsEmbarkation(Boolean.TRUE.equals(stopDto.getIsEmbarkation()));
            stop.setIsDisembarkation(Boolean.TRUE.equals(stopDto.getIsDisembarkation()));

            updatedStops.add(stop);
            activeStopKeyMap.put(stop.getClientKey(), stop);
        }

        // 3. Reconcile Entries
        List<TripPlanEntryDraftDto> entryDtos = request.getEntries() != null ? request.getEntries() : List.of();
        Set<String> entryKeys = new HashSet<>();

        for (TripPlanEntryDraftDto entryDto : entryDtos) {
            if (entryDto.getClientKey() == null || entryDto.getClientKey().isBlank()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "CLIENT_KEY_REQUIRED", "clientKey của mục kế hoạch không được để trống.");
            }
            if (!entryKeys.add(entryDto.getClientKey())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "DUPLICATE_CLIENT_KEY", "clientKey bị trùng lặp trong mục kế hoạch: " + entryDto.getClientKey());
            }

            if (entryDto.getKind() == TripPlanEntryKind.SHORE_EXCURSION) {
                if (entryDto.getStopClientKey() == null || !activeStopKeyMap.containsKey(entryDto.getStopClientKey())) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "SHORE_REQUIRES_STOP",
                            "Mục tham quan trên bờ '" + entryDto.getTitle() + "' bắt buộc phải liên kết với một điểm dừng cảng hợp lệ (stopClientKey: "
                                    + entryDto.getStopClientKey() + ")");
                }
            }
        }

        // Check reference integrity: stops to be removed must not have entries referencing them
        for (TripPlanStop existingStop : version.getStops()) {
            if (!activeStopKeyMap.containsKey(existingStop.getClientKey())) {
                boolean hasReferencingEntries = entryDtos.stream()
                        .anyMatch(e -> existingStop.getClientKey().equals(e.getStopClientKey()));
                if (hasReferencingEntries) {
                    throw new BusinessException(HttpStatus.CONFLICT, "REFERENCE_INTEGRITY_VIOLATION",
                            "Không thể xóa điểm dừng cảng " + existingStop.getClientKey()
                                    + " vì vẫn còn hoạt động tham quan trên bờ tham chiếu đến nó.");
                }
            }
        }

        version.getStops().clear();
        version.getStops().addAll(updatedStops);

        // Confirm route type (tripType) if undetermined and embarkation/disembarkation ports are specified
        TripPlanStop embarkStop = updatedStops.stream().filter(s -> Boolean.TRUE.equals(s.getIsEmbarkation())).findFirst().orElse(null);
        TripPlanStop disembarkStop = updatedStops.stream().filter(s -> Boolean.TRUE.equals(s.getIsDisembarkation())).findFirst().orElse(null);
        if (embarkStop != null && disembarkStop != null && embarkStop.getPort() != null && disembarkStop.getPort() != null) {
            if (trip.getTripType() == null) {
                boolean samePort = Objects.equals(embarkStop.getPort().getPortId(), disembarkStop.getPort().getPortId());
                trip.setTripType(samePort ? ItineraryType.ROUND_TRIP : ItineraryType.ONE_WAY);
                if (tripRepository != null) {
                    tripRepository.save(trip);
                }
            }
        }

        Map<String, TripPlanEntry> existingEntryMap = version.getEntries().stream()
                .collect(Collectors.toMap(TripPlanEntry::getClientKey, e -> e));

        List<TripPlanEntry> updatedEntries = new ArrayList<>();
        for (TripPlanEntryDraftDto entryDto : entryDtos) {
            TripPlanEntry entry = existingEntryMap.get(entryDto.getClientKey());
            if (entry == null) {
                entry = new TripPlanEntry();
                entry.setVersion(version);
                entry.setClientKey(entryDto.getClientKey());
            }

            TripPlanDay day = dayMap.get(entryDto.getDayNumber());
            if (day == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_DAY_REFERENCE", "Không tìm thấy ngày: " + entryDto.getDayNumber());
            }
            entry.setDay(day);
            entry.setKind(entryDto.getKind());

            if (entryDto.getKind() == TripPlanEntryKind.SHORE_EXCURSION) {
                entry.setStop(activeStopKeyMap.get(entryDto.getStopClientKey()));
            } else if (entryDto.getStopClientKey() != null) {
                entry.setStop(activeStopKeyMap.get(entryDto.getStopClientKey()));
            } else {
                entry.setStop(null);
            }

            entry.setSourceActivityId(entryDto.getSourceActivityId());
            entry.setSourceExcursionId(entryDto.getSourceExcursionId());
            entry.setServiceId(entryDto.getServiceId());
            entry.setSeriesClientKey(entryDto.getSeriesClientKey());
            if (entryDto.getDestinationId() != null) {
                Destination dest = destinationRepository.findById(entryDto.getDestinationId()).orElse(null);
                entry.setDestination(dest);
            } else {
                entry.setDestination(null);
            }

            entry.setTitle(entryDto.getTitle().trim());
            entry.setStartDayNumber(entryDto.getStartDayNumber());
            entry.setPlannedStartTime(entryDto.getPlannedStartTime());
            entry.setEndDayNumber(entryDto.getEndDayNumber());
            entry.setPlannedEndTime(entryDto.getPlannedEndTime());

            if (entryDto.getTimeZoneId() != null && !entryDto.getTimeZoneId().isBlank()) {
                entry.setTimeZoneId(entryDto.getTimeZoneId().trim());
            } else if (entryDto.getKind() == TripPlanEntryKind.SHORE_EXCURSION && entry.getStop() != null
                    && entry.getStop().getPort() != null && entry.getStop().getPort().getTimeZoneId() != null) {
                entry.setTimeZoneId(entry.getStop().getPort().getTimeZoneId());
            } else {
                entry.setTimeZoneId("Asia/Ho_Chi_Minh");
            }

            entry.setLocationText(entryDto.getLocationText());
            entry.setMeetingPoint(entryDto.getMeetingPoint());
            entry.setNotes(entryDto.getNotes());

            updatedEntries.add(entry);
        }

        version.getEntries().clear();
        version.getEntries().addAll(updatedEntries);

        // Increment revision and calculate content hash
        version.setRevision(version.getRevision() + 1);
        version.setContentHash(computeContentHash(version));

        version = versionRepository.save(version);
        log.info("Saved draft aggregate for Trip {} Version {} with new revision {}",
                trip.getTripCode(), version.getVersionNumber(), version.getRevision());

        if (auditService != null) {
            auditService.record(actorId, "SAVE_DRAFT", "TripScheduleVersion", version.getVersionId(),
                    "SUCCESS", "Saved draft content with revision " + version.getRevision());
        }

        return mapToDetailResponse(version);
    }

    @Transactional(readOnly = true)
    public ValidationReport validateVersion(UUID versionId) {
        TripScheduleVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("TripScheduleVersion", versionId));
        List<PlanViolation> violations = validateVersionInternal(version);
        boolean hasErrors = violations.stream().anyMatch(v -> "ERROR".equalsIgnoreCase(v.getSeverity()));
        return ValidationReport.builder()
                .versionId(version.getVersionId())
                .revision(version.getRevision())
                .validForSubmit(!hasErrors)
                .violations(violations)
                .deferredChecks(List.of("FLEET_COMPATIBILITY_DEFERRED", "NEEDS_SAILING_DATE"))
                .policyVersion("v1.0")
                .build();
    }

    @Transactional
    public TripScheduleVersionDetailResponse submitForReview(UUID tripId, Integer versionNumber, UUID actorId) {
        TripScheduleVersion version = getVersion(tripId, versionNumber);
        return executeSubmitForReview(version, null, actorId);
    }

    @Transactional
    public TripScheduleVersionDetailResponse submitForReviewById(UUID versionId, Long expectedRevision, UUID actorId) {
        TripScheduleVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("TripScheduleVersion", versionId));
        return executeSubmitForReview(version, expectedRevision, actorId);
    }

    private TripScheduleVersionDetailResponse executeSubmitForReview(TripScheduleVersion version, Long expectedRevision, UUID actorId) {
        if (version.getTrip().getStatus() == TripStatus.ARCHIVED) {
            throw new BusinessException(HttpStatus.CONFLICT, "TRIP_ARCHIVED",
                    "Không thể gửi duyệt phiên bản của Trip đã được lưu trữ (ARCHIVED)");
        }

        if (version.getStatus() != TripScheduleVersionStatus.DRAFT) {
            throw new BusinessException(HttpStatus.CONFLICT, "INVALID_STATUS",
                    "Chỉ có thể gửi phê duyệt phiên bản đang ở trạng thái DRAFT");
        }

        if (expectedRevision != null && !expectedRevision.equals(version.getRevision())) {
            throw new BusinessException(HttpStatus.CONFLICT, "STALE_REVISION",
                    "Revision của phiên bản không khớp (expected: " + expectedRevision + ", current: " + version.getRevision() + ")");
        }

        List<PlanViolation> violations = validateVersionInternal(version);
        List<PlanViolation> errorViolations = violations.stream()
                .filter(v -> "ERROR".equalsIgnoreCase(v.getSeverity()))
                .collect(Collectors.toList());
        if (!errorViolations.isEmpty()) {
            PlanViolation first = errorViolations.get(0);
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, first.getCode(), first.getMessage(),
                    Map.of("violations", errorViolations));
        }

        User submitter = null;
        if (actorId != null) {
            submitter = userRepository.findById(actorId).orElse(null);
        }

        version.setStatus(TripScheduleVersionStatus.IN_REVIEW);
        version.setSubmitter(submitter);
        version.setSubmittedRevision(version.getRevision());
        version.setRejectionReason(null);

        version = versionRepository.save(version);
        log.info("Submitted Trip {} Version {} for review by user {}",
                version.getTrip().getTripCode(), version.getVersionNumber(), actorId);

        if (auditService != null) {
            auditService.record(actorId, "SUBMIT", "TripScheduleVersion", version.getVersionId(),
                    "SUCCESS", "Submitted version for review");
        }

        return mapToDetailResponse(version);
    }

    @Transactional
    public TripScheduleVersionDetailResponse returnToDraft(UUID tripId, Integer versionNumber, VersionReturnRequest request, UUID actorId) {
        TripScheduleVersion version = getVersion(tripId, versionNumber);
        return executeReturnToDraft(version, request, actorId, "REJECTION_REASON_REQUIRED");
    }

    @Transactional
    public TripScheduleVersionDetailResponse returnToDraftById(UUID versionId, VersionReturnRequest request, UUID actorId) {
        TripScheduleVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("TripScheduleVersion", versionId));
        return executeReturnToDraft(version, request, actorId, "REJECTION_REASON_REQUIRED");
    }

    private TripScheduleVersionDetailResponse executeReturnToDraft(TripScheduleVersion version, VersionReturnRequest request, UUID actorId, String emptyReasonCode) {
        if (version.getTrip().getStatus() == TripStatus.ARCHIVED) {
            throw new BusinessException(HttpStatus.CONFLICT, "TRIP_ARCHIVED",
                    "Không thể trả về bản nháp phiên bản của Trip đã được lưu trữ (ARCHIVED)");
        }

        if (version.getStatus() != TripScheduleVersionStatus.IN_REVIEW) {
            throw new BusinessException(HttpStatus.CONFLICT, "INVALID_STATUS",
                    "Chỉ có thể trả về bản nháp từ trạng thái IN_REVIEW.");
        }

        if (request.getExpectedRevision() != null && !request.getExpectedRevision().equals(version.getRevision())) {
            throw new BusinessException(HttpStatus.CONFLICT, "STALE_REVISION",
                    "Revision của phiên bản không khớp (expected: " + request.getExpectedRevision() + ", current: " + version.getRevision() + ")");
        }

        if (request.getRejectionReason() == null || request.getRejectionReason().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, emptyReasonCode,
                    "Cần cung cấp lý do từ chối / trả về bản nháp.");
        }

        version.setStatus(TripScheduleVersionStatus.DRAFT);
        version.setRejectionReason(request.getRejectionReason().trim());
        version.setSubmittedRevision(null);
        version.setRevision(version.getRevision() + 1);

        version = versionRepository.save(version);
        log.info("Returned Trip {} Version {} to DRAFT with reason: {}",
                version.getTrip().getTripCode(), version.getVersionNumber(), request.getRejectionReason());

        if (auditService != null) {
            auditService.record(actorId, "RETURN", "TripScheduleVersion", version.getVersionId(),
                    "SUCCESS", "Returned to draft: " + request.getRejectionReason());
        }

        return mapToDetailResponse(version);
    }

    @Transactional
    public TripScheduleVersionDetailResponse publishVersion(UUID tripId, Integer versionNumber, UUID actorId) {
        TripScheduleVersion version = getVersion(tripId, versionNumber);
        return executePublishVersion(version, null, actorId);
    }

    @Transactional
    public TripScheduleVersionDetailResponse publishVersionById(UUID versionId, Long expectedRevision, UUID actorId) {
        TripScheduleVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("TripScheduleVersion", versionId));
        return executePublishVersion(version, expectedRevision, actorId);
    }

    private TripScheduleVersionDetailResponse executePublishVersion(TripScheduleVersion version, Long expectedRevision, UUID actorId) {
        if (version.getTrip().getStatus() == TripStatus.ARCHIVED) {
            throw new BusinessException(HttpStatus.CONFLICT, "TRIP_ARCHIVED",
                    "Không thể xuất bản phiên bản của Trip đã được lưu trữ (ARCHIVED)");
        }

        if (version.getStatus() != TripScheduleVersionStatus.IN_REVIEW) {
            throw new BusinessException(HttpStatus.CONFLICT, "INVALID_STATUS",
                    "Chỉ có thể phê duyệt xuất bản phiên bản đang ở trạng thái IN_REVIEW.");
        }

        if (expectedRevision != null && !expectedRevision.equals(version.getRevision())) {
            throw new BusinessException(HttpStatus.CONFLICT, "STALE_REVISION",
                    "Revision của phiên bản không khớp (expected: " + expectedRevision + ", current: " + version.getRevision() + ")");
        }

        // Maker-Checker enforcement: Approver must NOT be creator or submitter
        UUID creatorId = version.getTrip().getCreatedByUser() != null ? version.getTrip().getCreatedByUser().getUserId() : null;
        UUID submitterId = version.getSubmitter() != null ? version.getSubmitter().getUserId() : null;

        if (actorId != null && (actorId.equals(creatorId) || actorId.equals(submitterId))) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "SELF_APPROVAL_FORBIDDEN",
                    "Người phê duyệt xuất bản (" + actorId + ") phải khác người tạo Trip (" + creatorId
                            + ") và người gửi duyệt (" + submitterId + ")");
        }

        // Revalidate in transaction
        List<PlanViolation> violations = validateVersionInternal(version);
        List<PlanViolation> errorViolations = violations.stream()
                .filter(v -> "ERROR".equalsIgnoreCase(v.getSeverity()))
                .collect(Collectors.toList());
        if (!errorViolations.isEmpty()) {
            PlanViolation first = errorViolations.get(0);
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, first.getCode(),
                    "Kế hoạch không đạt thẩm định khi xuất bản: " + first.getMessage(),
                    Map.of("violations", errorViolations));
        }

        User approver = null;
        if (actorId != null) {
            approver = userRepository.findById(actorId).orElse(null);
        }

        version.setStatus(TripScheduleVersionStatus.PUBLISHED);
        version.setPublishedBy(approver);
        version.setPublishedAt(Instant.now());

        // Trip transitions to READY and durations are permanently locked
        version.getTrip().setStatus(TripStatus.READY);
        version.getTrip().setStructuralLockedAt(Instant.now());

        version = versionRepository.save(version);
        log.info("Published Trip {} Version {} successfully by approver {}",
                version.getTrip().getTripCode(), version.getVersionNumber(), actorId);

        if (auditService != null) {
            auditService.record(actorId, "PUBLISH", "TripScheduleVersion", version.getVersionId(),
                    "SUCCESS", "Published version " + version.getVersionNumber());
        }

        return mapToDetailResponse(version);
    }

    @Transactional(readOnly = true)
    public List<TripAuditResponse> getVersionHistory(UUID versionId) {
        TripScheduleVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("TripScheduleVersion", versionId));

        List<AuditLog> logs = auditRepository != null
                ? auditRepository.findByEntityIdOrderByOccurredAtDesc(version.getVersionId())
                : List.of();

        return logs.stream().map(log -> TripAuditResponse.builder()
                .auditId(log.getAuditId())
                .actorUserId(log.getActorUserId())
                .action(log.getAction())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .result(log.getResult())
                .detail(log.getDetail())
                .occurredAt(log.getOccurredAt())
                .build()).collect(Collectors.toList());
    }

    @Transactional
    public TripScheduleVersionDetailResponse createNextDraftVersion(UUID tripId, UUID actorId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", tripId));

        if (trip.getStatus() == TripStatus.ARCHIVED) {
            throw new BusinessException(HttpStatus.CONFLICT, "TRIP_ARCHIVED",
                    "Không thể tạo phiên bản mới trên Trip đã được lưu trữ (ARCHIVED)");
        }

        boolean hasPublished = versionRepository.findByTrip_TripIdOrderByVersionNumberAsc(tripId).stream()
                .anyMatch(v -> v.getStatus() == TripScheduleVersionStatus.PUBLISHED);

        if (!hasPublished) {
            throw new BusinessException(HttpStatus.CONFLICT, "NO_PUBLISHED_VERSION",
                    "Chỉ có thể tạo phiên bản tiếp theo khi đã có ít nhất một phiên bản được xuất bản (PUBLISHED).");
        }

        boolean hasUnfinished = versionRepository.existsByTrip_TripIdAndStatusIn(tripId,
                List.of(TripScheduleVersionStatus.DRAFT, TripScheduleVersionStatus.IN_REVIEW));

        if (hasUnfinished) {
            throw new BusinessException(HttpStatus.CONFLICT, "CONCURRENT_UNFINISHED_VERSION",
                    "Đang có một phiên bản chưa hoàn tất (DRAFT hoặc IN_REVIEW). Vui lòng xử lý trước khi tạo phiên bản mới.");
        }

        TripScheduleVersion latestPublished = versionRepository.findByTrip_TripIdOrderByVersionNumberAsc(tripId).stream()
                .filter(v -> v.getStatus() == TripScheduleVersionStatus.PUBLISHED)
                .reduce((first, second) -> second)
                .orElseThrow();

        User creator = null;
        if (actorId != null) {
            creator = userRepository.findById(actorId).orElse(null);
        }

        TripScheduleVersion newVersion = new TripScheduleVersion();
        newVersion.setTrip(trip);
        newVersion.setVersionNumber(latestPublished.getVersionNumber() + 1);
        newVersion.setStatus(TripScheduleVersionStatus.DRAFT);
        newVersion.setRevision(1L);
        newVersion.setCreator(creator);
        newVersion.setSourceVersionId(latestPublished.getVersionId());

        // Deep clone days
        Map<Integer, TripPlanDay> dayMap = new HashMap<>();
        List<TripPlanDay> clonedDays = new ArrayList<>();
        for (TripPlanDay oldDay : latestPublished.getDays()) {
            TripPlanDay newDay = new TripPlanDay();
            newDay.setVersion(newVersion);
            newDay.setDayNumber(oldDay.getDayNumber());
            newDay.setDayType(oldDay.getDayType());
            newDay.setShipTimeZoneId(oldDay.getShipTimeZoneId());
            newDay.setTitle(oldDay.getTitle());
            newDay.setDescription(oldDay.getDescription());
            clonedDays.add(newDay);
            dayMap.put(newDay.getDayNumber(), newDay);
        }
        newVersion.setDays(clonedDays);

        // Deep clone stops
        Map<String, TripPlanStop> stopMap = new HashMap<>();
        List<TripPlanStop> clonedStops = new ArrayList<>();
        for (TripPlanStop oldStop : latestPublished.getStops()) {
            TripPlanStop newStop = new TripPlanStop();
            newStop.setVersion(newVersion);
            newStop.setClientKey(oldStop.getClientKey());
            newStop.setPort(oldStop.getPort());
            newStop.setDestination(oldStop.getDestination());
            newStop.setStopOrder(oldStop.getStopOrder());
            newStop.setArrivalDayNumber(oldStop.getArrivalDayNumber());
            newStop.setPlannedArrivalTime(oldStop.getPlannedArrivalTime());
            newStop.setDepartureDayNumber(oldStop.getDepartureDayNumber());
            newStop.setPlannedDepartureTime(oldStop.getPlannedDepartureTime());
            newStop.setCallType(oldStop.getCallType());
            newStop.setArrivalMethod(oldStop.getArrivalMethod());
            newStop.setAllAboardDayNumber(oldStop.getAllAboardDayNumber());
            newStop.setAllAboardTime(oldStop.getAllAboardTime());
            newStop.setIsEmbarkation(oldStop.getIsEmbarkation());
            newStop.setIsDisembarkation(oldStop.getIsDisembarkation());
            clonedStops.add(newStop);
            stopMap.put(newStop.getClientKey(), newStop);
        }
        newVersion.setStops(clonedStops);

        // Deep clone entries
        List<TripPlanEntry> clonedEntries = new ArrayList<>();
        for (TripPlanEntry oldEntry : latestPublished.getEntries()) {
            TripPlanEntry newEntry = new TripPlanEntry();
            newEntry.setVersion(newVersion);
            newEntry.setClientKey(oldEntry.getClientKey());
            newEntry.setDay(dayMap.get(oldEntry.getDay().getDayNumber()));
            newEntry.setKind(oldEntry.getKind());
            if (oldEntry.getStop() != null) {
                newEntry.setStop(stopMap.get(oldEntry.getStop().getClientKey()));
            }
            newEntry.setSourceActivityId(oldEntry.getSourceActivityId());
            newEntry.setSourceExcursionId(oldEntry.getSourceExcursionId());
            newEntry.setServiceId(oldEntry.getServiceId());
            newEntry.setSeriesClientKey(oldEntry.getSeriesClientKey());
            newEntry.setDestination(oldEntry.getDestination());
            newEntry.setTitle(oldEntry.getTitle());
            newEntry.setStartDayNumber(oldEntry.getStartDayNumber());
            newEntry.setPlannedStartTime(oldEntry.getPlannedStartTime());
            newEntry.setEndDayNumber(oldEntry.getEndDayNumber());
            newEntry.setPlannedEndTime(oldEntry.getPlannedEndTime());
            newEntry.setTimeZoneId(oldEntry.getTimeZoneId());
            newEntry.setLocationText(oldEntry.getLocationText());
            newEntry.setMeetingPoint(oldEntry.getMeetingPoint());
            newEntry.setNotes(oldEntry.getNotes());
            clonedEntries.add(newEntry);
        }
        newVersion.setEntries(clonedEntries);
        newVersion.setContentHash(computeContentHash(newVersion));

        newVersion = versionRepository.save(newVersion);
        log.info("Created next draft Version {} for Trip {} by user {}",
                newVersion.getVersionNumber(), trip.getTripCode(), actorId);

        return mapToDetailResponse(newVersion);
    }

    public List<PlanViolation> validateVersionInternal(TripScheduleVersion version) {
        List<PlanViolation> violations = new ArrayList<>();
        Trip trip = version.getTrip();
        List<TripPlanDay> days = version.getDays();
        List<TripPlanStop> stops = version.getStops();
        List<TripPlanEntry> entries = version.getEntries();

        // 1. Validate Day Count
        if (days.size() != trip.getDurationDays()) {
            violations.add(PlanViolation.builder()
                    .code("INVALID_DAY_COUNT")
                    .severity("ERROR")
                    .entityType("VERSION")
                    .field("durationDays")
                    .fieldPath("trip.durationDays")
                    .actionHint("ADJUST_DAY_COUNT")
                    .message("Số ngày trong lịch trình (" + days.size() + ") không khớp với số ngày của Trip (" + trip.getDurationDays() + ")")
                    .build());
        }

        // 2. Validate Day Type
        for (TripPlanDay day : days) {
            if (day.getDayType() == null) {
                violations.add(PlanViolation.builder()
                        .code("DAY_TYPE_REQUIRED")
                        .severity("ERROR")
                        .entityType("DAY")
                        .dayNumber(day.getDayNumber())
                        .field("dayType")
                        .fieldPath("days[" + day.getDayNumber() + "].dayType")
                        .actionHint("SELECT_DAY_TYPE")
                        .message("Ngày " + day.getDayNumber() + " chưa được xác định loại ngày (PORT_DAY hoặc SEA_DAY)")
                        .build());
            }
        }

        // 3. Validate Empty Schedule
        if (stops.isEmpty() && entries.isEmpty()) {
            violations.add(PlanViolation.builder()
                    .code("EMPTY_SCHEDULE")
                    .severity("ERROR")
                    .entityType("VERSION")
                    .field("stops")
                    .fieldPath("stops")
                    .actionHint("ADD_STOP")
                    .message("Lịch trình không thể hoàn toàn trống rỗng khi gửi phê duyệt.")
                    .build());
        }

        // Map occupancy of stops per day
        Map<Integer, List<TripPlanStop>> stopsByDay = new HashMap<>();
        for (int i = 1; i <= trip.getDurationDays(); i++) {
            stopsByDay.put(i, new ArrayList<>());
        }

        boolean hasEmbarkation = false;
        boolean hasDisembarkation = false;

        for (TripPlanStop stop : stops) {
            if (stop.getArrivalDayNumber() < 1 || stop.getDepartureDayNumber() > trip.getDurationDays()
                    || stop.getArrivalDayNumber() > stop.getDepartureDayNumber()) {
                violations.add(PlanViolation.builder()
                        .code("INVALID_STOP_DAY_INTERVAL")
                        .severity("ERROR")
                        .entityType("STOP")
                        .clientKey(stop.getClientKey())
                        .dayNumber(stop.getArrivalDayNumber())
                        .field("arrivalDayNumber")
                        .fieldPath("stops[clientKey=" + stop.getClientKey() + "].arrivalDayNumber")
                        .actionHint("EDIT_STOP_DAYS")
                        .message("Khoảng ngày của điểm dừng " + stop.getClientKey() + " không hợp lệ ("
                                + stop.getArrivalDayNumber() + " -> " + stop.getDepartureDayNumber() + ")")
                        .build());
            } else {
                for (int d = stop.getArrivalDayNumber(); d <= stop.getDepartureDayNumber(); d++) {
                    stopsByDay.get(d).add(stop);
                }
            }

            if (Boolean.TRUE.equals(stop.getIsEmbarkation())) {
                if (stop.getArrivalDayNumber() != 1) {
                    violations.add(PlanViolation.builder()
                            .code("EMBARKATION_MUST_BE_DAY_1")
                            .severity("ERROR")
                            .entityType("STOP")
                            .clientKey(stop.getClientKey())
                            .dayNumber(stop.getArrivalDayNumber())
                            .field("isEmbarkation")
                            .fieldPath("stops[clientKey=" + stop.getClientKey() + "].isEmbarkation")
                            .actionHint("EDIT_EMBARKATION")
                            .message("Cảng đón khách (Embarkation) chỉ được đặt tại Ngày 1 (hiện tại đặt ở Ngày " + stop.getArrivalDayNumber() + ")")
                            .build());
                }
                hasEmbarkation = true;
            }

            if (Boolean.TRUE.equals(stop.getIsDisembarkation())) {
                if (!stop.getDepartureDayNumber().equals(trip.getDurationDays())) {
                    violations.add(PlanViolation.builder()
                            .code("DISEMBARKATION_MUST_BE_LAST_DAY")
                            .severity("ERROR")
                            .entityType("STOP")
                            .clientKey(stop.getClientKey())
                            .dayNumber(stop.getDepartureDayNumber())
                            .field("isDisembarkation")
                            .fieldPath("stops[clientKey=" + stop.getClientKey() + "].isDisembarkation")
                            .actionHint("EDIT_DISEMBARKATION")
                            .message("Cảng trả khách (Disembarkation) chỉ được đặt tại Ngày cuối cùng (" + trip.getDurationDays()
                                    + "), hiện tại đặt ở Ngày " + stop.getDepartureDayNumber())
                            .build());
                }
                hasDisembarkation = true;
            }

            // Time logic for same day call
            if (stop.getArrivalDayNumber().equals(stop.getDepartureDayNumber())) {
                LocalTime arr = stop.getPlannedArrivalTime();
                LocalTime dep = stop.getPlannedDepartureTime();
                if (arr != null && dep != null && !dep.isAfter(arr)) {
                    violations.add(PlanViolation.builder()
                            .code("INVALID_PORT_CALL_TIMES")
                            .severity("ERROR")
                            .entityType("STOP")
                            .clientKey(stop.getClientKey())
                            .dayNumber(stop.getArrivalDayNumber())
                            .field("plannedDepartureTime")
                            .fieldPath("stops[clientKey=" + stop.getClientKey() + "].plannedDepartureTime")
                            .actionHint("EDIT_PORT_CALL_TIMES")
                            .message("Tại ngày " + stop.getArrivalDayNumber() + " (cảng "
                                    + (stop.getPort() != null ? stop.getPort().getPortName() : "")
                                    + "), giờ rời cảng (" + dep + ") phải sau giờ cập cảng (" + arr + ")")
                            .build());
                }
            }

            // All aboard logic
            if (stop.getAllAboardTime() != null) {
                int aaDay = stop.getAllAboardDayNumber() != null ? stop.getAllAboardDayNumber() : stop.getDepartureDayNumber();
                if (aaDay > stop.getDepartureDayNumber() || (aaDay == stop.getDepartureDayNumber()
                        && stop.getPlannedDepartureTime() != null && stop.getAllAboardTime().isAfter(stop.getPlannedDepartureTime()))) {
                    violations.add(PlanViolation.builder()
                            .code("INVALID_ALL_ABOARD_TIME")
                            .severity("ERROR")
                            .entityType("STOP")
                            .clientKey(stop.getClientKey())
                            .dayNumber(stop.getDepartureDayNumber())
                            .field("allAboardTime")
                            .fieldPath("stops[clientKey=" + stop.getClientKey() + "].allAboardTime")
                            .actionHint("SET_ALL_ABOARD_TIME")
                            .message("Giờ tất cả lên tàu phải trước hoặc bằng giờ rời cảng.")
                            .build());
                }
            }

            boolean hasExcursionOnStop = entries.stream().anyMatch(e ->
                    e.getKind() == TripPlanEntryKind.SHORE_EXCURSION
                    && e.getStop() != null
                    && ((stop.getClientKey() != null && stop.getClientKey().equals(e.getStop().getClientKey()))
                        || (stop.getStopId() != null && stop.getStopId().equals(e.getStop().getStopId()))));
            if (hasExcursionOnStop && stop.getAllAboardTime() == null) {
                violations.add(PlanViolation.builder()
                        .code("ALL_ABOARD_REQUIRED_FOR_EXCURSION")
                        .severity("ERROR")
                        .entityType("STOP")
                        .clientKey(stop.getClientKey())
                        .dayNumber(stop.getDepartureDayNumber())
                        .field("allAboardTime")
                        .fieldPath("stops[clientKey=" + stop.getClientKey() + "].allAboardTime")
                        .actionHint("SET_ALL_ABOARD_TIME")
                        .message("Điểm dừng " + stop.getClientKey() + " có tour tham quan trên bờ nhưng chưa thiết lập giờ tất cả lên tàu (allAboardTime).")
                        .build());
            }
        }

        // Validate Day Type & Port Day consistency
        for (TripPlanDay day : days) {
            List<TripPlanStop> dayStops = stopsByDay.get(day.getDayNumber());
            if (day.getDayType() == ItineraryDayType.PORT_DAY) {
                if (dayStops == null || dayStops.isEmpty()) {
                    violations.add(PlanViolation.builder()
                            .code("PORT_DAY_REQUIRES_PORT_CALL")
                            .severity("ERROR")
                            .entityType("DAY")
                            .dayNumber(day.getDayNumber())
                            .field("dayType")
                            .fieldPath("days[" + day.getDayNumber() + "].dayType")
                            .actionHint("ADD_PORT_CALL_OR_CHANGE_TO_SEA")
                            .message("Ngày " + day.getDayNumber() + " là PORT_DAY nhưng không có điểm dừng cảng nào ghé qua.")
                            .build());
                }
            } else if (day.getDayType() == ItineraryDayType.SEA_DAY) {
                if (dayStops != null && !dayStops.isEmpty()) {
                    violations.add(PlanViolation.builder()
                            .code("SEA_DAY_FORBIDS_PORT_CALL")
                            .severity("ERROR")
                            .entityType("DAY")
                            .dayNumber(day.getDayNumber())
                            .field("dayType")
                            .fieldPath("days[" + day.getDayNumber() + "].dayType")
                            .actionHint("CHANGE_TO_PORT_DAY_OR_REMOVE_STOP")
                            .message("Ngày " + day.getDayNumber() + " là SEA_DAY nhưng lại có tàu ghé cảng.")
                            .build());
                }
            }
        }

        if (!hasEmbarkation && !stops.isEmpty()) {
            violations.add(PlanViolation.builder()
                    .code("MISSING_EMBARKATION")
                    .severity("ERROR")
                    .entityType("VERSION")
                    .dayNumber(1)
                    .field("stops")
                    .fieldPath("stops")
                    .actionHint("ADD_EMBARKATION_STOP")
                    .message("Lịch trình bắt buộc phải có ít nhất một điểm đón khách (isEmbarkation = true) tại Ngày 1.")
                    .build());
        }

        if (!hasDisembarkation && !stops.isEmpty()) {
            violations.add(PlanViolation.builder()
                    .code("MISSING_DISEMBARKATION")
                    .severity("ERROR")
                    .entityType("VERSION")
                    .dayNumber(trip.getDurationDays())
                    .field("stops")
                    .fieldPath("stops")
                    .actionHint("ADD_DISEMBARKATION_STOP")
                    .message("Lịch trình bắt buộc phải có ít nhất một điểm trả khách (isDisembarkation = true) tại Ngày cuối cùng (" + trip.getDurationDays() + ").")
                    .build());
        }

        // Validate or confirm Route Type (tripType)
        TripPlanStop embarkStop = stops.stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsEmbarkation()))
                .findFirst().orElse(null);
        TripPlanStop disembarkStop = stops.stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsDisembarkation()))
                .findFirst().orElse(null);

        if (embarkStop != null && disembarkStop != null && embarkStop.getPort() != null && disembarkStop.getPort() != null) {
            boolean samePort = Objects.equals(embarkStop.getPort().getPortId(), disembarkStop.getPort().getPortId());
            if (trip.getTripType() == null) {
                trip.setTripType(samePort ? ItineraryType.ROUND_TRIP : ItineraryType.ONE_WAY);
                if (tripRepository != null) {
                    tripRepository.save(trip);
                }
            } else if (trip.getTripType() == ItineraryType.ROUND_TRIP && !samePort) {
                violations.add(PlanViolation.builder()
                        .code("TRIP_TYPE_MISMATCH")
                        .severity("ERROR")
                        .entityType("TRIP")
                        .field("tripType")
                        .fieldPath("trip.tripType")
                        .actionHint("MATCH_PORTS_OR_CHANGE_TYPE")
                        .message("Tuyến được chọn là ROUND_TRIP (khứ hồi) nhưng cảng đón ("
                                + embarkStop.getPort().getPortCode() + ") và cảng trả ("
                                + disembarkStop.getPort().getPortCode() + ") khác nhau.")
                        .build());
            } else if (trip.getTripType() == ItineraryType.ONE_WAY && samePort) {
                violations.add(PlanViolation.builder()
                        .code("TRIP_TYPE_MISMATCH")
                        .severity("ERROR")
                        .entityType("TRIP")
                        .field("tripType")
                        .fieldPath("trip.tripType")
                        .actionHint("MATCH_PORTS_OR_CHANGE_TYPE")
                        .message("Tuyến được chọn là ONE_WAY (một chiều) nhưng cảng đón và cảng trả lại cùng một cảng ("
                                + embarkStop.getPort().getPortCode() + ").")
                        .build());
            }
        }

        // Validate entries
        for (TripPlanEntry entry : entries) {
            if (entry.getStartDayNumber() == null || entry.getEndDayNumber() == null
                    || entry.getStartDayNumber() < 1 || entry.getEndDayNumber() > trip.getDurationDays()
                    || entry.getStartDayNumber() > entry.getEndDayNumber()) {
                violations.add(PlanViolation.builder()
                        .code("INVALID_ENTRY_DAY_INTERVAL")
                        .severity("ERROR")
                        .entityType("ENTRY")
                        .clientKey(entry.getClientKey())
                        .dayNumber(entry.getStartDayNumber())
                        .field("dayNumber")
                        .fieldPath("entries[clientKey=" + entry.getClientKey() + "].dayNumber")
                        .actionHint("EDIT_FIELD")
                        .message("Khoảng ngày của hoạt động '" + entry.getTitle() + "' không hợp lệ ("
                                + entry.getStartDayNumber() + " -> " + entry.getEndDayNumber() + ")")
                        .build());
            }

            if (entry.getKind() == TripPlanEntryKind.SHORE_EXCURSION) {
                TripPlanStop stop = entry.getStop();
                if (stop == null) {
                    violations.add(PlanViolation.builder()
                            .code("SHORE_REQUIRES_STOP")
                            .severity("ERROR")
                            .entityType("ENTRY")
                            .clientKey(entry.getClientKey())
                            .dayNumber(entry.getStartDayNumber())
                            .field("stopClientKey")
                            .fieldPath("entries[clientKey=" + entry.getClientKey() + "].stopClientKey")
                            .actionHint("SELECT_STOP")
                            .message("Hoạt động trên bờ '" + entry.getTitle() + "' bắt buộc phải thuộc một điểm dừng cảng.")
                            .build());
                } else {
                    if (entry.getStartDayNumber() < stop.getArrivalDayNumber()
                            || (entry.getStartDayNumber().equals(stop.getArrivalDayNumber())
                            && stop.getPlannedArrivalTime() != null && entry.getPlannedStartTime() != null
                            && entry.getPlannedStartTime().isBefore(stop.getPlannedArrivalTime()))) {
                        violations.add(PlanViolation.builder()
                                .code("SHORE_BEFORE_ARRIVAL")
                                .severity("ERROR")
                                .entityType("ENTRY")
                                .clientKey(entry.getClientKey())
                                .dayNumber(entry.getStartDayNumber())
                                .field("start")
                                .fieldPath("entries[clientKey=" + entry.getClientKey() + "].start")
                                .actionHint("EDIT_FIELD")
                                .message("Hoạt động trên bờ '" + entry.getTitle() + "' không thể bắt đầu trước khi tàu cập cảng.")
                                .build());
                    }

                    int deadlineDay = stop.getAllAboardDayNumber() != null ? stop.getAllAboardDayNumber() : stop.getDepartureDayNumber();
                    LocalTime deadlineTime = stop.getAllAboardTime() != null ? stop.getAllAboardTime() : stop.getPlannedDepartureTime();

                    if (entry.getEndDayNumber() > deadlineDay
                            || (entry.getEndDayNumber().equals(deadlineDay) && deadlineTime != null
                            && entry.getPlannedEndTime() != null && entry.getPlannedEndTime().isAfter(deadlineTime))) {
                        violations.add(PlanViolation.builder()
                                .code("SHORE_AFTER_DEPARTURE")
                                .severity("ERROR")
                                .entityType("ENTRY")
                                .clientKey(entry.getClientKey())
                                .dayNumber(entry.getEndDayNumber())
                                .field("end")
                                .fieldPath("entries[clientKey=" + entry.getClientKey() + "].end")
                                .actionHint("EDIT_FIELD")
                                .message("Hoạt động trên bờ '" + entry.getTitle() + "' phải kết thúc trước giờ tất cả lên tàu / rời cảng.")
                                .build());
                    }
                }
            } else {
                if (entry.getStartDayNumber() > entry.getEndDayNumber() || (entry.getStartDayNumber().equals(entry.getEndDayNumber())
                        && entry.getPlannedStartTime() != null && entry.getPlannedEndTime() != null
                        && !entry.getPlannedEndTime().isAfter(entry.getPlannedStartTime()))) {
                    violations.add(PlanViolation.builder()
                            .code("INVALID_ONBOARD_TIME")
                            .severity("ERROR")
                            .entityType("ENTRY")
                            .clientKey(entry.getClientKey())
                            .dayNumber(entry.getStartDayNumber())
                            .field("plannedEndTime")
                            .fieldPath("entries[clientKey=" + entry.getClientKey() + "].plannedEndTime")
                            .actionHint("EDIT_FIELD")
                            .message("Hoạt động '" + entry.getTitle() + "' có thời gian kết thúc không sau thời gian bắt đầu.")
                            .build());
                }
            }
        }

        return violations;
    }

    private TripScheduleVersion getVersion(UUID tripId, Integer versionNumber) {
        return versionRepository.findByTrip_TripIdAndVersionNumber(tripId, versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException("TripScheduleVersion", versionNumber));
    }

    public TripScheduleVersionDetailResponse mapToDetailResponse(TripScheduleVersion version) {
        TripScheduleVersionDetailResponse res = new TripScheduleVersionDetailResponse();
        res.setVersionId(version.getVersionId());
        res.setTripId(version.getTrip().getTripId());
        res.setTripCode(version.getTrip().getTripCode());
        res.setVersionNumber(version.getVersionNumber());
        res.setStatus(version.getStatus());
        res.setRevision(version.getRevision());
        res.setContentHash(version.getContentHash());
        if (version.getCreator() != null) {
            res.setCreatorId(version.getCreator().getUserId());
        }
        if (version.getSubmitter() != null) {
            res.setSubmitterId(version.getSubmitter().getUserId());
        }
        res.setSubmittedRevision(version.getSubmittedRevision());
        if (version.getPublishedBy() != null) {
            res.setPublishedBy(version.getPublishedBy().getUserId());
        }
        res.setPublishedAt(version.getPublishedAt());
        res.setRejectionReason(version.getRejectionReason());
        res.setSourceVersionId(version.getSourceVersionId());
        res.setTimePolicyVersion(version.getTimePolicyVersion());
        res.setPlanningPolicySnapshot(version.getPlanningPolicySnapshot());
        res.setCreatedAt(version.getCreatedAt());
        res.setUpdatedAt(version.getUpdatedAt());

        res.setDays(version.getDays().stream()
                .map(this::mapToDayResponse)
                .collect(Collectors.toList()));

        res.setStops(version.getStops().stream()
                .map(this::mapToStopResponse)
                .collect(Collectors.toList()));

        res.setEntries(version.getEntries().stream()
                .map(this::mapToEntryResponse)
                .collect(Collectors.toList()));

        return res;
    }

    public TripPlanDayResponse mapToDayResponse(TripPlanDay day) {
        TripPlanDayResponse res = new TripPlanDayResponse();
        res.setDayId(day.getDayId());
        res.setDayNumber(day.getDayNumber());
        res.setDayType(day.getDayType());
        res.setShipTimeZoneId(day.getShipTimeZoneId());
        res.setTitle(day.getTitle());
        res.setDescription(day.getDescription());
        return res;
    }

    public TripPlanStopResponse mapToStopResponse(TripPlanStop stop) {
        TripPlanStopResponse res = new TripPlanStopResponse();
        res.setStopId(stop.getStopId());
        res.setClientKey(stop.getClientKey());
        if (stop.getPort() != null) {
            res.setPortId(stop.getPort().getPortId());
            res.setPortCode(stop.getPort().getPortCode());
            res.setPortName(stop.getPort().getPortName());
            res.setCountryCode(stop.getPort().getCountryCode());
            res.setTimeZoneId(stop.getPort().getTimeZoneId());
        }
        if (stop.getDestination() != null) {
            res.setDestinationId(stop.getDestination().getDestinationId());
            res.setDestinationName(stop.getDestination().getDestinationName());
        }
        res.setStopOrder(stop.getStopOrder());
        res.setArrivalDayNumber(stop.getArrivalDayNumber());
        res.setPlannedArrivalTime(stop.getPlannedArrivalTime());
        res.setDepartureDayNumber(stop.getDepartureDayNumber());
        res.setPlannedDepartureTime(stop.getPlannedDepartureTime());
        res.setCallType(stop.getCallType());
        res.setArrivalMethod(stop.getArrivalMethod());
        res.setAllAboardDayNumber(stop.getAllAboardDayNumber());
        res.setAllAboardTime(stop.getAllAboardTime());
        res.setIsEmbarkation(stop.getIsEmbarkation());
        res.setIsDisembarkation(stop.getIsDisembarkation());
        return res;
    }

    public TripPlanEntryResponse mapToEntryResponse(TripPlanEntry entry) {
        TripPlanEntryResponse res = new TripPlanEntryResponse();
        res.setEntryId(entry.getEntryId());
        res.setClientKey(entry.getClientKey());
        if (entry.getDay() != null) {
            res.setDayId(entry.getDay().getDayId());
            res.setDayNumber(entry.getDay().getDayNumber());
        }
        res.setKind(entry.getKind());
        if (entry.getStop() != null) {
            res.setStopId(entry.getStop().getStopId());
            res.setStopClientKey(entry.getStop().getClientKey());
        }
        res.setSourceActivityId(entry.getSourceActivityId());
        res.setSourceExcursionId(entry.getSourceExcursionId());
        res.setServiceId(entry.getServiceId());
        res.setSeriesClientKey(entry.getSeriesClientKey());
        if (entry.getDestination() != null) {
            res.setDestinationId(entry.getDestination().getDestinationId());
            res.setDestinationName(entry.getDestination().getDestinationName());
        }
        res.setTitle(entry.getTitle());
        res.setStartDayNumber(entry.getStartDayNumber());
        res.setPlannedStartTime(entry.getPlannedStartTime());
        res.setEndDayNumber(entry.getEndDayNumber());
        res.setPlannedEndTime(entry.getPlannedEndTime());
        res.setTimeZoneId(entry.getTimeZoneId());
        res.setLocationText(entry.getLocationText());
        res.setMeetingPoint(entry.getMeetingPoint());
        res.setNotes(entry.getNotes());
        return res;
    }

    private String computeContentHash(TripScheduleVersion version) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            sb.append(version.getTrip().getTripId()).append("|")
              .append(version.getVersionNumber()).append("|");
            for (TripPlanDay day : version.getDays()) {
                sb.append("D(").append(day.getDayNumber()).append(":")
                  .append(day.getDayType()).append(":")
                  .append(day.getShipTimeZoneId()).append(");");
            }
            for (TripPlanStop stop : version.getStops()) {
                sb.append("S(").append(stop.getClientKey()).append(",")
                  .append(stop.getStopOrder()).append(",")
                  .append(stop.getPort() != null ? stop.getPort().getPortId() : "").append(",")
                  .append(stop.getArrivalDayNumber()).append(",")
                  .append(stop.getDepartureDayNumber()).append(");");
            }
            for (TripPlanEntry entry : version.getEntries()) {
                sb.append("E(").append(entry.getClientKey()).append(",")
                  .append(entry.getKind()).append(",")
                  .append(entry.getStartDayNumber()).append(",")
                  .append(entry.getPlannedStartTime()).append(");");
            }
            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }
}
