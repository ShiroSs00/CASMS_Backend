package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.service.CruiseServiceLocationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/cruise")
public class CruiseServiceLocationController {
    private final CruiseServiceLocationService service;

    public record Proposal(@NotNull UUID serviceId, @NotNull UUID deckId,
                           @NotBlank @Size(max = 500) String note) {}
    public record Review(@NotNull Long revision, @NotNull Boolean confirm, @Size(max = 500) String note) {}

    @GetMapping("/service-locations/inbox")
    @PreAuthorize("hasRole('SHIP_MANAGER') and hasAuthority('CRUISE_PREPARATION_READ')")
    public ApiResponse<java.util.List<CruiseServiceLocationService.InboxItem>> inbox(
            @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(service.inbox(actor.getUserId()));
    }

    @GetMapping("/{cruiseId}/service-locations")
    @PreAuthorize("hasAuthority('CRUISE_READ')")
    public ApiResponse<CruiseServiceLocationService.View> view(
            @PathVariable UUID cruiseId, @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(service.view(cruiseId, actor));
    }

    @PostMapping("/{cruiseId}/service-locations")
    @PreAuthorize("hasAuthority('CRUISE_READ') and hasAuthority('TOUR_PACKAGE_MANAGE')")
    public ApiResponse<CruiseServiceLocationService.View> propose(
            @PathVariable UUID cruiseId, @Valid @RequestBody Proposal proposal,
            @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(service.propose(cruiseId, proposal.serviceId(),
                proposal.deckId(), proposal.note().strip(), actor));
    }

    @PostMapping("/{cruiseId}/service-locations/{serviceId}/review")
    @PreAuthorize("hasAuthority('CRUISE_READ') and hasAuthority('CRUISE_PREPARATION_READ')")
    public ApiResponse<CruiseServiceLocationService.View> review(
            @PathVariable UUID cruiseId, @PathVariable UUID serviceId,
            @Valid @RequestBody Review input, @AuthenticationPrincipal CasmsPrincipal actor) {
        return ApiResponse.success(service.review(cruiseId, serviceId, input.revision(), input.confirm(),
                input.note() == null ? null : input.note().strip(), actor));
    }
}
