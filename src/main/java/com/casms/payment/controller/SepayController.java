package com.casms.payment.controller;

import com.casms.common.exception.BusinessException;
import com.casms.common.response.ApiResponse;
import com.casms.identity.enums.Permission;
import com.casms.payment.dto.request.CardSurchargeConfigRequest;
import com.casms.payment.dto.request.SepayCardCheckoutRequest;
import com.casms.payment.dto.request.SepayCheckoutRequest;
import com.casms.payment.dto.request.SepayQuickCheckoutRequest;
import com.casms.payment.dto.request.SepayReconciliationResolveRequest;
import com.casms.payment.dto.request.SepayWebhookPayload;
import com.casms.payment.dto.response.BookingPaymentOptionsResponse;
import com.casms.payment.dto.response.PaymentStatusCheckResponse;
import com.casms.payment.dto.response.SepayCheckoutResponse;
import com.casms.payment.dto.response.SepayConfigResponse;
import com.casms.payment.dto.response.SepayReconciliationListResponse;
import com.casms.payment.dto.response.SepayTransactionResponse;
import com.casms.payment.dto.response.SepayWebhookResponse;
import com.casms.payment.enums.SepayReconciliationStatus;
import com.casms.payment.enums.SepayTransactionStatus;
import com.casms.payment.service.SepayReconciliationService;
import com.casms.payment.service.SepayService;
import com.casms.security.principal.CasmsPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(
        name = "SePay Payment",
        description =
                "Tích hợp cổng thanh toán SePay: Hỗ trợ thanh toán Tiền Việt (VND) và Đô la Mỹ"
                    + " (USD) qua VietQR ngân hàng, đối soát Finance và thẻ quốc tế")
@RestController
@RequestMapping("/payment/sepay")
@RequiredArgsConstructor
public class SepayController {

    private final SepayService sepayService;
    private final SepayReconciliationService reconciliationService;

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
            summary = "Xem cấu hình phụ phí quẹt thẻ ngân hàng (Linh hoạt theo thương hiệu thẻ)",
            description =
                    "Xem cấu hình tỷ lệ phụ phí thẻ quốc tế (Visa, Mastercard, JCB, Amex) và thẻ ATM"
                        + " nội địa / Napas.")
    @GetMapping("/config/surcharge")
    public ApiResponse<SepayConfigResponse> getSurchargeConfig() {
        return ApiResponse.success(sepayService.getSepayConfig());
    }

    @Operation(
            summary = "Cập nhật linh hoạt tỷ lệ phụ phí thẻ ngân hàng tại runtime (Admin / Manager)",
            description =
                    "Cho phép thay đổi động tỷ lệ phụ thu cà thẻ theo từng thương hiệu (VISA, MASTERCARD, JCB,"
                        + " AMEX, NAPAS) hoặc bật/tắt toàn bộ phụ phí thẻ mà không cần khởi động lại hệ thống.")
    @PutMapping("/config/surcharge")
    public ApiResponse<SepayConfigResponse> updateSurchargeConfig(
            @RequestBody @Valid CardSurchargeConfigRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        CasmsPrincipal authenticated = requirePrincipal(principal);
        boolean canManage = hasPermSafe(authenticated, Permission.PAYMENT_PROCESS);
        return ApiResponse.success(
                "Cập nhật cấu hình phụ phí quẹt thẻ thành công",
                sepayService.updateSurchargeConfig(request, authenticated.getUserId(), canManage));
    }

    @Operation(
            summary = "Xem 3 phương án thanh toán của đơn đặt tour (Hỗ trợ VND & USD)",
            description =
                    "Hiển thị số tiền, khoản đề xuất, lý do không đủ điều kiện và lệnh VietQR đang"
                        + " chờ (nếu có). Khách chỉ xem được đặt chỗ của mình; Finance dùng quyền"
                        + " PAYMENT_REPORT hoặc PAYMENT_PROCESS.")
    @GetMapping("/options/{bookingId}")
    public ApiResponse<BookingPaymentOptionsResponse> getPaymentOptions(
            @Parameter(description = "ID đơn đặt tour", required = true) @PathVariable
                    UUID bookingId,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        CasmsPrincipal authenticated = requirePrincipal(principal);
        boolean canReadAll =
                hasPermSafe(authenticated, Permission.PAYMENT_REPORT)
                        || hasPermSafe(authenticated, Permission.PAYMENT_PROCESS);
        return ApiResponse.success(
                sepayService.getPaymentOptions(bookingId, authenticated.getUserId(), canReadAll));
    }

    @Operation(
            summary = "Tạo thanh toán qua SePay (VietQR hoặc Thẻ Visa/Mastercard bằng VND / USD)",
            description =
                    "Tạo lệnh thanh toán theo 1 trong 3 loại: FULL (toàn bộ 100%), DEPOSIT (đặt"
                        + " cọc) hoặc STAGE (theo đợt). Hỗ trợ Idempotency và mở lại lệnh Pending"
                        + " cố định.")
    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SepayCheckoutResponse> createCheckout(
            @RequestBody @Valid SepayCheckoutRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        CasmsPrincipal authenticated = requirePrincipal(principal);
        boolean canManageAll = hasPermSafe(authenticated, Permission.PAYMENT_PROCESS);
        return ApiResponse.success(
                "Tạo yêu cầu thanh toán SePay thành công",
                sepayService.createCheckout(request, authenticated.getUserId(), canManageAll));
    }

    @Operation(
            summary =
                    "Thanh toán qua SePay bằng thẻ quốc tế Visa / Mastercard (Hỗ trợ Tiền Việt VND"
                        + " & USD)",
            description =
                    "Thanh toán trực tiếp bằng thẻ quốc tế VISA hoặc MASTERCARD qua cổng SePay.")
    @PostMapping("/card-checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SepayCheckoutResponse> createCardCheckout(
            @RequestBody @Valid SepayCardCheckoutRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        CasmsPrincipal authenticated = requirePrincipal(principal);
        boolean canManageAll = hasPermSafe(authenticated, Permission.PAYMENT_PROCESS);
        return ApiResponse.success(
                "Xử lý thanh toán thẻ quốc tế Visa/Mastercard qua SePay thành công",
                sepayService.createCardCheckout(request, authenticated.getUserId(), canManageAll));
    }

    @Operation(
            summary = "Tạo thanh toán SePay nhanh theo số tiền (Hỗ trợ VND, USD, VietQR, Visa, Mastercard)",
            description =
                    "Tạo lệnh thanh toán nhanh theo số tiền bằng Tiền Việt (VND) hoặc USD, qua"
                        + " VietQR hoặc thẻ quốc tế Visa / Mastercard.")
    @PostMapping("/quick-checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SepayCheckoutResponse> createQuickCheckout(
            @RequestBody @Valid SepayQuickCheckoutRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        UUID userId = principal != null ? principal.getUserId() : null;
        boolean canManageAll = principal != null && hasPermSafe(principal, Permission.PAYMENT_PROCESS);
        return ApiResponse.success(
                "Tạo yêu cầu thanh toán nhanh thành công",
                sepayService.createQuickCheckout(request, userId, canManageAll));
    }

    @Operation(
            summary = "Kiểm tra trạng thái thanh toán",
            description =
                    "Kiểm tra giao dịch đã được ngân hàng xác nhận hay chưa (dùng cho Frontend"
                        + " polling). Khách chỉ tra cứu giao dịch thuộc đặt chỗ của mình.")
    @GetMapping("/status/{paymentId}")
    public ApiResponse<PaymentStatusCheckResponse> getPaymentStatus(
            @Parameter(description = "ID giao dịch thanh toán", required = true) @PathVariable
                    UUID paymentId,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        CasmsPrincipal authenticated = requirePrincipal(principal);
        boolean canReadAll =
                hasPermSafe(authenticated, Permission.PAYMENT_REPORT)
                        || hasPermSafe(authenticated, Permission.PAYMENT_PROCESS);
        return ApiResponse.success(
                sepayService.getPaymentStatus(paymentId, authenticated.getUserId(), canReadAll));
    }

    @Operation(
            summary = "Webhook tiếp nhận biến động số dư từ SePay",
            description =
                    "Webhook tự động được SePay gọi khi tiền vào tài khoản. Idempotent theo"
                        + " sepayId, khớp chính xác lệnh VietQR hoặc chuyển Finance đối soát.")
    @PostMapping("/webhook")
    public ApiResponse<SepayWebhookResponse> handleWebhook(
            @RequestBody @Valid SepayWebhookPayload payload,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return ApiResponse.success(
                "Webhook processed", sepayService.processWebhook(payload, authHeader));
    }

    @Operation(
            summary = "Danh sách giao dịch SePay và giao dịch ngoại lệ cần đối soát (Finance)",
            description =
                    "Finance xem danh sách giao dịch nhận từ SePay, số tiền dự kiến vs thực nhận,"
                        + " lý do ngoại lệ và trạng thái đối soát.")
    @PreAuthorize("hasAnyAuthority('PAYMENT_REPORT', 'PAYMENT_PROCESS')")
    @GetMapping("/reconciliation")
    public ApiResponse<SepayReconciliationListResponse> listReconciliationTransactions(
            @RequestParam(required = false) SepayTransactionStatus status,
            @RequestParam(required = false) SepayReconciliationStatus reconciliationStatus,
            @RequestParam(required = false) UUID bookingId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                reconciliationService.list(
                        status, reconciliationStatus, bookingId, search, page, size));
    }

    @Operation(
            summary = "Chi tiết giao dịch SePay và lịch sử xử lý đối soát (Finance)",
            description =
                    "Xem chi tiết chứng từ ngân hàng, số dự kiến, thực nhận và toàn bộ nhật ký"
                        + " audit đối soát của giao dịch.")
    @PreAuthorize("hasAnyAuthority('PAYMENT_REPORT', 'PAYMENT_PROCESS')")
    @GetMapping("/reconciliation/{transactionId}")
    public ApiResponse<SepayTransactionResponse> getReconciliationDetail(
            @PathVariable UUID transactionId) {
        return ApiResponse.success(reconciliationService.getDetail(transactionId));
    }

    @Operation(
            summary = "Xử lý đối soát giao dịch ngoại lệ SePay (Finance, có audit & chống xử lý lặp)",
            description =
                    "Ghi nhận kết quả đối soát kèm lý do và chứng từ: ghi nhận vào đặt chỗ hợp lệ,"
                        + " chuyển quy trình hoàn tiền, hoặc đóng đối soát thủ công.")
    @PreAuthorize("hasAuthority('PAYMENT_PROCESS')")
    @PostMapping("/reconciliation/{transactionId}/resolve")
    public ApiResponse<SepayTransactionResponse> resolveReconciliation(
            @PathVariable UUID transactionId,
            @RequestBody @Valid SepayReconciliationResolveRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        CasmsPrincipal authenticated = requirePrincipal(principal);
        return ApiResponse.success(
                "Đã ghi nhận kết quả đối soát",
                reconciliationService.resolve(
                        transactionId,
                        request,
                        authenticated.getUserId(),
                        authenticated.getUsername()));
    }

    @Operation(
            summary = "Thử ghi sổ lại giao dịch SePay gặp lỗi tạm thời (RETRY_REQUIRED)",
            description =
                    "Phục hồi an toàn sự kiện SePay đã tiếp nhận nhưng lỗi tạm thời ở bước ghi sổ"
                        + " mà không nhân đôi số tiền.")
    @PreAuthorize("hasAuthority('PAYMENT_PROCESS')")
    @PostMapping("/reconciliation/{transactionId}/retry")
    public ApiResponse<SepayTransactionResponse> retryReconciliationPosting(
            @PathVariable UUID transactionId,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        CasmsPrincipal authenticated = requirePrincipal(principal);
        return ApiResponse.success(
                "Đã thử ghi sổ lại giao dịch SePay",
                reconciliationService.retryPosting(
                        transactionId, authenticated.getUserId(), authenticated.getUsername()));
    }

    private CasmsPrincipal requirePrincipal(CasmsPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED,
                    "AUTH_REQUIRED",
                    "Vui lòng đăng nhập để thực hiện thao tác thanh toán.");
        }
        return principal;
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
