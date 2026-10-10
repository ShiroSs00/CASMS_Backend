package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.ship.dto.*;
import com.casms.ship.entity.CabinStatus;
import com.casms.ship.service.CabinService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cabin")
@RequiredArgsConstructor
public class CabinController {

    private final CabinService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CabinSummaryResponse> create(@RequestBody @Valid CabinCreateRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @GetMapping
    public ApiResponse<Page<CabinSummaryResponse>> list(Pageable pageable) {
        return ApiResponse.success(service.list(pageable));
    }

    @GetMapping("/{cabinId}")
    public ApiResponse<CabinSummaryResponse> detail(@PathVariable UUID cabinId) {
        return ApiResponse.success(service.detail(cabinId));
    }

    @PatchMapping("/{cabinId}")
    public ApiResponse<CabinSummaryResponse> update(@PathVariable UUID cabinId, 
                                                    @RequestBody @Valid CabinUpdateRequest request) {
        return ApiResponse.success(service.update(cabinId, request));
    }

    @DeleteMapping("/{cabinId}/unused")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deleteUnused(@PathVariable UUID cabinId) {
        service.deleteUnused(cabinId);
        return ApiResponse.success("Deleted unused cabin successfully", null);
    }

    @PostMapping("/search")
    public ApiResponse<List<CabinSummaryResponse>> searchAvailable(@RequestBody CabinSearchRequest request) {
        return ApiResponse.success(service.searchAvailable(request));
    }

    @GetMapping("/{cabinId}/capacity-check")
    public ApiResponse<Boolean> checkCapacity(@PathVariable UUID cabinId, 
                                              @RequestParam Integer requiredCapacity) {
        return ApiResponse.success(service.checkCapacity(cabinId, requiredCapacity));
    }

    @GetMapping("/{cabinId}/compatibility-check")
    public ApiResponse<Boolean> checkCompatibility(@PathVariable UUID cabinId, 
                                                   @RequestParam(required = false) Boolean requireAccessible) {
        return ApiResponse.success(service.checkCompatibility(cabinId, requireAccessible));
    }

    @PostMapping("/{cabinId}/status")
    public ApiResponse<CabinSummaryResponse> changeOperationalStatus(@PathVariable UUID cabinId, 
                                                                     @RequestParam CabinStatus status) {
        return ApiResponse.success(service.changeOperationalStatus(cabinId, status));
    }

    @GetMapping("/{cabinId}/assignments")
    public ApiResponse<List<CabinAssignmentHistoryResponse>> getAssignmentHistory(@PathVariable UUID cabinId) {
        return ApiResponse.success(service.getAssignmentHistory(cabinId));
    }

    @PostMapping("/{cabinId}/validate")
    public ApiResponse<Void> validateCabin(@PathVariable UUID cabinId) {
        service.validateCabin(cabinId);
        return ApiResponse.success("Cabin validation passed", null);
    }

    @GetMapping("/{cabinId}/availability")
    public ApiResponse<Boolean> checkAvailability(@PathVariable UUID cabinId) {
        return ApiResponse.success(service.checkAvailability(cabinId));
    }

    @PostMapping("/{cabinId}/block")
    public ApiResponse<CabinSummaryResponse> blockCabin(@PathVariable UUID cabinId, 
                                                        @RequestParam CabinStatus reasonStatus) {
        return ApiResponse.success(service.blockCabin(cabinId, reasonStatus));
    }

    @PostMapping("/{cabinId}/release")
    public ApiResponse<CabinSummaryResponse> releaseCabin(@PathVariable UUID cabinId) {
        return ApiResponse.success(service.releaseCabin(cabinId));
    }
}