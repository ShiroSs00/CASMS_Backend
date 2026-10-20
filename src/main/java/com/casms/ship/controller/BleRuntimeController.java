package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.ship.dto.request.BleReadingRequest;
import com.casms.ship.dto.response.BleTrackingResponse;
import com.casms.ship.service.BleRuntimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@Tag(name = "BLE Runtime", description = "Authenticated RSSI ingestion and range-only tracking snapshots")
@RestController
@RequiredArgsConstructor
@RequestMapping("/cruises/{cruiseId}/ble-receivers/{receiverId}")
public class BleRuntimeController {
    private final BleRuntimeService service;

    @Operation(summary = "Record a fresh BLE advertisement measured by the receiver")
    @PreAuthorize("hasAuthority('BLE_TELEMETRY_RECEIVE')")
    @PostMapping("/observations")
    public ApiResponse<Void> ingest(@PathVariable UUID cruiseId, @PathVariable UUID receiverId,
                                    @Valid @RequestBody BleReadingRequest request) {
        service.ingest(cruiseId, receiverId, request);
        return ApiResponse.success("BLE RSSI observation accepted", null);
    }

    @Operation(summary = "Read a range-only RSSI snapshot for an assigned tag")
    @PreAuthorize("hasAuthority('BLE_TELEMETRY_READ')")
    @GetMapping("/tracking")
    public ApiResponse<BleTrackingResponse> snapshot(@PathVariable UUID cruiseId, @PathVariable UUID receiverId,
                                                     @RequestParam String bleIdentifier) {
        return ApiResponse.success(service.snapshot(cruiseId, receiverId, bleIdentifier));
    }

    @Operation(summary = "Calibrate reference RSSI while the assigned tag is held at 1 meter")
    @PreAuthorize("hasAuthority('BLE_ANCHOR_UPDATE')")
    @PostMapping("/calibrate-at-one-meter")
    public ApiResponse<BleTrackingResponse> calibrate(@PathVariable UUID cruiseId, @PathVariable UUID receiverId,
                                                      @RequestParam String bleIdentifier) {
        return ApiResponse.success(service.calibrateAtOneMeter(cruiseId, receiverId, bleIdentifier));
    }
}
