package com.casms.trip.controller;

import com.casms.activity.entity.OnboardActivity;
import com.casms.activity.enums.OnboardActivityCategory;
import com.casms.activity.enums.OnboardActivityStatus;
import com.casms.activity.repository.OnboardActivityRepository;
import com.casms.activity.repository.ShoreExcursionRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.common.response.ApiResponse;
import com.casms.servicepackage.entity.Service;
import com.casms.servicepackage.repository.ServiceRepository;
import com.casms.ship.entity.Cabin;
import com.casms.ship.entity.Deck;
import com.casms.ship.entity.Port;
import com.casms.ship.enums.PortStatus;
import com.casms.ship.repository.CabinRepository;
import com.casms.ship.repository.DeckRepository;
import com.casms.ship.repository.DestinationRepository;
import com.casms.ship.repository.PortRepository;
import com.casms.trip.dto.response.ActivityCategoryResponse;
import com.casms.trip.dto.response.PlanningSourceActivityResponse;
import com.casms.trip.dto.response.PlanningSourceCabinResponse;
import com.casms.trip.dto.response.PlanningSourceDeckResponse;
import com.casms.trip.dto.response.PlanningSourceExcursionResponse;
import com.casms.trip.dto.response.PlanningSourcePortResponse;
import com.casms.trip.dto.response.PlanningSourceServiceResponse;
import com.casms.trip.dto.response.TripCatalogResponse;
import com.casms.trip.entity.Trip;
import com.casms.trip.repository.TripRepository;
import com.casms.trip.service.CountryScopeValidator;
import com.casms.trip.service.TripGeographyCatalog;
import com.casms.trip.service.VietnamGeographyRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import lombok.Setter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Tag(
        name = "Trip Planning Catalogs",
        description = "Danh mục chuẩn cho phân hệ lập kế hoạch hành trình")
@RestController
@RequestMapping("/trip-planning/catalogs")
@RequiredArgsConstructor
public class TripCatalogController {

    private final DestinationRepository destinationRepository;
    private final OnboardActivityRepository onboardActivityRepository;
    private final ShoreExcursionRepository shoreExcursionRepository;
    private final ServiceRepository serviceRepository;

    @Autowired
    @Setter
    private TripGeographyCatalog geographyCatalog;

    @Autowired(required = false)
    @Setter
    private DeckRepository deckRepository;

    @Autowired(required = false)
    @Setter
    private CabinRepository cabinRepository;

    @Autowired(required = false)
    @Setter
    private PortRepository portRepository;

    @Autowired(required = false)
    @Setter
    private TripRepository tripRepository;

    @Operation(
            summary = "Lấy danh mục lập kế hoạch hành trình",
            description =
                    "Trả về operatingAreas từ server, tripTypes,"
                        + " fleetIntegrationStatus=DEFERRED_EXTERNAL, supported policies,"
                        + " domesticRegions và countries")
    @PreAuthorize("hasAuthority('TRIP_READ')")
    @GetMapping
    public ApiResponse<TripCatalogResponse> getCatalogs() {
        List<String> operatingAreas =
                destinationRepository.findAllActiveDestinations().stream()
                        .map(d -> d.getDestinationName())
                        .distinct()
                        .collect(Collectors.toList());

        List<TripGeographyCatalog.Item> geography = geographyCatalog.list(true);
        List<TripCatalogResponse.RegionCatalogItem> domesticRegions = geography.stream()
                .filter(item -> item.kind() == TripGeographyCatalog.Kind.REGION)
                .map(item -> new TripCatalogResponse.RegionCatalogItem(
                        item.code(), item.label(), item.description()))
                .toList();
        List<TripCatalogResponse.CountryCatalogItem> countries = geography.stream()
                .filter(item -> item.kind() == TripGeographyCatalog.Kind.COUNTRY)
                .map(item -> new TripCatalogResponse.CountryCatalogItem(item.code(), item.label()))
                .toList();

        List<TripCatalogResponse.DurationGroupCatalogItem> durationGroups =
                List.of(
                        new TripCatalogResponse.DurationGroupCatalogItem(
                                "SHORT", "Chuyến ngắn", 1, 2, 1),
                        new TripCatalogResponse.DurationGroupCatalogItem(
                                "LONG", "Chuyến dài ngày", 3, 5, 3));

        TripCatalogResponse response =
                TripCatalogResponse.builder()
                        .operatingAreas(operatingAreas)
                        .tripTypes(List.of("ROUND_TRIP", "ONE_WAY"))
                        .fleetIntegrationStatus("DEFERRED_EXTERNAL")
                        .supportedPolicies(
                                List.of(
                                        "PORT_CALL_BUFFER_30M",
                                        "EMBARKATION_DAY_1",
                                        "DISEMBARKATION_LAST_DAY",
                                        "MAKER_CHECKER_SEPARATION",
                                        "TIME_BASIS_PORT_OR_SHIP",
                                        "DECK_CABIN_REFERENCE_POLICY"))
                        .domesticRegions(domesticRegions)
                        .countries(countries)
                        .durationGroups(durationGroups)
                        .build();

        return ApiResponse.success(response);
    }

    @Operation(
            summary = "Nguồn hoạt động trên tàu (Onboard Activities) cho lập kế hoạch",
            description =
                    "Cung cấp danh sách mẫu hoạt động trên tàu được phép tái sử dụng cho lập kế"
                        + " hoạch lịch trình, hỗ trợ lọc theo danh mục và tìm kiếm (yêu cầu"
                        + " TRIP_READ)")
    @PreAuthorize("hasAuthority('TRIP_READ')")
    @GetMapping("/sources/activities")
    public ApiResponse<List<PlanningSourceActivityResponse>> getSourceActivities(
            @Parameter(
                            description =
                                    "Lọc theo danh mục hoạt động (ENTERTAINMENT, SPORTS_WELLNESS,"
                                        + " FAMILY_KIDS, SPECIAL_EVENTS)")
                    @RequestParam(required = false)
                    OnboardActivityCategory category,
            @Parameter(description = "Từ khóa tìm kiếm theo tên hoặc mã hoạt động")
                    @RequestParam(required = false)
                    String keyword) {
        List<OnboardActivity> activities = onboardActivityRepository.findByCatalogEntryTrue();
        String kw = keyword != null ? keyword.trim().toLowerCase() : null;

        List<PlanningSourceActivityResponse> result =
                activities.stream()
                        .filter(a -> a.getCruise() == null && a.getStatus() != OnboardActivityStatus.CANCELLED)
                        .filter(a -> category == null || a.getCategory() == category)
                        .filter(
                                a ->
                                        kw == null
                                                || (a.getActivityName() != null
                                                        && a.getActivityName()
                                                                .toLowerCase()
                                                                .contains(kw))
                                                || (a.getActivityCode() != null
                                                        && a.getActivityCode()
                                                                .toLowerCase()
                                                                .contains(kw)))
                        .map(
                                a -> {
                                    var builder =
                                            PlanningSourceActivityResponse.builder()
                                                    .activityId(a.getOnboardActivityId())
                                                    .sourceActivityId(a.getOnboardActivityId())
                                                    .activityCode(a.getActivityCode())
                                                    .activityName(a.getActivityName())
                                                    .description(a.getDescription())
                                                    .location(a.getLocation())
                                                    .price(a.getPrice())
                                                    .currencyCode(a.getCurrencyCode());

                                    // Category info
                                    if (a.getCategory() != null) {
                                        builder.category(a.getCategory())
                                                .categoryName(a.getCategory().getDisplayName())
                                                .categoryIcon(a.getCategory().getIcon());
                                    }

                                    // Deck info — tầng nơi hoạt động diễn ra
                                    if (a.getDeck() != null) {
                                        builder.deckId(a.getDeck().getDeckId())
                                                .deckNumber(a.getDeck().getDeckNumber())
                                                .deckName(a.getDeck().getDeckName());
                                    }

                                    // Cabin info — phòng cụ thể & phân tách không gian
                                    if (a.getCabin() != null) {
                                        builder.cabinId(a.getCabin().getCabinId())
                                                .cabinNumber(a.getCabin().getCabinNumber())
                                                .roomName(a.getCabin().getRoomName())
                                                .locationType("FUNCTIONAL_ROOM");

                                        String disp =
                                                (a.getDeck() != null
                                                                ? "Tầng "
                                                                        + a.getDeck()
                                                                                .getDeckNumber()
                                                                        + " ("
                                                                        + a.getDeck().getDeckName()
                                                                        + ") - "
                                                                : "")
                                                        + "Phòng "
                                                        + a.getCabin().getCabinNumber()
                                                        + (a.getCabin().getRoomName() != null
                                                                ? " ("
                                                                        + a.getCabin().getRoomName()
                                                                        + ")"
                                                                : "");
                                        builder.displayLocation(disp);
                                    } else if (a.getDeck() != null) {
                                        builder.locationType("VENUE_OPEN_AREA");
                                        String disp =
                                                "Tầng "
                                                        + a.getDeck().getDeckNumber()
                                                        + " ("
                                                        + a.getDeck().getDeckName()
                                                        + ")"
                                                        + (a.getLocation() != null
                                                                ? " - " + a.getLocation()
                                                                : "");
                                        builder.displayLocation(disp);
                                    } else if (a.getLocation() != null) {
                                        builder.locationType("GENERAL");
                                        builder.displayLocation(a.getLocation());
                                    }

                                    return builder.build();
                                })
                        .collect(Collectors.toList());
        return ApiResponse.success(result);
    }

    public ApiResponse<List<PlanningSourceActivityResponse>> getSourceActivities(String keyword) {
        return getSourceActivities(null, keyword);
    }

    public ApiResponse<List<PlanningSourceActivityResponse>> getSourceActivities() {
        return getSourceActivities(null, null);
    }

    @Operation(
            summary = "Nguồn tham quan trên bờ (Shore Excursions) cho lập kế hoạch",
            description = "Tìm mẫu tour theo địa điểm. Bộ lọc cảng chỉ lấy các địa điểm do cảng phục vụ.")
    @PreAuthorize("hasAuthority('TRIP_READ')")
    @GetMapping("/sources/excursions")
    public ApiResponse<List<PlanningSourceExcursionResponse>> getSourceExcursions(
            @RequestParam(required = false) UUID portId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID destinationId) {
        Port port = portId == null ? null : portRepository.findById(portId).orElse(null);
        Set<UUID> served = port == null ? Set.of()
                : Set.copyOf(portRepository.findServedDestinationIds(portId));
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);

        var result = shoreExcursionRepository.findSelectableDefinitions(destinationId).stream()
                .filter(e -> portId == null || served.contains(e.getDestination().getDestinationId()))
                .filter(e -> kw.isEmpty()
                        || e.getExcursionName().toLowerCase(Locale.ROOT).contains(kw)
                        || e.getExcursionCode().toLowerCase(Locale.ROOT).contains(kw))
                .map(e -> PlanningSourceExcursionResponse.builder()
                        .excursionId(e.getShoreExcursionId())
                        .sourceExcursionId(e.getShoreExcursionId())
                        .excursionCode(e.getExcursionCode())
                        .excursionName(e.getExcursionName())
                        .meetingPoint(e.getMeetingPoint())
                        .portId(port == null ? null : port.getPortId())
                        .portCode(port == null ? null : port.getPortCode())
                        .portName(port == null ? null : port.getPortName())
                        .destinationId(e.getDestination().getDestinationId())
                        .destinationName(e.getDestination().getDestinationName())
                        .supplierName(e.getSupplier().getSupplierName())
                        .price(e.getPrice())
                        .currencyCode(e.getCurrencyCode())
                        .build())
                .toList();
        return ApiResponse.success(result);
    }

    public ApiResponse<List<PlanningSourceExcursionResponse>> getSourceExcursions(UUID portId, String keyword) {
        return getSourceExcursions(portId, keyword, null);
    }

    public ApiResponse<List<PlanningSourceExcursionResponse>> getSourceExcursions() {
        return getSourceExcursions(null, null, null);
    }

    @Operation(
            summary = "Nguồn dịch vụ / khung phục vụ (Services) cho lập kế hoạch",
            description =
                    "Cung cấp danh sách dịch vụ chuẩn cho khung phục vụ (SERVING_WINDOW), hỗ trợ"
                        + " tìm kiếm (yêu cầu TRIP_READ)")
    @PreAuthorize("hasAuthority('TRIP_READ')")
    @GetMapping("/sources/services")
    public ApiResponse<List<PlanningSourceServiceResponse>> getSourceServices(
            @Parameter(description = "Từ khóa tìm kiếm theo tên hoặc mã dịch vụ")
                    @RequestParam(required = false)
                    String keyword) {
        List<Service> services = serviceRepository.findAll();
        String kw = keyword != null ? keyword.trim().toLowerCase() : null;

        List<PlanningSourceServiceResponse> result =
                services.stream()
                        .filter(
                                s ->
                                        kw == null
                                                || (s.getServiceName() != null
                                                        && s.getServiceName()
                                                                .toLowerCase()
                                                                .contains(kw))
                                                || (s.getServiceCode() != null
                                                        && s.getServiceCode()
                                                                .toLowerCase()
                                                                .contains(kw)))
                        .map(
                                s ->
                                        PlanningSourceServiceResponse.builder()
                                                .serviceId(s.getServiceId())
                                                .serviceCode(s.getServiceCode())
                                                .serviceName(s.getServiceName())
                                                .serviceType(s.getServiceType())
                                                .description(s.getDescription())
                                                .build())
                        .collect(Collectors.toList());
        return ApiResponse.success(result);
    }

    public ApiResponse<List<PlanningSourceServiceResponse>> getSourceServices() {
        return getSourceServices(null);
    }

    @Operation(
            summary = "Danh sách boong / tầng tàu (Decks) cho lập kế hoạch",
            description =
                    "Cung cấp danh sách các tầng trên tàu phục vụ chọn vị trí diễn ra hoạt động"
                        + " trên biển (yêu cầu TRIP_READ)")
    @PreAuthorize("hasAuthority('TRIP_READ')")
    @GetMapping("/sources/decks")
    public ApiResponse<List<PlanningSourceDeckResponse>> getSourceDecks(
            @Parameter(description = "Lọc theo ID tàu du lịch") @RequestParam(required = false)
                    UUID shipId) {
        if (deckRepository == null) {
            return ApiResponse.success(List.of());
        }
        List<Deck> decks =
                shipId != null
                        ? deckRepository.findByShipShipIdOrderByDeckNumber(shipId)
                        : deckRepository.findAll(Sort.by(Sort.Direction.ASC, "deckNumber"));

        List<PlanningSourceDeckResponse> result =
                decks.stream()
                        .map(
                                d ->
                                        PlanningSourceDeckResponse.builder()
                                                .deckId(d.getDeckId())
                                                .deckNumber(d.getDeckNumber())
                                                .deckName(d.getDeckName())
                                                .shipId(
                                                        d.getShip() != null
                                                                ? d.getShip().getShipId()
                                                                : null)
                                                .shipName(
                                                        d.getShip() != null
                                                                ? d.getShip().getShipName()
                                                                : null)
                                                .build())
                        .collect(Collectors.toList());
        return ApiResponse.success(result);
    }

    public ApiResponse<List<PlanningSourceDeckResponse>> getSourceDecks() {
        return getSourceDecks(null);
    }

    @Operation(
            summary = "Danh sách phòng / cabin (Cabins) cho lập kế hoạch",
            description =
                    "Cung cấp danh sách phòng/khu vực cụ thể (spa, nhà hàng, phòng họp,...) phục vụ"
                        + " hướng dẫn hành khách tới đúng nơi diễn ra (yêu cầu TRIP_READ)")
    @PreAuthorize("hasAuthority('TRIP_READ')")
    @GetMapping("/sources/cabins")
    public ApiResponse<List<PlanningSourceCabinResponse>> getSourceCabins(
            @Parameter(description = "Lọc theo ID tầng/boong") @RequestParam(required = false)
                    UUID deckId,
            @Parameter(description = "Lọc theo ID tàu du lịch") @RequestParam(required = false)
                    UUID shipId,
            @Parameter(description = "Từ khóa tìm kiếm theo số phòng hoặc vị trí")
                    @RequestParam(required = false)
                    String keyword) {
        if (cabinRepository == null) {
            return ApiResponse.success(List.of());
        }
        List<Cabin> cabins;
        if (deckId != null) {
            if (shipId != null) {
                Deck deck =
                        deckRepository
                                .findById(deckId)
                                .orElseThrow(
                                        () ->
                                                new jakarta.persistence.EntityNotFoundException(
                                                        "Deck not found"));
                if (deck.getShip() == null || !shipId.equals(deck.getShip().getShipId()))
                    throw new com.casms.common.exception.BusinessException(
                            org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                            "ACTIVITY_VENUE_MISMATCH",
                            "Deck does not belong to requested ship",
                            java.util.Map.of("fieldPath", "deckId"));
            }
            cabins = cabinRepository.findByDeckDeckId(deckId);
        } else if (shipId != null) {
            cabins = cabinRepository.findByShipShipIdOrderByDeckDeckNumberAscCabinNumberAsc(shipId);
        } else {
            cabins = cabinRepository.findAll();
        }

        String kw = keyword != null ? keyword.trim().toLowerCase() : null;

        List<PlanningSourceCabinResponse> result =
                cabins.stream()
                        .filter(
                                c ->
                                        shipId == null
                                                || c.getShip() != null
                                                        && shipId.equals(c.getShip().getShipId()))
                        .filter(
                                c ->
                                        kw == null
                                                || (c.getCabinNumber() != null
                                                        && c.getCabinNumber()
                                                                .toLowerCase()
                                                                .contains(kw))
                                                || (c.getLocationCode() != null
                                                        && c.getLocationCode()
                                                                .toLowerCase()
                                                                .contains(kw)))
                        .map(
                                c -> {
                                    String disp =
                                            "Phòng "
                                                    + c.getCabinNumber()
                                                    + (c.getRoomName() != null
                                                            ? " - " + c.getRoomName()
                                                            : "");
                                    return PlanningSourceCabinResponse.builder()
                                            .cabinId(c.getCabinId())
                                            .cabinNumber(c.getCabinNumber())
                                            .roomName(c.getRoomName())
                                            .displayName(disp)
                                            .cabinType(c.getCabinType())
                                            .capacity(c.getCapacity())
                                            .deckId(
                                                    c.getDeck() != null
                                                            ? c.getDeck().getDeckId()
                                                            : null)
                                            .deckNumber(
                                                    c.getDeck() != null
                                                            ? c.getDeck().getDeckNumber()
                                                            : null)
                                            .deckName(
                                                    c.getDeck() != null
                                                            ? c.getDeck().getDeckName()
                                                            : null)
                                            .locationCode(c.getLocationCode())
                                            .shipId(
                                                    c.getShip() != null
                                                            ? c.getShip().getShipId()
                                                            : null)
                                            .build();
                                })
                        .collect(Collectors.toList());
        return ApiResponse.success(result);
    }

    public ApiResponse<List<PlanningSourceCabinResponse>> getSourceCabins() {
        return getSourceCabins(null, null, null);
    }

    @Operation(
            summary = "Danh mục 4 nhóm hoạt động trên tàu",
            description =
                    "Cung cấp danh mục các nhóm hoạt động chuẩn (Giải trí, Thể thao & Sức khỏe, Gia"
                        + " đình & Trẻ em, Sự kiện đặc biệt) cho Frontend render tabs/chips bộ lọc"
                        + " (yêu cầu TRIP_READ)")
    @PreAuthorize("hasAuthority('TRIP_READ')")
    @GetMapping("/catalogs/activity-categories")
    public ApiResponse<List<ActivityCategoryResponse>> getActivityCategories() {
        List<ActivityCategoryResponse> result =
                List.of(
                        ActivityCategoryResponse.builder()
                                .code(OnboardActivityCategory.ENTERTAINMENT)
                                .name(OnboardActivityCategory.ENTERTAINMENT.getDisplayName())
                                .icon(OnboardActivityCategory.ENTERTAINMENT.getIcon())
                                .description(
                                        "Đại nhạc hội, biểu diễn ảo thuật & xiếc, ban nhạc Jazz,"
                                            + " chiếu phim ngoài trời, tiệc DJ bể bơi, lớp trà"
                                            + " chiều")
                                .build(),
                        ActivityCategoryResponse.builder()
                                .code(OnboardActivityCategory.SPORTS_WELLNESS)
                                .name(OnboardActivityCategory.SPORTS_WELLNESS.getDisplayName())
                                .icon(OnboardActivityCategory.SPORTS_WELLNESS.getIcon())
                                .description(
                                        "Lớp Yoga & Thiền bình minh, trị liệu massage đá nóng Lotus"
                                            + " Spa, phòng Gym & Fitness")
                                .build(),
                        ActivityCategoryResponse.builder()
                                .code(OnboardActivityCategory.FAMILY_KIDS)
                                .name(OnboardActivityCategory.FAMILY_KIDS.getDisplayName())
                                .icon(OnboardActivityCategory.FAMILY_KIDS.getIcon())
                                .description(
                                        "Đấu trường thực tế ảo VR, tìm kho báu hải tặc quanh tàu,"
                                            + " rạp chiếu 3D, câu lạc bộ thiếu nhi Kids Club")
                                .build(),
                        ActivityCategoryResponse.builder()
                                .code(OnboardActivityCategory.SPECIAL_EVENTS)
                                .name(OnboardActivityCategory.SPECIAL_EVENTS.getDisplayName())
                                .icon(OnboardActivityCategory.SPECIAL_EVENTS.getIcon())
                                .description(
                                        "Đêm tiệc Gala Thuyền trưởng, White Night Deck Party, lễ"
                                            + " hội đếm ngược, dạ vũ khiêu vũ hoàng gia")
                                .build());
        return ApiResponse.success(result);
    }

    @Operation(
            summary = "Danh sách cảng ghé (Ports) cho lập kế hoạch hành trình",
            description =
                    "Cung cấp danh mục cảng biển đầy đủ thông tin UN/LOCODE, tên cảng, quốc gia,"
                        + " múi giờ và điểm đến liên kết (hỗ trợ lọc theo phạm vi quốc gia hoặc"
                        + " Trip)")
    @PreAuthorize("hasAuthority('TRIP_READ')")
    @GetMapping("/sources/ports")
    public ApiResponse<List<PlanningSourcePortResponse>> getSourcePorts(
            @Parameter(description = "Lọc theo ID điểm đến / khu vực")
                    @RequestParam(required = false)
                    UUID destinationId,
            @Parameter(description = "Từ khóa tìm kiếm theo tên hoặc mã cảng")
                    @RequestParam(required = false)
                    String keyword,
            @Parameter(description = "Lọc theo mã quốc gia ISO-2 (ví dụ: VN, TH)")
                    @RequestParam(required = false)
                    String countryCode,
            @Parameter(description = "Lọc theo danh sách mã quốc gia ISO-2")
                    @RequestParam(required = false)
                    List<String> countryCodes,
            @Parameter(description = "Lọc theo phạm vi quốc gia có thẩm quyền của Trip")
                    @RequestParam(required = false)
                    UUID tripId) {
        if (portRepository == null) {
            return ApiResponse.success(List.of());
        }

        // 1. Determine allowed country scope
        List<String> allowedCountryCodes = null;
        if (tripId != null) {
            if (tripRepository == null) {
                throw new BusinessException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "TRIP_REPO_UNAVAILABLE",
                        "TripRepository is not available.");
            }
            Trip trip =
                    tripRepository
                            .findById(tripId)
                            .orElseThrow(() -> new ResourceNotFoundException("Trip", tripId));

            if (CountryScopeValidator.isDomestic(trip)) {
                allowedCountryCodes = List.of("VN");
            } else if (CountryScopeValidator.isInternational(trip)) {
                List<String> tripCountries = trip.getCountryCodes();
                if (tripCountries == null || tripCountries.isEmpty()) {
                    throw new BusinessException(
                            HttpStatus.BAD_REQUEST,
                            "SCOPE_COUNTRY_REQUIRED",
                            "Chuyến đi quốc tế "
                                    + trip.getTripCode()
                                    + " chưa được cấu hình danh sách quốc gia (countryCodes). Vui"
                                    + " lòng cập nhật phạm vi quốc gia trước khi tra cứu cảng.");
                }
                allowedCountryCodes =
                        tripCountries.stream().map(c -> c.trim().toUpperCase()).toList();
            } else {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "SCOPE_COUNTRY_REQUIRED",
                        "Chuyến đi "
                                + trip.getTripCode()
                                + " chưa được xác định phạm vi địa lý (plannedScope).");
            }
        }

        // Explicit countryCode / countryCodes from request
        List<String> directCountries = new ArrayList<>();
        if (countryCode != null && !countryCode.isBlank()) {
            directCountries.add(countryCode.trim().toUpperCase());
        }
        if (countryCodes != null && !countryCodes.isEmpty()) {
            directCountries.addAll(
                    countryCodes.stream()
                            .filter(c -> c != null && !c.isBlank())
                            .map(c -> c.trim().toUpperCase())
                            .toList());
        }

        if (!directCountries.isEmpty()) {
            if (allowedCountryCodes != null) {
                // Verify all requested direct countries are within the trip's authoritative scope
                for (String dc : directCountries) {
                    if (!allowedCountryCodes.contains(dc)) {
                        throw new BusinessException(
                                HttpStatus.BAD_REQUEST,
                                "PORT_OUTSIDE_COUNTRY_SCOPE",
                                "Mã quốc gia '"
                                        + dc
                                        + "' không nằm trong phạm vi quốc gia có thẩm quyền của"
                                        + " Trip: "
                                        + String.join(", ", allowedCountryCodes));
                    }
                }
                allowedCountryCodes = directCountries;
            } else {
                allowedCountryCodes = directCountries;
            }
        }

        List<Port> ports;
        if (destinationId != null) {
            ports = portRepository.findByDestinationDestinationIdOrderByPortNameAsc(destinationId);
        } else {
            ports = portRepository.findAll();
        }

        String kw = keyword != null ? keyword.trim().toLowerCase() : null;
        final List<String> filterCountries = allowedCountryCodes;
        var geography = geographyCatalog == null ? null : geographyCatalog.selection();

        List<PlanningSourcePortResponse> result =
                ports.stream()
                        .filter(p -> p.getStatus() == PortStatus.ACTIVE)
                        .filter(p -> geography == null || geography.unavailable(p).isEmpty())
                        .filter(
                                p ->
                                        filterCountries == null
                                                || (p.getCountryCode() != null
                                                        && filterCountries.contains(
                                                                p.getCountryCode()
                                                                        .trim()
                                                                        .toUpperCase())))
                        .filter(
                                p ->
                                        kw == null
                                                || (p.getPortName() != null
                                                        && p.getPortName()
                                                                .toLowerCase()
                                                                .contains(kw))
                                                || (p.getPortCode() != null
                                                        && p.getPortCode()
                                                                .toLowerCase()
                                                                .contains(kw)))
                        .map(
                                p ->
                                        PlanningSourcePortResponse.builder()
                                                .portId(p.getPortId())
                                                .portCode(p.getPortCode())
                                                .portName(p.getPortName())
                                                .localityName(
                                                        p.getCoordinateSource() == null
                                                                ? null
                                                                : p.getCoordinateSource()
                                                                        .getLocationName())
                                                .countryCode(p.getCountryCode())
                                                .domesticRegion(
                                                        VietnamGeographyRegistry
                                                                .resolveDomesticRegion(p))
                                                .timeZoneId(p.getTimeZoneId())
                                                .destinationId(
                                                        p.getDestination() != null
                                                                ? p.getDestination()
                                                                        .getDestinationId()
                                                                : null)
                                                .destinationName(
                                                        p.getDestination() != null
                                                                ? p.getDestination()
                                                                        .getDestinationName()
                                                                : null)
                                                .servedDestinationIds(
                                                        portRepository.findServedDestinationIds(
                                                                p.getPortId()))
                                                .latitude(p.getLatitude())
                                                .longitude(p.getLongitude())
                                                .description(p.getDescription())
                                                .build())
                        .collect(Collectors.toList());
        return ApiResponse.success(result);
    }

    @PreAuthorize("hasRole('CRUISE_MANAGER')")
    @GetMapping("/geography")
    public ApiResponse<List<TripGeographyCatalog.Item>> listGeography() {
        return ApiResponse.success(geographyCatalog.list(false));
    }

    @PreAuthorize("hasRole('CRUISE_MANAGER')")
    @PostMapping("/geography/countries")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TripGeographyCatalog.Item> createCountry(
            @Valid @RequestBody CreateCountryRequest request) {
        return ApiResponse.success(geographyCatalog.createCountry(request.code(), request.label()));
    }

    @PreAuthorize("hasRole('CRUISE_MANAGER')")
    @PatchMapping("/geography/{kind}/{code}/status")
    public ApiResponse<TripGeographyCatalog.Item> changeGeographyStatus(
            @PathVariable TripGeographyCatalog.Kind kind,
            @PathVariable String code,
            @Valid @RequestBody GeographyStatusRequest request) {
        return ApiResponse.success(geographyCatalog.changeStatus(kind, code, request.status()));
    }

    public record GeographyStatusRequest(@NotNull TripGeographyCatalog.Status status) {}

    @PreAuthorize("hasRole('CRUISE_MANAGER')")
    @GetMapping("/geography/country-options")
    public ApiResponse<List<CountryOption>> geographyCountryOptions() {
        Locale vietnamese = Locale.forLanguageTag("vi");
        return ApiResponse.success(java.util.Arrays.stream(Locale.getISOCountries())
                .map(code -> new CountryOption(code,
                        new Locale.Builder().setRegion(code).build().getDisplayCountry(vietnamese)))
                .sorted(java.util.Comparator.comparing(CountryOption::label))
                .toList());
    }

    public record CountryOption(String code, String label) {}

    public record CreateCountryRequest(@NotNull String code, @NotNull String label) {}

    public ApiResponse<List<PlanningSourcePortResponse>> getSourcePorts() {
        return getSourcePorts(null, null, null, null, null);
    }
}
