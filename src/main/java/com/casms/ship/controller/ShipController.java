package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.ship.dto.*;
import com.casms.ship.entity.ShipStatus;
import com.casms.ship.service.ShipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ship")
@RequiredArgsConstructor
public class ShipController {

    private final ShipService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ShipResponse> create(@RequestBody @Valid ShipCreateRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @GetMapping
    public ApiResponse<Page<ShipResponse>> list(Pageable pageable) {
        return ApiResponse.success(service.list(pageable));
    }

    @GetMapping("/{shipId}")
    public ApiResponse<ShipResponse> detail(@PathVariable UUID shipId) {
        return ApiResponse.success(service.detail(shipId));
    }

    @PatchMapping("/{shipId}")
    public ApiResponse<ShipResponse> update(@PathVariable UUID shipId, 
                                            @RequestBody @Valid ShipUpdateRequest request) {
        return ApiResponse.success(service.update(shipId, request));
    }

    @DeleteMapping("/{shipId}/unused")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deleteUnused(@PathVariable UUID shipId) {
        service.deleteUnused(shipId);
        return ApiResponse.success("Deleted unused ship successfully", null);
    }

    @PostMapping("/{shipId}/status")
    public ApiResponse<ShipResponse> changeStatus(@PathVariable UUID shipId, 
                                                  @RequestParam ShipStatus status) {
        return ApiResponse.success(service.changeStatus(shipId, status));
    }

    @GetMapping("/{shipId}/availability")
    public ApiResponse<Boolean> checkScheduleAvailability(
            @PathVariable UUID shipId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {
        return ApiResponse.success(service.checkScheduleAvailability(shipId, start, end));
    }

    @GetMapping("/{shipId}/capacity-summary")
    public ApiResponse<ShipCapacitySummaryResponse> getCapacitySummary(@PathVariable UUID shipId) {
        return ApiResponse.success(service.getCapacitySummary(shipId));
    }

    @GetMapping("/{shipId}/affected-cruises")
    public ApiResponse<List<CruiseSummaryResponse>> getAffectedCruises(
            @PathVariable UUID shipId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {
        return ApiResponse.success(service.getAffectedCruises(shipId, start, end));
    }

    @GetMapping("/{shipId}/current-cruise")
    public ApiResponse<CruiseSummaryResponse> getCurrentCruise(@PathVariable UUID shipId) {
        return ApiResponse.success(service.getCurrentCruise(shipId));
    }

    @PostMapping("/{shipId}/validate")
    public ApiResponse<Void> validateShip(@PathVariable UUID shipId) {
        service.validateShip(shipId);
        return ApiResponse.success("Ship validation passed", null);
    }

    @GetMapping("/{shipId}/readiness")
    public ApiResponse<Boolean> validateOperationalReadiness(@PathVariable UUID shipId) {
        return ApiResponse.success(service.validateOperationalReadiness(shipId));
    }
}
