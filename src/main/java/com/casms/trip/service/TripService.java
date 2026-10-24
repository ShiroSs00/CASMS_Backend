package com.casms.trip.service;

import com.casms.audit.AuditService;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.common.service.BusinessCodeGenerator;
import com.casms.identity.entity.User;
import com.casms.identity.repository.UserRepository;
import com.casms.trip.dto.request.ScheduleVersionCreateRequest;
import com.casms.trip.dto.request.TripCreateRequest;
import com.casms.trip.dto.request.TripDraftPatchRequest;
import com.casms.trip.dto.request.TripUpdateRequest;
import com.casms.trip.dto.response.*;
import com.casms.trip.entity.Trip;
import com.casms.trip.entity.TripPlanDay;
import com.casms.trip.entity.TripPlanEntry;
import com.casms.trip.entity.TripPlanStop;
import com.casms.trip.entity.TripScheduleVersion;
import com.casms.trip.enums.TripScheduleVersionStatus;
import com.casms.trip.enums.TripStatus;
import com.casms.trip.repository.TripPlanDayRepository;
import com.casms.trip.repository.TripRepository;
import com.casms.trip.repository.TripScheduleVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository tripRepository;
    private final TripScheduleVersionRepository versionRepository;
    private final TripPlanDayRepository dayRepository;
    private final UserRepository userRepository;
    private final BusinessCodeGenerator businessCodeGenerator;
    private final AuditService auditService;

    @Transactional
    public TripDetailResponse createTrip(TripCreateRequest request, UUID actorId) {
        if (request.getDurationDays() == null || request.getDurationDays() < 1) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_DURATION", "Số ngày (N) phải lớn hơn hoặc bằng 1");
        }

        int durationNights = request.getDurationNights() != null
                ? request.getDurationNights()
                : Math.max(0, request.getDurationDays() - 1);

        String tripCode = businessCodeGenerator.next("TR");

        User creator = null;
        if (actorId != null) {
            creator = userRepository.findById(actorId).orElse(null);
        }

        Trip trip = new Trip();
        trip.setTripCode(tripCode);
        trip.setName(request.getName().trim());
        trip.setDescription(request.getDescription());
        trip.setDurationDays(request.getDurationDays());
        trip.setDurationNights(durationNights);
        trip.setTripType(request.getTripType());
        trip.setPlannedScope(request.getPlannedScope());
        trip.setShipRequirements(request.getShipRequirements());
        trip.setCandidateDestinations(request.getCandidateDestinations());
        trip.setStatus(TripStatus.DRAFT);
        trip.setRevision(1L);
        trip.setCreatedByUser(creator);

        trip = tripRepository.save(trip);

        // Pre-populate Version 1 with N relative TripPlanDay records
        TripScheduleVersion v1 = new TripScheduleVersion();
        v1.setTrip(trip);
        v1.setVersionNumber(1);
        v1.setStatus(TripScheduleVersionStatus.DRAFT);
        v1.setRevision(1L);
        v1.setCreator(creator);

        List<TripPlanDay> days = new ArrayList<>();
        for (int i = 1; i <= request.getDurationDays(); i++) {
            TripPlanDay day = new TripPlanDay();
            day.setVersion(v1);
            day.setDayNumber(i);
            day.setDayType(null); // Unset in draft
            day.setShipTimeZoneId("Asia/Ho_Chi_Minh");
            day.setTitle("Ngày " + i);
            days.add(day);
        }
        v1.setDays(days);
        v1 = versionRepository.save(v1);

        trip.getVersions().add(v1);
        log.info("Created Trip {} ({}) with initial Version 1 and {} relative day frames",
                trip.getTripCode(), trip.getTripId(), request.getDurationDays());

        if (auditService != null) {
            auditService.record(actorId, "CREATE_TRIP", "Trip", trip.getTripId(), "SUCCESS", "Created Trip " + trip.getTripCode());
        }

        return mapToDetailResponse(trip);
    }

    @Transactional
    public TripDetailResponse patchTripDraft(UUID tripId, TripDraftPatchRequest request, UUID actorId) {
        Trip trip = getTripById(tripId);

        if (request.getExpectedRevision() != null && !request.getExpectedRevision().equals(trip.getRevision())) {
            throw new BusinessException(HttpStatus.CONFLICT, "STALE_REVISION",
                    "Revision của Trip không khớp (expected: " + request.getExpectedRevision() + ", current: " + trip.getRevision() + ")");
        }

        boolean isLocked = trip.getStructuralLockedAt() != null || versionRepository.findByTrip_TripIdOrderByVersionNumberAsc(tripId).stream()
                .anyMatch(v -> v.getStatus() == TripScheduleVersionStatus.PUBLISHED);

        if (isLocked) {
            if ((request.getDurationDays() != null && !request.getDurationDays().equals(trip.getDurationDays()))
                    || (request.getDurationNights() != null && !request.getDurationNights().equals(trip.getDurationNights()))) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "TRIP_DURATION_LOCKED",
                        "Không thể thay đổi số ngày hoặc số đêm khi Trip đã có phiên bản được phê duyệt xuất bản.");
            }
        } else if (request.getDurationDays() != null && !request.getDurationDays().equals(trip.getDurationDays())) {
            int oldDays = trip.getDurationDays();
            int newDays = request.getDurationDays();
            trip.setDurationDays(newDays);

            List<TripScheduleVersion> draftVersions = versionRepository.findByTrip_TripIdOrderByVersionNumberAsc(tripId)
                    .stream()
                    .filter(v -> v.getStatus() == TripScheduleVersionStatus.DRAFT)
                    .collect(Collectors.toList());

            for (TripScheduleVersion draftVer : draftVersions) {
                if (newDays > oldDays) {
                    for (int i = oldDays + 1; i <= newDays; i++) {
                        TripPlanDay day = new TripPlanDay();
                        day.setVersion(draftVer);
                        day.setDayNumber(i);
                        day.setDayType(null);
                        day.setShipTimeZoneId("Asia/Ho_Chi_Minh");
                        day.setTitle("Ngày " + i);
                        draftVer.getDays().add(day);
                    }
                } else {
                    draftVer.getDays().removeIf(d -> d.getDayNumber() > newDays);
                }
                draftVer.setRevision(draftVer.getRevision() + 1);
                versionRepository.save(draftVer);
            }
        }

        if (request.getDurationNights() != null) {
            trip.setDurationNights(request.getDurationNights());
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            trip.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            trip.setDescription(request.getDescription());
        }
        if (request.getTripType() != null) {
            trip.setTripType(request.getTripType());
        }
        if (request.getPlannedScope() != null) {
            trip.setPlannedScope(request.getPlannedScope());
        }
        if (request.getShipRequirements() != null) {
            trip.setShipRequirements(request.getShipRequirements());
        }
        if (request.getCandidateDestinations() != null) {
            trip.setCandidateDestinations(request.getCandidateDestinations());
        }

        trip.setRevision(trip.getRevision() + 1);
        trip = tripRepository.save(trip);

        if (auditService != null) {
            auditService.record(actorId, "PATCH_DRAFT", "Trip", trip.getTripId(), "SUCCESS", "Patched Trip " + trip.getTripCode());
        }
        return mapToDetailResponse(trip);
    }

    @Transactional
    public TripDetailResponse updateTrip(UUID tripId, TripUpdateRequest request, UUID actorId) {
        TripDraftPatchRequest patch = new TripDraftPatchRequest();
        patch.setName(request.getName());
        patch.setDescription(request.getDescription());
        patch.setDurationDays(request.getDurationDays());
        patch.setDurationNights(request.getDurationNights());
        patch.setTripType(request.getTripType());
        patch.setPlannedScope(request.getPlannedScope());
        patch.setShipRequirements(request.getShipRequirements());
        patch.setCandidateDestinations(request.getCandidateDestinations());
        return patchTripDraft(tripId, patch, actorId);
    }

    @Transactional
    public TripDetailResponse cloneTrip(UUID tripId, UUID sourceVersionId, UUID actorId) {
        Trip sourceTrip = getTripById(tripId);

        User actor = null;
        if (actorId != null) {
            actor = userRepository.findById(actorId).orElse(null);
        }

        Trip newTrip = new Trip();
        newTrip.setTripCode(businessCodeGenerator.next("TR"));
        newTrip.setName("Bản sao - " + sourceTrip.getName());
        newTrip.setDescription(sourceTrip.getDescription());
        newTrip.setDurationDays(sourceTrip.getDurationDays());
        newTrip.setDurationNights(sourceTrip.getDurationNights());
        newTrip.setTripType(sourceTrip.getTripType());
        newTrip.setPlannedScope(sourceTrip.getPlannedScope());
        newTrip.setShipRequirements(sourceTrip.getShipRequirements());
        newTrip.setCandidateDestinations(sourceTrip.getCandidateDestinations());
        newTrip.setStatus(TripStatus.DRAFT);
        newTrip.setRevision(1L);
        newTrip.setCreatedByUser(actor);

        newTrip = tripRepository.save(newTrip);

        // Find source version if specified or fallback to latest
        TripScheduleVersion sourceVer = null;
        if (sourceVersionId != null) {
            sourceVer = versionRepository.findById(sourceVersionId).orElse(null);
        }
        if (sourceVer == null) {
            sourceVer = versionRepository.findTopByTrip_TripIdOrderByVersionNumberDesc(tripId).orElse(null);
        }

        TripScheduleVersion newVer = new TripScheduleVersion();
        newVer.setTrip(newTrip);
        newVer.setVersionNumber(1);
        newVer.setStatus(TripScheduleVersionStatus.DRAFT);
        newVer.setRevision(1L);
        newVer.setCreator(actor);

        if (sourceVer != null) {
            newVer.setSourceVersionId(sourceVer.getVersionId());
            Map<Integer, TripPlanDay> dayMap = new HashMap<>();
            List<TripPlanDay> days = new ArrayList<>();
            for (TripPlanDay oldDay : sourceVer.getDays()) {
                TripPlanDay newDay = new TripPlanDay();
                newDay.setVersion(newVer);
                newDay.setDayNumber(oldDay.getDayNumber());
                newDay.setDayType(oldDay.getDayType());
                newDay.setShipTimeZoneId(oldDay.getShipTimeZoneId());
                newDay.setTitle(oldDay.getTitle());
                newDay.setDescription(oldDay.getDescription());
                days.add(newDay);
                dayMap.put(newDay.getDayNumber(), newDay);
            }
            newVer.setDays(days);

            Map<String, TripPlanStop> stopMap = new HashMap<>();
            List<TripPlanStop> stops = new ArrayList<>();
            for (TripPlanStop oldStop : sourceVer.getStops()) {
                TripPlanStop newStop = new TripPlanStop();
                newStop.setVersion(newVer);
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
                stops.add(newStop);
                stopMap.put(newStop.getClientKey(), newStop);
            }
            newVer.setStops(stops);

            List<TripPlanEntry> entries = new ArrayList<>();
            for (TripPlanEntry oldEntry : sourceVer.getEntries()) {
                TripPlanEntry newEntry = new TripPlanEntry();
                newEntry.setVersion(newVer);
                newEntry.setClientKey(oldEntry.getClientKey());
                newEntry.setDay(dayMap.get(oldEntry.getDay().getDayNumber()));
                newEntry.setKind(oldEntry.getKind());
                if (oldEntry.getStop() != null) {
                    newEntry.setStop(stopMap.get(oldEntry.getStop().getClientKey()));
                }
                newEntry.setServiceId(oldEntry.getServiceId());
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
                entries.add(newEntry);
            }
            newVer.setEntries(entries);
        } else {
            List<TripPlanDay> days = new ArrayList<>();
            for (int i = 1; i <= newTrip.getDurationDays(); i++) {
                TripPlanDay day = new TripPlanDay();
                day.setVersion(newVer);
                day.setDayNumber(i);
                day.setShipTimeZoneId("Asia/Ho_Chi_Minh");
                day.setTitle("Ngày " + i);
                days.add(day);
            }
            newVer.setDays(days);
        }

        newVer = versionRepository.save(newVer);
        newTrip.getVersions().add(newVer);

        if (auditService != null) {
            auditService.record(actorId, "CLONE_TRIP", "Trip", newTrip.getTripId(), "SUCCESS",
                    "Cloned from Trip " + sourceTrip.getTripCode() + " to " + newTrip.getTripCode());
        }

        TripDetailResponse response = mapToDetailResponse(newTrip);
        response.setClonedDraftVersionId(newVer.getVersionId());
        return response;
    }

    @Transactional(readOnly = true)
    public List<TripScheduleVersionSummaryResponse> getScheduleVersions(UUID tripId) {
        Trip trip = getTripById(tripId);
        List<TripScheduleVersion> versions = versionRepository.findByTrip_TripIdOrderByVersionNumberAsc(tripId);
        return versions.stream().map(this::mapToVersionSummaryResponse).collect(Collectors.toList());
    }

    @Transactional
    public TripScheduleVersionDetailResponse createScheduleVersion(UUID tripId, ScheduleVersionCreateRequest request, UUID actorId) {
        Trip trip = getTripById(tripId);

        boolean hasUnfinished = versionRepository.existsByTrip_TripIdAndStatusIn(tripId,
                List.of(TripScheduleVersionStatus.DRAFT, TripScheduleVersionStatus.IN_REVIEW));

        if (hasUnfinished) {
            throw new BusinessException(HttpStatus.CONFLICT, "INVALID_STATE",
                    "Đang có một phiên bản chưa hoàn tất (DRAFT hoặc IN_REVIEW). Vui lòng xử lý trước khi tạo phiên bản mới.");
        }

        List<TripScheduleVersion> existingVersions = versionRepository.findByTrip_TripIdOrderByVersionNumberAsc(tripId);
        int nextVersionNumber = existingVersions.isEmpty() ? 1 : existingVersions.get(existingVersions.size() - 1).getVersionNumber() + 1;

        User creator = null;
        if (actorId != null) {
            creator = userRepository.findById(actorId).orElse(null);
        }

        TripScheduleVersion newVer = new TripScheduleVersion();
        newVer.setTrip(trip);
        newVer.setVersionNumber(nextVersionNumber);
        newVer.setStatus(TripScheduleVersionStatus.DRAFT);
        newVer.setRevision(1L);
        newVer.setCreator(creator);

        TripScheduleVersion sourceVer = null;
        if (request != null && request.getSourceVersionId() != null) {
            sourceVer = versionRepository.findById(request.getSourceVersionId())
                    .orElseThrow(() -> new ResourceNotFoundException("TripScheduleVersion", request.getSourceVersionId()));
            if (!sourceVer.getTrip().getTripId().equals(tripId)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "sourceVersionId phải thuộc về cùng một Trip.");
            }
        }

        if (sourceVer != null) {
            newVer.setSourceVersionId(sourceVer.getVersionId());
            Map<Integer, TripPlanDay> dayMap = new HashMap<>();
            List<TripPlanDay> days = new ArrayList<>();
            for (TripPlanDay oldDay : sourceVer.getDays()) {
                TripPlanDay newDay = new TripPlanDay();
                newDay.setVersion(newVer);
                newDay.setDayNumber(oldDay.getDayNumber());
                newDay.setDayType(oldDay.getDayType());
                newDay.setShipTimeZoneId(oldDay.getShipTimeZoneId());
                newDay.setTitle(oldDay.getTitle());
                newDay.setDescription(oldDay.getDescription());
                days.add(newDay);
                dayMap.put(newDay.getDayNumber(), newDay);
            }
            newVer.setDays(days);

            Map<String, TripPlanStop> stopMap = new HashMap<>();
            List<TripPlanStop> stops = new ArrayList<>();
            for (TripPlanStop oldStop : sourceVer.getStops()) {
                TripPlanStop newStop = new TripPlanStop();
                newStop.setVersion(newVer);
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
                stops.add(newStop);
                stopMap.put(newStop.getClientKey(), newStop);
            }
            newVer.setStops(stops);

            List<TripPlanEntry> entries = new ArrayList<>();
            for (TripPlanEntry oldEntry : sourceVer.getEntries()) {
                TripPlanEntry newEntry = new TripPlanEntry();
                newEntry.setVersion(newVer);
                newEntry.setClientKey(oldEntry.getClientKey());
                newEntry.setDay(dayMap.get(oldEntry.getDay().getDayNumber()));
                newEntry.setKind(oldEntry.getKind());
                if (oldEntry.getStop() != null) {
                    newEntry.setStop(stopMap.get(oldEntry.getStop().getClientKey()));
                }
                newEntry.setServiceId(oldEntry.getServiceId());
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
                entries.add(newEntry);
            }
            newVer.setEntries(entries);
        } else {
            List<TripPlanDay> days = new ArrayList<>();
            for (int i = 1; i <= trip.getDurationDays(); i++) {
                TripPlanDay day = new TripPlanDay();
                day.setVersion(newVer);
                day.setDayNumber(i);
                day.setShipTimeZoneId("Asia/Ho_Chi_Minh");
                day.setTitle("Ngày " + i);
                days.add(day);
            }
            newVer.setDays(days);
        }

        newVer = versionRepository.save(newVer);
        auditService.record(actorId, "CREATE_SCHEDULE_VERSION", "TripScheduleVersion", newVer.getVersionId(),
                "SUCCESS", "Created Version " + newVer.getVersionNumber() + " for Trip " + trip.getTripCode());

        return mapToScheduleVersionDetail(newVer);
    }

    @Transactional(readOnly = true)
    public Page<TripSummaryResponse> listTrips(TripStatus status, Pageable pageable) {
        Page<Trip> page = (status != null)
                ? tripRepository.findByStatus(status, pageable)
                : tripRepository.findAll(pageable);
        return page.map(this::mapToSummaryResponse);
    }

    @Transactional(readOnly = true)
    public TripDetailResponse getTripDetail(UUID tripId) {
        Trip trip = getTripById(tripId);
        return mapToDetailResponse(trip);
    }

    @Transactional
    public TripDetailResponse archiveTrip(UUID tripId, UUID actorId) {
        Trip trip = getTripById(tripId);
        trip.setStatus(TripStatus.ARCHIVED);
        trip = tripRepository.save(trip);
        auditService.record(actorId, "ARCHIVE_TRIP", "Trip", tripId, "SUCCESS", "Archived Trip " + trip.getTripCode());
        return mapToDetailResponse(trip);
    }

    public Trip getTripById(UUID tripId) {
        return tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", tripId));
    }

    public TripSummaryResponse mapToSummaryResponse(Trip trip) {
        TripSummaryResponse res = new TripSummaryResponse();
        res.setTripId(trip.getTripId());
        res.setTripCode(trip.getTripCode());
        res.setName(trip.getName());
        res.setDescription(trip.getDescription());
        res.setDurationDays(trip.getDurationDays());
        res.setDurationNights(trip.getDurationNights());
        res.setTripType(trip.getTripType());
        res.setPlannedScope(trip.getPlannedScope());
        res.setStatus(trip.getStatus());
        res.setStructuralLockedAt(trip.getStructuralLockedAt());
        res.setRevision(trip.getRevision());
        res.setCreatedAt(trip.getCreatedAt());
        res.setUpdatedAt(trip.getUpdatedAt());

        List<TripScheduleVersion> versions = (trip.getVersions() != null && !trip.getVersions().isEmpty())
                ? trip.getVersions()
                : versionRepository.findByTrip_TripIdOrderByVersionNumberAsc(trip.getTripId());
        if (!versions.isEmpty()) {
            TripScheduleVersion latest = versions.get(versions.size() - 1);
            res.setLatestVersionNumber(latest.getVersionNumber());
            res.setLatestVersionStatus(latest.getStatus());
        }
        return res;
    }

    public TripDetailResponse mapToDetailResponse(Trip trip) {
        TripDetailResponse res = new TripDetailResponse();
        res.setTripId(trip.getTripId());
        res.setTripCode(trip.getTripCode());
        res.setName(trip.getName());
        res.setDescription(trip.getDescription());
        res.setDurationDays(trip.getDurationDays());
        res.setDurationNights(trip.getDurationNights());
        res.setTripType(trip.getTripType());
        res.setPlannedScope(trip.getPlannedScope());
        res.setShipRequirements(trip.getShipRequirements());
        res.setCandidateDestinations(trip.getCandidateDestinations());
        res.setStatus(trip.getStatus());
        res.setStructuralLockedAt(trip.getStructuralLockedAt());
        res.setRevision(trip.getRevision());
        if (trip.getCreatedByUser() != null) {
            res.setCreatedByUserId(trip.getCreatedByUser().getUserId());
        }
        res.setCreatedAt(trip.getCreatedAt());
        res.setUpdatedAt(trip.getUpdatedAt());

        List<TripScheduleVersion> versions = (trip.getVersions() != null && !trip.getVersions().isEmpty())
                ? trip.getVersions()
                : versionRepository.findByTrip_TripIdOrderByVersionNumberAsc(trip.getTripId());
        if (!versions.isEmpty()) {
            TripScheduleVersion latest = versions.get(versions.size() - 1);
            res.setLatestVersionNumber(latest.getVersionNumber());
            res.setLatestVersionStatus(latest.getStatus());
        }

        res.setVersions(versions.stream()
                .map(this::mapToVersionSummaryResponse)
                .collect(Collectors.toList()));
        return res;
    }

    public TripScheduleVersionSummaryResponse mapToVersionSummaryResponse(TripScheduleVersion version) {
        TripScheduleVersionSummaryResponse res = new TripScheduleVersionSummaryResponse();
        res.setVersionId(version.getVersionId());
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
        res.setCreatedAt(version.getCreatedAt());
        res.setUpdatedAt(version.getUpdatedAt());
        return res;
    }

    public TripScheduleVersionDetailResponse mapToScheduleVersionDetail(TripScheduleVersion version) {
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

        res.setDays(version.getDays().stream().map(d -> {
            TripPlanDayResponse dayRes = new TripPlanDayResponse();
            dayRes.setDayId(d.getDayId());
            dayRes.setDayNumber(d.getDayNumber());
            dayRes.setDayType(d.getDayType());
            dayRes.setShipTimeZoneId(d.getShipTimeZoneId());
            dayRes.setTitle(d.getTitle());
            dayRes.setDescription(d.getDescription());
            return dayRes;
        }).collect(Collectors.toList()));

        res.setStops(version.getStops().stream().map(s -> {
            TripPlanStopResponse stopRes = new TripPlanStopResponse();
            stopRes.setStopId(s.getStopId());
            stopRes.setClientKey(s.getClientKey());
            if (s.getPort() != null) {
                stopRes.setPortId(s.getPort().getPortId());
                stopRes.setPortCode(s.getPort().getPortCode());
                stopRes.setPortName(s.getPort().getPortName());
                stopRes.setCountryCode(s.getPort().getCountryCode());
                stopRes.setTimeZoneId(s.getPort().getTimeZoneId());
            }
            stopRes.setStopOrder(s.getStopOrder());
            stopRes.setArrivalDayNumber(s.getArrivalDayNumber());
            stopRes.setPlannedArrivalTime(s.getPlannedArrivalTime());
            stopRes.setDepartureDayNumber(s.getDepartureDayNumber());
            stopRes.setPlannedDepartureTime(s.getPlannedDepartureTime());
            stopRes.setCallType(s.getCallType());
            stopRes.setArrivalMethod(s.getArrivalMethod());
            stopRes.setAllAboardDayNumber(s.getAllAboardDayNumber());
            stopRes.setAllAboardTime(s.getAllAboardTime());
            stopRes.setIsEmbarkation(s.getIsEmbarkation());
            stopRes.setIsDisembarkation(s.getIsDisembarkation());
            return stopRes;
        }).collect(Collectors.toList()));

        res.setEntries(version.getEntries().stream().map(e -> {
            TripPlanEntryResponse entryRes = new TripPlanEntryResponse();
            entryRes.setEntryId(e.getEntryId());
            entryRes.setClientKey(e.getClientKey());
            if (e.getDay() != null) {
                entryRes.setDayId(e.getDay().getDayId());
                entryRes.setDayNumber(e.getDay().getDayNumber());
            }
            entryRes.setKind(e.getKind());
            if (e.getStop() != null) {
                entryRes.setStopId(e.getStop().getStopId());
                entryRes.setStopClientKey(e.getStop().getClientKey());
            }
            entryRes.setTitle(e.getTitle());
            entryRes.setStartDayNumber(e.getStartDayNumber());
            entryRes.setPlannedStartTime(e.getPlannedStartTime());
            entryRes.setEndDayNumber(e.getEndDayNumber());
            entryRes.setPlannedEndTime(e.getPlannedEndTime());
            entryRes.setTimeZoneId(e.getTimeZoneId());
            entryRes.setLocationText(e.getLocationText());
            entryRes.setMeetingPoint(e.getMeetingPoint());
            entryRes.setNotes(e.getNotes());
            return entryRes;
        }).collect(Collectors.toList()));

        return res;
    }
}
