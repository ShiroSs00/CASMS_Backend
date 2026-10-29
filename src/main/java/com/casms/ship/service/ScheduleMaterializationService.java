package com.casms.ship.service;

import com.casms.activity.entity.OnboardActivity;
import com.casms.activity.entity.ShoreExcursion;
import com.casms.activity.repository.OnboardActivityRepository;
import com.casms.activity.repository.ShoreExcursionRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.ship.dto.request.CruiseSchedulePreviewRequest;
import com.casms.ship.dto.response.CruiseSchedulePreviewResponse;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.CruiseStop;
import com.casms.ship.entity.Itinerary;
import com.casms.ship.entity.ItineraryDay;
import com.casms.ship.entity.Port;
import com.casms.ship.enums.CruiseStopArrivalMethod;
import com.casms.ship.enums.CruiseStopCallType;
import com.casms.ship.enums.CruiseStopStatus;
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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Domain Service chuyển đổi lịch trình tương đối của TripScheduleVersion
 * thành lịch trình thực tế cho chuyến khởi hành Cruise (BE-03 / C02, C03, C04).
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

    /**
     * Preview (C02): Tính toán xem trước toàn bộ lịch trình thực tế mà không commit database.
     */
    @Transactional(readOnly = true)
    public CruiseSchedulePreviewResponse previewSchedule(UUID sourceVersionId, Instant departureDatetime) {
        if (sourceVersionId == null) {
            throw new BusinessException("SOURCE_VERSION_REQUIRED", "sourceVersionId là bắt buộc khi xem trước lịch trình");
        }
        if (departureDatetime == null) {
            throw new BusinessException("DEPARTURE_DATETIME_REQUIRED", "departureDatetime là bắt buộc khi xem trước lịch trình");
        }

        TripScheduleVersion version = tripScheduleVersionRepository.findById(sourceVersionId)
                .orElseThrow(() -> new ResourceNotFoundException("TripScheduleVersion", sourceVersionId));

        if (version.getStatus() != TripScheduleVersionStatus.PUBLISHED) {
            throw new BusinessException("VERSION_NOT_PUBLISHED",
                    "Chỉ có thể tạo hoặc xem trước lịch trình từ phiên bản TripScheduleVersion đã PUBLISHED (hiện tại: " + version.getStatus() + ")");
        }

        Trip trip = version.getTrip();
        if (trip == null || trip.getStatus() == TripStatus.ARCHIVED) {
            throw new BusinessException("TRIP_ARCHIVED", "Trip nguồn đã bị ARCHIVED hoặc không tồn tại.");
        }

        return computeProjectedSchedule(version, departureDatetime);
    }

    /**
     * Materialize (C03): Khởi tạo và lưu toàn bộ Itinerary, ItineraryDay, CruiseStop thực tế
     * vào database cho Cruise trong cùng transaction.
     */
    @Transactional
    public Itinerary materializeSchedule(Cruise cruise, TripScheduleVersion version) {
        log.info("Materializing relative schedule from version {} into cruise {}",
                version.getVersionId(), cruise.getCruiseId());

        if (version.getStatus() != TripScheduleVersionStatus.PUBLISHED) {
            throw new BusinessException("VERSION_NOT_PUBLISHED",
                    "Chỉ có thể áp dụng lịch trình từ phiên bản TripScheduleVersion đã PUBLISHED (hiện tại: " + version.getStatus() + ")");
        }

        Trip trip = version.getTrip();
        if (trip == null || trip.getStatus() == TripStatus.ARCHIVED) {
            throw new BusinessException("TRIP_ARCHIVED", "Trip nguồn đã bị ARCHIVED hoặc không tồn tại.");
        }

        // 1. Tính toán trước toàn bộ lịch
        CruiseSchedulePreviewResponse projection = computeProjectedSchedule(version, cruise.getDepartureDatetime());

        // 2. Cập nhật ngày kết thúc chuyến đi và khóa sourceVersionRevision
        if (projection.getReturnDatetime() != null) {
            cruise.setReturnDatetime(projection.getReturnDatetime());
        }
        if (version.getRevision() != null) {
            cruise.setSourceVersionRevision(version.getRevision());
        }
        cruiseRepository.save(cruise);

        // 3. Xóa Itinerary cũ nếu có dở dang (idempotent overwrite draft)
        if (itineraryRepository.findByCruiseCruiseId(cruise.getCruiseId()).isPresent()) {
            throw new BusinessException("ITINERARY_ALREADY_MATERIALIZED",
                    "Existing cruise itinerary must not be overwritten by source materialization");
        }

        // 4. Tạo Itinerary mới
        LocalDate startDate = ItineraryCalendar.start(cruise);
        LocalDate endDate = ItineraryCalendar.end(cruise);

        Itinerary itinerary = new Itinerary();
        itinerary.setCruise(cruise);
        itinerary.setItineraryName(cruise.getCruiseName());
        itinerary.setStartDate(startDate);
        itinerary.setEndDate(endDate);
        itinerary.setItineraryType(trip.getTripType() != null ? trip.getTripType() : ItineraryType.ROUND_TRIP);
        itinerary.setStatus(ItineraryStatus.DRAFT);
        // BE-02: Kế thừa phê duyệt từ TripScheduleVersion PUBLISHED nếu nội dung không đổi
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
            day.setDayType(dayDto.getDayType() != null ? dayDto.getDayType() : ItineraryDayType.PORT_DAY);
            day.setShipTimeZoneId(dayDto.getShipTimeZoneId());
            day.setDescription(dayDto.getTitle());
            day = itineraryDayRepository.save(day);
            dayMap.put(day.getDayNumber(), day);
        }

        // 6. Tạo CruiseStop
        List<TripPlanStop> planStops = version.getStops() != null ? new ArrayList<>(version.getStops()) : new ArrayList<>();
        planStops.sort(Comparator.comparing(TripPlanStop::getStopOrder, Comparator.nullsLast(Comparator.naturalOrder())));

        Map<UUID, TripPlanStop> planStopMap = new HashMap<>();
        for (TripPlanStop ps : planStops) {
            planStopMap.put(ps.getStopId(), ps);
        }

        for (CruiseSchedulePreviewResponse.ProjectedDayDto dayDto : projection.getDays()) {
            if (dayDto.getStops() == null) continue;
            for (CruiseSchedulePreviewResponse.ProjectedStopDto stopDto : dayDto.getStops()) {
                TripPlanStop sourceStop = planStopMap.get(stopDto.getSourceStopId());
                int arrDayNum = sourceStop != null && sourceStop.getArrivalDayNumber() != null ? sourceStop.getArrivalDayNumber() : dayDto.getDayNumber();
                int depDayNum = sourceStop != null && sourceStop.getDepartureDayNumber() != null ? sourceStop.getDepartureDayNumber() : arrDayNum;

                ItineraryDay arrDay = dayMap.get(arrDayNum);
                ItineraryDay depDay = dayMap.get(depDayNum);
                if (arrDay == null && !dayMap.isEmpty()) arrDay = dayMap.values().iterator().next();
                if (depDay == null) depDay = arrDay;

                Port port = sourceStop != null ? sourceStop.getPort() : null;
                if (port == null && stopDto.getPortId() != null) {
                    port = portRepository.findById(stopDto.getPortId()).orElse(null);
                }

                CruiseStop stop = new CruiseStop();
                stop.setItinerary(itinerary);
                stop.setArrivalItineraryDay(arrDay);
                stop.setDepartureItineraryDay(depDay);
                stop.setPort(port);
                stop.setDestination(sourceStop != null ? sourceStop.getDestination() : null);
                stop.setStopOrder(stopDto.getStopOrder());
                stop.setPlannedArrival(stopDto.getPlannedArrival());
                stop.setPlannedDeparture(stopDto.getPlannedDeparture());
                stop.setAllAboardTime(stopDto.getAllAboardTime());
                stop.setCallType(stopDto.getCallType() != null ? stopDto.getCallType() : CruiseStopCallType.NORMAL);
                stop.setArrivalMethod(stopDto.getArrivalMethod() != null ? stopDto.getArrivalMethod() : CruiseStopArrivalMethod.BERTH);
                stop.setTenderTransferMinutes(stopDto.getTenderTransferMinutes());
                stop.setStatus(CruiseStopStatus.PLANNED);
                if (sourceStop != null) {
                    stop.setNotes("sourceStopId:" + sourceStop.getStopId());
                }
                cruiseStopRepository.save(stop);
            }
        }

        // Map operational programs (onboard activities and shore excursions) from source entries
        try {
            operationalProgramMappingService.mapOperationalPrograms(cruise, version);
        } catch (Exception ex) {
            log.warn("Failed to map operational programs for cruise {}: {}", cruise.getCruiseId(), ex.getMessage());
        }

        log.info("Successfully materialized itinerary {} with {} days and stops for cruise {}",
                itinerary.getItineraryId(), dayMap.size(), cruise.getCruiseCode());
        return itinerary;
    }

    /**
     * Đọc lại lịch trình thực tế đã materialized của Cruise (C04).
     */
    @Transactional(readOnly = true)
    public CruiseSchedulePreviewResponse getCruiseSchedule(UUID cruiseId) {
        Cruise cruise = cruiseRepository.findById(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));

        Itinerary itinerary = itineraryRepository.findByCruiseCruiseId(cruiseId).orElse(null);
        if (itinerary == null) {
            return CruiseSchedulePreviewResponse.builder()
                    .itineraryId(null)
                    .itineraryStatus(null)
                    .materialized(false)
                    .sourceTripId(cruise.getSourceTrip() != null ? cruise.getSourceTrip().getTripId() : null)
                    .sourceTripCode(cruise.getSourceTrip() != null ? cruise.getSourceTrip().getTripCode() : null)
                    .sourceTripName(cruise.getSourceTrip() != null ? cruise.getSourceTrip().getName() : null)
                    .sourceVersionId(cruise.getSourceVersion() != null ? cruise.getSourceVersion().getVersionId() : null)
                    .sourceVersionNumber(cruise.getSourceVersion() != null ? cruise.getSourceVersion().getVersionNumber() : null)
                    .sourceVersionRevision(cruise.getSourceVersion() != null ? cruise.getSourceVersion().getRevision() : null)
                    .departureDatetime(cruise.getDepartureDatetime())
                    .returnDatetime(cruise.getReturnDatetime())
                    .departurePortId(cruise.getDeparturePort() != null ? cruise.getDeparturePort().getPortId() : null)
                    .departurePortName(cruise.getDeparturePort() != null ? cruise.getDeparturePort().getPortName() : null)
                    .departurePortCode(cruise.getDeparturePort() != null ? cruise.getDeparturePort().getPortCode() : null)
                    .returnPortId(cruise.getReturnPort() != null ? cruise.getReturnPort().getPortId() : null)
                    .returnPortName(cruise.getReturnPort() != null ? cruise.getReturnPort().getPortName() : null)
                    .returnPortCode(cruise.getReturnPort() != null ? cruise.getReturnPort().getPortCode() : null)
                    .days(Collections.emptyList())
                    .warnings(List.of("Chuyến đi chưa được khởi tạo lịch trình thực tế (Itinerary chưa tồn tại)"))
                    .build();
        }

        List<ItineraryDay> days = itineraryDayRepository.findByItineraryItineraryIdOrderByDayNumberAsc(itinerary.getItineraryId());
        List<CruiseStop> stops = cruiseStopRepository.findByItineraryItineraryIdOrderByStopOrderAsc(itinerary.getItineraryId());

        Map<UUID, List<CruiseStop>> dayStopsMap = new HashMap<>();
        for (CruiseStop stop : stops) {
            UUID dayId = stop.getArrivalItineraryDay() != null ? stop.getArrivalItineraryDay().getItineraryDayId() : null;
            if (dayId != null) {
                dayStopsMap.computeIfAbsent(dayId, k -> new ArrayList<>()).add(stop);
            }
        }

        List<OnboardActivity> activities = onboardActivityRepository.findByCruise_CruiseId(cruiseId);
        List<ShoreExcursion> excursions = shoreExcursionRepository.findByCruiseStop_Itinerary_Cruise_CruiseId(cruiseId);

        List<CruiseSchedulePreviewResponse.ProjectedDayDto> dayDtos = new ArrayList<>();
        for (ItineraryDay d : days) {
            List<CruiseStop> dayStops = dayStopsMap.getOrDefault(d.getItineraryDayId(), Collections.emptyList());
            List<CruiseSchedulePreviewResponse.ProjectedStopDto> stopDtos = dayStops.stream().map(s -> {
                Port p = s.getPort();
                UUID sourceStopId = null;
                if (s.getNotes() != null && s.getNotes().startsWith("sourceStopId:")) {
                    try {
                        sourceStopId = UUID.fromString(s.getNotes().substring("sourceStopId:".length()));
                    } catch (Exception ignored) {}
                }
                return CruiseSchedulePreviewResponse.ProjectedStopDto.builder()
                        .cruiseStopId(s.getCruiseStopId())
                        .sourceStopId(sourceStopId)
                        .stopOrder(s.getStopOrder())
                        .portId(p != null ? p.getPortId() : null)
                        .portCode(p != null ? p.getPortCode() : null)
                        .portName(p != null ? p.getPortName() : null)
                        .countryCode(p != null ? p.getCountryCode() : null)
                        .portTimeZoneId(p != null ? p.getTimeZoneId() : null)
                        .plannedArrival(s.getPlannedArrival())
                        .plannedDeparture(s.getPlannedDeparture())
                        .allAboardTime(s.getAllAboardTime())
                        .callType(s.getCallType())
                        .arrivalMethod(s.getArrivalMethod())
                        .tenderTransferMinutes(s.getTenderTransferMinutes())
                        .build();
            }).toList();

            ZoneId dayZone = d.getShipTimeZoneId() != null
                    ? PortTimeZoneHelper.resolveZone(d.getShipTimeZoneId())
                    : ZoneOffset.UTC;

            List<CruiseSchedulePreviewResponse.ProjectedEntryDto> dayEntries = new ArrayList<>();
            for (OnboardActivity act : activities) {
                if (act.getStartTime() != null) {
                    LocalDate actDate = act.getStartTime().atZone(dayZone).toLocalDate();
                    if (Objects.equals(actDate, d.getDate())) {
                        dayEntries.add(CruiseSchedulePreviewResponse.ProjectedEntryDto.builder()
                                .targetId(act.getOnboardActivityId())
                                .targetCode(act.getActivityCode())
                                .kind(TripPlanEntryKind.ONBOARD_ACTIVITY)
                                .category(act.getCategory())
                                .status(act.getStatus() != null ? act.getStatus().name() : null)
                                .plannedStartTime(act.getStartTime())
                                .plannedEndTime(act.getEndTime())
                                .build());
                    }
                }
            }

            for (ShoreExcursion exc : excursions) {
                if (exc.getStartTime() != null) {
                    ZoneId excZone = (exc.getPort() != null && exc.getPort().getTimeZoneId() != null)
                            ? PortTimeZoneHelper.resolveZone(exc.getPort().getTimeZoneId())
                            : dayZone;
                    LocalDate excDate = exc.getStartTime().atZone(excZone).toLocalDate();
                    if (Objects.equals(excDate, d.getDate())) {
                        dayEntries.add(CruiseSchedulePreviewResponse.ProjectedEntryDto.builder()
                                .targetId(exc.getShoreExcursionId())
                                .targetCode(exc.getExcursionCode())
                                .kind(TripPlanEntryKind.SHORE_EXCURSION)
                                .status(exc.getStatus() != null ? exc.getStatus().name() : null)
                                .plannedStartTime(exc.getStartTime())
                                .plannedEndTime(exc.getPlannedReturnTime())
                                .stopOrder(exc.getCruiseStop() != null ? exc.getCruiseStop().getStopOrder() : null)
                                .build());
                    }
                }
            }

            dayDtos.add(CruiseSchedulePreviewResponse.ProjectedDayDto.builder()
                    .itineraryDayId(d.getItineraryDayId())
                    .dayNumber(d.getDayNumber())
                    .calendarDate(d.getDate())
                    .dayType(d.getDayType())
                    .title(d.getDescription())
                    .shipTimeZoneId(d.getShipTimeZoneId())
                    .stops(stopDtos)
                    .entries(dayEntries)
                    .build());
        }

        Trip sourceTrip = cruise.getSourceTrip();
        TripScheduleVersion sourceVersion = cruise.getSourceVersion();

        return CruiseSchedulePreviewResponse.builder()
                .itineraryId(itinerary.getItineraryId())
                .itineraryStatus(itinerary.getStatus() != null ? itinerary.getStatus().name() : null)
                .materialized(true)
                .sourceTripId(sourceTrip != null ? sourceTrip.getTripId() : null)
                .sourceTripCode(sourceTrip != null ? sourceTrip.getTripCode() : null)
                .sourceTripName(sourceTrip != null ? sourceTrip.getName() : null)
                .sourceVersionId(sourceVersion != null ? sourceVersion.getVersionId() : null)
                .sourceVersionNumber(sourceVersion != null ? sourceVersion.getVersionNumber() : null)
                .sourceVersionRevision(sourceVersion != null ? sourceVersion.getRevision() : null)
                .departureDatetime(cruise.getDepartureDatetime())
                .returnDatetime(cruise.getReturnDatetime())
                .durationDays(sourceTrip != null ? sourceTrip.getDurationDays() : days.size())
                .durationNights(sourceTrip != null ? sourceTrip.getDurationNights() : Math.max(0, days.size() - 1))
                .departurePortId(cruise.getDeparturePort() != null ? cruise.getDeparturePort().getPortId() : null)
                .departurePortName(cruise.getDeparturePort() != null ? cruise.getDeparturePort().getPortName() : null)
                .departurePortCode(cruise.getDeparturePort() != null ? cruise.getDeparturePort().getPortCode() : null)
                .returnPortId(cruise.getReturnPort() != null ? cruise.getReturnPort().getPortId() : null)
                .returnPortName(cruise.getReturnPort() != null ? cruise.getReturnPort().getPortName() : null)
                .returnPortCode(cruise.getReturnPort() != null ? cruise.getReturnPort().getPortCode() : null)
                .days(dayDtos)
                .build();
    }

    /**
     * Thuật toán tính toán chuẩn xác ngày, giờ, múi giờ giữa relative version và actual schedule.
     */
    private CruiseSchedulePreviewResponse computeProjectedSchedule(TripScheduleVersion version, Instant departureDatetime) {
        Trip trip = version.getTrip();
        List<TripPlanDay> planDays = version.getDays() != null ? new ArrayList<>(version.getDays()) : new ArrayList<>();
        planDays.sort(Comparator.comparing(TripPlanDay::getDayNumber, Comparator.nullsLast(Comparator.naturalOrder())));

        List<TripPlanStop> planStops = version.getStops() != null ? new ArrayList<>(version.getStops()) : new ArrayList<>();
        planStops.sort(Comparator.comparing(TripPlanStop::getStopOrder, Comparator.nullsLast(Comparator.naturalOrder())));

        List<TripPlanEntry> planEntries = version.getEntries() != null ? new ArrayList<>(version.getEntries()) : new ArrayList<>();

        // Xác định cảng khởi hành để lấy múi giờ gốc của Day 1
        TripPlanStop firstStop = planStops.isEmpty() ? null : planStops.get(0);
        TripPlanStop lastStop = planStops.isEmpty() ? null : planStops.get(planStops.size() - 1);

        if (firstStop == null || firstStop.getPort() == null) {
            throw new BusinessException("MISSING_DEPARTURE_STOP", "Phiên bản lịch trình nguồn không có điểm dừng khởi hành.");
        }

        // BE-04 / G06: Giờ cơ sở tàu (Ship Base Timezone) luôn là Asia/Ho_Chi_Minh
        ZoneId shipZone = PortTimeZoneHelper.SHIP_TIME_ZONE;
        LocalDate day1Date = departureDatetime.atZone(shipZone).toLocalDate();

        int totalDays = trip.getDurationDays() != null ? trip.getDurationDays() : Math.max(1, planDays.size());

        // Validate Country Scope cho tất cả các cảng trong lịch trình
        for (TripPlanStop stop : planStops) {
            if (stop.getPort() != null) {
                CountryScopeValidator.validatePort(stop.getPort(), trip, "Cảng ghé thứ " + stop.getStopOrder());
            }
        }

        // Tính toán các lần cập cảng (Stops)
        List<CruiseSchedulePreviewResponse.ProjectedStopDto> projectedStops = new ArrayList<>();
        for (TripPlanStop stop : planStops) {
            Port port = stop.getPort();
            if (port == null) {
                throw new BusinessException("MISSING_PORT", "Điểm dừng không có thông tin cảng.");
            }
            ZoneId portZone = PortTimeZoneHelper.resolveZone(port.getTimeZoneId());

            int arrDayNum = stop.getArrivalDayNumber() != null ? stop.getArrivalDayNumber() : 1;
            int depDayNum = stop.getDepartureDayNumber() != null ? stop.getDepartureDayNumber() : arrDayNum;

            LocalDate arrDate = day1Date.plusDays(arrDayNum - 1);
            LocalDate depDate = day1Date.plusDays(depDayNum - 1);

            if(stop.getPlannedArrivalTime()==null||stop.getPlannedDepartureTime()==null)
                throw new BusinessException("PORT_CALL_TIME_REQUIRED","Source port-call times required");
            Instant plannedArr = stop.getPlannedArrivalTime() != null
                    ? PortTimeZoneHelper.strictLocalInstant(LocalDateTime.of(arrDate, stop.getPlannedArrivalTime()), portZone)
                    : departureDatetime;

            Instant plannedDep = stop.getPlannedDepartureTime() != null
                    ? PortTimeZoneHelper.strictLocalInstant(LocalDateTime.of(depDate, stop.getPlannedDepartureTime()), portZone)
                    : plannedArr.plusSeconds(3600);

            if (plannedDep.isBefore(plannedArr) || plannedDep.equals(plannedArr)) {
                throw new BusinessException("INVALID_TIME_RANGE",
                        String.format("Lịch ghé cảng %s có giờ rời (%s) không sau giờ cập (%s).",
                                port.getPortName(), plannedDep, plannedArr));
            }

            Instant allAboard = stop.getAllAboardTime() != null
                    ? PortTimeZoneHelper.strictLocalInstant(LocalDateTime.of(day1Date.plusDays((stop.getAllAboardDayNumber() != null ? stop.getAllAboardDayNumber() : depDayNum) - 1), stop.getAllAboardTime()), portZone)
                    : plannedDep;

            if(allAboard.isBefore(plannedArr)||allAboard.isAfter(plannedDep))
                throw new BusinessException("INVALID_ALL_ABOARD_TIME","All-aboard must be inside the actual port-call window");
            CruiseSchedulePreviewResponse.ProjectedStopDto stopDto = CruiseSchedulePreviewResponse.ProjectedStopDto.builder()
                    .sourceStopId(stop.getStopId())
                    .stopOrder(stop.getStopOrder())
                    .portId(port.getPortId())
                    .portCode(port.getPortCode())
                    .portName(port.getPortName())
                    .countryCode(port.getCountryCode())
                    .portTimeZoneId(portZone.getId())
                    .plannedArrival(plannedArr)
                    .plannedDeparture(plannedDep)
                    .allAboardTime(allAboard)
                    .callType(stop.getCallType() != null ? stop.getCallType() : CruiseStopCallType.NORMAL)
                    .arrivalMethod(stop.getArrivalMethod() != null ? stop.getArrivalMethod() : CruiseStopArrivalMethod.BERTH)
                    .tenderTransferMinutes(null)
                    .build();
            projectedStops.add(stopDto);
        }

        // Kiểm tra Invariant: không overlap, di chuyển dương giữa 2 cảng khác nhau, tuần tự thời gian
        for (int i = 0; i < projectedStops.size(); i++) {
            for (int j = i + 1; j < projectedStops.size(); j++) {
                CruiseSchedulePreviewResponse.ProjectedStopDto a = projectedStops.get(i);
                CruiseSchedulePreviewResponse.ProjectedStopDto b = projectedStops.get(j);

                if (a.getPlannedArrival().isBefore(b.getPlannedDeparture()) && b.getPlannedArrival().isBefore(a.getPlannedDeparture())) {
                    throw new BusinessException("PORT_CALL_OVERLAP",
                            String.format("Hải trình có sự chồng lấn giữa lần ghé %s (%s - %s) và lần ghé %s (%s - %s).",
                                    a.getPortName(), a.getPlannedArrival(), a.getPlannedDeparture(),
                                    b.getPortName(), b.getPlannedArrival(), b.getPlannedDeparture()));
                }

                boolean isSamePort = Objects.equals(a.getPortId(), b.getPortId());
                if (!isSamePort && (a.getPlannedDeparture().equals(b.getPlannedArrival()) || b.getPlannedDeparture().equals(a.getPlannedArrival()))) {
                    throw new BusinessException("NON_POSITIVE_TRANSIT_TIME",
                            String.format("Thời gian di chuyển giữa hai cảng khác nhau (%s và %s) phải là số dương.",
                                    a.getPortName(), b.getPortName()));
                }

                if (a.getStopOrder() != null && b.getStopOrder() != null) {
                    if (a.getStopOrder() < b.getStopOrder() && !a.getPlannedDeparture().isBefore(b.getPlannedArrival())) {
                        throw new BusinessException("INVALID_STOP_SEQUENCE_TIME",
                                String.format("Lần ghé thứ %d (%s) phải kết thúc trước khi lần ghé thứ %d (%s) bắt đầu.",
                                        a.getStopOrder(), a.getPortName(), b.getStopOrder(), b.getPortName()));
                    }
                }
            }
        }

        // Phân bổ stops và entries theo từng ngày
        Map<Integer, List<CruiseSchedulePreviewResponse.ProjectedStopDto>> dayStopsMap = new HashMap<>();
        for (CruiseSchedulePreviewResponse.ProjectedStopDto s : projectedStops) {
            TripPlanStop sourceStop = planStops.stream().filter(ps -> ps.getStopId().equals(s.getSourceStopId())).findFirst().orElse(null);
            int dayNum = sourceStop != null && sourceStop.getArrivalDayNumber() != null ? sourceStop.getArrivalDayNumber() : 1;
            dayStopsMap.computeIfAbsent(dayNum, k -> new ArrayList<>()).add(s);
        }

        Map<Integer, List<CruiseSchedulePreviewResponse.ProjectedEntryDto>> dayEntriesMap = new HashMap<>();
        for (TripPlanEntry entry : planEntries) {
            int startDay = entry.getStartDayNumber() != null ? entry.getStartDayNumber() : 1;
            int endDay = entry.getEndDayNumber() != null ? entry.getEndDayNumber() : startDay;

            LocalDate startCal = day1Date.plusDays(startDay - 1);
            LocalDate endCal = day1Date.plusDays(endDay - 1);

            ZoneId entryZone;
            if (entry.getKind() == TripPlanEntryKind.SHORE_EXCURSION) {
                TripPlanStop stop = entry.getStop();
                if (stop != null && stop.getPort() != null && stop.getPort().getTimeZoneId() != null) {
                    entryZone = PortTimeZoneHelper.resolveZone(stop.getPort().getTimeZoneId());
                } else {
                    entryZone = shipZone;
                }
            } else {
                entryZone = shipZone;
            }

            Instant startInst = entry.getPlannedStartTime() != null
                    ? PortTimeZoneHelper.strictLocalInstant(LocalDateTime.of(startCal, entry.getPlannedStartTime()), entryZone)
                    : null;
            Instant endInst = entry.getPlannedEndTime() != null
                    ? PortTimeZoneHelper.strictLocalInstant(LocalDateTime.of(endCal, entry.getPlannedEndTime()), entryZone)
                    : null;

            Integer stopOrder = entry.getStop() != null ? entry.getStop().getStopOrder() : null;

            CruiseSchedulePreviewResponse.ProjectedEntryDto entryDto = CruiseSchedulePreviewResponse.ProjectedEntryDto.builder()
                    .sourceEntryId(entry.getEntryId())
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

        Instant projectedReturn = projectedStops.getLast().getPlannedDeparture();
        if(!projectedReturn.isAfter(departureDatetime))throw new BusinessException("INVALID_SCHEDULE","Return must be after departure");
        totalDays = Math.toIntExact(java.time.temporal.ChronoUnit.DAYS.between(day1Date,projectedReturn.atZone(shipZone).toLocalDate())+1);
        List<CruiseSchedulePreviewResponse.ProjectedDayDto> projectedDays = new ArrayList<>();
        for (int d = 1; d <= totalDays; d++) {
            final int dayIndex = d;
            TripPlanDay planDay = planDays.stream().filter(pd -> pd.getDayNumber() != null && pd.getDayNumber() == dayIndex).findFirst().orElse(null);

            LocalDate calDate = day1Date.plusDays(d - 1);
            ItineraryDayType dayType = planDay != null && planDay.getDayType() != null
                    ? planDay.getDayType()
                    : (d == 1 ? ItineraryDayType.PORT_DAY : (d == totalDays ? ItineraryDayType.PORT_DAY : ItineraryDayType.SEA_DAY));

            String title = planDay != null ? planDay.getTitle() : "Ngày " + d;

            projectedDays.add(CruiseSchedulePreviewResponse.ProjectedDayDto.builder()
                    .dayNumber(d)
                    .calendarDate(calDate)
                    .dayType(dayType)
                    .title(title)
                    .shipTimeZoneId(shipZone.getId())
                    .stops(dayStopsMap.getOrDefault(d, Collections.emptyList()))
                    .entries(dayEntriesMap.getOrDefault(d, Collections.emptyList()))
                    .build());
        }

        // Tính toán returnDatetime
        Instant returnDatetime = projectedReturn;

        return CruiseSchedulePreviewResponse.builder()
                .materialized(false)
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
                .departurePortId(firstStop.getPort().getPortId())
                .departurePortName(firstStop.getPort().getPortName())
                .departurePortCode(firstStop.getPort().getPortCode())
                .returnPortId(lastStop != null && lastStop.getPort() != null ? lastStop.getPort().getPortId() : null)
                .returnPortName(lastStop != null && lastStop.getPort() != null ? lastStop.getPort().getPortName() : null)
                .returnPortCode(lastStop != null && lastStop.getPort() != null ? lastStop.getPort().getPortCode() : null)
                .days(projectedDays)
                .build();
    }
}
