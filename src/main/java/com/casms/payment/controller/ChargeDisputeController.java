package com.casms.payment.controller;

import com.casms.common.response.ApiResponse;
import com.casms.payment.dto.request.ChargeDisputeCreateRequest;
import com.casms.payment.dto.request.ChargeDisputeResolveRequest;
import com.casms.payment.dto.response.ChargeDisputeResponse;
import com.casms.payment.service.ChargeDisputeService;
import com.casms.security.principal.CasmsPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(
        name = "Charge Dispute Management",
        description =
                "Quản lý khiếu nại sai sót chi phí dịch vụ trên tàu (Folio Disputes) và xử lý hoàn"
                    + " tác")
@RestController
@RequestMapping("/charge-disputes")
@RequiredArgsConstructor
public class ChargeDisputeController {

    private final ChargeDisputeService disputeService;

    @Operation(
            summary = "Hành khách hoặc Lễ tân tạo khiếu nại khoản chi phí dịch vụ trên tàu bị sai")
    @PreAuthorize("isAuthenticated()")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChargeDisputeResponse> createDispute(
            @RequestBody @Valid ChargeDisputeCreateRequest request) {
        return ApiResponse.success(
                "Dispute reported successfully", disputeService.createDispute(request));
    }

    @Operation(
            summary =
                    "Nhân viên tài chính / Lễ tân giải quyết khiếu nại (hoàn tác / điều chỉnh khoản"
                        + " phí)")
    @PreAuthorize("hasAnyAuthority('PAYMENT_REFUND', 'PAYMENT_PROCESS')")
    @PostMapping("/{disputeId}/resolve")
    public ApiResponse<ChargeDisputeResponse> resolveDispute(
            @Parameter(description = "ID khiếu nại", required = true) @PathVariable UUID disputeId,
            @RequestBody @Valid ChargeDisputeResolveRequest request,
            Authentication auth) {
        UUID staffId = null;
        if (auth != null && auth.getPrincipal() instanceof CasmsPrincipal principal) {
            staffId = principal.getUserId();
        }
        return ApiResponse.success(
                "Dispute resolved successfully",
                disputeService.resolveDispute(disputeId, request, staffId));
    }

    @Operation(summary = "Xem chi tiết khiếu nại khoản chi phí")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{disputeId}")
    public ApiResponse<ChargeDisputeResponse> getDispute(
            @Parameter(description = "ID khiếu nại", required = true) @PathVariable
                    UUID disputeId) {
        return ApiResponse.success(disputeService.getDispute(disputeId));
    }

    @Operation(summary = "Lấy danh sách khiếu nại theo hành khách")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/passenger/{passengerId}")
    public ApiResponse<List<ChargeDisputeResponse>> getDisputesForPassenger(
            @Parameter(description = "ID hành khách", required = true) @PathVariable
                    UUID passengerId) {
        return ApiResponse.success(disputeService.getDisputesForPassenger(passengerId));
    }

    @Operation(summary = "Lấy danh sách khiếu nại theo tài khoản phòng trên tàu")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/onboard/{onboardAccountId}")
    public ApiResponse<List<ChargeDisputeResponse>> getDisputesForOnboardAccount(
            @Parameter(description = "ID tài khoản phòng trên tàu", required = true) @PathVariable
                    UUID onboardAccountId) {
        return ApiResponse.success(disputeService.getDisputesForOnboardAccount(onboardAccountId));
    }
}
