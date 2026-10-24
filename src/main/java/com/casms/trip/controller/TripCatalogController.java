package com.casms.trip.controller;

import com.casms.common.response.ApiResponse;
import com.casms.ship.repository.DestinationRepository;
import com.casms.trip.dto.response.TripCatalogResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "Trip Planning Catalogs", description = "Danh mục chuẩn cho phân hệ lập kế hoạch hành trình")
@RestController
@RequestMapping("/trip-planning/catalogs")
@RequiredArgsConstructor
public class TripCatalogController {

    private final DestinationRepository destinationRepository;

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
}
