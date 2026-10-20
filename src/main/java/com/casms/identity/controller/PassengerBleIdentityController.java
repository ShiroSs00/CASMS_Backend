package com.casms.identity.controller;

import com.casms.common.response.ApiResponse;
import com.casms.identity.dto.PassengerBleAssignRequest;
import com.casms.identity.dto.PassengerBleIdentityResponse;
import com.casms.identity.dto.PassengerBleRevokeRequest;
import com.casms.identity.service.PassengerBleIdentityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@Tag(name = "Passenger BLE Identity", description = "Gán, đổi, thu hồi và truy vết vòng đời BLE tag theo cruise")
@RestController
@RequiredArgsConstructor
@RequestMapping("/cruises/{cruiseId}/passenger-ble-identities")
public class PassengerBleIdentityController {
    private final PassengerBleIdentityService service;

    @Operation(summary = "Cấp phát BLE tag cho hành khách")
    @PreAuthorize("hasAuthority('PASSENGER_BLE_ASSIGN')")
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PassengerBleIdentityResponse> assign(@PathVariable UUID cruiseId,
            @Valid @RequestBody PassengerBleAssignRequest request) {
        return ApiResponse.success(service.assign(cruiseId, request.getBookingPassengerId(),
                request.getBleIdentifier(), request.getDeviceName()));
    }

    @PreAuthorize("hasAuthority('PASSENGER_BLE_READ')")
    @GetMapping("/active")
    public ApiResponse<PassengerBleIdentityResponse> active(@PathVariable UUID cruiseId,
            @RequestParam UUID bookingPassengerId) {
        return ApiResponse.success(service.active(cruiseId, bookingPassengerId));
    }

    @PreAuthorize("hasAuthority('PASSENGER_BLE_READ')")
    @GetMapping
    public ApiResponse<List<PassengerBleIdentityResponse>> activeForCruise(@PathVariable UUID cruiseId) {
        return ApiResponse.success(service.activeForCruise(cruiseId));
    }

    @PreAuthorize("hasAuthority('PASSENGER_BLE_HISTORY')")
    @GetMapping("/history")
    public ApiResponse<List<PassengerBleIdentityResponse>> history(@PathVariable UUID cruiseId,
            @RequestParam UUID bookingPassengerId) {
        return ApiResponse.success(service.history(cruiseId, bookingPassengerId));
    }

    @Operation(summary = "Thu hồi BLE tag, lưu giữ lịch sử")
    @PreAuthorize("hasAuthority('PASSENGER_BLE_REVOKE')")
    @PostMapping("/{identityId}/revoke")
    public ApiResponse<PassengerBleIdentityResponse> revoke(@PathVariable UUID cruiseId,
            @PathVariable UUID identityId, @Valid @RequestBody PassengerBleRevokeRequest request) {
        return ApiResponse.success(service.revoke(cruiseId, identityId, request.getReason()));
    }
}
