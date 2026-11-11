package com.casms.payment.controller;

import com.casms.common.response.ApiResponse;
import com.casms.payment.dto.request.RefundApproveRequest;
import com.casms.payment.dto.request.RefundCompleteRequest;
import com.casms.payment.dto.request.RefundCreateRequest;
import com.casms.payment.dto.response.RefundResponse;
import com.casms.payment.service.RefundService;
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
        name = "Refund Management",
        description =
                "Quản lý yêu cầu hoàn tiền, phê duyệt hoàn tiền và đối soát giao dịch hoàn tiền")
@RestController
@RequestMapping("/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    @Operation(summary = "Tạo yêu cầu hoàn tiền cho giao dịch thanh toán gốc")
    @PreAuthorize("hasAnyAuthority('PAYMENT_REFUND', 'PAYMENT_PROCESS')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RefundResponse> createRefund(
            @RequestBody @Valid RefundCreateRequest request) {
        return ApiResponse.success(
                "Refund requested successfully", refundService.createRefund(request));
    }

    @Operation(summary = "Phê duyệt yêu cầu hoàn tiền (dành cho Kế toán / Quản lý)")
    @PreAuthorize("hasAuthority('PAYMENT_REFUND')")
    @PostMapping("/{refundId}/approve")
    public ApiResponse<RefundResponse> approveRefund(
            @Parameter(description = "ID yêu cầu hoàn tiền", required = true) @PathVariable
                    UUID refundId,
            @RequestBody @Valid RefundApproveRequest request,
            Authentication auth) {
        UUID approverId = null;
        if (auth != null && auth.getPrincipal() instanceof CasmsPrincipal principal) {
            approverId = principal.getUserId();
        }
        return ApiResponse.success(
                "Refund approved", refundService.approveRefund(refundId, request, approverId));
    }

    @Operation(summary = "Xác nhận hoàn tất chuyển tiền hoàn trả (gắn mã biên lai/giao dịch)")
    @PreAuthorize("hasAuthority('PAYMENT_REFUND')")
    @PostMapping("/{refundId}/complete")
    public ApiResponse<RefundResponse> completeRefund(
            @Parameter(description = "ID yêu cầu hoàn tiền", required = true) @PathVariable
                    UUID refundId,
            @RequestBody @Valid RefundCompleteRequest request) {
        return ApiResponse.success(
                "Refund completed successfully", refundService.completeRefund(refundId, request));
    }

    @Operation(summary = "Từ chối yêu cầu hoàn tiền")
    @PreAuthorize("hasAuthority('PAYMENT_REFUND')")
    @PostMapping("/{refundId}/reject")
    public ApiResponse<RefundResponse> rejectRefund(
            @Parameter(description = "ID yêu cầu hoàn tiền", required = true) @PathVariable
                    UUID refundId,
            @RequestParam(required = false, defaultValue = "Rejected by finance authority")
                    String reason) {
        return ApiResponse.success("Refund rejected", refundService.rejectRefund(refundId, reason));
    }

    @Operation(summary = "Xem chi tiết yêu cầu hoàn tiền")
    @PreAuthorize("hasAnyAuthority('PAYMENT_READ', 'PAYMENT_REFUND')")
    @GetMapping("/{refundId}")
    public ApiResponse<RefundResponse> getRefund(
            @Parameter(description = "ID yêu cầu hoàn tiền", required = true) @PathVariable
                    UUID refundId) {
        return ApiResponse.success(refundService.getRefund(refundId));
    }

    @Operation(summary = "Lấy danh sách yêu cầu hoàn tiền theo đơn đặt tour")
    @PreAuthorize("hasAnyAuthority('PAYMENT_READ', 'PAYMENT_REFUND')")
    @GetMapping("/booking/{bookingId}")
    public ApiResponse<List<RefundResponse>> getRefundsForBooking(
            @Parameter(description = "ID đơn đặt tour", required = true) @PathVariable
                    UUID bookingId) {
        return ApiResponse.success(refundService.getRefundsForBooking(bookingId));
    }

    @Operation(summary = "Lấy danh sách yêu cầu hoàn tiền theo giao dịch thanh toán")
    @PreAuthorize("hasAnyAuthority('PAYMENT_READ', 'PAYMENT_REFUND')")
    @GetMapping("/payment/{paymentId}")
    public ApiResponse<List<RefundResponse>> getRefundsForPayment(
            @Parameter(description = "ID giao dịch thanh toán", required = true) @PathVariable
                    UUID paymentId) {
        return ApiResponse.success(refundService.getRefundsForPayment(paymentId));
    }
}
