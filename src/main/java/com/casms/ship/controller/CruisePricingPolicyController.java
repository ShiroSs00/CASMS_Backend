package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.dto.request.CruisePricingPolicyRequest;
import com.casms.ship.dto.response.CruisePricingPolicyResponse;
import com.casms.ship.service.CruisePricingPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping({"/cruises/{cruiseId}/pricing-policy", "/cruise/{cruiseId}/pricing-policy", "/api/cruises/{cruiseId}/pricing-policy", "/api/cruise/{cruiseId}/pricing-policy"})
@RequiredArgsConstructor
@Tag(name = "Cruise Pricing Policy", description = "Quản lý chính sách giá thương mại của chuyến đi (BE-H01)")
public class CruisePricingPolicyController {

    private final CruisePricingPolicyService pricingPolicyService;

    @Operation(summary = "Xem chính sách giá của chuyến đi", description = "Truy xuất cấu hình giá, phí cảng, thuế, tiền cọc và điều khoản hủy vé")
    @PreAuthorize("hasAnyAuthority('CRUISE_READ', 'CRUISE_UPDATE_DRAFT', 'CRUISE_OPEN_SALE', 'BOOKING_READ')")
    @GetMapping
    public ApiResponse<CruisePricingPolicyResponse> getPricingPolicy(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId) {
        return ApiResponse.success(pricingPolicyService.getPricingPolicy(cruiseId));
    }

    @Operation(summary = "Cấu hình / cập nhật chính sách giá", description = "Thiết lập giá cơ sở từng hạng phòng, tỷ lệ cọc, thuế và hạn thanh toán")
    @PreAuthorize("hasAnyAuthority('CRUISE_UPDATE_DRAFT', 'CRUISE_OPEN_SALE')")
    @PutMapping
    public ApiResponse<CruisePricingPolicyResponse> savePricingPolicy(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId,
            @RequestBody @Valid CruisePricingPolicyRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        boolean isApprover = principal != null && (
                principal.hasPermission(com.casms.identity.enums.Permission.CRUISE_OPEN_SALE) ||
                principal.hasPermission(com.casms.identity.enums.Permission.CRUISE_UPDATE_DRAFT));
        return ApiResponse.success("Lưu chính sách giá thành công",
                pricingPolicyService.savePricingPolicy(cruiseId, request, actorId, isApprover));
    }

    @Operation(summary = "Phê duyệt chính sách giá", description = "Phê duyệt chính sách giá thương mại để đủ điều kiện mở bán (Sale Readiness Gate)")
    @PreAuthorize("hasAnyAuthority('CRUISE_OPEN_SALE', 'CRUISE_UPDATE_DRAFT')")
    @PostMapping("/approve")
    public ApiResponse<CruisePricingPolicyResponse> approvePricingPolicy(
            @Parameter(description = "ID chuyến đi", required = true) @PathVariable UUID cruiseId,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID actorId = principal != null ? principal.getUserId() : null;
        return ApiResponse.success("Phê duyệt chính sách giá thành công",
                pricingPolicyService.approvePricingPolicy(cruiseId, actorId));
    }
}
