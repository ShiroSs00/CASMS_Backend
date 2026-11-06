package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.service.CruiseMainflowService;
import com.casms.ship.service.CruisePlanPackageSelectionService;
import com.casms.ship.service.DepartureSaleTermsService;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/cruise-plans")
public class CruiseMainflowController {
    private final CruiseMainflowService service;

    private final CruisePlanPackageSelectionService selections;
    private final DepartureSaleTermsService saleTerms;

    @GetMapping("/{cruiseId}/cruise-packages")
    @PreAuthorize("hasAuthority('CRUISE_READ') and hasAnyRole('CRUISE_MANAGER','OPERATIONS_MANAGER')")
    public ApiResponse<CruisePlanPackageSelectionService.View> packageSelection(@PathVariable UUID cruiseId) {
        return ApiResponse.success(selections.read(cruiseId));
    }

    @PutMapping("/{cruiseId}/cruise-packages")
    @PreAuthorize("hasRole('CRUISE_MANAGER') and hasAuthority('CRUISE_UPDATE_DRAFT')")
    public ApiResponse<CruisePlanPackageSelectionService.View> selectPackages(
            @PathVariable UUID cruiseId, @Valid @RequestBody CruisePlanPackageSelectionService.Write input,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success(selections.save(cruiseId, input, principal.getUserId()));
    }

    @GetMapping("/{cruiseId}/commercial-terms/sale")
    @PreAuthorize("(hasRole('OPERATIONS_MANAGER') and hasAuthority('CRUISE_PRICING_APPROVE')) or "
            + "(hasRole('CRUISE_MANAGER') and hasAuthority('CRUISE_READ'))")
    public ApiResponse<DepartureSaleTermsService.View> saleTerms(@PathVariable UUID cruiseId) {
        return ApiResponse.success(saleTerms.read(cruiseId));
    }

    @GetMapping("/{cruiseId}/commercial-terms/sale/history")
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('CRUISE_PRICING_APPROVE')")
    public ApiResponse<List<DepartureSaleTermsService.HistoryEntry>> saleTermsHistory(@PathVariable UUID cruiseId) {
        return ApiResponse.success(saleTerms.history(cruiseId));
    }

    @PutMapping("/{cruiseId}/commercial-terms/sale")
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('CRUISE_PRICING_APPROVE')")
    public ApiResponse<DepartureSaleTermsService.View> saveSaleTerms(
            @PathVariable UUID cruiseId, @Valid @RequestBody DepartureSaleTermsService.Write input,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success(saleTerms.save(cruiseId, input, principal.getUserId()));
    }

    @GetMapping("/{cruiseId}/ship-assignment-context")
    @PreAuthorize(
            "hasRole('CRUISE_MANAGER') and"
                + " hasAnyAuthority('CRUISE_ASSIGN_SHIP','CRUISE_CHANGE_SHIP','CRUISE_UNASSIGN_SHIP')")
    public ApiResponse<CruiseMainflowService.AssignmentContext> assignmentContext(
            @PathVariable UUID cruiseId) {
        return ApiResponse.success(service.assignmentContext(cruiseId));
    }

    @GetMapping("/{cruiseId}")
    @PreAuthorize(
            "hasAuthority('CRUISE_READ') and"
                + " hasAnyRole('CRUISE_MANAGER','OPERATIONS_MANAGER','SHIP_MANAGER')")
    public ApiResponse<CruiseMainflowService.View> get(@PathVariable UUID cruiseId) {
        return ApiResponse.success(service.get(cruiseId));
    }

    @GetMapping
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('CRUISE_READ')")
    public ApiResponse<List<CruiseMainflowService.View>> queue() {
        return ApiResponse.success(service.queue());
    }

    @PutMapping("/{cruiseId}/windows")
    @PreAuthorize("hasRole('CRUISE_MANAGER') and hasAuthority('CRUISE_UPDATE_DRAFT')")
    public ApiResponse<CruiseMainflowService.View> windows(
            @PathVariable UUID cruiseId,
            @RequestBody CruiseMainflowService.Windows request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success(service.windows(cruiseId, request, principal.getUserId()));
    }

    @PostMapping("/{cruiseId}/{action:submit|withdraw}")
    @PreAuthorize("hasRole('CRUISE_MANAGER') and hasAuthority('CRUISE_UPDATE_DRAFT')")
    public ApiResponse<CruiseMainflowService.View> submit(
            @PathVariable UUID cruiseId,
            @PathVariable String action,
            @RequestBody CruiseMainflowService.Action request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success(
                service.transition(
                        cruiseId,
                        action.toUpperCase(java.util.Locale.ROOT),
                        request,
                        principal.getUserId()));
    }

    @PostMapping("/{cruiseId}/{action:approve|reject|request-revision}")
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('ITINERARY_APPROVE')")
    public ApiResponse<CruiseMainflowService.View> review(
            @PathVariable UUID cruiseId,
            @PathVariable String action,
            @RequestBody CruiseMainflowService.Action request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success(
                service.transition(
                        cruiseId,
                        action.toUpperCase(java.util.Locale.ROOT).replace('-', '_'),
                        request,
                        principal.getUserId()));
    }

    @PostMapping("/{cruiseId}/ready")
    @PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('CRUISE_VALIDATE')")
    public ApiResponse<CruiseMainflowService.View> ready(
            @PathVariable UUID cruiseId,
            @RequestBody CruiseMainflowService.Action request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success(service.setReady(cruiseId, request, principal.getUserId()));
    }
}
