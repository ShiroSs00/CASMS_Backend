package com.casms.trip.controller;

import com.casms.activity.entity.OnboardActivity;
import com.casms.activity.entity.ShoreExcursion;
import com.casms.activity.repository.OnboardActivityRepository;
import com.casms.activity.repository.ShoreExcursionRepository;
import com.casms.common.response.ApiResponse;
import com.casms.servicepackage.entity.Service;
import com.casms.servicepackage.repository.ServiceRepository;
import com.casms.ship.repository.DestinationRepository;
import com.casms.trip.dto.response.PlanningSourceActivityResponse;
import com.casms.trip.dto.response.PlanningSourceExcursionResponse;
import com.casms.trip.dto.response.PlanningSourceServiceResponse;
import com.casms.trip.dto.response.TripCatalogResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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

    @Operation(summary = "Lấy danh mục lập kế hoạch hành trình", description = "Trả về operatingAreas từ server, tripTypes, fleetIntegrationStatus=DEFERRED_EXTERNAL và supported policies")
    @PreAuthorize("hasAuthority('TRIP_READ')")
    @GetMapping
    public ApiResponse<TripCatalogResponse> getCatalogs() {
        List<String> operatingAreas = destinationRepository.findAllActiveDestinations().stream()
                .map(d -> d.getDestinationName())
                .distinct()
                .collect(Collectors.toList());

        TripCatalogResponse response = TripCatalogResponse.builder()
                .operatingAreas(operatingAreas)
                .tripTypes(List.of("ROUND_TRIP", "ONE_WAY"))
                .fleetIntegrationStatus("DEFERRED_EXTERNAL")
                .supportedPolicies(List.of(
                        "PORT_CALL_BUFFER_30M",
                        "EMBARKATION_DAY_1",
                        "DISEMBARKATION_LAST_DAY",
                        "MAKER_CHECKER_SEPARATION",
                        "TIME_BASIS_PORT_OR_SHIP"
                ))
                .build();

        return ApiResponse.success(response);
    }

    @Operation(summary = "Nguồn hoạt động trên tàu (Onboard Activities) cho lập kế hoạch",
               description = "Cung cấp danh sách mẫu hoạt động trên tàu được phép tái sử dụng cho lập kế hoạch lịch trình (yêu cầu TRIP_READ, không yêu cầu quyền vận hành ACTIVITY_READ)")
    @PreAuthorize("hasAuthority('TRIP_READ')")
    @GetMapping("/sources/activities")
    public ApiResponse<List<PlanningSourceActivityResponse>> getSourceActivities() {
        List<OnboardActivity> activities = onboardActivityRepository.findAll();
        List<PlanningSourceActivityResponse> result = activities.stream()
                .map(a -> PlanningSourceActivityResponse.builder()
                        .activityId(a.getOnboardActivityId())
                        .sourceActivityId(a.getOnboardActivityId())
                        .activityCode(a.getActivityCode())
                        .activityName(a.getActivityName())
                        .description(a.getDescription())
                        .location(a.getLocation())
                        .price(a.getPrice())
                        .currencyCode(a.getCurrencyCode())
                        .build())
                .collect(Collectors.toList());
        return ApiResponse.success(result);
    }

    @Operation(summary = "Nguồn tham quan trên bờ (Shore Excursions) cho lập kế hoạch",
               description = "Cung cấp danh sách mẫu tour tham quan trên bờ được phép tái sử dụng cho lập kế hoạch lịch trình, có thể lọc theo cảng (yêu cầu TRIP_READ)")
    @PreAuthorize("hasAuthority('TRIP_READ')")
    @GetMapping("/sources/excursions")
    public ApiResponse<List<PlanningSourceExcursionResponse>> getSourceExcursions(
            @Parameter(description = "Lọc theo ID cảng ghé") @RequestParam(required = false) UUID portId) {
        List<ShoreExcursion> excursions = shoreExcursionRepository.findAll();
        List<PlanningSourceExcursionResponse> result = excursions.stream()
                .filter(e -> portId == null || (e.getCruiseStop() != null && e.getCruiseStop().getPort() != null
                        && portId.equals(e.getCruiseStop().getPort().getPortId())))
                .map(e -> {
                    UUID pId = null;
                    String pCode = null;
                    String pName = null;
                    if (e.getCruiseStop() != null && e.getCruiseStop().getPort() != null) {
                        pId = e.getCruiseStop().getPort().getPortId();
                        pCode = e.getCruiseStop().getPort().getPortCode();
                        pName = e.getCruiseStop().getPort().getPortName();
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
                            .price(e.getPrice())
                            .currencyCode(e.getCurrencyCode())
                            .build();
                })
                .collect(Collectors.toList());
        return ApiResponse.success(result);
    }

    @Operation(summary = "Nguồn dịch vụ / khung phục vụ (Services) cho lập kế hoạch",
               description = "Cung cấp danh sách dịch vụ chuẩn cho khung phục vụ (SERVING_WINDOW) (yêu cầu TRIP_READ)")
    @PreAuthorize("hasAuthority('TRIP_READ')")
    @GetMapping("/sources/services")
    public ApiResponse<List<PlanningSourceServiceResponse>> getSourceServices() {
        List<Service> services = serviceRepository.findAll();
        List<PlanningSourceServiceResponse> result = services.stream()
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
}
