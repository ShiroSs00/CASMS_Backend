package com.casms.payment.controller;

import com.casms.common.response.ApiResponse;
import com.casms.identity.enums.Permission;
import com.casms.payment.dto.request.SepayCardCheckoutRequest;
import com.casms.payment.dto.request.SepayCheckoutRequest;
import com.casms.payment.dto.request.SepayQuickCheckoutRequest;
import com.casms.payment.dto.request.SepayWebhookPayload;
import com.casms.payment.dto.response.BookingPaymentOptionsResponse;
import com.casms.payment.dto.response.PaymentStatusCheckResponse;
import com.casms.payment.dto.response.SepayCheckoutResponse;
import com.casms.payment.dto.response.SepayConfigResponse;
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
                "Tích hợp cổng thanh toán SePay: Hỗ trợ thanh toán Tiền Việt (VND) và Đô la Mỹ"
                    + " (USD) qua VietQR ngân hàng và thẻ quốc tế Visa / Mastercard")
@RestController
@RequestMapping("/payment/sepay")
@RequiredArgsConstructor
public class SepayController {

    private final SepayService sepayService;

    @Operation(
            summary = "Xem cấu hình cổng thanh toán SePay (VND, USD, Visa, Mastercard, VietQR)",
            description =
                    "Trả về cấu hình hiện hành của cổng thanh toán SePay: danh sách tiền tệ hỗ trợ"
                        + " (VND, USD), danh sách thương hiệu thẻ quốc tế chấp nhận (VISA,"
                        + " MASTERCARD), tỷ giá quy đổi USD/VND, và thông tin tài khoản thụ hưởng.")
    @GetMapping("/config")
    public ApiResponse<SepayConfigResponse> getSepayConfig() {
        return ApiResponse.success(sepayService.getSepayConfig());
    }

    @Operation(
            summary = "Xem 3 phương án thanh toán của đơn đặt tour (Hỗ trợ VND & USD)",
            description =
                    "Hiển thị số tiền USD và VNĐ cho 3 phương án: FULL (100%), DEPOSIT (Cọc),"
                        + " STAGES (Theo giai đoạn), cùng các kênh thanh toán hỗ trợ (VIETQR, VISA,"
                        + " MASTERCARD)")
    @GetMapping("/options/{bookingId}")
    public ApiResponse<BookingPaymentOptionsResponse> getPaymentOptions(
            @Parameter(description = "ID đơn đặt tour", required = true) @PathVariable
                    UUID bookingId,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID userId = principal != null ? principal.getUserId() : null;
        boolean canReadAll =
                hasPermSafe(principal, Permission.PAYMENT_READ)
                        || hasPermSafe(principal, Permission.PAYMENT_REPORT);
        return ApiResponse.success(sepayService.getPaymentOptions(bookingId, userId, canReadAll));
    }

    @Operation(
            summary = "Tạo thanh toán qua SePay (VietQR hoặc Thẻ Visa/Mastercard bằng VND / USD)",
            description =
                    "Tạo lệnh thanh toán theo 1 trong 3 loại: FULL (toàn bộ 100%), DEPOSIT (đặt"
                        + " cọc) hoặc STAGE (theo đợt). Hỗ trợ chọn tiền tệ VND hoặc USD, và kênh"
                        + " VIETQR hoặc thẻ quốc tế VISA / MASTERCARD.")
    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SepayCheckoutResponse> createCheckout(
            @RequestBody @Valid SepayCheckoutRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID userId = principal != null ? principal.getUserId() : null;
        boolean canReadAll = hasPermSafe(principal, Permission.PAYMENT_PROCESS);
        return ApiResponse.success(
                "Tạo yêu cầu thanh toán SePay thành công",
                sepayService.createCheckout(request, userId, canReadAll));
    }

    @Operation(
            summary =
                    "Thanh toán qua SePay bằng thẻ quốc tế Visa / Mastercard (Hỗ trợ Tiền Việt VND"
                        + " & USD)",
            description =
                    "Thanh toán trực tiếp bằng thẻ quốc tế VISA hoặc MASTERCARD qua cổng SePay với"
                        + " đơn vị tiền tệ tuỳ chọn là Tiền Việt (VND) hoặc Đô la Mỹ (USD). Hỗ trợ"
                        + " cả thanh toán theo đơn đặt chỗ (FULL, DEPOSIT, STAGE) lẫn thanh toán"
                        + " nhanh theo số tiền.")
    @PostMapping("/card-checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SepayCheckoutResponse> createCardCheckout(
            @RequestBody @Valid SepayCardCheckoutRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID userId = principal != null ? principal.getUserId() : null;
        boolean canReadAll = hasPermSafe(principal, Permission.PAYMENT_PROCESS);
        return ApiResponse.success(
                "Xử lý thanh toán thẻ quốc tế Visa/Mastercard qua SePay thành công",
                sepayService.createCardCheckout(request, userId, canReadAll));
    }

    @Operation(
            summary = "Tạo thanh toán SePay nhanh theo số tiền (Hỗ trợ VND, USD, VietQR, Visa, Mastercard)",
            description =
                    "Tạo lệnh thanh toán nhanh theo số tiền bằng Tiền Việt (VND) hoặc USD, qua"
                        + " VietQR hoặc thẻ quốc tế Visa / Mastercard.")
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
                    "Kiểm tra giao dịch đã được ngân hàng hoặc cổng thẻ Visa/Mastercard xác nhận"
                        + " hay chưa (dùng cho Frontend polling)")
    @GetMapping("/status/{paymentId}")
    public ApiResponse<PaymentStatusCheckResponse> getPaymentStatus(
            @Parameter(description = "ID giao dịch thanh toán", required = true) @PathVariable
                    UUID paymentId,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID userId = principal != null ? principal.getUserId() : null;
        boolean canReadAll =
                hasPermSafe(principal, Permission.PAYMENT_READ)
                        || hasPermSafe(principal, Permission.PAYMENT_REPORT);
        return ApiResponse.success(sepayService.getPaymentStatus(paymentId, userId, canReadAll));
    }

    @Operation(
            summary = "Webhook tiếp nhận biến động số dư & giao dịch thẻ từ SePay (VND & USD)",
            description =
                    "Webhook tự động được SePay gọi khi khách chuyển khoản VietQR hoặc thanh toán"
                        + " thẻ quốc tế Visa/Mastercard thành công (hỗ trợ cả VND và USD)."
                        + " Idempotent & tự động xác nhận booking.")
    @PostMapping("/webhook")
    public ApiResponse<SepayWebhookResponse> handleWebhook(
            @RequestBody @Valid SepayWebhookPayload payload,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return ApiResponse.success(
                "Webhook processed", sepayService.processWebhook(payload, authHeader));
    }

    private boolean hasPermSafe(CasmsPrincipal principal, Permission perm) {
        if (principal == null) {
            return false;
        }
        try {
            return principal.hasPermission(perm);
        } catch (Exception e) {
            return false;
        }
    }
}
