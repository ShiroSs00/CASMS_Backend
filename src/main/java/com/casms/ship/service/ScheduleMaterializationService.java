package com.casms.ship.service;

import com.casms.activity.entity.OnboardActivity;
import com.casms.activity.entity.ShoreExcursion;
import com.casms.activity.repository.OnboardActivityRepository;
import com.casms.activity.repository.ShoreExcursionRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.ship.dto.response.CruiseSchedulePreviewResponse;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.CruiseStop;
import com.casms.ship.entity.Itinerary;
import com.casms.ship.entity.ItineraryDay;
import com.casms.ship.entity.Port;
import com.casms.ship.enums.CruiseStopArrivalMethod;
import com.casms.ship.enums.CruiseStopCallType;
import com.casms.ship.enums.CruiseStopStatus;
import com.casms.ship.enums.ItineraryDayRole;
import com.casms.ship.enums.ItineraryDayType;
import com.casms.ship.enums.ItineraryStatus;
import com.casms.ship.enums.ItineraryType;
import com.casms.ship.repository.CruiseRepository;
import com.casms.ship.repository.CruiseStopRepository;
import com.casms.ship.repository.ItineraryDayRepository;
import com.casms.ship.repository.ItineraryRepository;
import com.casms.ship.repository.PortRepository;
import com.casms.ship.util.PortTimeZoneHelper;
import com.casms.trip.entity.Trip;
import com.casms.trip.entity.TripPlanDay;
import com.casms.trip.entity.TripPlanEntry;
import com.casms.trip.entity.TripPlanStop;
import com.casms.trip.entity.TripScheduleVersion;
import com.casms.trip.enums.TripPlanEntryKind;
import com.casms.trip.enums.TripScheduleVersionStatus;
import com.casms.trip.enums.TripStatus;
import com.casms.trip.repository.TripScheduleVersionRepository;
import com.casms.trip.service.CountryScopeValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Domain Service chuyển đổi lịch trình tương đối của TripScheduleVersion thành lịch trình thực tế
 * cho chuyến khởi hành Cruise (BE-03 / C02, C03, C04).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleMaterializationService {

    private final TripScheduleVersionRepository tripScheduleVersionRepository;
    private final ItineraryRepository itineraryRepository;
    private final ItineraryDayRepository itineraryDayRepository;
    private final CruiseStopRepository cruiseStopRepository;
    private final CruiseRepository cruiseRepository;
    private final PortRepository portRepository;
    private final OperationalProgramMappingService operationalProgramMappingService;
    private final OnboardActivityRepository onboardActivityRepository;
    private final ShoreExcursionRepository shoreExcursionRepository;
    private final PortWindowEvaluator portWindowEvaluator;

    /** Preview (C02): Tính toán xem trước toàn bộ lịch trình thực tế mà không commit database. */
    @Transactional(readOnly = true)
    public CruiseSchedulePreviewResponse previewSchedule(
            UUID sourceVersionId, Instant departureDatetime) {
        if (sourceVersionId == null) {
            throw new BusinessException(
                    "SOURCE_VERSION_REQUIRED",
                    "sourceVersionId là bắt buộc khi xem trước lịch trình");
        }
        if (departureDatetime == null) {
            throw new BusinessException(
                    "DEPARTURE_DATETIME_REQUIRED",
                    "departureDatetime là bắt buộc khi xem trước lịch trình");
        }

        TripScheduleVersion version =
                tripScheduleVersionRepository
                        .findById(sourceVersionId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "TripScheduleVersion", sourceVersionId));

        if (version.getStatus() != TripScheduleVersionStatus.PUBLISHED) {
            throw new BusinessException(
                    "VERSION_NOT_PUBLISHED",
                    "Chỉ có thể tạo hoặc xem trước lịch trình từ phiên bản TripScheduleVersion đã"
                        + " PUBLISHED (hiện tại: "
                            + version.getStatus()
                            + ")");
        }

        Trip trip = version.getTrip();
        if (trip == null || trip.getStatus() == TripStatus.ARCHIVED) {
            throw new BusinessException(
                    "TRIP_ARCHIVED", "Trip nguồn đã bị ARCHIVED hoặc không tồn tại.");
        }

        return computeProjectedSchedule(version, departureDatetime);
    }

    /**
     * Create always reprojects and reevaluates inside its own transaction; never trusts an FE PASS.
     */
    public void requireMaterializationAllowed(CruiseSchedulePreviewResponse projection) {
        if (!"PASS".equals(projection.getPortCallWindowStatus()))
            throw new BusinessException(
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "PORT_CALL_WINDOW_VALIDATION_REQUIRED",
                    "Chưa đáp ứng khung giờ cảng cho ngày thực tế.",
                    Map.of(
                            "portWindowChecks",
                            projection.getPortWindowChecks() == null
                                    ? List.of()
                                    : projection.getPortWindowChecks()));
    }

    /**
     * Materialize (C03): Khởi tạo và lưu toàn bộ Itinerary, ItineraryDay, CruiseStop thực tế vào
     * database cho Cruise trong cùng transaction.
     */
    @Transactional
    public Itinerary materializeSchedule(Cruise cruise, TripScheduleVersion version) {
        log.info(
                "Materializing relative schedule from version {} into cruise {}",
                version.getVersionId(),
                cruise.getCruiseId());

        if (version.getStatus() != TripScheduleVersionStatus.PUBLISHED) {
            throw new BusinessException(
                    "VERSION_NOT_PUBLISHED",
                    "Chỉ có thể áp dụng lịch trình từ phiên bản TripScheduleVersion đã PUBLISHED"
                        + " (hiện tại: "
                            + version.getStatus()
                            + ")");
        }

        Trip trip = version.getTrip();
        if (trip == null || trip.getStatus() == TripStatus.ARCHIVED) {
            throw new BusinessException(
                    "TRIP_ARCHIVED", "Trip nguồn đã bị ARCHIVED hoặc không tồn tại.");
        }

        if (cruise.getSourceVersion() == null
                || !Objects.equals(cruise.getSourceVersion().getVersionId(), version.getVersionId())
                || cruise.getSourceTrip() == null
                || !Objects.equals(cruise.getSourceTrip().getTripId(), trip.getTripId())
                || cruise.getSourceVersionRevision() == null
                || !Objects.equals(cruise.getSourceVersionRevision(), version.getRevision())) {
            throw new BusinessException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "STALE_PREVIEW",
                    "Nguồn hoặc revision không khớp hồ sơ chuyến khởi hành. Không thể materialize"
                        + " từ một nguồn khác.");
        }

        // 1. Tính toán trước toàn bộ lịch
        CruiseSchedulePreviewResponse projection =
                computeProjectedSchedule(version, cruise.getDepartureDatetime());
        requireMaterializationAllowed(projection);

        // 2. Cập nhật ngày kết thúc chuyến đi và khóa sourceVersionRevision
        if (projection.getReturnDatetime() != null) {
            cruise.setReturnDatetime(projection.getReturnDatetime());
        }
        if (version.getRevision() != null) {
            cruise.setSourceVersionRevision(version.getRevision());
        }
        cruiseRepository.save(cruise);

        // 3. Never overwrite an existing snapshot
        if (itineraryRepository.findByCruiseCruiseId(cruise.getCruiseId()).isPresent()) {
            throw new BusinessException(
                    "ITINERARY_ALREADY_MATERIALIZED",
                    "Existing cruise itinerary must not be overwritten by source materialization");
        }

        // 4. Tạo Itinerary mới
        LocalDate startDate = ItineraryCalendar.start(cruise);
        LocalDate endDate = ItineraryCalendar.end(cruise);

        Itinerary itinerary = new Itinerary();
        itinerary.setDeparturePortFacts(
                version.getDays().stream()
                        .filter(d -> d.getDayNumber() == 1)
                        .map(
                                d ->
                                        d.getDeparturePortFacts() != null
                                                ? d.getDeparturePortFacts()
                                                : com.casms.ship.dto.PortFacts.capture(
                                                        d.getDeparturePort()))
                        .filter(java.util.Objects::nonNull)
                        .findFirst()
                        .orElseGet(
                                () ->
                                        projection.getDays().stream()
                                                .flatMap(d -> d.getStops().stream())
                                                .map(
                                                        CruiseSchedulePreviewResponse
                                                                        .ProjectedStopDto
                                                                ::getPortFacts)
                                                .filter(java.util.Objects::nonNull)
                                                .findFirst()
                                                .orElse(null)));
        itinerary.setReturnPortFacts(
                projection.getDays().stream()
                        .flatMap(d -> d.getStops().stream())
                        .max(
                                java.util.Comparator.comparing(
                                        CruiseSchedulePreviewResponse.ProjectedStopDto
                                                ::getStopOrder))
                        .map(CruiseSchedulePreviewResponse.ProjectedStopDto::getPortFacts)
                        .orElse(null));

        itinerary.setCruise(cruise);
        itinerary.setItineraryName(cruise.getCruiseName());
        itinerary.setSourceDurationNights(trip.getDurationNights());
        itinerary.setSourceContentHash(version.getContentHash());
        itinerary.setStartDate(startDate);
        itinerary.setEndDate(endDate);
        itinerary.setItineraryType(
                trip.getTripType() != null ? trip.getTripType() : ItineraryType.ROUND_TRIP);
        itinerary.setStatus(ItineraryStatus.DRAFT);
        // A published template never grants operational plan approval.
        itinerary.setApprovalInherited(false);
        itinerary.setContentModifiedAfterMaterialization(false);
        itinerary = itineraryRepository.save(itinerary);

        // 5. Tạo ItineraryDay
        Map<Integer, ItineraryDay> dayMap = new HashMap<>();
        for (CruiseSchedulePreviewResponse.ProjectedDayDto dayDto : projection.getDays()) {
            ItineraryDay day = new ItineraryDay();
            day.setItinerary(itinerary);
            day.setDayNumber(dayDto.getDayNumber());
            day.setDate(dayDto.getCalendarDate());
            day.setDayType(dayDto.getDayType());
            day.setDayRole(dayDto.getDayRole());
            day.setShipTimeZoneId(dayDto.getShipTimeZoneId());
            day.setDescription(dayDto.getTitle());
            day.setSourceDayDescription(dayDto.getDescription());
            day = itineraryDayRepository.save(day);
            dayMap.put(day.getDayNumber(), day);
        }

        // 6. Tạo CruiseStop
        List<TripPlanStop> planStops =
                version.getStops() != null
                        ? new ArrayList<>(version.getStops())
                        : new ArrayList<>();
        planStops.sort(
                Comparator.comparing(
                        TripPlanStop::getStopOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())));

        Map<UUID, TripPlanStop> planStopMap = new HashMap<>();
        for (TripPlanStop ps : planStops) {
            planStopMap.put(ps.getStopId(), ps);
        }

        for (CruiseSchedulePreviewResponse.ProjectedDayDto dayDto : projection.getDays()) {
            if (dayDto.getStops() == null) continue;
            for (CruiseSchedulePreviewResponse.ProjectedStopDto stopDto : dayDto.getStops()) {
                TripPlanStop sourceStop = planStopMap.get(stopDto.getSourceStopId());
                int arrDayNum =
                        sourceStop != null && sourceStop.getArrivalDayNumber() != null
                                ? sourceStop.getArrivalDayNumber()
                                : dayDto.getDayNumber();
                int depDayNum =
                        sourceStop != null && sourceStop.getDepartureDayNumber() != null
                                ? sourceStop.getDepartureDayNumber()
                                : arrDayNum;

                ItineraryDay arrDay = dayMap.get(arrDayNum);
                ItineraryDay depDay = dayMap.get(depDayNum);
                if (sourceStop == null || arrDay == null || depDay == null) {
                    throw new BusinessException(
                            org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                            "SOURCE_STOP_INVALID",
                            "Lần ghé cảng nguồn không khớp ngày lịch trình; không tự chọn ngày thay"
                                + " thế.");
                }

                Port port = sourceStop != null ? sourceStop.getPort() : null;
                if (port == null && stopDto.getPortId() != null) {
                    port = portRepository.findById(stopDto.getPortId()).orElse(null);
                }

                CruiseStop stop = new CruiseStop();
                stop.setItinerary(itinerary);
                stop.setArrivalItineraryDay(arrDay);
                stop.setDepartureItineraryDay(depDay);
                stop.setPort(port);
                stop.setSourceStopId(sourceStop.getStopId());
                stop.setPortFacts(stopDto.getPortFacts());
                stop.setPortWindowEvidence(stopDto.getPortWindowChecks());
                for (var sourceVisit : sourceStop.getVisits()) {
                    var visit = new com.casms.ship.entity.CruiseDestinationVisit();
                    visit.setStop(stop);
                    visit.setSourceVisitId(sourceVisit.getVisitId());
                    visit.setDestination(sourceVisit.getDestination());
                    visit.setSequence(sourceVisit.getSequence());
                    visit.setDestinationNameSnapshot(
                            sourceVisit.getDestinationNameSnapshot() != null
                                    ? sourceVisit.getDestinationNameSnapshot()
                                    : sourceVisit.getDestination().getDestinationName());
                    stop.getVisits().add(visit);
                }
                stop.setDestination(sourceStop != null ? sourceStop.getDestination() : null);
                stop.setStopOrder(stopDto.getStopOrder());
                stop.setPlannedArrival(stopDto.getPlannedArrival());
                stop.setPlannedDeparture(stopDto.getPlannedDeparture());
                stop.setAllAboardTime(stopDto.getAllAboardTime());
                stop.setCallType(
                        stopDto.getCallType() != null
                                ? stopDto.getCallType()
                                : CruiseStopCallType.NORMAL);
                stop.setArrivalMethod(
                        stopDto.getArrivalMethod() != null
                                ? stopDto.getArrivalMethod()
                                : CruiseStopArrivalMethod.BERTH);
                stop.setTenderTransferMinutes(stopDto.getTenderTransferMinutes());
                stop.setStatus(CruiseStopStatus.PLANNED);
                if (sourceStop != null) {
                    stop.setNotes("sourceStopId:" + sourceStop.getStopId());
                }
                cruiseStopRepository.save(stop);
            }
        }

        // Program failures roll back the same Cruise/Itinerary transaction.
        var mapping = operationalProgramMappingService.mapOperationalPrograms(cruise, version);
        if (mapping == null
                || mapping.getItems() == null
                || mapping.getItems().size() != version.getEntries().size()
                || mapping.getItems().stream()
                        .anyMatch(
                                item ->
                                        item.getTargetId() == null
                                                || "FAILED".equals(item.getStatus()))) {
            throw new BusinessException(
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "SOURCE_PROGRAM_MATERIALIZATION_FAILED",
                    "Không thể tạo đầy đủ chương trình từ nguồn. Chuyến khởi hành và lịch trình"
                        + " chưa được lưu.");
        }

        log.info(
                "Successfully materialized itinerary {} with {} days and stops for cruise {}",
                itinerary.getItineraryId(),
                dayMap.size(),
                cruise.getCruiseCode());
        return itinerary;
    }

    /** Đọc lại lịch trình thực tế đã materialized của Cruise (C04). */
    @Transactional(readOnly = true)
    public CruiseSchedulePreviewResponse getCruiseSchedule(UUID cruiseId) {
        Cruise cruise =
                cruiseRepository
                        .findById(cruiseId)
                        .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));

        Itinerary itinerary = itineraryRepository.findByCruiseCruiseId(cruiseId).orElse(null);
        if (itinerary == null) {
            return CruiseSchedulePreviewResponse.builder()
                    .itineraryId(null)
                    .itineraryStatus(null)
                    .materialized(false)
                    .sourceTripId(
                            cruise.getSourceTrip() != null
                                    ? cruise.getSourceTrip().getTripId()
                                    : null)
                    .sourceTripCode(
                            cruise.getSourceTrip() != null
                                    ? cruise.getSourceTrip().getTripCode()
                                    : null)
                    .sourceTripName(
                            cruise.getSourceTrip() != null
                                    ? cruise.getSourceTrip().getName()
                                    : null)
                    .sourceVersionId(
                            cruise.getSourceVersion() != null
                                    ? cruise.getSourceVersion().getVersionId()
                                    : null)
                    .sourceVersionNumber(
                            cruise.getSourceVersion() != null
                                    ? cruise.getSourceVersion().getVersionNumber()
                                    : null)
                    .sourceVersionRevision(cruise.getSourceVersionRevision())
                    .departureDatetime(cruise.getDepartureDatetime())
                    .returnDatetime(cruise.getReturnDatetime())
                    .departurePortId(
                            cruise.getDeparturePort() != null
                                    ? cruise.getDeparturePort().getPortId()
                                    : null)
                    .departurePortName(
                            cruise.getDeparturePort() != null
                                    ? cruise.getDeparturePort().getPortName()
                                    : null)
                    .departurePortCode(
                            cruise.getDeparturePort() != null
                                    ? cruise.getDeparturePort().getPortCode()
                                    : null)
                    .returnPortId(
                            cruise.getReturnPort() != null
                                    ? cruise.getReturnPort().getPortId()
                                    : null)
                    .returnPortName(
                            cruise.getReturnPort() != null
                                    ? cruise.getReturnPort().getPortName()
                                    : null)
                    .returnPortCode(
                            cruise.getReturnPort() != null
                                    ? cruise.getReturnPort().getPortCode()
                                    : null)
                    .days(Collections.emptyList())
                    .warnings(
                            List.of(
                                    "Chuyến khởi hành chưa được khởi tạo lịch trình thực tế"
                                        + " (Itinerary chưa tồn tại)"))
                    .build();
        }

        List<ItineraryDay> days =
                itineraryDayRepository.findByItineraryItineraryIdOrderByDayNumberAsc(
                        itinerary.getItineraryId());
        List<CruiseStop> stops =
                cruiseStopRepository.findByItineraryItineraryIdOrderByStopOrderAsc(
                        itinerary.getItineraryId());

        Map<UUID, List<CruiseStop>> dayStopsMap = new HashMap<>();
        for (CruiseStop stop : stops) {
            UUID dayId =
                    stop.getArrivalItineraryDay() != null
                            ? stop.getArrivalItineraryDay().getItineraryDayId()
                            : null;
            if (dayId != null) {
                dayStopsMap.computeIfAbsent(dayId, k -> new ArrayList<>()).add(stop);
            }
        }

        List<OnboardActivity> activities =
                onboardActivityRepository.findByCruise_CruiseId(cruiseId);
        List<ShoreExcursion> excursions =
                shoreExcursionRepository.findByCruiseStop_Itinerary_Cruise_CruiseId(cruiseId);

        List<CruiseSchedulePreviewResponse.ProjectedDayDto> dayDtos = new ArrayList<>();
        for (ItineraryDay d : days) {
            List<CruiseStop> dayStops =
                    dayStopsMap.getOrDefault(d.getItineraryDayId(), Collections.emptyList());
            List<CruiseSchedulePreviewResponse.ProjectedStopDto> stopDtos =
                    dayStops.stream()
                            .map(
                                    s -> {
                                        Port p = s.getPort();
                                        UUID sourceStopId = s.getSourceStopId();
                                        if (sourceStopId == null
                                                && s.getNotes() != null
                                                && s.getNotes().startsWith("sourceStopId:")) {
                                            try {
                                                sourceStopId =
                                                        UUID.fromString(
                                                                s.getNotes()
                                                                        .substring(
                                                                                "sourceStopId:"
                                                                                        .length()));
                                            } catch (Exception ignored) {
                                            }
                                        }
                                        return CruiseSchedulePreviewResponse.ProjectedStopDto
                                                .builder()
                                                .cruiseStopId(s.getCruiseStopId())
                                                .sourceStopId(sourceStopId)
                                                .stopOrder(s.getStopOrder())
                                                .portId(p != null ? p.getPortId() : null)
                                                .portCode(
                                                        s.getPortFacts() == null
                                                                ? null
                                                                : s.getPortFacts().portCode())
                                                .portName(
                                                        s.getPortFacts() == null
                                                                ? null
                                                                : s.getPortFacts().portName())
                                                .countryCode(
                                                        s.getPortFacts() == null
                                                                ? null
                                                                : s.getPortFacts().countryCode())
                                                .portTimeZoneId(
                                                        s.getPortFacts() == null
                                                                ? null
                                                                : s.getPortFacts().timeZoneId())
                                                .portFacts(s.getPortFacts())
                                                .portWindowChecks(s.getPortWindowEvidence())
                                                .visits(
                                                        s.getVisits().stream()
                                                                .map(
                                                                        v ->
                                                                                new com.casms.trip
                                                                                        .dto
                                                                                        .response
                                                                                        .DestinationVisitResponse(
                                                                                        v
                                                                                                .getVisitId(),
                                                                                        v.getSourceVisitId()
                                                                                                .toString(),
                                                                                        v.getDestination()
                                                                                                .getDestinationId(),
                                                                                        v
                                                                                                .getDestinationNameSnapshot(),
                                                                                        v
                                                                                                .getSequence()))
                                                                .toList())
                                                .plannedArrival(s.getPlannedArrival())
                                                .plannedDeparture(s.getPlannedDeparture())
                                                .allAboardTime(s.getAllAboardTime())
                                                .callType(s.getCallType())
                                                .arrivalMethod(s.getArrivalMethod())
                                                .tenderTransferMinutes(s.getTenderTransferMinutes())
                                                .build();
                                    })
                            .toList();

            ZoneId dayZone =
                    d.getShipTimeZoneId() != null
                            ? PortTimeZoneHelper.resolveZone(d.getShipTimeZoneId())
                            : ZoneOffset.UTC;

            List<CruiseSchedulePreviewResponse.ProjectedEntryDto> dayEntries = new ArrayList<>();
            for (OnboardActivity act : activities) {
                if (act.getStartTime() != null) {
                    LocalDate actDate = act.getStartTime().atZone(dayZone).toLocalDate();
                    if (Objects.equals(actDate, d.getDate())) {
                        dayEntries.add(
                                CruiseSchedulePreviewResponse.ProjectedEntryDto.builder()
                                        .title(act.getActivityName())
                                        .timeZoneId(PortTimeZoneHelper.SHIP_TIME_ZONE.getId())
                                        .locationText(act.getLocation())
                                        .meetingPoint(act.getMeetingPoint())
                                        .notes(act.getDescription())
                                        .targetId(act.getOnboardActivityId())
                                        .targetCode(act.getActivityCode())
                                        .kind(TripPlanEntryKind.ONBOARD_ACTIVITY)
                                        .category(act.getCategory())
                                        .status(
                                                act.getStatus() != null
                                                        ? act.getStatus().name()
                                                        : null)
                                        .plannedStartTime(act.getStartTime())
                                        .plannedEndTime(act.getEndTime())
                                        .build());
                    }
                }
            }

            for (ShoreExcursion exc : excursions) {
                if (exc.getStartTime() != null) {
                    ZoneId excZone =
                            (exc.getCruiseStop() != null
                                            && exc.getCruiseStop().getPortFacts() != null)
                                    ? PortTimeZoneHelper.resolveZone(
                                            exc.getCruiseStop().getPortFacts().timeZoneId())
                                    : dayZone;
                    LocalDate excDate = exc.getStartTime().atZone(excZone).toLocalDate();
                    if (Objects.equals(excDate, d.getDate())) {
                        dayEntries.add(
                                CruiseSchedulePreviewResponse.ProjectedEntryDto.builder()
                                        .title(exc.getExcursionName())
                                        .timeZoneId(excZone.getId())
                                        .meetingPoint(exc.getMeetingPoint())
                                        .locationText(exc.getLocationText())
                                        .notes(exc.getNotes())
                                        .targetId(exc.getShoreExcursionId())
                                        .targetCode(exc.getExcursionCode())
                                        .kind(TripPlanEntryKind.SHORE_EXCURSION)
                                        .status(
                                                exc.getStatus() != null
                                                        ? exc.getStatus().name()
                                                        : null)
                                        .plannedStartTime(exc.getStartTime())
                                        .plannedEndTime(exc.getPlannedReturnTime())
                                        .stopOrder(
                                                exc.getCruiseStop() != null
                                                        ? exc.getCruiseStop().getStopOrder()
                                                        : null)
                                        .build());
                    }
                }
            }

            dayDtos.add(
                    CruiseSchedulePreviewResponse.ProjectedDayDto.builder()
                            .itineraryDayId(d.getItineraryDayId())
                            .dayNumber(d.getDayNumber())
                            .calendarDate(d.getDate())
                            .dayType(d.getDayType())
                            .dayRole(d.getDayRole())
                            .title(d.getDescription())
                            .description(d.getSourceDayDescription())
                            .shipTimeZoneId(d.getShipTimeZoneId())
                            .stops(stopDtos)
                            .entries(dayEntries)
                            .build());
        }

        Trip sourceTrip = cruise.getSourceTrip();
        TripScheduleVersion sourceVersion = cruise.getSourceVersion();

        return CruiseSchedulePreviewResponse.builder()
                .itineraryId(itinerary.getItineraryId())
                .itineraryStatus(
                        itinerary.getStatus() != null ? itinerary.getStatus().name() : null)
                .materialized(true)
                .sourceContentHash(itinerary.getSourceContentHash())
                .sourceTripId(sourceTrip != null ? sourceTrip.getTripId() : null)
                .sourceTripCode(sourceTrip != null ? sourceTrip.getTripCode() : null)
                .sourceTripName(sourceTrip != null ? sourceTrip.getName() : null)
                .sourceVersionId(sourceVersion != null ? sourceVersion.getVersionId() : null)
                .sourceVersionNumber(
                        sourceVersion != null ? sourceVersion.getVersionNumber() : null)
                .sourceVersionRevision(cruise.getSourceVersionRevision())
                .departureDatetime(cruise.getDepartureDatetime())
                .returnDatetime(cruise.getReturnDatetime())
                .durationDays(days.size())
                .durationNights(itinerary.getSourceDurationNights())
                .departurePortId(
                        cruise.getDeparturePort() != null
                                ? cruise.getDeparturePort().getPortId()
                                : null)
                .departurePortName(
                        itinerary.getDeparturePortFacts() != null
                                ? itinerary.getDeparturePortFacts().portName()
                                : null)
                .departurePortCode(
                        itinerary.getDeparturePortFacts() != null
                                ? itinerary.getDeparturePortFacts().portCode()
                                : null)
                .returnPortId(
                        cruise.getReturnPort() != null ? cruise.getReturnPort().getPortId() : null)
                .returnPortName(
                        itinerary.getReturnPortFacts() != null
                                ? itinerary.getReturnPortFacts().portName()
                                : null)
                .returnPortCode(
                        itinerary.getReturnPortFacts() != null
                                ? itinerary.getReturnPortFacts().portCode()
                                : null)
                .days(dayDtos)
                .build();
    }

    /**
     * Thuật toán tính toán chuẩn xác ngày, giờ, múi giờ giữa relative version và actual schedule.
     */
    private int sourceDurationDays(TripScheduleVersion version) {
        Trip trip = version.getTrip();
        Integer days = trip == null ? null : trip.getDurationDays();
        Integer nights = trip == null ? null : trip.getDurationNights();
        if (trip == null
                || trip.getDurationGroup() == null
                || days == null
                || !trip.getDurationGroup().isValidDays(days)
                || nights == null
                || nights < 0
                || nights > days
                || version.getDays() == null
                || version.getDays().size() != days) {
            throw invalidSourceDuration();
        }
        Set<Integer> numbers = new HashSet<>();
        for (TripPlanDay day : version.getDays()) {
            if (day == null
                    || day.getDayType() == null
                    || day.getDayNumber() == null
                    || day.getDayNumber() < 1
                    || day.getDayNumber() > days
                    || !numbers.add(day.getDayNumber())) {
                throw invalidSourceDuration();
            }
        }
        return days;
    }

    private BusinessException invalidSourceDuration() {
        return new BusinessException(
                org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                "SOURCE_DURATION_INVALID",
                "Thời lượng nguồn thiếu hoặc không khớp nhóm, số ngày, số đêm và các ngày lịch"
                    + " trình. Vui lòng kiểm tra lại nguồn.");
    }

    private CruiseSchedulePreviewResponse computeProjectedSchedule(
            TripScheduleVersion version, Instant departureDatetime) {
        if (version.getRevision() == null || version.getRevision() < 1) {
            throw new BusinessException(
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "SOURCE_REVISION_REQUIRED",
                    "Phiên bản nguồn chưa có revision hợp lệ.");
        }
        if (version.getEntries().stream()
                .anyMatch(entry -> entry.getKind() == TripPlanEntryKind.SERVING_WINDOW)) {
            throw new BusinessException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "LEGACY_SOURCE_MATERIALIZATION_BLOCKED",
                    "Mẫu đã chốt có khung phục vụ lịch sử. Chưa thể tạo chuyến từ nguồn này khi"
                        + " CR-REVIEW-04 chưa được quyết định.");
        }
        int totalDays = sourceDurationDays(version);
        Trip trip = version.getTrip();
        List<TripPlanDay> planDays =
                version.getDays() != null ? new ArrayList<>(version.getDays()) : new ArrayList<>();
        planDays.sort(
                Comparator.comparing(
                        TripPlanDay::getDayNumber,
                        Comparator.nullsLast(Comparator.naturalOrder())));

        List<TripPlanStop> planStops =
                version.getStops() != null
                        ? new ArrayList<>(version.getStops())
                        : new ArrayList<>();
        planStops.sort(
                Comparator.comparing(
                        TripPlanStop::getStopOrder,
                        Comparator.nullsLast(Comparator.naturalOrder())));

        List<TripPlanEntry> planEntries =
                version.getEntries() != null
                        ? new ArrayList<>(version.getEntries())
                        : new ArrayList<>();

        // Xác định cảng khởi hành để lấy múi giờ gốc của Day 1
        TripPlanStop firstStop = planStops.isEmpty() ? null : planStops.get(0);
        TripPlanStop lastStop = planStops.isEmpty() ? null : planStops.get(planStops.size() - 1);

        boolean startsAtSea =
                planDays.stream()
                        .anyMatch(
                                day ->
                                        Integer.valueOf(1).equals(day.getDayNumber())
                                                && day.getDayType() == ItineraryDayType.SEA_DAY);
        if (firstStop == null || firstStop.getPort() == null) {
            throw new BusinessException(
                    "MISSING_DEPARTURE_STOP",
                    "Phiên bản lịch trình nguồn không có điểm dừng khởi hành.");
        }

        TripPlanDay departureDay =
                planDays.stream()
                        .filter(day -> Integer.valueOf(1).equals(day.getDayNumber()))
                        .findFirst()
                        .orElse(null);
        Port departurePort =
                departureDay != null && departureDay.getDeparturePort() != null
                        ? departureDay.getDeparturePort()
                        : startsAtSea ? null : firstStop.getPort();
        if (departurePort != null)
            CountryScopeValidator.validatePort(departurePort, trip, "Cảng khởi hành");

        // BE-04 / G06: Giờ cơ sở tàu (Ship Base Timezone) luôn là Asia/Ho_Chi_Minh
        ZoneId shipZone = PortTimeZoneHelper.SHIP_TIME_ZONE;
        LocalDate day1Date = departureDatetime.atZone(shipZone).toLocalDate();

        // Validate Country Scope cho tất cả các cảng trong lịch trình
        for (TripPlanStop stop : planStops) {
            if (stop.getPort() != null) {
                CountryScopeValidator.validatePort(
                        stop.getPort(), trip, "Cảng ghé thứ " + stop.getStopOrder());
            }
        }

        // Tính toán các lần cập cảng (Stops)
        List<CruiseSchedulePreviewResponse.ProjectedStopDto> projectedStops = new ArrayList<>();
        Set<Integer> stopOrders = new HashSet<>();
        for (TripPlanStop stop : planStops) {
            if (stop.getStopId() == null
                    || stop.getStopOrder() == null
                    || stop.getStopOrder() < 1
                    || !stopOrders.add(stop.getStopOrder())
                    || stop.getArrivalDayNumber() == null
                    || stop.getDepartureDayNumber() == null
                    || stop.getArrivalDayNumber() < 1
                    || stop.getDepartureDayNumber() > totalDays
                    || stop.getDepartureDayNumber() < stop.getArrivalDayNumber()) {
                throw new BusinessException(
                        org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                        "SOURCE_STOP_INVALID",
                        "Thứ tự hoặc ngày lần ghé cảng nguồn không hợp lệ; không tự suy đoán.");
            }
            Port port = stop.getPort();
            if (port == null) {
                throw new BusinessException("MISSING_PORT", "Điểm dừng không có thông tin cảng.");
            }
            var portFacts =
                    stop.getPortFacts() != null
                            ? stop.getPortFacts()
                            : com.casms.ship.dto.PortFacts.capture(port);
            ZoneId portZone = PortTimeZoneHelper.resolveZone(portFacts.timeZoneId());

            int arrDayNum = stop.getArrivalDayNumber() != null ? stop.getArrivalDayNumber() : 1;
            int depDayNum =
                    stop.getDepartureDayNumber() != null ? stop.getDepartureDayNumber() : arrDayNum;

            LocalDate arrDate = day1Date.plusDays(arrDayNum - 1);
            LocalDate depDate = day1Date.plusDays(depDayNum - 1);

            if (stop.getPlannedArrivalTime() == null || stop.getPlannedDepartureTime() == null)
                throw new BusinessException(
                        "PORT_CALL_TIME_REQUIRED", "Source port-call times required");
            Instant plannedArr =
                    stop.getPlannedArrivalTime() != null
                            ? PortTimeZoneHelper.strictLocalInstant(
                                    LocalDateTime.of(arrDate, stop.getPlannedArrivalTime()),
                                    portZone)
                            : departureDatetime;

            Instant plannedDep =
                    stop.getPlannedDepartureTime() != null
                            ? PortTimeZoneHelper.strictLocalInstant(
                                    LocalDateTime.of(depDate, stop.getPlannedDepartureTime()),
                                    portZone)
                            : plannedArr.plusSeconds(3600);

            if (plannedDep.isBefore(plannedArr) || plannedDep.equals(plannedArr)) {
                throw new BusinessException(
                        "INVALID_TIME_RANGE",
                        String.format(
                                "Lịch ghé cảng %s có giờ rời (%s) không sau giờ cập (%s).",
                                port.getPortName(), plannedDep, plannedArr));
            }

            Instant allAboard =
                    stop.getAllAboardTime() != null
                            ? PortTimeZoneHelper.strictLocalInstant(
                                    LocalDateTime.of(
                                            day1Date.plusDays(
                                                    (stop.getAllAboardDayNumber() != null
                                                                    ? stop.getAllAboardDayNumber()
                                                                    : depDayNum)
                                                            - 1),
                                            stop.getAllAboardTime()),
                                    portZone)
                            : plannedDep;

            if (allAboard.isBefore(plannedArr) || allAboard.isAfter(plannedDep))
                throw new BusinessException(
                        "INVALID_ALL_ABOARD_TIME",
                        "All-aboard must be inside the actual port-call window");
            CruiseSchedulePreviewResponse.ProjectedStopDto stopDto =
                    CruiseSchedulePreviewResponse.ProjectedStopDto.builder()
                            .sourceStopId(stop.getStopId())
                            .stopOrder(stop.getStopOrder())
                            .portId(port.getPortId())
                            .portCode(port.getPortCode())
                            .portName(portFacts.portName())
                            .countryCode(portFacts.countryCode())
                            .portFacts(portFacts)
                            .visits(
                                    stop.getVisits().stream()
                                            .map(
                                                    v ->
                                                            new com.casms.trip.dto.response
                                                                    .DestinationVisitResponse(
                                                                    v.getVisitId(),
                                                                    v.getClientKey(),
                                                                    v.getDestination()
                                                                            .getDestinationId(),
                                                                    v.getDestinationNameSnapshot()
                                                                                    != null
                                                                            ? v
                                                                                    .getDestinationNameSnapshot()
                                                                            : v.getDestination()
                                                                                    .getDestinationName(),
                                                                    v.getSequence()))
                                            .toList())
                            .portTimeZoneId(portZone.getId())
                            .plannedArrival(plannedArr)
                            .plannedDeparture(plannedDep)
                            .allAboardTime(allAboard)
                            .callType(
                                    stop.getCallType() != null
                                            ? stop.getCallType()
                                            : CruiseStopCallType.NORMAL)
                            .arrivalMethod(
                                    stop.getArrivalMethod() != null
                                            ? stop.getArrivalMethod()
                                            : CruiseStopArrivalMethod.BERTH)
                            .tenderTransferMinutes(null)
                            .build();
            projectedStops.add(stopDto);
        }

        // Kiểm tra Invariant: không overlap, di chuyển dương giữa 2 cảng khác nhau, tuần tự thời
        // gian
        for (int i = 0; i < projectedStops.size(); i++) {
            for (int j = i + 1; j < projectedStops.size(); j++) {
                CruiseSchedulePreviewResponse.ProjectedStopDto a = projectedStops.get(i);
                CruiseSchedulePreviewResponse.ProjectedStopDto b = projectedStops.get(j);

                if (a.getPlannedArrival().isBefore(b.getPlannedDeparture())
                        && b.getPlannedArrival().isBefore(a.getPlannedDeparture())) {
                    throw new BusinessException(
                            "PORT_CALL_OVERLAP",
                            String.format(
                                    "Hải trình có sự chồng lấn giữa lần ghé %s (%s - %s) và lần ghé"
                                        + " %s (%s - %s).",
                                    a.getPortName(),
                                    a.getPlannedArrival(),
                                    a.getPlannedDeparture(),
                                    b.getPortName(),
                                    b.getPlannedArrival(),
                                    b.getPlannedDeparture()));
                }

                boolean isSamePort = Objects.equals(a.getPortId(), b.getPortId());
                if (!isSamePort
                        && (a.getPlannedDeparture().equals(b.getPlannedArrival())
                                || b.getPlannedDeparture().equals(a.getPlannedArrival()))) {
                    throw new BusinessException(
                            "NON_POSITIVE_TRANSIT_TIME",
                            String.format(
                                    "Thời gian di chuyển giữa hai cảng khác nhau (%s và %s) phải là"
                                        + " số dương.",
                                    a.getPortName(), b.getPortName()));
                }

                if (a.getStopOrder() != null && b.getStopOrder() != null) {
                    if (a.getStopOrder() < b.getStopOrder()
                            && !a.getPlannedDeparture().isBefore(b.getPlannedArrival())) {
                        throw new BusinessException(
                                "INVALID_STOP_SEQUENCE_TIME",
                                String.format(
                                        "Lần ghé thứ %d (%s) phải kết thúc trước khi lần ghé thứ %d"
                                            + " (%s) bắt đầu.",
                                        a.getStopOrder(),
                                        a.getPortName(),
                                        b.getStopOrder(),
                                        b.getPortName()));
                    }
                }
            }
        }

        // Phân bổ stops và entries theo từng ngày
        Map<Integer, List<CruiseSchedulePreviewResponse.ProjectedStopDto>> dayStopsMap =
                new HashMap<>();
        for (CruiseSchedulePreviewResponse.ProjectedStopDto s : projectedStops) {
            TripPlanStop sourceStop =
                    planStops.stream()
                            .filter(ps -> ps.getStopId().equals(s.getSourceStopId()))
                            .findFirst()
                            .orElse(null);
            int dayNum =
                    sourceStop != null && sourceStop.getArrivalDayNumber() != null
                            ? sourceStop.getArrivalDayNumber()
                            : 1;
            dayStopsMap.computeIfAbsent(dayNum, k -> new ArrayList<>()).add(s);
        }

        Instant projectedReturn = projectedStops.getLast().getPlannedDeparture();
        if (!projectedReturn.isAfter(departureDatetime))
            throw new BusinessException("INVALID_SCHEDULE", "Return must be after departure");
        Map<Integer, List<CruiseSchedulePreviewResponse.ProjectedEntryDto>> dayEntriesMap =
                new HashMap<>();
        for (TripPlanEntry entry : planEntries) {
            if (entry.getStartDayNumber() == null
                    || entry.getEndDayNumber() == null
                    || entry.getStartDayNumber() < 1
                    || entry.getEndDayNumber() > totalDays
                    || entry.getEndDayNumber() < entry.getStartDayNumber()) {
                throw new BusinessException(
                        org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                        "SOURCE_PROGRAM_SCHEDULE_REQUIRED",
                        "Ngày chương trình không thuộc lịch trình nguồn.");
            }
            if (entry.getKind() == TripPlanEntryKind.SHORE_EXCURSION
                    && (entry.getStop() == null
                            || planStops.stream()
                                    .noneMatch(
                                            stop ->
                                                    Objects.equals(
                                                            stop.getStopId(),
                                                            entry.getStop().getStopId()))
                            || entry.getStop().getPort() == null
                            || entry.getStop().getPort().getTimeZoneId() == null)) {
                throw new BusinessException(
                        org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                        "SOURCE_EXCURSION_OUTSIDE_PORT_CALL",
                        "Tour nguồn chưa liên kết đúng lần ghé cảng và múi giờ cảng.");
            }
            int startDay = entry.getStartDayNumber() != null ? entry.getStartDayNumber() : 1;
            int endDay = entry.getEndDayNumber() != null ? entry.getEndDayNumber() : startDay;

            LocalDate startCal = day1Date.plusDays(startDay - 1);
            LocalDate endCal = day1Date.plusDays(endDay - 1);

            ZoneId entryZone;
            if (entry.getKind() == TripPlanEntryKind.SHORE_EXCURSION) {
                TripPlanStop stop = entry.getStop();
                if (stop != null
                        && stop.getPort() != null
                        && stop.getPort().getTimeZoneId() != null) {
                    entryZone =
                            PortTimeZoneHelper.resolveZone(
                                    stop.getPortFacts() != null
                                            ? stop.getPortFacts().timeZoneId()
                                            : stop.getPort().getTimeZoneId());
                } else {
                    entryZone = shipZone;
                }
            } else {
                entryZone = shipZone;
            }

            Instant startInst =
                    entry.getPlannedStartTime() != null
                            ? PortTimeZoneHelper.strictLocalInstant(
                                    LocalDateTime.of(startCal, entry.getPlannedStartTime()),
                                    entryZone)
                            : null;
            Instant endInst =
                    entry.getPlannedEndTime() != null
                            ? PortTimeZoneHelper.strictLocalInstant(
                                    LocalDateTime.of(endCal, entry.getPlannedEndTime()), entryZone)
                            : null;

            if (entry.getKind() == TripPlanEntryKind.ONBOARD_ACTIVITY
                    || entry.getKind() == TripPlanEntryKind.SHORE_EXCURSION) {
                if (entry.getStartDayNumber() == null
                        || entry.getEndDayNumber() == null
                        || startInst == null
                        || endInst == null
                        || !endInst.isAfter(startInst))
                    throw new BusinessException(
                            org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                            "SOURCE_PROGRAM_SCHEDULE_REQUIRED",
                            "Lịch trình nguồn có chương trình chưa đủ ngày giờ hoặc giờ kết thúc"
                                + " không sau giờ bắt đầu: "
                                    + entry.getTitle(),
                            Map.of(
                                    "name",
                                    Objects.toString(entry.getTitle(), ""),
                                    "entryId",
                                    entry.getEntryId()));
                if (startInst.isBefore(departureDatetime) || endInst.isAfter(projectedReturn))
                    throw new BusinessException(
                            org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                            "SOURCE_PROGRAM_OUTSIDE_CRUISE",
                            "Chương trình trong lịch trình nguồn nằm ngoài thời gian chuyến: "
                                    + entry.getTitle(),
                            Map.of(
                                    "name",
                                    Objects.toString(entry.getTitle(), ""),
                                    "entryId",
                                    entry.getEntryId()));
                if (entry.getKind() == TripPlanEntryKind.SHORE_EXCURSION) {
                    var sourceStop = entry.getStop();
                    var projectedStop =
                            sourceStop == null
                                    ? null
                                    : projectedStops.stream()
                                            .filter(
                                                    stop ->
                                                            Objects.equals(
                                                                    stop.getSourceStopId(),
                                                                    sourceStop.getStopId()))
                                            .findFirst()
                                            .orElse(null);
                    Instant limit =
                            projectedStop == null
                                    ? null
                                    : projectedStop.getAllAboardTime() != null
                                                    && projectedStop
                                                            .getAllAboardTime()
                                                            .isBefore(
                                                                    projectedStop
                                                                            .getPlannedDeparture())
                                            ? projectedStop.getAllAboardTime()
                                            : projectedStop.getPlannedDeparture();
                    if (projectedStop == null
                            || startInst.isBefore(projectedStop.getPlannedArrival())
                            || endInst.isAfter(limit))
                        throw new BusinessException(
                                org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                                "SOURCE_EXCURSION_OUTSIDE_PORT_CALL",
                                "Tour trong lịch trình nguồn nằm ngoài lần ghé cảng hoặc vượt hạn"
                                    + " trở lại tàu: "
                                        + entry.getTitle(),
                                Map.of(
                                        "name",
                                        Objects.toString(entry.getTitle(), ""),
                                        "entryId",
                                        entry.getEntryId()));
                }
            }

            Integer stopOrder = entry.getStop() != null ? entry.getStop().getStopOrder() : null;

            CruiseSchedulePreviewResponse.ProjectedEntryDto entryDto =
                    CruiseSchedulePreviewResponse.ProjectedEntryDto.builder()
                            .sourceEntryId(entry.getEntryId())
                            .title(entry.getTitle())
                            .timeZoneId(
                                    entry.getKind() == TripPlanEntryKind.SHORE_EXCURSION
                                                    && entry.getStop() != null
                                                    && entry.getStop().getPort() != null
                                            ? (entry.getStop().getPortFacts() != null
                                                    ? entry.getStop().getPortFacts().timeZoneId()
                                                    : entry.getStop().getPort().getTimeZoneId())
                                            : PortTimeZoneHelper.SHIP_TIME_ZONE.getId())
                            .locationText(entry.getLocationText())
                            .meetingPoint(entry.getMeetingPoint())
                            .notes(entry.getNotes())
                            .clientKey(entry.getClientKey())
                            .kind(entry.getKind())
                            .category(entry.getCategory())
                            .sourceActivityId(entry.getSourceActivityId())
                            .sourceExcursionId(entry.getSourceExcursionId())
                            .plannedStartTime(startInst)
                            .plannedEndTime(endInst)
                            .stopOrder(stopOrder)
                            .build();
            dayEntriesMap.computeIfAbsent(startDay, k -> new ArrayList<>()).add(entryDto);
        }

        int projectedDayCount =
                Math.toIntExact(
                        java.time.temporal.ChronoUnit.DAYS.between(
                                        day1Date, projectedReturn.atZone(shipZone).toLocalDate())
                                + 1);
        if (projectedDayCount != totalDays) {
            throw new BusinessException(
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                    "SOURCE_DURATION_INVALID",
                    "Ngày kết thúc theo giờ tàu không khớp số ngày của nguồn; cần đối soát lịch"
                        + " trước khi tạo chuyến.");
        }
        List<CruiseSchedulePreviewResponse.ProjectedDayDto> projectedDays = new ArrayList<>();
        for (int d = 1; d <= totalDays; d++) {
            final int dayIndex = d;
            TripPlanDay planDay =
                    planDays.stream()
                            .filter(
                                    pd ->
                                            pd.getDayNumber() != null
                                                    && pd.getDayNumber() == dayIndex)
                            .findFirst()
                            .orElse(null);

            LocalDate calDate = day1Date.plusDays(d - 1);
            ItineraryDayType dayType =
                    planDay != null && planDay.getDayType() != null
                            ? planDay.getDayType()
                            : (d == 1
                                    ? ItineraryDayType.PORT_DAY
                                    : (d == totalDays
                                            ? ItineraryDayType.PORT_DAY
                                            : ItineraryDayType.SEA_DAY));

            String title = planDay != null ? planDay.getTitle() : "Ngày " + d;

            projectedDays.add(
                    CruiseSchedulePreviewResponse.ProjectedDayDto.builder()
                            .dayNumber(d)
                            .calendarDate(calDate)
                            .dayType(dayType)
                            .dayRole(ItineraryDayRole.forDay(d, totalDays))
                            .title(title)
                            .description(planDay == null ? null : planDay.getDescription())
                            .shipTimeZoneId(shipZone.getId())
                            .stops(dayStopsMap.getOrDefault(d, Collections.emptyList()))
                            .entries(dayEntriesMap.getOrDefault(d, Collections.emptyList()))
                            .build());
        }

        // Lock unique ports in stable order for write transactions, then use current authoritative
        // policy.
        if (!org.springframework.transaction.support.TransactionSynchronizationManager
                .isCurrentTransactionReadOnly()) {
            var lockIds = new java.util.TreeSet<UUID>();
            projectedStops.forEach(call -> lockIds.add(call.getPortId()));
            if (departurePort != null) lockIds.add(departurePort.getPortId());
            lockIds.forEach(id -> portRepository.lockDirectory(id).orElseThrow());
        }
        java.util.List<com.casms.ship.dto.PortWindowContract.Check> portChecks =
                new java.util.ArrayList<>();
        for (var projected : projectedStops) {
            var checks =
                    portWindowEvaluator.evaluate(
                            projected.getPortId(),
                            projected.getPortTimeZoneId(),
                            projected.getPlannedArrival(),
                            projected.getPlannedDeparture());
            projected.setPortWindowChecks(checks);
            portChecks.addAll(checks);
        }
        if (departurePort != null
                && projectedStops.stream()
                        .noneMatch(
                                c ->
                                        c.getPortId().equals(departurePort.getPortId())
                                                && Objects.equals(
                                                        c.getPlannedDeparture(),
                                                        departureDatetime))) {
            var departureFacts =
                    departureDay != null && departureDay.getDeparturePortFacts() != null
                            ? departureDay.getDeparturePortFacts()
                            : com.casms.ship.dto.PortFacts.capture(departurePort);
            portChecks.addAll(
                    portWindowEvaluator.departureMovement(
                            departurePort.getPortId(),
                            departureFacts.timeZoneId(),
                            departureDatetime));
        }
        boolean portPassed =
                !portChecks.isEmpty()
                        && portChecks.stream().allMatch(c -> "PASS".equals(c.status()));
        // Tính toán returnDatetime
        Instant returnDatetime = projectedReturn;

        return CruiseSchedulePreviewResponse.builder()
                .materialized(false)
                .sourceContentHash(version.getContentHash())
                .portCallWindowStatus(portPassed ? "PASS" : "BLOCK")
                .portWindowChecks(portChecks)
                .materializationBlockers(
                        portPassed
                                ? List.of()
                                : portChecks.isEmpty()
                                        ? List.of("WINDOW_NOT_CONFIGURED")
                                        : portChecks.stream()
                                                .filter(c -> !"PASS".equals(c.status()))
                                                .map(
                                                        com.casms.ship.dto.PortWindowContract.Check
                                                                ::code)
                                                .distinct()
                                                .toList())
                .sourceTripId(trip.getTripId())
                .sourceTripCode(trip.getTripCode())
                .sourceTripName(trip.getName())
                .sourceVersionId(version.getVersionId())
                .sourceVersionNumber(version.getVersionNumber())
                .sourceVersionRevision(version.getRevision())
                .departureDatetime(departureDatetime)
                .returnDatetime(returnDatetime)
                .durationDays(trip.getDurationDays())
                .durationNights(trip.getDurationNights())
                .departurePortId(departurePort == null ? null : departurePort.getPortId())
                .departurePortName(
                        departurePort == null
                                ? null
                                : departureDay != null
                                                && departureDay.getDeparturePortFacts() != null
                                        ? departureDay.getDeparturePortFacts().portName()
                                        : firstStop != null && firstStop.getPortFacts() != null
                                                ? firstStop.getPortFacts().portName()
                                                : departurePort.getPortName())
                .departurePortCode(departurePort == null ? null : departurePort.getPortCode())
                .returnPortId(
                        lastStop != null && lastStop.getPort() != null
                                ? lastStop.getPort().getPortId()
                                : null)
                .returnPortName(
                        lastStop == null
                                ? null
                                : lastStop.getPortFacts() != null
                                        ? lastStop.getPortFacts().portName()
                                        : lastStop.getPort() != null
                                                ? lastStop.getPort().getPortName()
                                                : null)
                .returnPortCode(
                        lastStop != null && lastStop.getPort() != null
                                ? lastStop.getPort().getPortCode()
                                : null)
                .days(projectedDays)
                .build();
    }
}
