package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.dto.request.CruiseDelayRequest;
import com.casms.ship.service.CruiseDelayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/cruises/{cruiseId}/delays")
@RequiredArgsConstructor
public class CruiseDelayController {
    private final CruiseDelayService service;

    @GetMapping
    @PreAuthorize("hasAuthority('CRUISE_READ')")
    public ApiResponse<CruiseDelayService.History> history(@PathVariable UUID cruiseId) {
        return ApiResponse.success(service.history(cruiseId));
    }

    @PostMapping("/preview")
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('CRUISE_START')")
    public ApiResponse<CruiseDelayService.Preview> preview(
            @PathVariable UUID cruiseId, @Valid @RequestBody CruiseDelayRequest request) {
        return ApiResponse.success(service.preview(cruiseId, request));
    }

    @PostMapping
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('CRUISE_START')")
    public ApiResponse<CruiseDelayService.Event> confirm(
            @PathVariable UUID cruiseId,
            @Valid @RequestBody CruiseDelayRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success(service.confirm(cruiseId, request, principal.getUserId()));
    }
}
