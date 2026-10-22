package com.casms.payment.controller;

import com.casms.common.response.ApiResponse;
import com.casms.payment.dto.request.PaymentConfirmRequest;
import com.casms.payment.dto.request.PaymentCreateRequest;
import com.casms.payment.dto.response.PaymentResponse;
import com.casms.payment.service.PaymentService;
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

@Tag(name = "Finance & Payment", description = "Quản lý thanh toán, xác nhận giao dịch và đồng bộ trạng thái đơn đặt tour")
@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService service;

    @Operation(summary = "Khởi tạo giao dịch thanh toán", description = "Tạo yêu cầu thanh toán cho đơn đặt tour (hỗ trợ bảo đảm Idempotency)")
    @PreAuthorize("hasAuthority('PAYMENT_PROCESS')")
    @PostMapping("/process")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PaymentResponse> createPayment(
            @RequestBody @Valid PaymentCreateRequest request) {
        return ApiResponse.success("Payment initiated successfully", service.createPayment(request));
    }

    @Operation(summary = "Xác nhận giao dịch thanh toán thành công", description = "Xác nhận kết quả thanh toán từ cổng thanh toán/quầy thu ngân, tự động chuyển đơn sang Confirmed nếu đủ tiền")
    @PreAuthorize("hasAuthority('PAYMENT_PROCESS')")
    @PostMapping("/{paymentId}/confirm")
    public ApiResponse<PaymentResponse> confirmPayment(
            @Parameter(description = "ID giao dịch thanh toán", required = true) @PathVariable UUID paymentId,
            @RequestBody @Valid PaymentConfirmRequest request) {
        return ApiResponse.success("Payment confirmed successfully", service.confirmPayment(paymentId, request));
    }

    @Operation(summary = "Lịch sử thanh toán của đơn đặt tour", description = "Lấy toàn bộ các lần thanh toán cọc/toàn bộ của một mã booking")
    @PreAuthorize("hasAnyAuthority('PAYMENT_READ', 'PAYMENT_REPORT')")
    @GetMapping("/booking/{bookingId}")
    public ApiResponse<List<PaymentResponse>> getPaymentsForBooking(
            @Parameter(description = "ID đơn đặt tour", required = true) @PathVariable UUID bookingId,
            Authentication authentication) {
        var principal = (com.casms.security.principal.CasmsPrincipal) authentication.getPrincipal();
        return ApiResponse.success(service.getPaymentsForBooking(bookingId, principal.getUserId(),
                principal.hasPermission(com.casms.identity.enums.Permission.PAYMENT_REPORT)));
    }

    @Operation(summary = "Chi tiết giao dịch thanh toán", description = "Tra cứu trạng thái và thông tin chi tiết một giao dịch thanh toán theo ID")
    @PreAuthorize("hasAnyAuthority('PAYMENT_READ', 'PAYMENT_REPORT')")
    @GetMapping("/{paymentId}")
    public ApiResponse<PaymentResponse> getPaymentDetail(
            @Parameter(description = "ID giao dịch thanh toán", required = true) @PathVariable UUID paymentId,
            Authentication authentication) {
        var principal = (com.casms.security.principal.CasmsPrincipal) authentication.getPrincipal();
        return ApiResponse.success(service.getPaymentDetail(paymentId, principal.getUserId(),
                principal.hasPermission(com.casms.identity.enums.Permission.PAYMENT_REPORT)));
    }
}
