package com.casms.trip.service;

import com.casms.audit.AuditService;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.common.service.BusinessCodeGenerator;
import com.casms.identity.entity.User;
import com.casms.identity.repository.UserRepository;
import com.casms.trip.dto.request.*;
import com.casms.trip.dto.response.*;
import com.casms.trip.entity.Trip;
import com.casms.trip.entity.TripPlanDay;
import com.casms.trip.entity.TripPlanEntry;
import com.casms.trip.entity.TripPlanStop;
import com.casms.trip.entity.TripScheduleVersion;
import com.casms.trip.enums.TripDayManageAction;
import com.casms.trip.enums.TripDurationGroup;
import com.casms.trip.enums.TripScheduleVersionStatus;
import com.casms.trip.enums.TripStatus;
import com.casms.trip.repository.TripPlanDayRepository;
import com.casms.trip.repository.TripRepository;
import com.casms.trip.repository.TripScheduleVersionRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.casms.audit.AuditLog;
import com.casms.audit.AuditRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TripService {

    private final TripRepository tripRepository;
    private final TripScheduleVersionRepository versionRepository;
    private final TripPlanDayRepository dayRepository;
    private final UserRepository userRepository;
    private final BusinessCodeGenerator businessCodeGenerator;
    private final AuditService auditService;
    private AuditRepository auditRepository;

    public TripService(TripRepository tripRepository,
                       TripScheduleVersionRepository versionRepository,
                       TripPlanDayRepository dayRepository,
                       UserRepository userRepository,
                       BusinessCodeGenerator businessCodeGenerator,
                       AuditService auditService) {
        this.tripRepository = tripRepository;
        this.versionRepository = versionRepository;
        this.dayRepository = dayRepository;
        this.userRepository = userRepository;
        this.businessCodeGenerator = businessCodeGenerator;
        this.auditService = auditService;
    }

    @Autowired
    public TripService(TripRepository tripRepository,
                       TripScheduleVersionRepository versionRepository,
                       TripPlanDayRepository dayRepository,
                       UserRepository userRepository,
                       BusinessCodeGenerator businessCodeGenerator,
                       AuditService auditService,
                       @Autowired(required = false) AuditRepository auditRepository) {
        this(tripRepository, versionRepository, dayRepository, userRepository, businessCodeGenerator, auditService);
        this.auditRepository = auditRepository;
    }

    public void setAuditRepository(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @Transactional
    public TripDetailResponse createTrip(TripCreateRequest request, UUID actorId) {
        TripDurationGroup durationGroup = request.getDurationGroup();
        if (durationGroup == null) {
            if (request.getDurationDays() != null && request.getDurationDays() >= 3) {
                durationGroup = TripDurationGroup.LONG;
            } else {
                durationGroup = TripDurationGroup.SHORT;
            }
        }

        int durationDays;
        if (request.getDurationDays() != null) {
            if (!durationGroup.isValidDays(request.getDurationDays())) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_DURATION",
                        "Số ngày (" + request.getDurationDays() + ") không nằm trong giới hạn của nhóm "
                                + durationGroup + " (" + durationGroup.getMinDays() + " - " + durationGroup.getMaxDays() + " ngày)");
            }
            durationDays = request.getDurationDays();
        } else {
            durationDays = durationGroup.getDefaultDays();
        }

        int durationNights = request.getDurationNights() != null
                ? request.getDurationNights()
                : Math.max(0, durationDays - 1);

        String tripCode = businessCodeGenerator.next("TR");

        User creator = null;
        if (actorId != null) {
            creator = userRepository.findById(actorId).orElse(null);
        }

        Trip trip = new Trip();
        trip.setTripCode(tripCode);
        trip.setName(request.getName().trim());
        trip.setDescription(request.getDescription());
        trip.setDurationGroup(durationGroup);
        trip.setDurationDays(durationDays);
        trip.setDurationNights(durationNights);
        trip.setTripType(request.getTripType());
        trip.setPlannedScope(request.getPlannedScope() != null ? request.getPlannedScope() : "DOMESTIC");
        trip.setDomesticRegions(request.getDomesticRegions());
        trip.setCountryCodes(request.getCountryCodes());
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
        for (int i = 1; i <= durationDays; i++) {
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
        log.info("Created Trip {} ({}) with initial Version 1 and {} relative day frames for group {}",
                trip.getTripCode(), trip.getTripId(), durationDays, durationGroup);

        if (auditService != null) {
            auditService.record(actorId, "CREATE_TRIP", "Trip", trip.getTripId(), "SUCCESS", "Created Trip " + trip.getTripCode());
        }

        return mapToDetailResponse(trip);
    }

    @Transactional
    public TripDetailResponse patchTripDraft(UUID tripId, TripDraftPatchRequest request, UUID actorId) {
        Trip trip = getTripById(tripId);

        if (trip.getStatus() == TripStatus.ARCHIVED) {
            throw new BusinessException(HttpStatus.CONFLICT, "TRIP_ARCHIVED",
                    "Không thể chỉnh sửa Trip đã được lưu trữ (ARCHIVED)");
        }

        if (request.getExpectedRevision() != null && !request.getExpectedRevision().equals(trip.getRevision())) {
            throw new BusinessException(HttpStatus.CONFLICT, "STALE_REVISION",
                    "Revision của Trip không khớp (expected: " + request.getExpectedRevision() + ", current: " + trip.getRevision() + ")");
        }

        boolean isLocked = trip.getStructuralLockedAt() != null || versionRepository.findByTrip_TripIdOrderByVersionNumberAsc(tripId).stream()
                .anyMatch(v -> v.getStatus() == TripScheduleVersionStatus.PUBLISHED);

        if (isLocked) {
            if ((request.getDurationDays() != null && !request.getDurationDays().equals(trip.getDurationDays()))
                    || (request.getDurationNights() != null && !request.getDurationNights().equals(trip.getDurationNights()))
                    || (request.getDurationGroup() != null && !request.getDurationGroup().equals(trip.getDurationGroup()))) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "TRIP_DURATION_LOCKED",
                        "Không thể thay đổi nhóm thời lượng, số ngày hoặc số đêm khi Trip đã có phiên bản được phê duyệt xuất bản.");
            }
        }

        // BE-09: durationDays can only change via /days/manage endpoint
        if (request.getDurationDays() != null && !request.getDurationDays().equals(trip.getDurationDays())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_DAY_COUNT",
                    "Số ngày (durationDays) chỉ có thể thay đổi thông qua endpoint quản lý ngày /days/manage.");
        }

        // BE-03: durationGroup policy
        if (request.getDurationGroup() != null && !request.getDurationGroup().equals(trip.getDurationGroup())) {
            if (versionRepository.existsByTrip_TripIdAndStatus(tripId, TripScheduleVersionStatus.IN_REVIEW)) {
                throw new BusinessException(HttpStatus.CONFLICT, "CONCURRENT_REVIEW_IN_PROGRESS",
                        "Không thể thay đổi nhóm thời lượng khi đang có phiên bản chờ duyệt (IN_REVIEW).");
            }
            if (!request.getDurationGroup().isValidDays(trip.getDurationDays())) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_DURATION",
                        "Nhóm thời lượng " + request.getDurationGroup() + " (" + request.getDurationGroup().getMinDays()
                                + " - " + request.getDurationGroup().getMaxDays() + " ngày) không tương thích với số ngày hiện tại ("
                                + trip.getDurationDays() + " ngày). Vui lòng điều chỉnh số ngày qua /days/manage trước.");
            }
            trip.setDurationGroup(request.getDurationGroup());
        }

        if (request.getDurationNights() != null && !request.getDurationNights().equals(trip.getDurationNights())) {
            if (isLocked) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "TRIP_DURATION_LOCKED",
                        "Không thể thay đổi số đêm khi Trip đã có phiên bản được phê duyệt xuất bản.");
            }
            if (request.getDurationNights() < 0 || request.getDurationNights() > trip.getDurationDays()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_INPUT",
                        "Số đêm (durationNights) không hợp lệ (phải từ 0 đến " + trip.getDurationDays() + ").");
            }
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
        if (request.getDomesticRegions() != null) {
            trip.setDomesticRegions(request.getDomesticRegions());
        }
        if (request.getCountryCodes() != null) {
            trip.setCountryCodes(request.getCountryCodes());
        }
        if (request.getShipRequirements() != null) {
            trip.setShipRequirements(request.getShipRequirements());
        }
        if (request.getCandidateDestinations() != null) {
            trip.setCandidateDestinations(request.getCandidateDestinations());
        }

        trip = tripRepository.save(trip);
        tripRepository.flush();

        if (auditService != null) {
            auditService.record(actorId, "PATCH_DRAFT", "Trip", trip.getTripId(), "SUCCESS", "Patched Trip " + trip.getTripCode());
        }
        return mapToDetailResponse(trip);
    }

    @Transactional
    public TripDetailResponse updateTrip(UUID tripId, TripUpdateRequest request, UUID actorId) {
        TripDraftPatchRequest patch = new TripDraftPatchRequest();
        patch.setExpectedRevision(request.getExpectedRevision());
        patch.setName(request.getName());
        patch.setDescription(request.getDescription());
        patch.setDurationGroup(request.getDurationGroup());
        patch.setDurationDays(request.getDurationDays());
        patch.setDurationNights(request.getDurationNights());
        patch.setTripType(request.getTripType());
        patch.setPlannedScope(request.getPlannedScope());
        patch.setDomesticRegions(request.getDomesticRegions());
        patch.setCountryCodes(request.getCountryCodes());
        patch.setShipRequirements(request.getShipRequirements());
        patch.setCandidateDestinations(request.getCandidateDestinations());
        return patchTripDraft(tripId, patch, actorId);
    }

    @Transactional
    public TripDayManageResponse manageDays(UUID tripId, UUID versionId, TripDayManageRequest request, UUID actorId) {
        Trip trip = getTripById(tripId);

        if (trip.getStatus() == TripStatus.ARCHIVED) {
            throw new BusinessException(HttpStatus.CONFLICT, "TRIP_ARCHIVED",
                    "Không thể quản lý ngày trên Trip đã được lưu trữ (ARCHIVED)");
        }

        boolean isLocked = trip.getStructuralLockedAt() != null || versionRepository.findByTrip_TripIdOrderByVersionNumberAsc(tripId).stream()
                .anyMatch(v -> v.getStatus() == TripScheduleVersionStatus.PUBLISHED);
        if (isLocked) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "TRIP_DURATION_LOCKED",
                    "Không thể thay đổi thời lượng khi Trip đã có phiên bản được phê duyệt xuất bản.");
        }

        if (versionRepository.existsByTrip_TripIdAndStatus(tripId, TripScheduleVersionStatus.IN_REVIEW)) {
            throw new BusinessException(HttpStatus.CONFLICT, "CONCURRENT_REVIEW_IN_PROGRESS",
                    "Không thể thay đổi số ngày khi đang có phiên bản khác chờ duyệt (IN_REVIEW)");
        }

        TripScheduleVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("TripScheduleVersion", versionId));

        if (!version.getTrip().getTripId().equals(tripId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_INPUT",
                    "Phiên bản lịch trình không thuộc về Trip này");
        }

        if (version.getStatus() != TripScheduleVersionStatus.DRAFT) {
            throw new BusinessException(HttpStatus.CONFLICT, "VERSION_FROZEN",
                    "Chỉ có thể quản lý ngày trên phiên bản DRAFT");
        }

        if (request.getExpectedTripRevision() == null || !request.getExpectedTripRevision().equals(trip.getRevision())) {
            throw new BusinessException(HttpStatus.CONFLICT, "STALE_REVISION",
                    "Revision của Trip không khớp (expected: " + request.getExpectedTripRevision() + ", current: " + trip.getRevision() + ")");
        }

        if (request.getExpectedVersionRevision() == null || !request.getExpectedVersionRevision().equals(version.getRevision())) {
            throw new BusinessException(HttpStatus.CONFLICT, "STALE_REVISION",
                    "Revision của phiên bản không khớp (expected: " + request.getExpectedVersionRevision() + ", current: " + version.getRevision() + ")");
        }

        TripDurationGroup group = trip.getDurationGroup() != null ? trip.getDurationGroup() : TripDurationGroup.SHORT;

        if (request.getAction() == TripDayManageAction.ADD) {
            int newDayNumber = trip.getDurationDays() + 1;
            if (newDayNumber > group.getMaxDays()) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "DURATION_LIMIT_EXCEEDED",
                        "Nhóm thời lượng " + group + " chỉ cho phép tối đa " + group.getMaxDays() + " ngày");
            }

            List<TripScheduleVersion> draftVersions = versionRepository.findByTrip_TripIdOrderByVersionNumberAsc(tripId).stream()
                    .filter(v -> v.getStatus() == TripScheduleVersionStatus.DRAFT)
                    .collect(Collectors.toList());

            for (TripScheduleVersion draftVer : draftVersions) {
                TripPlanDay day = new TripPlanDay();
                day.setVersion(draftVer);
                day.setDayNumber(newDayNumber);
                day.setDayType(draftVer.getVersionId().equals(versionId) ? request.getDayType() : null);
                day.setShipTimeZoneId("Asia/Ho_Chi_Minh");
                day.setTitle("Ngày " + newDayNumber);
                draftVer.getDays().add(day);
                draftVer = versionRepository.save(draftVer);
                versionRepository.flush();
            }

            trip.setDurationDays(newDayNumber);
            trip.setDurationNights(Math.max(0, newDayNumber - 1));
            trip = tripRepository.save(trip);
            tripRepository.flush();

            if (auditService != null) {
                auditService.record(actorId, "MANAGE_DAYS_ADD", "Trip", tripId, "SUCCESS",
                        "Added Day " + newDayNumber + " to Trip " + trip.getTripCode() + " (primary version: " + versionId + ")");
                for (TripScheduleVersion draftVer : draftVersions) {
                    auditService.record(actorId, "MANAGE_DAYS_ADD", "TripScheduleVersion", draftVer.getVersionId(), "SUCCESS",
                            "Added Day " + newDayNumber + " via Trip " + trip.getTripCode());
                }
            }
        } else if (request.getAction() == TripDayManageAction.REMOVE) {
            int currentDays = trip.getDurationDays();
            int newDayNumber = currentDays - 1;
            if (newDayNumber < group.getMinDays()) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "DURATION_LIMIT_EXCEEDED",
                        "Nhóm thời lượng " + group + " yêu cầu tối thiểu " + group.getMinDays() + " ngày");
            }

            int targetDay = request.getDayNumber() != null ? request.getDayNumber() : currentDays;
            if (targetDay < 1 || targetDay > currentDays) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_DAY_NUMBER",
                        "Ngày cần xóa không hợp lệ: " + targetDay + ". Chỉ có thể chọn từ 1 đến " + currentDays);
            }

            List<TripPlanEntry> conflictingEntries = version.getEntries().stream()
                    .filter(e -> e.getStartDayNumber() <= targetDay && e.getEndDayNumber() >= targetDay)
                    .collect(Collectors.toList());
            if (!conflictingEntries.isEmpty()) {
                String titles = conflictingEntries.stream().map(TripPlanEntry::getTitle).collect(Collectors.joining(", "));
                throw new BusinessException(HttpStatus.CONFLICT, "DAY_CONTENT_CONFLICT",
                        "Không thể xóa Ngày " + targetDay + " vì đang chứa hoạt động: " + titles + ". Vui lòng dời hoặc xóa các mục này trước.");
            }

            List<TripPlanStop> conflictingStops = version.getStops().stream()
                    .filter(s -> s.getArrivalDayNumber() <= targetDay && s.getDepartureDayNumber() >= targetDay)
                    .collect(Collectors.toList());
            if (!conflictingStops.isEmpty()) {
                String ports = conflictingStops.stream()
                        .map(s -> s.getPort() != null ? s.getPort().getPortName() : s.getClientKey())
                        .collect(Collectors.joining(", "));
                throw new BusinessException(HttpStatus.CONFLICT, "DAY_CONTENT_CONFLICT",
                        "Không thể xóa Ngày " + targetDay + " vì đang có điểm dừng cảng ghé qua: " + ports + ". Vui lòng dời hoặc xóa điểm dừng này trước.");
            }

            List<TripScheduleVersion> draftVersions = versionRepository.findByTrip_TripIdOrderByVersionNumberAsc(tripId).stream()
                    .filter(v -> v.getStatus() == TripScheduleVersionStatus.DRAFT)
                    .collect(Collectors.toList());

            for (TripScheduleVersion draftVer : draftVersions) {
                if (!draftVer.getVersionId().equals(versionId)) {
                    boolean otherHasEntries = draftVer.getEntries().stream()
                            .anyMatch(e -> e.getStartDayNumber() <= targetDay && e.getEndDayNumber() >= targetDay);
                    boolean otherHasStops = draftVer.getStops().stream()
                            .anyMatch(s -> s.getArrivalDayNumber() <= targetDay && s.getDepartureDayNumber() >= targetDay);
                    if (otherHasEntries || otherHasStops) {
                        throw new BusinessException(HttpStatus.CONFLICT, "DAY_CONTENT_CONFLICT",
                                "Không thể xóa Ngày " + targetDay + " vì đang chứa nội dung trong phiên bản nháp " + draftVer.getVersionNumber() + ".");
                    }
                }

                draftVer.getDays().removeIf(d -> d.getDayNumber() == targetDay);
                for (TripPlanDay d : draftVer.getDays()) {
                    if (d.getDayNumber() > targetDay) {
                        d.setDayNumber(d.getDayNumber() - 1);
                        if (d.getTitle() == null || d.getTitle().matches("^Ngày \\d+$")) {
                            d.setTitle("Ngày " + d.getDayNumber());
                        }
                    }
                }

                for (TripPlanStop s : draftVer.getStops()) {
                    if (s.getArrivalDayNumber() > targetDay) {
                        s.setArrivalDayNumber(s.getArrivalDayNumber() - 1);
                    }
                    if (s.getDepartureDayNumber() > targetDay) {
                        s.setDepartureDayNumber(s.getDepartureDayNumber() - 1);
                    }
                    if (s.getAllAboardDayNumber() != null && s.getAllAboardDayNumber() > targetDay) {
                        s.setAllAboardDayNumber(s.getAllAboardDayNumber() - 1);
                    }
                }

                for (TripPlanEntry e : draftVer.getEntries()) {
                    if (e.getStartDayNumber() > targetDay) {
                        e.setStartDayNumber(e.getStartDayNumber() - 1);
                    }
                    if (e.getEndDayNumber() > targetDay) {
                        e.setEndDayNumber(e.getEndDayNumber() - 1);
                    }
                }

                draftVer = versionRepository.save(draftVer);
                versionRepository.flush();
            }

            trip.setDurationDays(newDayNumber);
            trip.setDurationNights(Math.max(0, newDayNumber - 1));
            trip = tripRepository.save(trip);
            tripRepository.flush();

            if (auditService != null) {
                auditService.record(actorId, "MANAGE_DAYS_REMOVE", "Trip", tripId, "SUCCESS",
                        "Removed Day " + targetDay + " from Trip " + trip.getTripCode() + " (primary version: " + versionId + ")");
                for (TripScheduleVersion draftVer : draftVersions) {
                    auditService.record(actorId, "MANAGE_DAYS_REMOVE", "TripScheduleVersion", draftVer.getVersionId(), "SUCCESS",
                            "Removed Day " + targetDay + " via Trip " + trip.getTripCode());
                }
            }
        }

        TripScheduleVersion updatedVersion = versionRepository.findById(versionId).orElse(version);
        return new TripDayManageResponse(mapToDetailResponse(trip), mapToScheduleVersionDetail(updatedVersion));
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
        newTrip.setDurationGroup(sourceTrip.getDurationGroup());
        newTrip.setDurationDays(sourceTrip.getDurationDays());
        newTrip.setDurationNights(sourceTrip.getDurationNights());
        newTrip.setTripType(sourceTrip.getTripType());
        newTrip.setPlannedScope(sourceTrip.getPlannedScope());
        newTrip.setDomesticRegions(sourceTrip.getDomesticRegions());
        newTrip.setCountryCodes(sourceTrip.getCountryCodes());
        newTrip.setShipRequirements(sourceTrip.getShipRequirements());
        newTrip.setCandidateDestinations(sourceTrip.getCandidateDestinations());
        newTrip.setStatus(TripStatus.DRAFT);
        newTrip.setRevision(1L);
        newTrip.setCreatedByUser(actor);

        newTrip = tripRepository.save(newTrip);

        TripScheduleVersion sourceVer = null;
        if (sourceVersionId != null) {
            sourceVer = versionRepository.findById(sourceVersionId).orElse(null);
            if (sourceVer != null && sourceVer.getStatus() == TripScheduleVersionStatus.CANCELLED) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "CANNOT_CLONE_CANCELLED_VERSION",
                        "Không thể nhân bản (clone) từ phiên bản lịch trình đã bị hủy (CANCELLED).");
            }
        }
        if (sourceVer == null) {
            // Prioritize latest PUBLISHED version, then fallback to latest non-cancelled version
            sourceVer = versionRepository.findByTrip_TripIdOrderByVersionNumberAsc(tripId).stream()
                    .filter(v -> v.getStatus() == TripScheduleVersionStatus.PUBLISHED)
                    .reduce((first, second) -> second)
                    .orElseGet(() -> versionRepository.findByTrip_TripIdOrderByVersionNumberAsc(tripId).stream()
                            .filter(v -> v.getStatus() != TripScheduleVersionStatus.CANCELLED)
                            .reduce((first, second) -> second)
                            .orElse(null));
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

        if (trip.getStatus() == TripStatus.ARCHIVED) {
            throw new BusinessException(HttpStatus.CONFLICT, "TRIP_ARCHIVED",
                    "Không thể tạo phiên bản mới trên Trip đã được lưu trữ (ARCHIVED)");
        }

        boolean hasUnfinished = versionRepository.existsByTrip_TripIdAndStatusIn(tripId,
                List.of(TripScheduleVersionStatus.DRAFT, TripScheduleVersionStatus.IN_REVIEW));

        if (hasUnfinished) {
            throw new BusinessException(HttpStatus.CONFLICT, "CONCURRENT_UNFINISHED_VERSION",
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
        return listTrips(status, null, null, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<TripSummaryResponse> listTrips(
            TripStatus status,
            TripDurationGroup durationGroup,
            String plannedScope,
            String search,
            Boolean includeArchived,
            Pageable pageable) {

        Specification<Trip> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            } else if (!Boolean.TRUE.equals(includeArchived)) {
                predicates.add(cb.notEqual(root.get("status"), TripStatus.ARCHIVED));
            }

            if (durationGroup != null) {
                predicates.add(cb.equal(root.get("durationGroup"), durationGroup));
            }

            if (plannedScope != null && !plannedScope.isBlank()) {
                predicates.add(cb.equal(root.get("plannedScope"), plannedScope.trim()));
            }

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("tripCode")), pattern)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Trip> page = tripRepository.findAll(spec, pageable);
        return page.map(this::mapToSummaryResponse);
    }

    @Transactional(readOnly = true)
    public TripDetailResponse getTripDetail(UUID tripId) {
        Trip trip = getTripById(tripId);
        return mapToDetailResponse(trip);
    }

    @Transactional
    public TripDetailResponse archiveTrip(UUID tripId, TripArchiveRequest request, UUID actorId) {
        Trip trip = getTripById(tripId);
        if (trip.getStatus() == TripStatus.ARCHIVED) {
            throw new BusinessException(HttpStatus.CONFLICT, "TRIP_ARCHIVED", "Trip đã ở trạng thái lưu trữ (ARCHIVED)");
        }
        if (request != null && request.getExpectedRevision() != null && !request.getExpectedRevision().equals(trip.getRevision())) {
            throw new BusinessException(HttpStatus.CONFLICT, "STALE_REVISION",
                    "Revision của Trip không khớp (expected: " + request.getExpectedRevision() + ", current: " + trip.getRevision() + ")");
        }
        if (versionRepository.existsByTrip_TripIdAndStatus(tripId, TripScheduleVersionStatus.IN_REVIEW)) {
            throw new BusinessException(HttpStatus.CONFLICT, "CONCURRENT_REVIEW_IN_PROGRESS",
                    "Không thể lưu trữ Trip khi đang có phiên bản chờ duyệt (IN_REVIEW). Vui lòng xử lý hoặc thu hồi phiên bản trước.");
        }
        trip.setStatus(TripStatus.ARCHIVED);
        trip = tripRepository.save(trip);
        tripRepository.flush();
        if (auditService != null) {
            String detail = "Archived Trip " + trip.getTripCode()
                    + (request != null && request.getReason() != null && !request.getReason().isBlank()
                            ? ". Lý do: " + request.getReason().trim() : "");
            auditService.record(actorId, "ARCHIVE_TRIP", "Trip", tripId, "SUCCESS", detail);
        }
        return mapToDetailResponse(trip);
    }

    @Transactional
    public TripDetailResponse archiveTrip(UUID tripId, UUID actorId) {
        return archiveTrip(tripId, null, actorId);
    }

    public Trip getTripById(UUID tripId) {
        return tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", tripId));
    }

    @Autowired(required = false)
    public void setIdempotencyService(IdempotencyService idempotencyService) {
        if (idempotencyService != null) {
            idempotencyService.setEntityRecoveryFunction(this::getTripDetail);
        }
    }

    public TripSummaryResponse mapToSummaryResponse(Trip trip) {
        TripSummaryResponse res = new TripSummaryResponse();
        res.setTripId(trip.getTripId());
        res.setTripCode(trip.getTripCode());
        res.setName(trip.getName());
        res.setDescription(trip.getDescription());
        res.setDurationGroup(trip.getDurationGroup());
        res.setDurationDays(trip.getDurationDays());
        res.setDurationNights(trip.getDurationNights());
        res.setTripType(trip.getTripType());
        res.setPlannedScope(trip.getPlannedScope());
        res.setDomesticRegions(trip.getDomesticRegions());
        res.setCountryCodes(trip.getCountryCodes());
        res.setStatus(trip.getStatus());
        res.setStructuralLockedAt(trip.getStructuralLockedAt());
        res.setRevision(trip.getRevision());
        if (trip.getCreatedByUser() != null) {
            res.setCreatedByUserId(trip.getCreatedByUser().getUserId());
            res.setCreatedByUserName(trip.getCreatedByUser().getUsername());
        }
        res.setCreatedAt(trip.getCreatedAt());
        res.setUpdatedAt(trip.getUpdatedAt());

        List<TripScheduleVersion> versions = (trip.getVersions() != null && !trip.getVersions().isEmpty())
                ? trip.getVersions()
                : versionRepository.findByTrip_TripIdOrderByVersionNumberAsc(trip.getTripId());
        if (!versions.isEmpty()) {
            TripScheduleVersion latest = versions.stream()
                    .filter(v -> v.getStatus() != TripScheduleVersionStatus.CANCELLED)
                    .reduce((first, second) -> second)
                    .orElse(versions.get(versions.size() - 1));
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
        res.setDurationGroup(trip.getDurationGroup());
        res.setDurationDays(trip.getDurationDays());
        res.setDurationNights(trip.getDurationNights());
        res.setTripType(trip.getTripType());
        res.setPlannedScope(trip.getPlannedScope());
        res.setDomesticRegions(trip.getDomesticRegions());
        res.setCountryCodes(trip.getCountryCodes());
        res.setShipRequirements(trip.getShipRequirements());
        res.setCandidateDestinations(trip.getCandidateDestinations());
        res.setStatus(trip.getStatus());
        res.setStructuralLockedAt(trip.getStructuralLockedAt());
        res.setRevision(trip.getRevision());
        if (trip.getCreatedByUser() != null) {
            res.setCreatedByUserId(trip.getCreatedByUser().getUserId());
            res.setCreatedByUserName(trip.getCreatedByUser().getUsername());
        }
        res.setCreatedAt(trip.getCreatedAt());
        res.setUpdatedAt(trip.getUpdatedAt());

        List<TripScheduleVersion> versions = (trip.getVersions() != null && !trip.getVersions().isEmpty())
                ? trip.getVersions()
                : versionRepository.findByTrip_TripIdOrderByVersionNumberAsc(trip.getTripId());
        if (!versions.isEmpty()) {
            TripScheduleVersion latest = versions.stream()
                    .filter(v -> v.getStatus() != TripScheduleVersionStatus.CANCELLED)
                    .reduce((first, second) -> second)
                    .orElse(versions.get(versions.size() - 1));
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
            res.setCreatorName(version.getCreator().getUsername());
        }
        if (version.getSubmitter() != null) {
            res.setSubmitterId(version.getSubmitter().getUserId());
            res.setSubmitterName(version.getSubmitter().getUsername());
        }
        res.setSubmittedRevision(version.getSubmittedRevision());
        res.setSubmittedAt(version.getSubmittedAt());
        if (version.getPublishedBy() != null) {
            res.setPublishedBy(version.getPublishedBy().getUserId());
            res.setPublishedByName(version.getPublishedBy().getUsername());
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
            res.setCreatorName(version.getCreator().getUsername());
        }
        if (version.getSubmitter() != null) {
            res.setSubmitterId(version.getSubmitter().getUserId());
            res.setSubmitterName(version.getSubmitter().getUsername());
        }
        res.setSubmittedRevision(version.getSubmittedRevision());
        res.setSubmittedAt(version.getSubmittedAt());
        if (version.getPublishedBy() != null) {
            res.setPublishedBy(version.getPublishedBy().getUserId());
            res.setPublishedByName(version.getPublishedBy().getUsername());
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
            if (s.getDestination() != null) {
                stopRes.setDestinationId(s.getDestination().getDestinationId());
                stopRes.setDestinationName(s.getDestination().getDestinationName());
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
            entryRes.setSourceActivityId(e.getSourceActivityId());
            entryRes.setSourceExcursionId(e.getSourceExcursionId());
            entryRes.setServiceId(e.getServiceId());
            entryRes.setSeriesClientKey(e.getSeriesClientKey());
            if (e.getDestination() != null) {
                entryRes.setDestinationId(e.getDestination().getDestinationId());
                entryRes.setDestinationName(e.getDestination().getDestinationName());
            }
            entryRes.setTitle(e.getTitle());
            entryRes.setStartDayNumber(e.getStartDayNumber());
            entryRes.setPlannedStartTime(e.getPlannedStartTime());
            entryRes.setEndDayNumber(e.getEndDayNumber());
            entryRes.setPlannedEndTime(e.getPlannedEndTime());
            entryRes.setTimeZoneId(e.getTimeZoneId());
            entryRes.setLocationText(e.getLocationText());

            if (e.getDeck() != null) {
                entryRes.setDeckId(e.getDeck().getDeckId());
                entryRes.setDeckNumber(e.getDeck().getDeckNumber());
                entryRes.setDeckName(e.getDeck().getDeckName());
            }
            if (e.getCabin() != null) {
                entryRes.setCabinId(e.getCabin().getCabinId());
                entryRes.setCabinNumber(e.getCabin().getCabinNumber());
            }

            entryRes.setMeetingPoint(e.getMeetingPoint());
            entryRes.setNotes(e.getNotes());
            return entryRes;
        }).collect(Collectors.toList()));

        return res;
    }

    @Transactional(readOnly = true)
    public List<TripAuditResponse> getTripHistory(UUID tripId) {
        Trip trip = getTripById(tripId);
        List<AuditLog> logs = auditRepository != null
                ? auditRepository.findByEntityIdOrderByOccurredAtDesc(trip.getTripId())
                : List.of();
        return logs.stream().map(this::mapToAuditResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<TripAuditResponse> getTripHistory(UUID tripId, Pageable pageable) {
        Trip trip = getTripById(tripId);
        if (auditRepository == null) {
            return Page.empty(pageable);
        }
        if (pageable == null) {
            pageable = PageRequest.of(0, 20);
        }
        if (pageable.getSort().isUnsorted()) {
            pageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                    Sort.by(Sort.Direction.DESC, "occurredAt"));
        }
        Page<AuditLog> logs = auditRepository.findByEntityIdOrderByOccurredAtDesc(trip.getTripId(), pageable);
        return logs.map(this::mapToAuditResponse);
    }

    private TripAuditResponse mapToAuditResponse(AuditLog log) {
        String actorName = null;
        if (log.getActorUserId() != null && userRepository != null) {
            actorName = userRepository.findById(log.getActorUserId())
                    .map(User::getUsername)
                    .orElse(null);
        }
        return TripAuditResponse.builder()
                .auditId(log.getAuditId())
                .actorUserId(log.getActorUserId())
                .actorName(actorName)
                .action(log.getAction())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .result(log.getResult())
                .detail(log.getDetail())
                .occurredAt(log.getOccurredAt())
                .build();
    }
}
