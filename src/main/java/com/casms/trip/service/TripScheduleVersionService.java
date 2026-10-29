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
import com.casms.activity.enums.OnboardActivityStatus;
import com.casms.activity.enums.ShoreExcursionStatus;
import com.casms.activity.repository.OnboardActivityRepository;
import com.casms.activity.repository.ShoreExcursionRepository;
import com.casms.ship.repository.CabinRepository;
import com.casms.ship.repository.DeckRepository;
import com.casms.trip.dto.request.*;
import com.casms.trip.dto.response.*;
import com.casms.trip.entity.*;
import com.casms.trip.enums.TripPlanEntryKind;
import com.casms.trip.enums.TripScheduleVersionStatus;
import com.casms.trip.enums.TripStatus;
import com.casms.trip.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    @Autowired(required = false)
    @Setter
    private OnboardActivityRepository onboardActivityRepository;

    @Autowired(required = false)
    @Setter
    private ShoreExcursionRepository shoreExcursionRepository;

    @Autowired(required = false)
    @Setter
    private DeckRepository deckRepository;

    @Autowired(required = false)
    @Setter
    private CabinRepository cabinRepository;

    @Transactional(readOnly = true)
    public TripScheduleVersionDetailResponse getVersionDetail(UUID tripId, Integer versionNumber) {
        TripScheduleVersion version = getVersion(tripId, versionNumber);
        return mapToDetailResponse(version);
    }

    @Transactional(readOnly = true)
    public TripScheduleVersionDetailResponse getLatestVersionDetail(UUID tripId) {
        List<TripScheduleVersion> versions = versionRepository.findByTrip_TripIdOrderByVersionNumberAsc(tripId);
        if (versions.isEmpty()) {
            throw new ResourceNotFoundException("TripScheduleVersion", tripId);
        }
        // Prioritize active working version (DRAFT, IN_REVIEW), then PUBLISHED, then fallback to highest version
        TripScheduleVersion version = versions.stream()
                .filter(v -> v.getStatus() == TripScheduleVersionStatus.DRAFT || v.getStatus() == TripScheduleVersionStatus.IN_REVIEW)
                .reduce((first, second) -> second)
                .orElseGet(() -> versions.stream()
                        .filter(v -> v.getStatus() == TripScheduleVersionStatus.PUBLISHED)
                        .reduce((first, second) -> second)
                        .orElse(versions.get(versions.size() - 1)));
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

                // BE-11.2: Validate port country against Trip scope
                if (CountryScopeValidator.isDomestic(trip)) {
                    if (port.getCountryCode() != null && !"VN".equalsIgnoreCase(port.getCountryCode().trim())) {
                        throw new BusinessException(HttpStatus.BAD_REQUEST, "PORT_OUTSIDE_COUNTRY_SCOPE",
                                "Cảng '" + port.getPortName() + "' (mã: " + port.getPortCode() + ", quốc gia: "
                                        + port.getCountryCode() + ") không thuộc phạm vi quốc gia Việt Nam (VN) của chuyến nội địa.");
                    }
                    if (trip.getDomesticRegions() != null && !trip.getDomesticRegions().isEmpty()) {
                        String region = resolveDomesticRegion(port);
                        if (region == null || !trip.getDomesticRegions().contains(region)) {
                            throw new BusinessException(HttpStatus.BAD_REQUEST, "PORT_OUTSIDE_DOMESTIC_REGION",
                                    "Cảng '" + port.getPortName() + "' (miền: " + (region != null ? region : "Chưa xác định")
                                            + ") không thuộc các miền đã đăng ký: " + String.join(", ", trip.getDomesticRegions()));
                        }
                    }
                } else if (CountryScopeValidator.isInternational(trip)) {
                    List<String> registeredCountries = trip.getCountryCodes();
                    if (registeredCountries == null || registeredCountries.isEmpty()) {
                        throw new BusinessException(HttpStatus.BAD_REQUEST, "SCOPE_COUNTRY_REQUIRED",
                                "Chuyến đi quốc tế " + trip.getTripCode() + " chưa được cấu hình danh sách quốc gia (countryCodes).");
                    }
                    String country = port.getCountryCode() != null ? port.getCountryCode().trim().toUpperCase() : "";
                    List<String> upperReg = registeredCountries.stream().map(c -> c.trim().toUpperCase()).toList();
                    if (!upperReg.contains(country)) {
                        throw new BusinessException(HttpStatus.BAD_REQUEST, "PORT_OUTSIDE_COUNTRY_SCOPE",
                                "Cảng '" + port.getPortName() + "' (mã: " + port.getPortCode() + ", quốc gia: "
                                        + country + ") không thuộc danh sách quốc gia đăng ký của Trip: "
                                        + String.join(", ", upperReg));
                    }
                }

                portCache.put(stopDto.getPortId(), port);
            }
        }

        // BE-12: Port Call Timeline, Overlap, and Transit Time Invariants
        for (int i = 0; i < stopDtos.size(); i++) {
            TripPlanStopDraftDto stopA = stopDtos.get(i);
            Port portA = portCache.get(stopA.getPortId());
            long arrMinA = toTimelineMinutes(stopA.getArrivalDayNumber(), stopA.getPlannedArrivalTime(), false);
            long depMinA = toTimelineMinutes(stopA.getDepartureDayNumber(), stopA.getPlannedDepartureTime(), true);

            // Invariant: departure must be strictly after arrival
            if (depMinA <= arrMinA) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_PORT_CALL_TIMES",
                        "Tại điểm dừng '" + stopA.getClientKey() + "' (" + (portA != null ? portA.getPortName() : "")
                                + "), thời điểm rời cảng (Ngày " + stopA.getDepartureDayNumber() + " "
                                + (stopA.getPlannedDepartureTime() != null ? stopA.getPlannedDepartureTime() : "")
                                + ") phải sau thời điểm cập cảng (Ngày " + stopA.getArrivalDayNumber() + " "
                                + (stopA.getPlannedArrivalTime() != null ? stopA.getPlannedArrivalTime() : "") + ").");
            }

            for (int j = i + 1; j < stopDtos.size(); j++) {
                TripPlanStopDraftDto stopB = stopDtos.get(j);
                Port portB = portCache.get(stopB.getPortId());
                long arrMinB = toTimelineMinutes(stopB.getArrivalDayNumber(), stopB.getPlannedArrivalTime(), false);
                long depMinB = toTimelineMinutes(stopB.getDepartureDayNumber(), stopB.getPlannedDepartureTime(), true);

                // Invariant: Overlap check arrA < depB && arrB < depA
                if (arrMinA < depMinB && arrMinB < depMinA) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "PORT_CALL_OVERLAP",
                            "Khoảng thời gian lưu cảng " + (portA != null ? portA.getPortName() : stopA.getClientKey())
                                    + " (Ngày " + stopA.getArrivalDayNumber() + " " + stopA.getPlannedArrivalTime()
                                    + " - Ngày " + stopA.getDepartureDayNumber() + " " + stopA.getPlannedDepartureTime()
                                    + ") chồng lấn với cảng " + (portB != null ? portB.getPortName() : stopB.getClientKey())
                                    + " (Ngày " + stopB.getArrivalDayNumber() + " " + stopB.getPlannedArrivalTime()
                                    + " - Ngày " + stopB.getDepartureDayNumber() + " " + stopB.getPlannedDepartureTime() + ").");
                }

                // Invariant: Non-positive transit time between two distinct ports on boundary
                if (depMinA == arrMinB || depMinB == arrMinA) {
                    if (!stopA.getPortId().equals(stopB.getPortId())) {
                        throw new BusinessException(HttpStatus.BAD_REQUEST, "NON_POSITIVE_TRANSIT_TIME",
                                "Thời gian di chuyển giữa hai cảng khác nhau ("
                                        + (portA != null ? portA.getPortName() : stopA.getClientKey()) + " -> "
                                        + (portB != null ? portB.getPortName() : stopB.getClientKey())
                                        + ") phải lớn hơn 0 (không thể cập cảng mới ngay tại thời điểm rời cảng cũ mà không có thời gian di chuyển).");
                    }
                }

                // Invariant: stopOrder chronological sequence
                if (stopA.getStopOrder() != null && stopB.getStopOrder() != null) {
                    if (stopA.getStopOrder() < stopB.getStopOrder() && depMinA > arrMinB) {
                        throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_STOP_SEQUENCE_TIME",
                                "Thứ tự điểm dừng (" + stopA.getStopOrder() + " -> " + stopB.getStopOrder()
                                        + ") không khớp với trình tự thời gian di chuyển (Cảng "
                                        + (portA != null ? portA.getPortName() : stopA.getClientKey())
                                        + " rời sau khi Cảng " + (portB != null ? portB.getPortName() : stopB.getClientKey()) + " cập).");
                    } else if (stopB.getStopOrder() < stopA.getStopOrder() && depMinB > arrMinA) {
                        throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_STOP_SEQUENCE_TIME",
                                "Thứ tự điểm dừng (" + stopB.getStopOrder() + " -> " + stopA.getStopOrder()
                                        + ") không khớp với trình tự thời gian di chuyển (Cảng "
                                        + (portB != null ? portB.getPortName() : stopB.getClientKey())
                                        + " rời sau khi Cảng " + (portA != null ? portA.getPortName() : stopA.getClientKey()) + " cập).");
                    }
                }
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

                // BE-12.3: Excursion must be within port call window
                TripPlanStop stop = activeStopKeyMap.get(entryDto.getStopClientKey());
                Port stopPort = stop.getPort();
                int excStartDay = entryDto.getStartDayNumber() != null ? entryDto.getStartDayNumber() : (entryDto.getDayNumber() != null ? entryDto.getDayNumber() : 1);
                int excEndDay = entryDto.getEndDayNumber() != null ? entryDto.getEndDayNumber() : excStartDay;
                long excStartMin = toTimelineMinutes(excStartDay, entryDto.getPlannedStartTime(), false);
                long excEndMin = toTimelineMinutes(excEndDay, entryDto.getPlannedEndTime(), true);

                long stopArrMin = toTimelineMinutes(stop.getArrivalDayNumber(), stop.getPlannedArrivalTime(), false);
                int deadlineDay = stop.getAllAboardDayNumber() != null ? stop.getAllAboardDayNumber() : stop.getDepartureDayNumber();
                LocalTime deadlineTime = stop.getAllAboardTime() != null ? stop.getAllAboardTime() : stop.getPlannedDepartureTime();
                long deadlineMin = toTimelineMinutes(deadlineDay, deadlineTime, true);

                if (excStartMin < stopArrMin) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "SHORE_EXCURSION_OUTSIDE_PORT_WINDOW",
                            "Tour tham quan trên bờ '" + entryDto.getTitle() + "' không thể bắt đầu trước khi tàu cập cảng '"
                                    + (stopPort != null ? stopPort.getPortName() : stop.getClientKey()) + "' (Giờ bắt đầu: Ngày " + excStartDay
                                    + " " + entryDto.getPlannedStartTime() + ", Giờ tàu cập: Ngày " + stop.getArrivalDayNumber()
                                    + " " + stop.getPlannedArrivalTime() + ").");
                }
                if (excEndMin > deadlineMin) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "SHORE_EXCURSION_OUTSIDE_PORT_WINDOW",
                            "Tour tham quan trên bờ '" + entryDto.getTitle() + "' phải kết thúc trước hạn lên tàu / rời cảng '"
                                    + (stopPort != null ? stopPort.getPortName() : stop.getClientKey()) + "' (Giờ kết thúc: Ngày " + excEndDay
                                    + " " + entryDto.getPlannedEndTime() + ", Hạn lên tàu: Ngày " + deadlineDay + " " + deadlineTime + ").");
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

        // Synchronize route type (tripType) based on embarkation/disembarkation ports
        syncTripTypeFromStops(version);

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

            Integer primaryDayNumber = entryDto.getStartDayNumber() != null ? entryDto.getStartDayNumber() : entryDto.getDayNumber();
            TripPlanDay day = dayMap.get(primaryDayNumber);
            if (day == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_DAY_REFERENCE", "Không tìm thấy ngày: " + primaryDayNumber);
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

            // Gán Deck và Cabin cho mục hoạt động / dịch vụ
            if (entryDto.getDeckId() != null && deckRepository != null) {
                entry.setDeck(deckRepository.findById(entryDto.getDeckId()).orElse(null));
            } else {
                entry.setDeck(null);
            }

            if (entryDto.getCabinId() != null && cabinRepository != null) {
                var cabin = cabinRepository.findById(entryDto.getCabinId()).orElse(null);
                entry.setCabin(cabin);
                if (entry.getDeck() == null && cabin != null && cabin.getDeck() != null) {
                    entry.setDeck(cabin.getDeck());
                }
            } else {
                entry.setCabin(null);
            }

            // Kế thừa vị trí (Deck/Cabin) và danh mục (Category) từ nguồn hoạt động nếu không chọn thủ công
            if (entry.getSourceActivityId() != null && onboardActivityRepository != null) {
                var srcActOpt = onboardActivityRepository.findById(entry.getSourceActivityId());
                if (srcActOpt.isPresent()) {
                    var srcAct = srcActOpt.get();
                    if (entry.getDeck() == null && entry.getCabin() == null) {
                        if (srcAct.getDeck() != null) {
                            entry.setDeck(srcAct.getDeck());
                        }
                        if (srcAct.getCabin() != null) {
                            entry.setCabin(srcAct.getCabin());
                        }
                    }
                    if (entryDto.getCategory() == null && srcAct.getCategory() != null) {
                        entry.setCategory(srcAct.getCategory());
                    }
                }
            }

            if (entryDto.getCategory() != null) {
                entry.setCategory(entryDto.getCategory());
            }

            entry.setMeetingPoint(entryDto.getMeetingPoint());
            entry.setNotes(entryDto.getNotes());

            updatedEntries.add(entry);
        }

        version.getEntries().clear();
        version.getEntries().addAll(updatedEntries);

        // Calculate content hash
        version.setContentHash(computeContentHash(version));
        version.setUpdatedAt(Instant.now());

        version = versionRepository.save(version);
        versionRepository.flush();
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
        throw new BusinessException(HttpStatus.GONE, "TEMPLATE_REVIEW_RETIRED", "CRM finalizes templates without OPS review");
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
        version.setUpdatedAt(Instant.now());

        version = versionRepository.save(version);
        versionRepository.flush();
        log.info("Returned Trip {} Version {} to DRAFT with reason: {}",
                version.getTrip().getTripCode(), version.getVersionNumber(), request.getRejectionReason());

        if (auditService != null) {
            auditService.record(actorId, "RETURN", "TripScheduleVersion", version.getVersionId(),
                    "SUCCESS", "Returned to draft: " + request.getRejectionReason());
        }

        return mapToDetailResponse(version);
    }

    @Transactional
    public TripScheduleVersionDetailResponse cancelDraft(UUID tripId, Integer versionNumber, VersionCancelRequest request, UUID actorId) {
        TripScheduleVersion version = getVersion(tripId, versionNumber);
        return executeCancelDraft(version, request, actorId);
    }

    @Transactional
    public TripScheduleVersionDetailResponse cancelDraftById(UUID versionId, VersionCancelRequest request, UUID actorId) {
        TripScheduleVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("TripScheduleVersion", versionId));
        return executeCancelDraft(version, request, actorId);
    }

    private TripScheduleVersionDetailResponse executeCancelDraft(TripScheduleVersion version, VersionCancelRequest request, UUID actorId) {
        if (version.getTrip().getStatus() == TripStatus.ARCHIVED) {
            throw new BusinessException(HttpStatus.CONFLICT, "TRIP_ARCHIVED",
                    "Không thể hủy phiên bản của Trip đã được lưu trữ (ARCHIVED)");
        }

        if (version.getStatus() != TripScheduleVersionStatus.DRAFT) {
            throw new BusinessException(HttpStatus.CONFLICT, "INVALID_STATUS",
                    "Chỉ có thể hủy phiên bản ở trạng thái DRAFT.");
        }

        if (request != null && request.getExpectedRevision() != null && !request.getExpectedRevision().equals(version.getRevision())) {
            throw new BusinessException(HttpStatus.CONFLICT, "STALE_REVISION",
                    "Revision của phiên bản không khớp (expected: " + request.getExpectedRevision() + ", current: " + version.getRevision() + ")");
        }

        version.setStatus(TripScheduleVersionStatus.CANCELLED);
        if (request != null && request.getReason() != null && !request.getReason().isBlank()) {
            version.setRejectionReason(request.getReason().trim());
        }

        version = versionRepository.save(version);
        versionRepository.flush();
        log.info("Cancelled draft Trip {} Version {} by user {}",
                version.getTrip().getTripCode(), version.getVersionNumber(), actorId);

        if (auditService != null) {
            String detail = request != null && request.getReason() != null ? request.getReason() : "Cancelled draft version";
            auditService.record(actorId, "CANCEL_DRAFT", "TripScheduleVersion", version.getVersionId(),
                    "SUCCESS", detail);
        }

        return mapToDetailResponse(version);
    }

    @Transactional
    public TripScheduleVersionDetailResponse withdraw(UUID tripId, Integer versionNumber, VersionWithdrawRequest request, UUID actorId) {
        TripScheduleVersion version = getVersion(tripId, versionNumber);
        return executeWithdraw(version, request, actorId);
    }

    @Transactional
    public TripScheduleVersionDetailResponse withdrawById(UUID versionId, VersionWithdrawRequest request, UUID actorId) {
        TripScheduleVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("TripScheduleVersion", versionId));
        return executeWithdraw(version, request, actorId);
    }

    private TripScheduleVersionDetailResponse executeWithdraw(TripScheduleVersion version, VersionWithdrawRequest request, UUID actorId) {
        if (version.getTrip().getStatus() == TripStatus.ARCHIVED) {
            throw new BusinessException(HttpStatus.CONFLICT, "TRIP_ARCHIVED",
                    "Không thể thu hồi phiên bản của Trip đã được lưu trữ (ARCHIVED)");
        }

        if (version.getStatus() != TripScheduleVersionStatus.IN_REVIEW) {
            throw new BusinessException(HttpStatus.CONFLICT, "INVALID_STATUS",
                    "Chỉ có thể thu hồi phiên bản đang ở trạng thái IN_REVIEW.");
        }

        if (request != null && request.getExpectedRevision() != null && !request.getExpectedRevision().equals(version.getRevision())) {
            throw new BusinessException(HttpStatus.CONFLICT, "STALE_REVISION",
                    "Revision của phiên bản không khớp (expected: " + request.getExpectedRevision() + ", current: " + version.getRevision() + ")");
        }

        version.setStatus(TripScheduleVersionStatus.DRAFT);
        version.setSubmittedRevision(null);
        version.setSubmittedAt(null);
        version.setUpdatedAt(Instant.now());

        version = versionRepository.save(version);
        versionRepository.flush();
        log.info("Withdrawn Trip {} Version {} from IN_REVIEW to DRAFT by actor {}",
                version.getTrip().getTripCode(), version.getVersionNumber(), actorId);

        if (auditService != null) {
            String detail = "Withdrawn from review" + (request != null && request.getReason() != null && !request.getReason().isBlank()
                    ? ": " + request.getReason().trim() : "");
            auditService.record(actorId, "WITHDRAW", "TripScheduleVersion", version.getVersionId(),
                    "SUCCESS", detail);
        }

        return mapToDetailResponse(version);
    }

    @Deprecated
    public TripScheduleVersionDetailResponse publishVersion(UUID tripId, Integer versionNumber, UUID actorId) {
        throw new BusinessException(HttpStatus.GONE, "TEMPLATE_REVIEW_RETIRED", "Use CRM template finalization");
    }

    @Deprecated
    public TripScheduleVersionDetailResponse publishVersionById(UUID versionId, Long expectedRevision, UUID actorId) {
        throw new BusinessException(HttpStatus.GONE, "TEMPLATE_REVIEW_RETIRED", "Use CRM template finalization");
    }

    /** CRM finalizes its own template. This is never OPS approval of a Cruise Plan. */
    @Transactional
    public TripScheduleVersionDetailResponse finalizeVersion(UUID versionId, Long expectedRevision, UUID actorId) {
        TripScheduleVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("TripScheduleVersion", versionId));
        if (actorId == null) throw new BusinessException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Authentication required");
        if (version.getTrip().getStatus() == TripStatus.ARCHIVED)
            throw new BusinessException(HttpStatus.CONFLICT, "TRIP_ARCHIVED", "Archived template cannot be finalized");
        if (expectedRevision == null || !expectedRevision.equals(version.getRevision()))
            throw new BusinessException(HttpStatus.CONFLICT, "STALE_REVISION", "Expected revision is required and must match");
        if (version.getStatus() != TripScheduleVersionStatus.DRAFT)
            throw new BusinessException(HttpStatus.CONFLICT, "VERSION_FROZEN", "Only draft templates can be finalized; withdraw legacy review first");
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

        version.setContentHash(computeContentHash(version));
        version.setStatus(TripScheduleVersionStatus.PUBLISHED);
        version.setPublishedBy(approver);
        version.setPublishedAt(Instant.now());

        // Trip transitions to READY and durations are permanently locked
        version.getTrip().setStatus(TripStatus.READY);
        version.getTrip().setStructuralLockedAt(Instant.now());

        tripRepository.save(version.getTrip());
        tripRepository.flush();
        version = versionRepository.save(version);
        versionRepository.flush();
        log.info("Published Trip {} Version {} successfully by approver {}",
                version.getTrip().getTripCode(), version.getVersionNumber(), actorId);

        if (auditService != null) {
            auditService.record(actorId, "FINALIZE", "TripScheduleVersion", version.getVersionId(),
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

        return logs.stream().map(this::mapToAuditResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<TripAuditResponse> getVersionHistory(UUID versionId, Pageable pageable) {
        TripScheduleVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new ResourceNotFoundException("TripScheduleVersion", versionId));

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

        Page<AuditLog> logs = auditRepository.findByEntityIdOrderByOccurredAtDesc(version.getVersionId(), pageable);
        return logs.map(this::mapToAuditResponse);
    }

    @Transactional(readOnly = true)
    public Page<TripReviewQueueItemResponse> getReviewQueue(Pageable pageable) {
        if (pageable == null) {
            pageable = PageRequest.of(0, 20);
        }
        if (pageable.getSort().isUnsorted()) {
            pageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                    Sort.by(Sort.Order.desc("submittedAt").nullsLast(), Sort.Order.asc("versionId")));
        }

        Page<TripScheduleVersion> versions = versionRepository.findReviewQueue(
                TripScheduleVersionStatus.IN_REVIEW, TripStatus.ARCHIVED, pageable);

        return versions.map(v -> TripReviewQueueItemResponse.builder()
                .versionId(v.getVersionId())
                .versionNumber(v.getVersionNumber())
                .status(v.getStatus())
                .revision(v.getRevision())
                .submittedRevision(v.getSubmittedRevision())
                .submittedAt(v.getSubmittedAt())
                .submitterId(v.getSubmitter() != null ? v.getSubmitter().getUserId() : null)
                .submitterName(v.getSubmitter() != null ? v.getSubmitter().getUsername() : null)
                .creatorId(v.getCreator() != null ? v.getCreator().getUserId() : null)
                .creatorName(v.getCreator() != null ? v.getCreator().getUsername() : null)
                .tripId(v.getTrip().getTripId())
                .tripCode(v.getTrip().getTripCode())
                .tripName(v.getTrip().getName())
                .durationGroup(v.getTrip().getDurationGroup())
                .durationDays(v.getTrip().getDurationDays())
                .durationNights(v.getTrip().getDurationNights())
                .tripType(v.getTrip().getTripType())
                .plannedScope(v.getTrip().getPlannedScope())
                .createdAt(v.getCreatedAt())
                .updatedAt(v.getUpdatedAt())
                .build());
    }

    private TripAuditResponse mapToAuditResponse(AuditLog log) {
        String actorName = null;
        if (log.getActorUserId() != null) {
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

        int maxVersionNumber = versionRepository.findByTrip_TripIdOrderByVersionNumberAsc(tripId).stream()
                .mapToInt(TripScheduleVersion::getVersionNumber)
                .max().orElse(latestPublished.getVersionNumber());

        TripScheduleVersion newVersion = new TripScheduleVersion();
        newVersion.setTrip(trip);
        newVersion.setVersionNumber(maxVersionNumber + 1);
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
            newEntry.setCategory(oldEntry.getCategory());
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
            newEntry.setDeck(oldEntry.getDeck());
            newEntry.setCabin(oldEntry.getCabin());
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

    /**
     * Đồng bộ kiểu tuyến (tripType: ROUND_TRIP / ONE_WAY) dựa trên cảng đón và cảng trả thực tế.
     * Tự động cập nhật kiểu tuyến khi đổi cảng đón/trả để người dùng không bị mắc kẹt lỗi TRIP_TYPE_MISMATCH.
     */
    public void syncTripTypeFromStops(TripScheduleVersion version) {
        if (version == null || version.getTrip() == null || version.getStops() == null) {
            return;
        }
        Trip trip = version.getTrip();
        TripPlanStop embarkStop = version.getStops().stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsEmbarkation()))
                .findFirst().orElse(null);
        TripPlanStop disembarkStop = version.getStops().stream()
                .filter(s -> Boolean.TRUE.equals(s.getIsDisembarkation()))
                .findFirst().orElse(null);

        if (embarkStop != null && disembarkStop != null && embarkStop.getPort() != null && disembarkStop.getPort() != null) {
            boolean samePort = Objects.equals(embarkStop.getPort().getPortId(), disembarkStop.getPort().getPortId());
            ItineraryType inferredType = samePort ? ItineraryType.ROUND_TRIP : ItineraryType.ONE_WAY;
            if (trip.getTripType() != inferredType) {
                trip.setTripType(inferredType);
                if (tripRepository != null && trip.getTripId() != null) {
                    tripRepository.save(trip);
                }
            }
        }
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

            // Time logic for port call (both same day and cross day)
            long stopArrMin = toTimelineMinutes(stop.getArrivalDayNumber(), stop.getPlannedArrivalTime(), false);
            long stopDepMin = toTimelineMinutes(stop.getDepartureDayNumber(), stop.getPlannedDepartureTime(), true);
            if (stopDepMin <= stopArrMin) {
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
                                + "), thời điểm rời cảng (" + stop.getPlannedDepartureTime() + ") phải sau thời điểm cập cảng (" + stop.getPlannedArrivalTime() + ")")
                        .build());
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

        // BE-12: Overlap check between all pairs of stops
        for (int i = 0; i < stops.size(); i++) {
            TripPlanStop stopA = stops.get(i);
            long arrA = toTimelineMinutes(stopA.getArrivalDayNumber(), stopA.getPlannedArrivalTime(), false);
            long depA = toTimelineMinutes(stopA.getDepartureDayNumber(), stopA.getPlannedDepartureTime(), true);

            for (int j = i + 1; j < stops.size(); j++) {
                TripPlanStop stopB = stops.get(j);
                long arrB = toTimelineMinutes(stopB.getArrivalDayNumber(), stopB.getPlannedArrivalTime(), false);
                long depB = toTimelineMinutes(stopB.getDepartureDayNumber(), stopB.getPlannedDepartureTime(), true);

                if (arrA < depB && arrB < depA) {
                    violations.add(PlanViolation.builder()
                            .code("PORT_CALL_OVERLAP")
                            .severity("ERROR")
                            .entityType("STOP")
                            .clientKey(stopA.getClientKey())
                            .dayNumber(stopA.getArrivalDayNumber())
                            .field("plannedArrivalTime")
                            .fieldPath("stops[clientKey=" + stopA.getClientKey() + "].plannedArrivalTime")
                            .actionHint("ADJUST_PORT_CALL_SCHEDULE")
                            .message("Khoảng thời gian lưu cảng '" + (stopA.getPort() != null ? stopA.getPort().getPortName() : stopA.getClientKey())
                                    + "' bị chồng lấn với cảng '" + (stopB.getPort() != null ? stopB.getPort().getPortName() : stopB.getClientKey()) + "'.")
                            .build());
                }

                if (depA == arrB || depB == arrA) {
                    if (stopA.getPort() != null && stopB.getPort() != null
                            && !Objects.equals(stopA.getPort().getPortId(), stopB.getPort().getPortId())) {
                        violations.add(PlanViolation.builder()
                                .code("NON_POSITIVE_TRANSIT_TIME")
                                .severity("ERROR")
                                .entityType("STOP")
                                .clientKey(stopA.getClientKey())
                                .dayNumber(stopA.getDepartureDayNumber())
                                .field("plannedDepartureTime")
                                .fieldPath("stops[clientKey=" + stopA.getClientKey() + "].plannedDepartureTime")
                                .actionHint("ADD_TRANSIT_BUFFER")
                                .message("Thời gian di chuyển giữa hai cảng khác nhau (" + stopA.getPort().getPortName()
                                        + " -> " + stopB.getPort().getPortName() + ") phải lớn hơn 0.")
                                .build());
                    }
                }

                if (stopA.getStopOrder() != null && stopB.getStopOrder() != null) {
                    if (stopA.getStopOrder() < stopB.getStopOrder() && depA > arrB) {
                        violations.add(PlanViolation.builder()
                                .code("INVALID_STOP_SEQUENCE_TIME")
                                .severity("ERROR")
                                .entityType("STOP")
                                .clientKey(stopA.getClientKey())
                                .dayNumber(stopA.getDepartureDayNumber())
                                .field("stopOrder")
                                .fieldPath("stops[clientKey=" + stopA.getClientKey() + "].stopOrder")
                                .actionHint("REORDER_STOPS_OR_ADJUST_TIMES")
                                .message("Thứ tự điểm dừng (" + stopA.getStopOrder() + " -> " + stopB.getStopOrder()
                                        + ") không khớp với trình tự thời gian di chuyển.")
                                .build());
                    } else if (stopB.getStopOrder() < stopA.getStopOrder() && depB > arrA) {
                        violations.add(PlanViolation.builder()
                                .code("INVALID_STOP_SEQUENCE_TIME")
                                .severity("ERROR")
                                .entityType("STOP")
                                .clientKey(stopB.getClientKey())
                                .dayNumber(stopB.getDepartureDayNumber())
                                .field("stopOrder")
                                .fieldPath("stops[clientKey=" + stopB.getClientKey() + "].stopOrder")
                                .actionHint("REORDER_STOPS_OR_ADJUST_TIMES")
                                .message("Thứ tự điểm dừng (" + stopB.getStopOrder() + " -> " + stopA.getStopOrder()
                                        + ") không khớp với trình tự thời gian di chuyển.")
                                .build());
                    }
                }
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
            ItineraryType expectedType = samePort ? ItineraryType.ROUND_TRIP : ItineraryType.ONE_WAY;
            if (trip.getTripType() != null && trip.getTripType() != expectedType) {
                violations.add(PlanViolation.builder()
                        .code("TRIP_TYPE_MISMATCH")
                        .severity("ERROR")
                        .entityType("TRIP")
                        .field("tripType")
                        .fieldPath("trip.tripType")
                        .actionHint("MATCH_PORTS_OR_CHANGE_TYPE")
                        .message("Tuyến được lưu là " + trip.getTripType() + " nhưng cảng đón ("
                                + embarkStop.getPort().getPortCode() + ") và cảng trả ("
                                + disembarkStop.getPort().getPortCode() + ") tương ứng với kiểu tuyến " + expectedType + ".")
                        .build());
            }
        }

        // Validate Planned Scope (BE-06B): Chuyến nội địa không được ghé cảng nước ngoài và phải thuộc miền đã chọn
        if (CountryScopeValidator.isDomestic(trip)) {
            List<String> registeredRegions = trip.getDomesticRegions();
            for (TripPlanStop stop : stops) {
                if (stop.getPort() != null) {
                    if (stop.getPort().getCountryCode() != null && !"VN".equalsIgnoreCase(stop.getPort().getCountryCode().trim())) {
                        violations.add(PlanViolation.builder()
                                .code("PORT_OUTSIDE_PLANNED_SCOPE")
                                .severity("ERROR")
                                .entityType("STOP")
                                .clientKey(stop.getClientKey())
                                .dayNumber(stop.getArrivalDayNumber())
                                .field("port")
                                .fieldPath("stops[clientKey=" + stop.getClientKey() + "].port")
                                .actionHint("REMOVE_OR_CHANGE_PORT")
                                .message("Cảng '" + stop.getPort().getPortName() + "' (quốc gia: "
                                        + stop.getPort().getCountryCode() + ") không thuộc phạm vi chuyến nội địa (DOMESTIC).")
                                .build());
                    } else if (registeredRegions != null && !registeredRegions.isEmpty()) {
                        String region = resolveDomesticRegion(stop.getPort());
                        if (region == null || !registeredRegions.contains(region)) {
                            violations.add(PlanViolation.builder()
                                    .code("PORT_OUTSIDE_DOMESTIC_REGION")
                                    .severity("ERROR")
                                    .entityType("STOP")
                                    .clientKey(stop.getClientKey())
                                    .dayNumber(stop.getArrivalDayNumber())
                                    .field("port")
                                    .fieldPath("stops[clientKey=" + stop.getClientKey() + "].port")
                                    .actionHint("REMOVE_OR_CHANGE_PORT")
                                    .message("Cảng '" + stop.getPort().getPortName() + "' (miền: "
                                            + (region != null ? region : "Chưa xác định") + ") không thuộc các miền đã đăng ký cho Trip: "
                                            + String.join(", ", registeredRegions))
                                    .build());
                        }
                    }
                }
            }
        } else if (CountryScopeValidator.isInternational(trip)) {
            List<String> registeredCountries = trip.getCountryCodes();
            if (registeredCountries != null && !registeredCountries.isEmpty()) {
                for (TripPlanStop stop : stops) {
                    if (stop.getPort() != null && stop.getPort().getCountryCode() != null) {
                        String country = stop.getPort().getCountryCode().trim().toUpperCase();
                        if (!registeredCountries.contains(country)) {
                            violations.add(PlanViolation.builder()
                                    .code("PORT_OUTSIDE_COUNTRY_SCOPE")
                                    .severity("ERROR")
                                    .entityType("STOP")
                                    .clientKey(stop.getClientKey())
                                    .dayNumber(stop.getArrivalDayNumber())
                                    .field("port")
                                    .fieldPath("stops[clientKey=" + stop.getClientKey() + "].port")
                                    .actionHint("REMOVE_OR_CHANGE_PORT")
                                    .message("Cảng '" + stop.getPort().getPortName() + "' (quốc gia: "
                                            + country + ") không thuộc danh sách quốc gia đăng ký: "
                                            + String.join(", ", registeredCountries))
                                    .build());
                        }
                    }
                }
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

                    if (entry.getStartDayNumber().equals(entry.getEndDayNumber())
                            && entry.getPlannedStartTime() != null && entry.getPlannedEndTime() != null
                            && !entry.getPlannedEndTime().isAfter(entry.getPlannedStartTime())) {
                        violations.add(PlanViolation.builder()
                                .code("INVALID_EXCURSION_TIME")
                                .severity("ERROR")
                                .entityType("ENTRY")
                                .clientKey(entry.getClientKey())
                                .dayNumber(entry.getStartDayNumber())
                                .field("plannedEndTime")
                                .fieldPath("entries[clientKey=" + entry.getClientKey() + "].plannedEndTime")
                                .actionHint("EDIT_FIELD")
                                .message("Hoạt động tham quan trên bờ '" + entry.getTitle() + "' có thời gian kết thúc không sau thời gian bắt đầu.")
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

            // BE-13: Quy tắc điểm tập trung (meetingPoint):
            // - Khung phục vụ (SERVING_WINDOW): KHÔNG bắt buộc meetingPoint.
            // - Tham quan trên bờ (SHORE_EXCURSION): BẮT BUỘC meetingPoint.
            // - Hoạt động trên tàu (ONBOARD_ACTIVITY): Cần meetingPoint hoặc locationText.
            if (entry.getKind() == TripPlanEntryKind.SHORE_EXCURSION) {
                if (entry.getMeetingPoint() == null || entry.getMeetingPoint().trim().isEmpty()) {
                    violations.add(PlanViolation.builder()
                            .code("MEETING_POINT_REQUIRED")
                            .severity("ERROR")
                            .entityType("ENTRY")
                            .clientKey(entry.getClientKey())
                            .dayNumber(entry.getStartDayNumber())
                            .field("meetingPoint")
                            .fieldPath("entries[clientKey=" + entry.getClientKey() + "].meetingPoint")
                            .actionHint("SET_MEETING_POINT")
                            .message("Hoạt động tham quan trên bờ '" + entry.getTitle() + "' bắt buộc phải có điểm tập trung (meetingPoint).")
                            .build());
                }
            } else if (entry.getKind() == TripPlanEntryKind.ONBOARD_ACTIVITY) {
                boolean hasMeeting = entry.getMeetingPoint() != null && !entry.getMeetingPoint().trim().isEmpty();
                boolean hasLocation = entry.getLocationText() != null && !entry.getLocationText().trim().isEmpty();
                if (!hasMeeting && !hasLocation) {
                    violations.add(PlanViolation.builder()
                            .code("MEETING_POINT_REQUIRED")
                            .severity("ERROR")
                            .entityType("ENTRY")
                            .clientKey(entry.getClientKey())
                            .dayNumber(entry.getStartDayNumber())
                            .field("meetingPoint")
                            .fieldPath("entries[clientKey=" + entry.getClientKey() + "].meetingPoint")
                            .actionHint("SET_MEETING_POINT")
                            .message("Hoạt động trên tàu '" + entry.getTitle() + "' cần có địa điểm tổ chức (location) hoặc điểm tập trung (meetingPoint).")
                            .build());
                }
            }

            // BE-07: Kiểm tra tồn tại và tính hợp lệ của hoạt động / tour nguồn (nếu có tham chiếu)
            if (entry.getSourceActivityId() != null && onboardActivityRepository != null) {
                var actOpt = onboardActivityRepository.findById(entry.getSourceActivityId());
                if (actOpt.isEmpty()) {
                    violations.add(PlanViolation.builder()
                            .code("SOURCE_ACTIVITY_NOT_FOUND")
                            .severity("ERROR")
                            .entityType("ENTRY")
                            .clientKey(entry.getClientKey())
                            .dayNumber(entry.getStartDayNumber())
                            .field("sourceActivityId")
                            .fieldPath("entries[clientKey=" + entry.getClientKey() + "].sourceActivityId")
                            .actionHint("SELECT_VALID_SOURCE")
                            .message("Hoạt động nguồn (sourceActivityId: " + entry.getSourceActivityId() + ") không tồn tại trong danh mục.")
                            .build());
                } else if (actOpt.get().getStatus() == OnboardActivityStatus.CANCELLED) {
                    violations.add(PlanViolation.builder()
                            .code("SOURCE_ACTIVITY_INACTIVE")
                            .severity("ERROR")
                            .entityType("ENTRY")
                            .clientKey(entry.getClientKey())
                            .dayNumber(entry.getStartDayNumber())
                            .field("sourceActivityId")
                            .fieldPath("entries[clientKey=" + entry.getClientKey() + "].sourceActivityId")
                            .actionHint("SELECT_VALID_SOURCE")
                            .message("Hoạt động nguồn '" + actOpt.get().getActivityName() + "' đã bị hủy / ngừng hoạt động.")
                            .build());
                }
            }

            if (entry.getSourceExcursionId() != null && shoreExcursionRepository != null) {
                var excOpt = shoreExcursionRepository.findById(entry.getSourceExcursionId());
                if (excOpt.isEmpty()) {
                    violations.add(PlanViolation.builder()
                            .code("SOURCE_EXCURSION_NOT_FOUND")
                            .severity("ERROR")
                            .entityType("ENTRY")
                            .clientKey(entry.getClientKey())
                            .dayNumber(entry.getStartDayNumber())
                            .field("sourceExcursionId")
                            .fieldPath("entries[clientKey=" + entry.getClientKey() + "].sourceExcursionId")
                            .actionHint("SELECT_VALID_SOURCE")
                            .message("Tour tham quan nguồn (sourceExcursionId: " + entry.getSourceExcursionId() + ") không tồn tại trong danh mục.")
                            .build());
                } else {
                    var exc = excOpt.get();
                    if (exc.getStatus() == ShoreExcursionStatus.CANCELLED) {
                        violations.add(PlanViolation.builder()
                                .code("SOURCE_EXCURSION_INACTIVE")
                                .severity("ERROR")
                                .entityType("ENTRY")
                                .clientKey(entry.getClientKey())
                                .dayNumber(entry.getStartDayNumber())
                                .field("sourceExcursionId")
                                .fieldPath("entries[clientKey=" + entry.getClientKey() + "].sourceExcursionId")
                                .actionHint("SELECT_VALID_SOURCE")
                                .message("Tour tham quan nguồn '" + exc.getExcursionName() + "' đã bị hủy / ngừng hoạt động.")
                                .build());
                    }

                    // Kiểm tra tour tham quan đúng cảng
                    UUID excursionPortId = exc.getPort() != null ? exc.getPort().getPortId()
                            : (exc.getCruiseStop() != null && exc.getCruiseStop().getPort() != null
                            ? exc.getCruiseStop().getPort().getPortId() : null);
                    if (excursionPortId != null && entry.getStop() != null && entry.getStop().getPort() != null) {
                        UUID stopPortId = entry.getStop().getPort().getPortId();
                        if (!excursionPortId.equals(stopPortId)) {
                            String excursionPortName = exc.getPort() != null ? exc.getPort().getPortName()
                                    : (exc.getCruiseStop() != null && exc.getCruiseStop().getPort() != null
                                    ? exc.getCruiseStop().getPort().getPortName() : "Khác");
                            violations.add(PlanViolation.builder()
                                    .code("SHORE_EXCURSION_PORT_MISMATCH")
                                    .severity("ERROR")
                                    .entityType("ENTRY")
                                    .clientKey(entry.getClientKey())
                                    .dayNumber(entry.getStartDayNumber())
                                    .field("sourceExcursionId")
                                    .fieldPath("entries[clientKey=" + entry.getClientKey() + "].sourceExcursionId")
                                    .actionHint("SELECT_VALID_SOURCE")
                                    .message("Tour tham quan nguồn thuộc cảng '" + excursionPortName
                                            + "' nhưng được gán vào điểm dừng tại cảng '"
                                            + entry.getStop().getPort().getPortName() + "'.")
                                    .build());
                        }
                    }
                }
            }
        }

        return violations;
    }

    public static String resolveDomesticRegion(Port port) {
        return VietnamGeographyRegistry.resolveDomesticRegion(port);
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
            res.setDomesticRegion(resolveDomesticRegion(stop.getPort()));
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
        if (entry.getSourceActivityId() != null && onboardActivityRepository != null) {
            onboardActivityRepository.findById(entry.getSourceActivityId())
                    .ifPresent(act -> res.setSourceActivityTitle(act.getActivityName()));
        }

        res.setSourceExcursionId(entry.getSourceExcursionId());
        if (entry.getSourceExcursionId() != null && shoreExcursionRepository != null) {
            shoreExcursionRepository.findById(entry.getSourceExcursionId())
                    .ifPresent(exc -> {
                        res.setSourceExcursionTitle(exc.getExcursionName());
                        if (exc.getSupplier() != null) {
                            res.setSupplierId(exc.getSupplier().getSupplierId());
                            res.setSupplierName(exc.getSupplier().getSupplierName());
                        }
                    });
        }

        res.setServiceId(entry.getServiceId());
        res.setSeriesClientKey(entry.getSeriesClientKey());
        if (entry.getDestination() != null) {
            res.setDestinationId(entry.getDestination().getDestinationId());
            res.setDestinationName(entry.getDestination().getDestinationName());
        } else if (entry.getStop() != null && entry.getStop().getPort() != null && entry.getStop().getPort().getDestination() != null) {
            res.setDestinationId(entry.getStop().getPort().getDestination().getDestinationId());
            res.setDestinationName(entry.getStop().getPort().getDestination().getDestinationName());
        }
        res.setTitle(entry.getTitle());
        res.setStartDayNumber(entry.getStartDayNumber());
        res.setPlannedStartTime(entry.getPlannedStartTime());
        res.setEndDayNumber(entry.getEndDayNumber());
        res.setPlannedEndTime(entry.getPlannedEndTime());
        res.setTimeZoneId(entry.getTimeZoneId());
        res.setLocationText(entry.getLocationText());

        if (entry.getCategory() != null) {
            res.setCategory(entry.getCategory());
            res.setCategoryName(entry.getCategory().getDisplayName());
            res.setCategoryIcon(entry.getCategory().getIcon());
        }

        if (entry.getDeck() != null) {
            res.setDeckId(entry.getDeck().getDeckId());
            res.setDeckNumber(entry.getDeck().getDeckNumber());
            res.setDeckName(entry.getDeck().getDeckName());
            if (entry.getDeck().getShip() != null) {
                res.setReferenceShipId(entry.getDeck().getShip().getShipId());
                res.setReferenceShipName(entry.getDeck().getShip().getShipName());
            }
        }
        if (entry.getCabin() != null) {
            res.setCabinId(entry.getCabin().getCabinId());
            res.setCabinNumber(entry.getCabin().getCabinNumber());
            res.setRoomName(entry.getCabin().getRoomName());
            if (res.getReferenceShipId() == null && entry.getCabin().getShip() != null) {
                res.setReferenceShipId(entry.getCabin().getShip().getShipId());
                res.setReferenceShipName(entry.getCabin().getShip().getShipName());
            }
            res.setLocationType("FUNCTIONAL_ROOM");
            String disp = (entry.getDeck() != null ? "Tầng " + entry.getDeck().getDeckNumber() + " (" + entry.getDeck().getDeckName() + ") - " : "")
                    + "Phòng " + entry.getCabin().getCabinNumber()
                    + (entry.getCabin().getRoomName() != null ? " (" + entry.getCabin().getRoomName() + ")" : "");
            res.setDisplayLocation(disp);
        } else if (entry.getDeck() != null) {
            res.setLocationType("VENUE_OPEN_AREA");
            String disp = "Tầng " + entry.getDeck().getDeckNumber() + " (" + entry.getDeck().getDeckName() + ")"
                    + (entry.getLocationText() != null ? " - " + entry.getLocationText() : "");
            res.setDisplayLocation(disp);
        } else if (entry.getLocationText() != null) {
            res.setLocationType("GENERAL");
            res.setDisplayLocation(entry.getLocationText());
        }

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

    public static long toTimelineMinutes(int dayNumber, LocalTime time, boolean isEnd) {
        long base = (dayNumber - 1) * 1440L;
        if (time != null) {
            return base + (time.toSecondOfDay() / 60);
        }
        return isEnd ? base + 1440L : base;
    }

    @Transactional(readOnly = true)
    public Page<EligibleTripSourceResponse> getEligibleSources(UUID tripId, String keyword, Pageable pageable) {
        String kw = (keyword != null && !keyword.isBlank()) ? "%" + keyword.trim().toLowerCase() + "%" : "";
        Page<TripScheduleVersion> page = versionRepository.findEligibleSourceVersions(tripId, kw, pageable);
        return page.map(this::mapToEligibleSourceResponse);
    }

    private EligibleTripSourceResponse mapToEligibleSourceResponse(TripScheduleVersion v) {
        Trip trip = v.getTrip();
        List<TripPlanStop> stops = v.getStops() != null ? new ArrayList<>(v.getStops()) : new ArrayList<>();
        stops.sort(Comparator.comparing(TripPlanStop::getStopOrder, Comparator.nullsLast(Comparator.naturalOrder())));

        TripPlanStop firstStop = stops.isEmpty() ? null : stops.get(0);
        TripPlanStop lastStop = stops.isEmpty() ? null : stops.get(stops.size() - 1);

        List<String> stopSummaries = stops.stream()
                .filter(s -> s.getPort() != null)
                .map(s -> String.format("%s (%s)", s.getPort().getPortName(), s.getPort().getPortCode()))
                .toList();

        return EligibleTripSourceResponse.builder()
                .versionId(v.getVersionId())
                .versionNumber(v.getVersionNumber())
                .revision(v.getRevision())
                .publishedAt(v.getPublishedAt())
                .tripId(trip != null ? trip.getTripId() : null)
                .tripCode(trip != null ? trip.getTripCode() : null)
                .tripName(trip != null ? trip.getName() : null)
                .durationDays(trip != null ? trip.getDurationDays() : null)
                .durationNights(trip != null ? trip.getDurationNights() : null)
                .plannedScope(trip != null ? trip.getPlannedScope() : null)
                .countryCodes(CountryScopeValidator.resolveAllowedCountries(trip))
                .domesticRegions(trip != null ? trip.getDomesticRegions() : Collections.emptyList())
                .departurePortId(firstStop != null && firstStop.getPort() != null ? firstStop.getPort().getPortId() : null)
                .departurePortName(firstStop != null && firstStop.getPort() != null ? firstStop.getPort().getPortName() : null)
                .departurePortCode(firstStop != null && firstStop.getPort() != null ? firstStop.getPort().getPortCode() : null)
                .returnPortId(lastStop != null && lastStop.getPort() != null ? lastStop.getPort().getPortId() : null)
                .returnPortName(lastStop != null && lastStop.getPort() != null ? lastStop.getPort().getPortName() : null)
                .returnPortCode(lastStop != null && lastStop.getPort() != null ? lastStop.getPort().getPortCode() : null)
                .totalStops(stops.size())
                .stopsSummary(stopSummaries)
                .build();
    }
}
