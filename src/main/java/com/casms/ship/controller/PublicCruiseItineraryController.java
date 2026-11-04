package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.ship.dto.response.PublicCruiseItineraryResponse;
import com.casms.ship.service.PublicCruiseItineraryService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PublicCruiseItineraryController {
    private final PublicCruiseItineraryService service;

    @GetMapping({"/cruise/{cruiseId}/public-itinerary", "/cruises/{cruiseId}/public-itinerary"})
    @PreAuthorize("permitAll()")
    @Operation(summary = "Lịch trình công khai của Departure đã phát hành",
            description = "Đọc ngày, cảng và DestinationVisit từ lịch trình thực tế. Dữ liệu lịch trình không xác nhận trạng thái mở bán. Dùng sellability và catalog Cruise Package riêng.")
    public ApiResponse<PublicCruiseItineraryResponse> get(@PathVariable UUID cruiseId) {
        return ApiResponse.success(service.get(cruiseId));
    }
}
