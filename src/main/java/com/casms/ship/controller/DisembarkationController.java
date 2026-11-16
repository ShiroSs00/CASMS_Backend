package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.identity.enums.Role;
import com.casms.ship.service.DisembarkationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/cruise/{cruiseId}/disembarkation")
public class DisembarkationController {
    private final DisembarkationService service;

    @GetMapping
    @PreAuthorize("hasAuthority('CRUISE_READ')")
    public ApiResponse<DisembarkationService.Plan> get(
            @PathVariable UUID cruiseId, @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(service.get(cruiseId, actor.hasRole(Role.OPERATIONS_MANAGER)
                && actor.hasAuthority("CRUISE_VALIDATE")));
    }

    @PutMapping
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('CRUISE_VALIDATE')")
    public ApiResponse<DisembarkationService.Plan> save(
            @PathVariable UUID cruiseId, @Valid @RequestBody DisembarkationService.Input input) {
        return ApiResponse.success(service.save(cruiseId, input));
    }
}
