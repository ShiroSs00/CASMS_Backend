package com.casms.ship.service;

import com.casms.activity.entity.OnboardActivity;
import com.casms.activity.entity.ShoreExcursion;
import com.casms.activity.entity.Supplier;
import com.casms.activity.enums.OnboardActivityCategory;
import com.casms.activity.enums.OnboardActivityStatus;
import com.casms.activity.enums.ShoreExcursionStatus;
import com.casms.activity.enums.SupplierStatus;
import com.casms.activity.enums.SupplierVerificationStatus;
import com.casms.activity.repository.OnboardActivityRepository;
import com.casms.activity.repository.RegistrationRepository;
import com.casms.activity.repository.ShoreExcursionRepository;
import com.casms.activity.repository.SupplierRepository;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.ship.dto.response.OperationalProgramMappingItemResponse;
import com.casms.ship.dto.response.OperationalProgramSyncResponse;
import com.casms.ship.entity.Cabin;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.CruiseStop;
import com.casms.ship.entity.Deck;
import com.casms.ship.entity.Itinerary;
import com.casms.ship.entity.Port;
import com.casms.ship.entity.Ship;
import com.casms.ship.repository.CruiseRepository;
import com.casms.ship.repository.CruiseStopRepository;
import com.casms.ship.repository.ItineraryRepository;
import com.casms.ship.repository.PortRepository;
import com.casms.ship.repository.ShipRepository;
import com.casms.trip.entity.TripPlanEntry;
import com.casms.trip.entity.TripPlanStop;
import com.casms.trip.entity.TripScheduleVersion;
import com.casms.trip.enums.TripPlanEntryKind;
import com.casms.trip.repository.TripPlanEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OperationalProgramMappingService {

    private final CruiseRepository cruiseRepository;
    private final ShipRepository shipRepository;
    private final PortRepository portRepository;
    private final TripPlanEntryRepository tripPlanEntryRepository;
    private final ItineraryRepository itineraryRepository;
    private final CruiseStopRepository cruiseStopRepository;
    private final OnboardActivityRepository onboardActivityRepository;
    private final ShoreExcursionRepository shoreExcursionRepository;
    private final SupplierRepository supplierRepository;
    private final RegistrationRepository registrationRepository;

    @Transactional
    public OperationalProgramSyncResponse syncProgramsForCruise(UUID cruiseId) {
        Cruise cruise = cruiseRepository.findById(cruiseId)
                .orElseThrow(() -> new ResourceNotFoundException("Cruise", cruiseId));

        if (cruise.getSourceVersion() == null) {
            log.info("Cruise {} has no source TripScheduleVersion, skipping program mapping", cruiseId);
            return OperationalProgramSyncResponse.builder()
                    .cruiseId(cruiseId)
                    .onboardActivitiesCount(0)
                    .shoreExcursionsCount(0)
                    .servingWindowsCount(0)
                    .items(new ArrayList<>())
                    .warnings(List.of("Chuyến đi không có phiên bản nguồn (sourceVersion)"))
                    .syncedAt(Instant.now())
                    .build();
        }

        return mapOperationalPrograms(cruise, cruise.getSourceVersion());
    }

    @Transactional
    public OperationalProgramSyncResponse mapOperationalPrograms(Cruise cruise, TripScheduleVersion sourceVersion) {
        List<TripPlanEntry> entries = tripPlanEntryRepository
                .findByVersion_VersionIdOrderByStartDayNumberAscPlannedStartTimeAsc(sourceVersion.getVersionId());

        if (cruise.getDepartureDatetime() == null) {
            log.warn("Cruise {} has no departureDatetime, skipping program mapping", cruise.getCruiseId());
            return OperationalProgramSyncResponse.builder()
                    .cruiseId(cruise.getCruiseId())
                    .onboardActivitiesCount(0)
                    .shoreExcursionsCount(0)
                    .servingWindowsCount(0)
                    .items(new ArrayList<>())
                    .warnings(List.of("Chuyến đi chưa được thiết lập thời điểm khởi hành (departureDatetime)"))
                    .syncedAt(Instant.now())
                    .build();
        }

        List<String> warnings = new ArrayList<>();
        List<OperationalProgramMappingItemResponse> items = new ArrayList<>();
        int activitiesCount = 0;
        int excursionsCount = 0;
        int servingWindowsCount = 0;

        // BE-R05a / D02: Xác định múi giờ cơ sở tàu (Ship Base Timezone) từ cảng khởi hành
        ZoneId shipZone;
        Port departurePort = null;
        if (cruise.getDeparturePort() != null && cruise.getDeparturePort().getPortId() != null) {
            departurePort = portRepository.findById(cruise.getDeparturePort().getPortId()).orElse(null);
        }
        if (departurePort != null && departurePort.getTimeZoneId() != null) {
            shipZone = com.casms.ship.util.PortTimeZoneHelper.resolveZone(departurePort.getTimeZoneId());
        } else {
            // Load itinerary stops to find departure port timezone if cruise.getDeparturePort is not loaded
            Optional<Itinerary> itOpt = itineraryRepository.findByCruiseCruiseId(cruise.getCruiseId());
            List<CruiseStop> tempStops = itOpt.isPresent()
                    ? cruiseStopRepository.findByItineraryItineraryIdOrderByStopOrderAsc(itOpt.get().getItineraryId())
                    : List.of();
            if (!tempStops.isEmpty() && tempStops.get(0).getPort() != null) {
                Port firstStopPort = portRepository.findById(tempStops.get(0).getPort().getPortId()).orElse(null);
                if (firstStopPort != null && firstStopPort.getTimeZoneId() != null) {
                    shipZone = com.casms.ship.util.PortTimeZoneHelper.resolveZone(firstStopPort.getTimeZoneId());
                } else {
                    throw new com.casms.common.exception.BusinessException("MISSING_PORT_TIME_ZONE", "Không xác định được múi giờ cơ sở của tàu từ cảng khởi hành.");
                }
            } else {
                throw new com.casms.common.exception.BusinessException("MISSING_PORT_TIME_ZONE", "Không xác định được múi giờ cơ sở của tàu từ cảng khởi hành.");
            }
        }

        LocalDate baseDate = cruise.getDepartureDatetime().atZone(shipZone).toLocalDate();

        // Resolve ship info safely to avoid LazyInitializationException on detached proxies
        Ship assignedShip = null;
        if (cruise.getShip() != null && cruise.getShip().getShipId() != null) {
            assignedShip = shipRepository.findById(cruise.getShip().getShipId()).orElse(null);
        }
        UUID assignedShipId = assignedShip != null ? assignedShip.getShipId() : null;
        String assignedShipName = assignedShip != null ? assignedShip.getShipName() : "Chưa gán tàu";

        // Load cruise itinerary and materialized stops for shore excursion linking
        Optional<Itinerary> itineraryOpt = itineraryRepository.findByCruiseCruiseId(cruise.getCruiseId());
        List<CruiseStop> stops = itineraryOpt.isPresent()
                ? cruiseStopRepository.findByItineraryItineraryIdOrderByStopOrderAsc(itineraryOpt.get().getItineraryId())
                : List.of();

        for (TripPlanEntry entry : entries) {
            List<String> itemWarnings = new ArrayList<>();
            StringBuilder requiredData = new StringBuilder();

            LocalDate startDate = baseDate.plusDays(Math.max(0, entry.getStartDayNumber() != null ? entry.getStartDayNumber() - 1 : 0));
            LocalDate endDate = baseDate.plusDays(Math.max(0, entry.getEndDayNumber() != null ? entry.getEndDayNumber() - 1 : 0));
            java.time.LocalTime startTimeVal = entry.getPlannedStartTime() != null ? entry.getPlannedStartTime() : java.time.LocalTime.of(8, 0);
            java.time.LocalTime endTimeVal = entry.getPlannedEndTime() != null ? entry.getPlannedEndTime() : startTimeVal.plusHours(2);
            Instant startTime = startDate.atTime(startTimeVal).atZone(shipZone).toInstant();
            Instant endTime = endDate.atTime(endTimeVal).atZone(shipZone).toInstant();

            try {
                // BE-R04 / D03: Tách bạch rõ rệt giữa SERVING_WINDOW, ONBOARD_ACTIVITY, SHORE_EXCURSION
                if (entry.getKind() == TripPlanEntryKind.SERVING_WINDOW) {
                    // 1. Khung phục vụ (SERVING_WINDOW): Thông tin dịch vụ, KHÔNG phải OnboardActivity có thể đặt chỗ
                    servingWindowsCount++;
                    items.add(OperationalProgramMappingItemResponse.builder()
                            .sourceEntryId(entry.getEntryId())
                            .clientKey(entry.getClientKey())
                            .kind(TripPlanEntryKind.SERVING_WINDOW)
                            .title(entry.getTitle())
                            .targetId(entry.getServiceId())
                            .targetCode("SW-" + (entry.getClientKey() != null ? entry.getClientKey() : "WINDOW"))
                            .targetType("SERVING_WINDOW")
                            .status("LINKED")
                            .reservationRequired(false)
                            .reason("Khung phục vụ thông tin tiện ích (không yêu cầu đặt chỗ trước)")
                            .warnings(itemWarnings)
                            .requiredData(null)
                            .build());

                } else if (entry.getKind() == TripPlanEntryKind.ONBOARD_ACTIVITY) {
                    // 2. Hoạt động trên tàu (ONBOARD_ACTIVITY): Có yêu cầu/hỗ trợ đăng ký
                    String activityCode = generateCode("OA", cruise.getCruiseCode(), entry.getClientKey(), entry.getEntryId());

                    // Kiểm tra tính tương thích của boong/phòng thuộc tàu đã gán
                    Deck deck = null;
                    if (entry.getDeck() != null) {
                        UUID deckShipId = entry.getDeck().getShip() != null ? entry.getDeck().getShip().getShipId() : null;
                        if (assignedShipId != null && assignedShipId.equals(deckShipId)) {
                            deck = entry.getDeck();
                        } else {
                            String msg = String.format("Tầng boong '%s' không thuộc con tàu được gán (%s)",
                                    entry.getDeck().getDeckName() != null ? entry.getDeck().getDeckName() : entry.getDeck().getDeckId(),
                                    assignedShipName);
                            itemWarnings.add(msg);
                            warnings.add(msg);
                            requiredData.append("Cần điều chỉnh tầng boong thuộc tàu đã gán; ");
                        }
                    }

                    Cabin cabin = null;
                    if (entry.getCabin() != null) {
                        UUID cabinShipId = entry.getCabin().getShip() != null ? entry.getCabin().getShip().getShipId() : null;
                        if (assignedShipId != null && assignedShipId.equals(cabinShipId)) {
                            cabin = entry.getCabin();
                        } else {
                            String msg = String.format("Phòng/Cabin '%s' không thuộc con tàu được gán (%s)",
                                    entry.getCabin().getCabinNumber() != null ? entry.getCabin().getCabinNumber() : entry.getCabin().getCabinId(),
                                    assignedShipName);
                            itemWarnings.add(msg);
                            warnings.add(msg);
                            requiredData.append("Cần điều chỉnh phòng/cabin thuộc tàu đã gán; ");
                        }
                    }

                    Optional<OnboardActivity> existingOpt = onboardActivityRepository.findByActivityCode(activityCode);
                    UUID activityId;
                    String status;
                    String reason;

                    if (existingOpt.isPresent()) {
                        OnboardActivity existing = existingOpt.get();
                        activityId = existing.getOnboardActivityId();

                        // Kiểm tra xem hoạt động đã có khách đăng ký giữ chỗ hay chưa
                        boolean hasRegistrations = registrationRepository.existsByOnboardActivity_OnboardActivityId(activityId);
                        if (hasRegistrations) {
                            String regMsg = "Hoạt động đã có khách đăng ký giữ chỗ; bảo toàn cấu hình hiện tại, không ghi đè";
                            itemWarnings.add(regMsg);
                            status = "LINKED";
                            reason = "Đã liên kết (bảo toàn cấu hình vì đã có khách đăng ký)";
                        } else {
                            // Cập nhật thông tin thời gian và vị trí một cách an toàn
                            existing.setActivityName(entry.getTitle());
                            existing.setCategory(entry.getCategory() != null ? entry.getCategory() : OnboardActivityCategory.ENTERTAINMENT);
                            existing.setDescription(entry.getNotes());
                            existing.setStartTime(startTime);
                            existing.setEndTime(endTime);
                            existing.setLocation(entry.getLocationText() != null ? entry.getLocationText() :
                                    (deck != null ? "Deck " + deck.getDeckNumber() : "Onboard"));
                            existing.setDeck(deck);
                            existing.setCabin(cabin);
                            onboardActivityRepository.save(existing);
                            status = "LINKED";
                            reason = "Đã cập nhật theo kế hoạch nguồn (Idempotent update)";
                        }
                    } else {
                        OnboardActivity activity = new OnboardActivity();
                        activity.setCruise(cruise);
                        activity.setActivityCode(activityCode);
                        activity.setActivityName(entry.getTitle());
                        activity.setCategory(entry.getCategory() != null ? entry.getCategory() : OnboardActivityCategory.ENTERTAINMENT);
                        activity.setDescription(entry.getNotes());
                        activity.setStartTime(startTime);
                        activity.setEndTime(endTime);
                        activity.setLocation(entry.getLocationText() != null ? entry.getLocationText() :
                                (deck != null ? "Deck " + deck.getDeckNumber() : "Onboard"));
                        activity.setDeck(deck);
                        activity.setCabin(cabin);

                        // Thừa hưởng thuộc tính từ template nếu có
                        if (entry.getSourceActivityId() != null) {
                            Optional<OnboardActivity> srcActOpt = onboardActivityRepository.findById(entry.getSourceActivityId());
                            if (srcActOpt.isPresent()) {
                                OnboardActivity srcAct = srcActOpt.get();
                                activity.setCapacity(srcAct.getCapacity());
                                activity.setPrice(srcAct.getPrice());
                                activity.setCurrencyCode(srcAct.getCurrencyCode());
                            }
                        }

                        if (activity.getCapacity() == null) {
                            activity.setCapacity(30);
                            itemWarnings.add("Chưa có cấu hình sức chứa từ nguồn, tạm đặt định biên tối thiểu 30");
                        }
                        if (activity.getPrice() == null) {
                            activity.setPrice(BigDecimal.ZERO);
                        }
                        if (activity.getCurrencyCode() == null) {
                            activity.setCurrencyCode("VND");
                        }
                        activity.setStatus(OnboardActivityStatus.PLANNED);
                        activity = onboardActivityRepository.save(activity);
                        activityId = activity.getOnboardActivityId();
                        status = "CREATED";
                        reason = "Tạo mới hoạt động trên tàu thành công";
                    }

                    activitiesCount++;
                    items.add(OperationalProgramMappingItemResponse.builder()
                            .sourceEntryId(entry.getEntryId())
                            .clientKey(entry.getClientKey())
                            .kind(TripPlanEntryKind.ONBOARD_ACTIVITY)
                            .title(entry.getTitle())
                            .targetId(activityId)
                            .targetCode(activityCode)
                            .targetType("ONBOARD_ACTIVITY")
                            .status(status)
                            .reservationRequired(true)
                            .reason(reason)
                            .warnings(itemWarnings)
                            .requiredData(requiredData.length() > 0 ? requiredData.toString().trim() : null)
                            .build());

                } else if (entry.getKind() == TripPlanEntryKind.SHORE_EXCURSION) {
                    // 3. Tour tham quan bờ (SHORE_EXCURSION): Bắt buộc tìm đúng lần ghé cảng, không fallback
                    TripPlanStop entryStop = entry.getStop();
                    CruiseStop matchedStop = null;

                    if (entryStop != null) {
                        // Ưu tiên 1: Tìm theo sourceStopId trong notes của CruiseStop
                        String sourceTag = "sourceStopId:" + entryStop.getStopId();
                        matchedStop = stops.stream()
                                .filter(s -> s.getNotes() != null && s.getNotes().contains(sourceTag))
                                .findFirst()
                                .orElse(null);

                        // Ưu tiên 2: Tìm theo stopOrder và PortId
                        if (matchedStop == null && entryStop.getPort() != null) {
                            matchedStop = stops.stream()
                                    .filter(s -> s.getStopOrder() != null && s.getStopOrder().equals(entryStop.getStopOrder())
                                            && s.getPort() != null && s.getPort().getPortId().equals(entryStop.getPort().getPortId()))
                                    .findFirst()
                                    .orElse(null);
                        }

                        // Ưu tiên 3: Tìm theo stopOrder
                        if (matchedStop == null && entryStop.getStopOrder() != null) {
                            matchedStop = stops.stream()
                                    .filter(s -> s.getStopOrder() != null && s.getStopOrder().equals(entryStop.getStopOrder()))
                                    .findFirst()
                                    .orElse(null);
                        }
                    }

                    // BE-R04: Không tìm đúng lần ghé thì báo lỗi/chưa link; TUYỆT ĐỐI không fallback về stops.get(0)
                    if (matchedStop == null) {
                        String stopErrMsg = "Không tìm thấy lần ghé cảng tương ứng cho tour bờ: " + entry.getTitle();
                        itemWarnings.add(stopErrMsg);
                        warnings.add(stopErrMsg);

                        items.add(OperationalProgramMappingItemResponse.builder()
                                .sourceEntryId(entry.getEntryId())
                                .clientKey(entry.getClientKey())
                                .kind(TripPlanEntryKind.SHORE_EXCURSION)
                                .title(entry.getTitle())
                                .targetId(null)
                                .targetCode(null)
                                .targetType("SHORE_EXCURSION")
                                .status("FAILED")
                                .reservationRequired(true)
                                .reason("Không tìm thấy lần ghé cảng tương ứng (Stop không khớp hoặc null)")
                                .warnings(itemWarnings)
                                .requiredData("Cần liên kết đúng lần ghé cảng trong hải trình")
                                .build());
                        continue;
                    }

                    // BE-R05a: Quy đổi thời gian tour bờ sang múi giờ cảng thực tế (Port Time)
                    if (matchedStop.getPort() != null && matchedStop.getPort().getPortId() != null) {
                        Port stopPort = portRepository.findById(matchedStop.getPort().getPortId()).orElse(null);
                        if (stopPort != null && stopPort.getTimeZoneId() != null) {
                            ZoneId excursionZone = com.casms.ship.util.PortTimeZoneHelper.resolveZone(stopPort.getTimeZoneId());
                            startTime = startDate.atTime(startTimeVal).atZone(excursionZone).toInstant();
                            endTime = endDate.atTime(endTimeVal).atZone(excursionZone).toInstant();
                        }
                    }

                    // Kiểm tra cửa sổ tour bờ so với khung giờ tàu cập cảng (Port Call Window)
                    if (matchedStop.getPlannedArrival() != null && matchedStop.getPlannedDeparture() != null) {
                        if (startTime.isBefore(matchedStop.getPlannedArrival()) || endTime.isAfter(matchedStop.getPlannedDeparture())) {
                            String winMsg = String.format("Thời gian tour bờ (%s - %s) nằm ngoài khung giờ tàu cập cảng (%s - %s)",
                                    startTime, endTime, matchedStop.getPlannedArrival(), matchedStop.getPlannedDeparture());
                            itemWarnings.add(winMsg);
                            warnings.add(winMsg);
                            requiredData.append("Thời gian tour ngoài khung giờ cảng; ");
                        }
                    }

                    // Kế thừa thông tin từ Tour mẫu nguồn (nếu có)
                    Supplier supplier = null;
                    Integer minParticipants = null;
                    Integer maxCapacity = null;
                    BigDecimal price = null;
                    String currencyCode = null;
                    String meetingPoint = entry.getMeetingPoint();

                    if (entry.getSourceExcursionId() != null) {
                        Optional<ShoreExcursion> templateOpt = shoreExcursionRepository.findById(entry.getSourceExcursionId());
                        if (templateOpt.isPresent()) {
                            ShoreExcursion template = templateOpt.get();
                            supplier = template.getSupplier();
                            minParticipants = template.getMinimumParticipants();
                            maxCapacity = template.getMaximumCapacity();
                            price = template.getPrice();
                            currencyCode = template.getCurrencyCode();
                            if (meetingPoint == null || meetingPoint.isBlank()) {
                                meetingPoint = template.getMeetingPoint();
                            }
                        } else {
                            itemWarnings.add("Không tìm thấy tour mẫu nguồn (sourceExcursionId: " + entry.getSourceExcursionId() + ")");
                        }
                    }

                    String excursionCode = generateCode("EX", cruise.getCruiseCode(), entry.getClientKey(), entry.getEntryId());

                    // BE-R04: Không chọn supplier ACTIVE đầu tiên hoặc tự tạo VERIFIED; không tự đặt giá/sức chứa ảo
                    if (supplier == null) {
                        String supMsg = "Chưa có nhà cung cấp (Supplier) được phân công cho tour bờ: " + entry.getTitle();
                        itemWarnings.add(supMsg);
                        warnings.add(supMsg);
                        items.add(OperationalProgramMappingItemResponse.builder()
                                .sourceEntryId(entry.getEntryId())
                                .clientKey(entry.getClientKey())
                                .kind(TripPlanEntryKind.SHORE_EXCURSION)
                                .title(entry.getTitle())
                                .targetId(null)
                                .targetCode(excursionCode)
                                .targetType("SHORE_EXCURSION")
                                .status("FAILED")
                                .reservationRequired(true)
                                .reason("Thiếu nhà cung cấp (Supplier) - Không tạo bản ghi với dữ liệu giả")
                                .warnings(itemWarnings)
                                .requiredData("Cần phân công Nhà cung cấp (Supplier)")
                                .build());
                        continue;
                    } else if (supplier.getStatus() != SupplierStatus.ACTIVE || supplier.getVerificationStatus() != SupplierVerificationStatus.VERIFIED) {
                        String supMsg = String.format("Nhà cung cấp '%s' chưa sẵn sàng (Status=%s, Verification=%s)",
                                supplier.getSupplierName(), supplier.getStatus(), supplier.getVerificationStatus());
                        itemWarnings.add(supMsg);
                        requiredData.append("Nhà cung cấp chưa VERIFIED; ");
                    }

                    if (maxCapacity == null || maxCapacity <= 0) {
                        itemWarnings.add("Chưa cấu hình sức chứa tối đa cho tour bờ");
                        requiredData.append("Cần cấu hình sức chứa tối đa; ");
                        maxCapacity = 0; // Để ghi DB nhưng ghi nhận lỗi readiness
                    }
                    Optional<ShoreExcursion> existingOpt = shoreExcursionRepository.findByExcursionCode(excursionCode);
                    UUID excursionId;
                    String status;
                    String reason;

                    if (existingOpt.isPresent()) {
                        ShoreExcursion existing = existingOpt.get();
                        excursionId = existing.getShoreExcursionId();

                        // Kiểm tra xem tour bờ đã có khách đăng ký giữ chỗ hay chưa
                        boolean hasRegistrations = registrationRepository.existsByShoreExcursion_ShoreExcursionId(excursionId);
                        if (hasRegistrations) {
                            String regMsg = "Tour bờ đã có khách đăng ký giữ chỗ; bảo toàn cấu hình hiện tại, không ghi đè";
                            itemWarnings.add(regMsg);
                            status = "LINKED";
                            reason = "Đã liên kết (bảo toàn cấu hình vì đã có khách đăng ký)";
                        } else {
                            existing.setCruiseStop(matchedStop);
                            existing.setPort(matchedStop.getPort());
                            existing.setExcursionName(entry.getTitle());
                            existing.setStartTime(startTime);
                            existing.setPlannedReturnTime(endTime);
                            existing.setMeetingPoint(meetingPoint != null ? meetingPoint : "Port Terminal Gate");
                            if (supplier != null) {
                                existing.setSupplier(supplier);
                            }
                            shoreExcursionRepository.save(existing);
                            status = "LINKED";
                            reason = "Đã cập nhật theo kế hoạch nguồn (Idempotent update)";
                        }
                    } else {
                        ShoreExcursion excursion = new ShoreExcursion();
                        excursion.setCruiseStop(matchedStop);
                        excursion.setPort(matchedStop.getPort());
                        excursion.setExcursionCode(excursionCode);
                        excursion.setExcursionName(entry.getTitle());
                        excursion.setStartTime(startTime);
                        excursion.setPlannedReturnTime(endTime);
                        excursion.setMeetingPoint(meetingPoint != null ? meetingPoint : "Port Terminal Gate");
                        excursion.setSupplier(supplier);
                        excursion.setMinimumParticipants(minParticipants != null ? minParticipants : 1);
                        excursion.setMaximumCapacity(maxCapacity);
                        excursion.setPrice(price != null ? price : BigDecimal.ZERO);
                        excursion.setCurrencyCode(currencyCode != null ? currencyCode : "USD");
                        excursion.setStatus(ShoreExcursionStatus.PLANNED);

                        excursion = shoreExcursionRepository.save(excursion);
                        excursionId = excursion.getShoreExcursionId();
                        status = "CREATED";
                        reason = "Tạo mới tour tham quan bờ thành công";
                    }

                    excursionsCount++;
                    items.add(OperationalProgramMappingItemResponse.builder()
                            .sourceEntryId(entry.getEntryId())
                            .clientKey(entry.getClientKey())
                            .kind(TripPlanEntryKind.SHORE_EXCURSION)
                            .title(entry.getTitle())
                            .targetId(excursionId)
                            .targetCode(excursionCode)
                            .targetType("SHORE_EXCURSION")
                            .status(status)
                            .reservationRequired(true)
                            .reason(reason)
                            .warnings(itemWarnings)
                            .requiredData(requiredData.length() > 0 ? requiredData.toString().trim() : null)
                            .build());
                }

            } catch (Exception ex) {
                log.error("Error mapping trip plan entry {} (clientKey={}): {}",
                        entry.getEntryId(), entry.getClientKey(), ex.getMessage(), ex);
                String err = "Lỗi khi xử lý mục " + entry.getTitle() + ": " + ex.getMessage();
                warnings.add(err);
                items.add(OperationalProgramMappingItemResponse.builder()
                        .sourceEntryId(entry.getEntryId())
                        .clientKey(entry.getClientKey())
                        .kind(entry.getKind())
                        .title(entry.getTitle())
                        .status("FAILED")
                        .reason(err)
                        .warnings(List.of(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()))
                        .build());
            }
        }

        log.info("Mapped operational programs for cruise {}: {} activities, {} excursions, {} serving windows (Total items: {})",
                cruise.getCruiseId(), activitiesCount, excursionsCount, servingWindowsCount, items.size());

        return OperationalProgramSyncResponse.builder()
                .cruiseId(cruise.getCruiseId())
                .onboardActivitiesCount(activitiesCount)
                .shoreExcursionsCount(excursionsCount)
                .servingWindowsCount(servingWindowsCount)
                .items(items)
                .warnings(warnings)
                .syncedAt(Instant.now())
                .build();
    }

    private String generateCode(String prefix, String cruiseCode, String clientKey, UUID entryId) {
        String safeCruise = (cruiseCode != null && !cruiseCode.isBlank()) ? cruiseCode : "CRUISE";
        String safeKey = (clientKey != null && !clientKey.isBlank()) ? clientKey.replaceAll("[^a-zA-Z0-9_-]", "") : "";
        String base = prefix + "-" + safeCruise + "-" + safeKey;
        if (base.length() <= 50) {
            return base;
        }
        String hash = Integer.toHexString(Objects.hash(safeCruise, clientKey, entryId));
        int maxPrefixLen = 50 - 1 - hash.length();
        String truncated = base.substring(0, Math.min(base.length(), maxPrefixLen));
        return truncated + "-" + hash;
    }
}

