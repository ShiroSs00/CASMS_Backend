package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.dto.request.CruisePreparationConfirmRequest;
import com.casms.ship.dto.request.CruisePreparationReturnRequest;
import com.casms.ship.service.CruisePreparationService;
import com.casms.ship.service.CruisePreparationService.GroupReview;
import com.casms.ship.service.CruisePreparationService.GroupReviewSummary;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/cruise-preparation-reviews", "/api/cruise-preparation-reviews"})
@PreAuthorize("hasRole('OPERATIONS_MANAGER') and hasAuthority('CRUISE_PREPARATION_CONFIRM')")
@RequiredArgsConstructor
public class CruisePreparationReviewController {
    private final CruisePreparationService preparation;

    @GetMapping
    public ApiResponse<List<GroupReviewSummary>> list(@AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success(preparation.submittedGroupReviews(principal.getUserId()));
    }

    @GetMapping("/{cruiseId}/{sectionCode}")
    public ApiResponse<GroupReview> detail(@PathVariable UUID cruiseId, @PathVariable String sectionCode,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success(preparation.submittedGroupReview(cruiseId, sectionCode, principal.getUserId()));
    }

    @PostMapping("/{cruiseId}/{sectionCode}/confirm")
    public ApiResponse<Void> confirm(@PathVariable UUID cruiseId, @PathVariable String sectionCode,
            @Valid @RequestBody CruisePreparationConfirmRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        preparation.submittedGroupReview(cruiseId, sectionCode, principal.getUserId());
        preparation.confirmSection(cruiseId, sectionCode, request, principal.getUserId(), principal.getUsername());
        return ApiResponse.success(null);
    }

    @PostMapping("/{cruiseId}/{sectionCode}/return")
    public ApiResponse<Void> requestChanges(@PathVariable UUID cruiseId, @PathVariable String sectionCode,
            @Valid @RequestBody CruisePreparationReturnRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        preparation.submittedGroupReview(cruiseId, sectionCode, principal.getUserId());
        preparation.returnSection(cruiseId, sectionCode, request, principal.getUserId());
        return ApiResponse.success(null);
    }

    @PostMapping("/{cruiseId}/{sectionCode}/reject")
    public ApiResponse<Void> reject(@PathVariable UUID cruiseId, @PathVariable String sectionCode,
            @Valid @RequestBody CruisePreparationReturnRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        preparation.submittedGroupReview(cruiseId, sectionCode, principal.getUserId());
        preparation.rejectSection(cruiseId, sectionCode, request, principal.getUserId());
        return ApiResponse.success(null);
    }
}
