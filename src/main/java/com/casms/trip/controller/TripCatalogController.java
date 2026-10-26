package com.casms.trip.controller;

import com.casms.activity.entity.OnboardActivity;
import com.casms.activity.entity.ShoreExcursion;
import com.casms.activity.repository.OnboardActivityRepository;
import com.casms.activity.repository.ShoreExcursionRepository;
import com.casms.common.response.ApiResponse;
import com.casms.servicepackage.entity.Service;
import com.casms.servicepackage.repository.ServiceRepository;
import com.casms.ship.entity.Cabin;
import com.casms.ship.entity.Deck;
import com.casms.ship.entity.Port;
import com.casms.ship.repository.CabinRepository;
import com.casms.ship.repository.DeckRepository;
import com.casms.ship.repository.DestinationRepository;
import com.casms.ship.repository.PortRepository;
import com.casms.activity.enums.OnboardActivityCategory;
import com.casms.trip.dto.response.ActivityCategoryResponse;
import com.casms.trip.dto.response.PlanningSourceActivityResponse;
import com.casms.trip.dto.response.PlanningSourceCabinResponse;
import com.casms.trip.dto.response.PlanningSourceDeckResponse;
import com.casms.trip.dto.response.PlanningSourceExcursionResponse;
import com.casms.trip.dto.response.PlanningSourcePortResponse;
import com.casms.trip.dto.response.PlanningSourceServiceResponse;
import com.casms.trip.dto.response.TripCatalogResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Tag(name = "Trip Planning Catalogs", description = "Danh mục chuẩn cho phân hệ lập kế hoạch hành trình")
@RestController
@RequestMapping("/trip-planning/catalogs")
@RequiredArgsConstructor
public class TripCatalogController {

    private final DestinationRepository destinationRepository;
    private final OnboardActivityRepository onboardActivityRepository;
    private final ShoreExcursionRepository shoreExcursionRepository;
    private final ServiceRepository serviceRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @lombok.Setter
    private DeckRepository deckRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @lombok.Setter
    private CabinRepository cabinRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @lombok.Setter
    private PortRepository portRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @lombok.Setter
    private com.casms.trip.repository.TripRepository tripRepository;

    @Operation(summary = "Lấy danh mục lập kế hoạch hành trình", description = "Trả về operatingAreas từ server, tripTypes, fleetIntegrationStatus=DEFERRED_EXTERNAL, supported policies, domesticRegions và countries")
    @PreAuthorize("hasAuthority('TRIP_READ')")
    @GetMapping
    public ApiResponse<TripCatalogResponse> getCatalogs() {
        List<String> operatingAreas = destinationRepository.findAllActiveDestinations().stream()
                .map(d -> d.getDestinationName())
                .distinct()
                .collect(Collectors.toList());

        List<TripCatalogResponse.RegionCatalogItem> domesticRegions = List.of(
                new TripCatalogResponse.RegionCatalogItem("BAC_BO", "Miền Bắc (Bắc Bộ)", "Bao gồm các cảng Vịnh Hạ Long, Hải Phòng và phụ cận"),
                new TripCatalogResponse.RegionCatalogItem("TRUNG_BO", "Miền Trung (Trung Bộ)", "Bao gồm các cảng Đà Nẵng, Nha Trang, Chân Mây Huế và phụ cận"),
                new TripCatalogResponse.RegionCatalogItem("NAM_BO", "Miền Nam (Nam Bộ)", "Bao gồm các cảng Sài Gòn, Cái Mép - Phú Mỹ, Phú Quốc và phụ cận")
        );

        List<TripCatalogResponse.CountryCatalogItem> countries = List.of(
                new TripCatalogResponse.CountryCatalogItem("VN", "Việt Nam"),
                new TripCatalogResponse.CountryCatalogItem("SG", "Singapore"),
                new TripCatalogResponse.CountryCatalogItem("TH", "Thái Lan"),
                new TripCatalogResponse.CountryCatalogItem("MY", "Malaysia"),
                new TripCatalogResponse.CountryCatalogItem("JP", "Nhật Bản")
        );

        List<TripCatalogResponse.DurationGroupCatalogItem> durationGroups = List.of(
                new TripCatalogResponse.DurationGroupCatalogItem("SHORT", "Chuyến ngắn", 1, 2, 1),
                new TripCatalogResponse.DurationGroupCatalogItem("MEDIUM", "Chuyến trung bình", 2, 3, 2),
                new TripCatalogResponse.DurationGroupCatalogItem("LONG", "Chuyến dài ngày", 3, 5, 3),
                new TripCatalogResponse.DurationGroupCatalogItem("EXTENDED", "Hải trình mở rộng", 5, 30, 7)
        );

        TripCatalogResponse response = TripCatalogResponse.builder()
                .operatingAreas(operatingAreas)
                .tripTypes(List.of("ROUND_TRIP", "ONE_WAY"))
                .fleetIntegrationStatus("DEFERRED_EXTERNAL")
                .supportedPolicies(List.of(
                        "PORT_CALL_BUFFER_30M",
                        "EMBARKATION_DAY_1",
                        "DISEMBARKATION_LAST_DAY",
                        "MAKER_CHECKER_SEPARATION",
                        "TIME_BASIS_PORT_OR_SHIP",
                        "DECK_CABIN_REFERENCE_POLICY"
                ))
                .domesticRegions(domesticRegions)
                .countries(countries)
                .durationGroups(durationGroups)
                .build();

        return ApiResponse.success(response);
    }

    @Operation(summary = "Nguồn hoạt động trên tàu (Onboard Activities) cho lập kế hoạch",
               description = "Cung cấp danh sách mẫu hoạt động trên tàu được phép tái sử dụng cho lập kế hoạch lịch trình, hỗ trợ lọc theo danh mục và tìm kiếm (yêu cầu TRIP_READ)")
    @PreAuthorize("hasAuthority('TRIP_READ')")
    @GetMapping("/sources/activities")
    public ApiResponse<List<PlanningSourceActivityResponse>> getSourceActivities(
            @Parameter(description = "Lọc theo danh mục hoạt động (ENTERTAINMENT, SPORTS_WELLNESS, FAMILY_KIDS, SPECIAL_EVENTS)")
            @RequestParam(required = false) OnboardActivityCategory category,
            @Parameter(description = "Từ khóa tìm kiếm theo tên hoặc mã hoạt động")
            @RequestParam(required = false) String keyword) {
        List<OnboardActivity> activities = onboardActivityRepository.findAll();
        String kw = keyword != null ? keyword.trim().toLowerCase() : null;

        List<PlanningSourceActivityResponse> result = activities.stream()
                .filter(a -> a.getStatus() != com.casms.activity.enums.OnboardActivityStatus.CANCELLED)
                .filter(a -> category == null || a.getCategory() == category)
                .filter(a -> kw == null || (a.getActivityName() != null && a.getActivityName().toLowerCase().contains(kw))
                        || (a.getActivityCode() != null && a.getActivityCode().toLowerCase().contains(kw)))
                .map(a -> {
                    var builder = PlanningSourceActivityResponse.builder()
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

                        String disp = (a.getDeck() != null ? "Tầng " + a.getDeck().getDeckNumber() + " (" + a.getDeck().getDeckName() + ") - " : "")
                                + "Phòng " + a.getCabin().getCabinNumber()
                                + (a.getCabin().getRoomName() != null ? " (" + a.getCabin().getRoomName() + ")" : "");
                        builder.displayLocation(disp);
                    } else if (a.getDeck() != null) {
                        builder.locationType("VENUE_OPEN_AREA");
                        String disp = "Tầng " + a.getDeck().getDeckNumber() + " (" + a.getDeck().getDeckName() + ")"
                                + (a.getLocation() != null ? " - " + a.getLocation() : "");
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

    @Operation(summary = "Nguồn tham quan trên bờ (Shore Excursions) cho lập kế hoạch",
               description = "Cung cấp danh sách mẫu tour tham quan trên bờ được phép tái sử dụng cho lập kế hoạch lịch trình, hỗ trợ lọc theo cảng và từ khóa (yêu cầu TRIP_READ)")
    @PreAuthorize("hasAuthority('TRIP_READ')")
    @GetMapping("/sources/excursions")
    public ApiResponse<List<PlanningSourceExcursionResponse>> getSourceExcursions(
            @Parameter(description = "Lọc theo ID cảng ghé") @RequestParam(required = false) UUID portId,
            @Parameter(description = "Từ khóa tìm kiếm theo tên hoặc mã tour") @RequestParam(required = false) String keyword) {
        List<ShoreExcursion> excursions = shoreExcursionRepository.findAll();
        String kw = keyword != null ? keyword.trim().toLowerCase() : null;

        List<PlanningSourceExcursionResponse> result = excursions.stream()
                .filter(e -> e.getStatus() != com.casms.activity.enums.ShoreExcursionStatus.CANCELLED)
                .filter(e -> {
                    UUID pId = null;
                    if (e.getPort() != null) {
                        pId = e.getPort().getPortId();
                    } else if (e.getCruiseStop() != null && e.getCruiseStop().getPort() != null) {
                        pId = e.getCruiseStop().getPort().getPortId();
                    }
                    return portId == null || (pId != null && portId.equals(pId));
                })
                .filter(e -> kw == null || (e.getExcursionName() != null && e.getExcursionName().toLowerCase().contains(kw))
                        || (e.getExcursionCode() != null && e.getExcursionCode().toLowerCase().contains(kw)))
                .map(e -> {
                    UUID pId = null;
                    String pCode = null;
                    String pName = null;
                    if (e.getPort() != null) {
                        pId = e.getPort().getPortId();
                        pCode = e.getPort().getPortCode();
                        pName = e.getPort().getPortName();
                    } else if (e.getCruiseStop() != null && e.getCruiseStop().getPort() != null) {
                        pId = e.getCruiseStop().getPort().getPortId();
                        pCode = e.getCruiseStop().getPort().getPortCode();
                        pName = e.getCruiseStop().getPort().getPortName();
                    }

                    // Destination info — điểm đến liên kết
                    UUID destId = null;
                    String destName = null;
                    if (e.getPort() != null && e.getPort().getDestination() != null) {
                        destId = e.getPort().getDestination().getDestinationId();
                        destName = e.getPort().getDestination().getDestinationName();
                    } else if (e.getCruiseStop() != null && e.getCruiseStop().getDestination() != null) {
                        destId = e.getCruiseStop().getDestination().getDestinationId();
                        destName = e.getCruiseStop().getDestination().getDestinationName();
                    } else if (e.getCruiseStop() != null && e.getCruiseStop().getPort() != null
                            && e.getCruiseStop().getPort().getDestination() != null) {
                        destId = e.getCruiseStop().getPort().getDestination().getDestinationId();
                        destName = e.getCruiseStop().getPort().getDestination().getDestinationName();
                    }

                    // Supplier info — nhà cung cấp tour
                    String supplierName = null;
                    if (e.getSupplier() != null) {
                        supplierName = e.getSupplier().getSupplierName();
                    }

                    return PlanningSourceExcursionResponse.builder()
                            .excursionId(e.getShoreExcursionId())
                            .sourceExcursionId(e.getShoreExcursionId())
                            .excursionCode(e.getExcursionCode())
                            .excursionName(e.getExcursionName())
                            .description(null)
                            .meetingPoint(e.getMeetingPoint())
                            .portId(pId)
                            .portCode(pCode)
                            .portName(pName)
                            .destinationId(destId)
                            .destinationName(destName)
                            .supplierName(supplierName)
                            .price(e.getPrice())
                            .currencyCode(e.getCurrencyCode())
                            .build();
                })
                .collect(Collectors.toList());
        return ApiResponse.success(result);
    }

    public ApiResponse<List<PlanningSourceExcursionResponse>> getSourceExcursions() {
        return getSourceExcursions(null, null);
    }

    @Operation(summary = "Nguồn dịch vụ / khung phục vụ (Services) cho lập kế hoạch",
               description = "Cung cấp danh sách dịch vụ chuẩn cho khung phục vụ (SERVING_WINDOW), hỗ trợ tìm kiếm (yêu cầu TRIP_READ)")
    @PreAuthorize("hasAuthority('TRIP_READ')")
    @GetMapping("/sources/services")
    public ApiResponse<List<PlanningSourceServiceResponse>> getSourceServices(
            @Parameter(description = "Từ khóa tìm kiếm theo tên hoặc mã dịch vụ") @RequestParam(required = false) String keyword) {
        List<Service> services = serviceRepository.findAll();
        String kw = keyword != null ? keyword.trim().toLowerCase() : null;

        List<PlanningSourceServiceResponse> result = services.stream()
                .filter(s -> kw == null || (s.getServiceName() != null && s.getServiceName().toLowerCase().contains(kw))
                        || (s.getServiceCode() != null && s.getServiceCode().toLowerCase().contains(kw)))
                .map(s -> PlanningSourceServiceResponse.builder()
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

    @Operation(summary = "Danh sách boong / tầng tàu (Decks) cho lập kế hoạch",
               description = "Cung cấp danh sách các tầng trên tàu phục vụ chọn vị trí diễn ra hoạt động trên biển (yêu cầu TRIP_READ)")
    @PreAuthorize("hasAuthority('TRIP_READ')")
    @GetMapping("/sources/decks")
    public ApiResponse<List<PlanningSourceDeckResponse>> getSourceDecks(
            @Parameter(description = "Lọc theo ID tàu du lịch") @RequestParam(required = false) UUID shipId) {
        if (deckRepository == null) {
            return ApiResponse.success(List.of());
        }
        List<Deck> decks = shipId != null
                ? deckRepository.findByShipShipIdOrderByDeckNumber(shipId)
                : deckRepository.findAll(Sort.by(Sort.Direction.ASC, "deckNumber"));

        List<PlanningSourceDeckResponse> result = decks.stream()
                .map(d -> PlanningSourceDeckResponse.builder()
                        .deckId(d.getDeckId())
                        .deckNumber(d.getDeckNumber())
                        .deckName(d.getDeckName())
                        .shipId(d.getShip() != null ? d.getShip().getShipId() : null)
                        .shipName(d.getShip() != null ? d.getShip().getShipName() : null)
                        .build())
                .collect(Collectors.toList());
        return ApiResponse.success(result);
    }

    public ApiResponse<List<PlanningSourceDeckResponse>> getSourceDecks() {
        return getSourceDecks(null);
    }

    @Operation(summary = "Danh sách phòng / cabin (Cabins) cho lập kế hoạch",
               description = "Cung cấp danh sách phòng/khu vực cụ thể (spa, nhà hàng, phòng họp,...) phục vụ hướng dẫn hành khách tới đúng nơi diễn ra (yêu cầu TRIP_READ)")
    @PreAuthorize("hasAuthority('TRIP_READ')")
    @GetMapping("/sources/cabins")
    public ApiResponse<List<PlanningSourceCabinResponse>> getSourceCabins(
            @Parameter(description = "Lọc theo ID tầng/boong") @RequestParam(required = false) UUID deckId,
            @Parameter(description = "Lọc theo ID tàu du lịch") @RequestParam(required = false) UUID shipId,
            @Parameter(description = "Từ khóa tìm kiếm theo số phòng hoặc vị trí") @RequestParam(required = false) String keyword) {
        if (cabinRepository == null) {
            return ApiResponse.success(List.of());
        }
        List<Cabin> cabins;
        if (deckId != null) {
            cabins = cabinRepository.findByDeckDeckId(deckId);
        } else if (shipId != null) {
            cabins = cabinRepository.findByShipShipIdOrderByDeckDeckNumberAscCabinNumberAsc(shipId);
        } else {
            cabins = cabinRepository.findAll();
        }

        String kw = keyword != null ? keyword.trim().toLowerCase() : null;

        List<PlanningSourceCabinResponse> result = cabins.stream()
                .filter(c -> kw == null || (c.getCabinNumber() != null && c.getCabinNumber().toLowerCase().contains(kw))
                        || (c.getLocationCode() != null && c.getLocationCode().toLowerCase().contains(kw)))
                .map(c -> {
                    String disp = "Phòng " + c.getCabinNumber()
                            + (c.getRoomName() != null ? " - " + c.getRoomName() : "");
                    return PlanningSourceCabinResponse.builder()
                            .cabinId(c.getCabinId())
                            .cabinNumber(c.getCabinNumber())
                            .roomName(c.getRoomName())
                            .displayName(disp)
                            .cabinType(c.getCabinType())
                            .capacity(c.getCapacity())
                            .deckId(c.getDeck() != null ? c.getDeck().getDeckId() : null)
                            .deckNumber(c.getDeck() != null ? c.getDeck().getDeckNumber() : null)
                            .deckName(c.getDeck() != null ? c.getDeck().getDeckName() : null)
                            .locationCode(c.getLocationCode())
                            .shipId(c.getShip() != null ? c.getShip().getShipId() : null)
                            .build();
                })
                .collect(Collectors.toList());
        return ApiResponse.success(result);
    }

    public ApiResponse<List<PlanningSourceCabinResponse>> getSourceCabins() {
        return getSourceCabins(null, null, null);
    }

    @Operation(summary = "Danh mục 4 nhóm hoạt động trên tàu",
               description = "Cung cấp danh mục các nhóm hoạt động chuẩn (Giải trí, Thể thao & Sức khỏe, Gia đình & Trẻ em, Sự kiện đặc biệt) cho Frontend render tabs/chips bộ lọc (yêu cầu TRIP_READ)")
    @PreAuthorize("hasAuthority('TRIP_READ')")
    @GetMapping("/catalogs/activity-categories")
    public ApiResponse<List<ActivityCategoryResponse>> getActivityCategories() {
        List<ActivityCategoryResponse> result = List.of(
                ActivityCategoryResponse.builder()
                        .code(OnboardActivityCategory.ENTERTAINMENT)
                        .name(OnboardActivityCategory.ENTERTAINMENT.getDisplayName())
                        .icon(OnboardActivityCategory.ENTERTAINMENT.getIcon())
                        .description("Đại nhạc hội, biểu diễn ảo thuật & xiếc, ban nhạc Jazz, chiếu phim ngoài trời, tiệc DJ bể bơi, lớp trà chiều")
                        .build(),
                ActivityCategoryResponse.builder()
                        .code(OnboardActivityCategory.SPORTS_WELLNESS)
                        .name(OnboardActivityCategory.SPORTS_WELLNESS.getDisplayName())
                        .icon(OnboardActivityCategory.SPORTS_WELLNESS.getIcon())
                        .description("Lớp Yoga & Thiền bình minh, trị liệu massage đá nóng Lotus Spa, phòng Gym & Fitness")
                        .build(),
                ActivityCategoryResponse.builder()
                        .code(OnboardActivityCategory.FAMILY_KIDS)
                        .name(OnboardActivityCategory.FAMILY_KIDS.getDisplayName())
                        .icon(OnboardActivityCategory.FAMILY_KIDS.getIcon())
                        .description("Đấu trường thực tế ảo VR, tìm kho báu hải tặc quanh tàu, rạp chiếu 3D, câu lạc bộ thiếu nhi Kids Club")
                        .build(),
                ActivityCategoryResponse.builder()
                        .code(OnboardActivityCategory.SPECIAL_EVENTS)
                        .name(OnboardActivityCategory.SPECIAL_EVENTS.getDisplayName())
                        .icon(OnboardActivityCategory.SPECIAL_EVENTS.getIcon())
                        .description("Đêm tiệc Gala Thuyền trưởng, White Night Deck Party, lễ hội đếm ngược, dạ vũ khiêu vũ hoàng gia")
                        .build()
        );
        return ApiResponse.success(result);
    }

    @Operation(summary = "Danh sách cảng ghé (Ports) cho lập kế hoạch hành trình",
               description = "Cung cấp danh mục cảng biển đầy đủ thông tin UN/LOCODE, tên cảng, quốc gia, múi giờ và điểm đến liên kết (hỗ trợ lọc theo phạm vi quốc gia hoặc Trip)")
    @PreAuthorize("hasAuthority('TRIP_READ')")
    @GetMapping("/sources/ports")
    public ApiResponse<List<PlanningSourcePortResponse>> getSourcePorts(
            @Parameter(description = "Lọc theo ID điểm đến / khu vực") @RequestParam(required = false) UUID destinationId,
            @Parameter(description = "Từ khóa tìm kiếm theo tên hoặc mã cảng") @RequestParam(required = false) String keyword,
            @Parameter(description = "Lọc theo mã quốc gia ISO-2 (ví dụ: VN, TH)") @RequestParam(required = false) String countryCode,
            @Parameter(description = "Lọc theo danh sách mã quốc gia ISO-2") @RequestParam(required = false) List<String> countryCodes,
            @Parameter(description = "Lọc theo phạm vi quốc gia có thẩm quyền của Trip") @RequestParam(required = false) UUID tripId) {
        if (portRepository == null) {
            return ApiResponse.success(List.of());
        }

        // 1. Determine allowed country scope
        List<String> allowedCountryCodes = null;
        if (tripId != null) {
            if (tripRepository == null) {
                throw new com.casms.common.exception.BusinessException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "TRIP_REPO_UNAVAILABLE", "TripRepository is not available.");
            }
            com.casms.trip.entity.Trip trip = tripRepository.findById(tripId)
                    .orElseThrow(() -> new com.casms.common.exception.ResourceNotFoundException("Trip", tripId));

            if ("DOMESTIC".equalsIgnoreCase(trip.getPlannedScope())) {
                allowedCountryCodes = List.of("VN");
            } else if ("INTERNATIONAL".equalsIgnoreCase(trip.getPlannedScope())) {
                List<String> tripCountries = trip.getCountryCodes();
                if (tripCountries == null || tripCountries.isEmpty()) {
                    throw new com.casms.common.exception.BusinessException(org.springframework.http.HttpStatus.BAD_REQUEST, "SCOPE_COUNTRY_REQUIRED",
                            "Chuyến đi quốc tế " + trip.getTripCode() + " chưa được cấu hình danh sách quốc gia (countryCodes). Vui lòng cập nhật phạm vi quốc gia trước khi tra cứu cảng.");
                }
                allowedCountryCodes = tripCountries.stream().map(c -> c.trim().toUpperCase()).toList();
            } else {
                throw new com.casms.common.exception.BusinessException(org.springframework.http.HttpStatus.BAD_REQUEST, "SCOPE_COUNTRY_REQUIRED",
                        "Chuyến đi " + trip.getTripCode() + " chưa được xác định phạm vi địa lý (plannedScope).");
            }
        }

        // Explicit countryCode / countryCodes from request
        List<String> directCountries = new java.util.ArrayList<>();
        if (countryCode != null && !countryCode.isBlank()) {
            directCountries.add(countryCode.trim().toUpperCase());
        }
        if (countryCodes != null && !countryCodes.isEmpty()) {
            directCountries.addAll(countryCodes.stream().filter(c -> c != null && !c.isBlank()).map(c -> c.trim().toUpperCase()).toList());
        }

        if (!directCountries.isEmpty()) {
            if (allowedCountryCodes != null) {
                // Verify all requested direct countries are within the trip's authoritative scope
                for (String dc : directCountries) {
                    if (!allowedCountryCodes.contains(dc)) {
                        throw new com.casms.common.exception.BusinessException(org.springframework.http.HttpStatus.BAD_REQUEST, "PORT_OUTSIDE_COUNTRY_SCOPE",
                                "Mã quốc gia '" + dc + "' không nằm trong phạm vi quốc gia có thẩm quyền của Trip: " + String.join(", ", allowedCountryCodes));
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

        List<PlanningSourcePortResponse> result = ports.stream()
                .filter(p -> p.getStatus() == com.casms.ship.enums.PortStatus.ACTIVE)
                .filter(p -> filterCountries == null || (p.getCountryCode() != null && filterCountries.contains(p.getCountryCode().trim().toUpperCase())))
                .filter(p -> kw == null || (p.getPortName() != null && p.getPortName().toLowerCase().contains(kw))
                        || (p.getPortCode() != null && p.getPortCode().toLowerCase().contains(kw)))
                .map(p -> PlanningSourcePortResponse.builder()
                        .portId(p.getPortId())
                        .portCode(p.getPortCode())
                        .portName(p.getPortName())
                        .countryCode(p.getCountryCode())
                        .domesticRegion(com.casms.trip.service.VietnamGeographyRegistry.resolveDomesticRegion(p))
                        .timeZoneId(p.getTimeZoneId())
                        .destinationId(p.getDestination() != null ? p.getDestination().getDestinationId() : null)
                        .destinationName(p.getDestination() != null ? p.getDestination().getDestinationName() : null)
                        .latitude(p.getLatitude())
                        .longitude(p.getLongitude())
                        .description(p.getDescription())
                        .build())
                .collect(Collectors.toList());
        return ApiResponse.success(result);
    }

    public ApiResponse<List<PlanningSourcePortResponse>> getSourcePorts() {
        return getSourcePorts(null, null, null, null, null);
    }
}
