package com.casms.payment.controller;

import com.casms.common.response.ApiResponse;
import com.casms.identity.enums.Permission;
import com.casms.payment.dto.request.SepayCheckoutRequest;
import com.casms.payment.dto.request.SepayQuickCheckoutRequest;
import com.casms.payment.dto.request.SepayWebhookPayload;
import com.casms.payment.dto.response.BookingPaymentOptionsResponse;
import com.casms.payment.dto.response.PaymentStatusCheckResponse;
import com.casms.payment.dto.response.SepayCheckoutResponse;
import com.casms.payment.dto.response.SepayWebhookResponse;
import com.casms.payment.service.SepayService;
import com.casms.security.principal.CasmsPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(
        name = "SePay Payment",
        description =
                "Tích hợp cổng thanh toán SePay: 3 hình thức thanh toán (Full, Đặt cọc, Giai đoạn),"
                    + " tạo mã VietQR và nhận Webhook")
@RestController
@RequestMapping("/payment/sepay")
@RequiredArgsConstructor
public class SepayController {

    private final SepayService sepayService;

    @Operation(
            summary = "Xem 3 phương án thanh toán của đơn đặt tour",
            description =
                    "Hiển thị số tiền USD và VNĐ cho 3 phương án: FULL (100%), DEPOSIT (Cọc),"
                        + " STAGES (Theo giai đoạn)")
    @GetMapping("/options/{bookingId}")
    public ApiResponse<BookingPaymentOptionsResponse> getPaymentOptions(
            @Parameter(description = "ID đơn đặt tour", required = true) @PathVariable
                    UUID bookingId,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID userId = principal != null ? principal.getUserId() : null;
        boolean canReadAll =
                principal != null
                        && (principal.hasPermission(Permission.PAYMENT_READ)
                                || principal.hasPermission(Permission.PAYMENT_REPORT));
        return ApiResponse.success(sepayService.getPaymentOptions(bookingId, userId, canReadAll));
    }

    @Operation(
            summary = "Tạo thanh toán qua SePay & sinh mã VietQR",
            description =
                    "Tạo lệnh thanh toán theo 1 trong 3 loại: FULL (toàn bộ 100%), DEPOSIT (đặt"
                        + " cọc) hoặc STAGE (theo đợt), trả về URL VietQR quét bằng app ngân hàng")
    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SepayCheckoutResponse> createCheckout(
            @RequestBody @Valid SepayCheckoutRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID userId = principal != null ? principal.getUserId() : null;
        boolean canReadAll =
                principal != null && principal.hasPermission(Permission.PAYMENT_PROCESS);
        return ApiResponse.success(
                "Tạo yêu cầu thanh toán SePay thành công",
                sepayService.createCheckout(request, userId, canReadAll));
    }

    @Operation(
            summary = "Tạo thanh toán SePay nhanh theo số tiền (Quick Payment)",
            description =
                    "Tạo mã thanh toán trực tiếp theo số tiền (VNĐ), lưu bản ghi trạng thái Pending"
                        + " vào CSDL và sinh mã VietQR. Tự động xác nhận khi khách quét mã và"
                        + " chuyển khoản.")
    @PostMapping("/quick-checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SepayCheckoutResponse> createQuickCheckout(
            @RequestBody @Valid SepayQuickCheckoutRequest request) {
        return ApiResponse.success(
                "Tạo yêu cầu thanh toán nhanh thành công",
                sepayService.createQuickCheckout(request));
    }

    @Operation(
            summary = "Kiểm tra trạng thái thanh toán",
            description =
                    "Kiểm tra giao dịch đã được ngân hàng xác nhận hay chưa (dùng cho Frontend"
                        + " polling)")
    @GetMapping("/status/{paymentId}")
    public ApiResponse<PaymentStatusCheckResponse> getPaymentStatus(
            @Parameter(description = "ID giao dịch thanh toán", required = true) @PathVariable
                    UUID paymentId,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID userId = principal != null ? principal.getUserId() : null;
        boolean canReadAll =
                principal != null
                        && (principal.hasPermission(Permission.PAYMENT_READ)
                                || principal.hasPermission(Permission.PAYMENT_REPORT));
        return ApiResponse.success(sepayService.getPaymentStatus(paymentId, userId, canReadAll));
    }

    @Operation(
            summary = "Webhook tiếp nhận biến động số dư từ SePay",
            description =
                    "Webhook tự động được SePay gọi khi khách chuyển khoản thành công vào tài khoản"
                        + " ngân hàng. Idempotent & tự động xác nhận booking.")
    @PostMapping("/webhook")
    public ApiResponse<SepayWebhookResponse> handleWebhook(
            @RequestBody @Valid SepayWebhookPayload payload,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return ApiResponse.success(
                "Webhook processed", sepayService.processWebhook(payload, authHeader));
    }
}
