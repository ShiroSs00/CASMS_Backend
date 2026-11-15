package com.casms.payment.controller;

import com.casms.common.response.ApiResponse;
import com.casms.payment.dto.guest.*;
import com.casms.payment.service.GuestPaymentExperienceService;
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
        name = "Guest Payment Experience",
        description = "Trải nghiệm thanh toán toàn diện cho khách du lịch: Thẻ quốc tế, Mã giảm giá, Chia tiền nhóm, Hóa đơn đỏ VAT, Sao kê trực tiếp trên tàu & Express Check-out")
@RestController
@RequestMapping("/payment/guest")
@RequiredArgsConstructor
public class GuestPaymentExperienceController {

    private final GuestPaymentExperienceService guestPaymentExperienceService;

    @Operation(
            summary = "Thanh toán bằng thẻ quốc tế (Credit / Debit Card)",
            description = "Hỗ trợ thẻ Visa, Mastercard, JCB, Amex với bảo mật 3D-Secure, kiểm tra hạn thẻ và xác thực quyền sở hữu đơn đặt chỗ")
    @PostMapping("/credit-card/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreditCardPaymentResponse> processCreditCard(
            @RequestBody @Valid CreditCardPaymentRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success(guestPaymentExperienceService.processCreditCardPayment(request, principal));
    }

    @Operation(
            summary = "Kiểm tra và áp dụng mã khuyến mãi / Voucher",
            description = "Xác thực mã giảm giá (ví dụ: SUMMER2026, VIPCRUISE, CASMSWELCOME), tính toán số tiền tiết kiệm và số tiền còn lại")
    @PostMapping("/promo/validate")
    public ApiResponse<PromoCodeResponse> validatePromoCode(
            @RequestBody @Valid PromoCodeValidateRequest request) {
        return ApiResponse.success(guestPaymentExperienceService.validateAndApplyPromoCode(request));
    }

    @Operation(
            summary = "Tạo lệnh chia sẻ tiền phòng theo nhóm (Group Split Payment)",
            description = "Chia đều tổng chi phí cho N thành viên trong cabin/đoàn, tạo riêng mã VietQR và link thanh toán cho từng người")
    @PostMapping("/split/create")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GroupSplitResponse> createGroupSplit(
            @RequestBody @Valid GroupSplitCreateRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success(guestPaymentExperienceService.createGroupSplitPayment(request, principal));
    }

    @Operation(
            summary = "Tra cứu tiến độ thanh toán nhóm",
            description = "Xem danh sách thành viên đã đóng tiền hay chưa và tỷ lệ % hoàn thành")
    @GetMapping("/split/{splitGroupId}")
    public ApiResponse<GroupSplitResponse> getGroupSplitStatus(
            @Parameter(description = "Mã định danh nhóm chia tiền", required = true)
            @PathVariable String splitGroupId) {
        return ApiResponse.success(guestPaymentExperienceService.getGroupSplitStatus(splitGroupId));
    }

    @Operation(
            summary = "Đăng ký phát hành hóa đơn đỏ VAT điện tử doanh nghiệp",
            description = "Cung cấp mã số thuế chuẩn 10-13 số, tên công ty, địa chỉ và email nhận hoá đơn GTGT hợp lệ theo quy định Tổng Cục Thuế")
    @PostMapping("/invoice/vat-request")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CorporateVatInvoiceResponse> requestVatInvoice(
            @RequestBody @Valid CorporateVatInvoiceRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success(guestPaymentExperienceService.issueCorporateVatInvoice(request, principal));
    }

    @Operation(
            summary = "Xem sao kê chi tiêu trực tiếp của Cabin trên tàu (Live Folio)",
            description = "Hiển thị chi tiết từng món đồ uống quầy bar, spa, nhà hàng thời gian thực, hạn mức tín dụng và số dư cần thanh toán chính xác từ CSDL")
    @GetMapping("/folio/statement")
    public ApiResponse<LiveFolioStatementResponse> getLiveFolio(
            @Parameter(description = "ID tài khoản phòng Onboard Account (tuỳ chọn)")
            @RequestParam(required = false) UUID onboardAccountId,
            @Parameter(description = "ID đơn đặt tour (tuỳ chọn)")
            @RequestParam(required = false) UUID bookingId,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success(guestPaymentExperienceService.getLiveFolioStatement(onboardAccountId, bookingId, principal));
    }

    @Operation(
            summary = "Làm thủ tục Express Check-out chốt phòng & Nhận thẻ thông hành rời tàu",
            description = "Tự động cấn trừ tiền cọc bảo đảm, thanh toán số dư trực tuyến và phát hành Thẻ Thông Hành Rời Tàu QR số (Gangway Clearance Pass) không cần xếp hàng tại quầy lễ tân")
    @PostMapping("/folio/express-checkout")
    public ApiResponse<ExpressFolioCheckoutResponse> expressCheckout(
            @RequestBody @Valid ExpressFolioCheckoutRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success(guestPaymentExperienceService.processExpressFolioCheckout(request, principal));
    }

    @Operation(
            summary = "Ghi nhận phong tỏa tiền cọc bảo đảm Cabin (Pre-Auth Security Deposit)",
            description = "Tạm giữ hạn mức thẻ hoặc chuyển khoản cọc bảo đảm khi làm thủ tục nhận phòng, lưu vết CSDL an toàn")
    @PostMapping("/folio/security-deposit")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SecurityDepositHoldResponse> holdSecurityDeposit(
            @RequestBody @Valid SecurityDepositHoldRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success(guestPaymentExperienceService.recordSecurityDepositHold(request, principal));
    }

    @Operation(
            summary = "Nâng hạng phòng (Cabin Upgrade) hoặc Kéo dài tour (2N1Đ -> 3N2Đ) trực tiếp trên tàu",
            description = "Đội tàu kiểm tra tình trạng phòng trống và ghi nhận khoản phụ thu chênh lệch trực tiếp vào sao kê phòng để quyết toán vào sáng ngày cuối")
    @PostMapping("/folio/onboard-upgrade")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OnboardUpgradeResponse> requestOnboardUpgrade(
            @RequestBody @Valid OnboardUpgradeRequest request,
            @AuthenticationPrincipal CasmsPrincipal principal) {
        return ApiResponse.success(guestPaymentExperienceService.processOnboardUpgrade(request, principal));
    }
}
