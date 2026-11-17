package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.ship.dto.request.ShipOperationalProfile;
import com.casms.ship.service.ShipTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/ships")
@RequiredArgsConstructor
public class ShipOperationalProfileController {
    private final ShipTypeService service;

    @PreAuthorize("hasRole('SHIP_MANAGER')")
    @PutMapping("/{shipId}/operational-profile")
    public ApiResponse<ShipOperationalProfile> replace(@PathVariable UUID shipId,
            @Valid @RequestBody ShipOperationalProfile profile) {
        return ApiResponse.success(service.replaceProfile(shipId, profile));
    }

    @PreAuthorize("hasRole('SHIP_MANAGER')")
    @GetMapping("/{shipId}/operational-profile")
    public ApiResponse<ShipOperationalProfile> detail(@PathVariable UUID shipId) {
        return ApiResponse.success(service.profile(shipId));
    }
}
