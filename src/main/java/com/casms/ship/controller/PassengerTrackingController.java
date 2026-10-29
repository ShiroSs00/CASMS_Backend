package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.ship.dto.response.PassengerTrackingResponse;
import com.casms.ship.service.BleRuntimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/cruises/{cruiseId}/passenger-tracking")
public class PassengerTrackingController {
    private final BleRuntimeService service;

    @GetMapping
    @PreAuthorize("hasRole('SHIP_MANAGER') and hasAuthority('BLE_TELEMETRY_READ')")
    public ApiResponse<PassengerTrackingResponse> snapshot(@PathVariable UUID cruiseId) {
        return ApiResponse.success(service.passengerOverview(cruiseId));
    }
}
