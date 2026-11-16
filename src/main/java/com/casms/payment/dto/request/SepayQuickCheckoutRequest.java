package com.casms.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SepayQuickCheckoutRequest {

    @Schema(
            description = "Số tiền thanh toán bằng VNĐ (tối thiểu 2.000 VNĐ nếu thanh toán bằng VNĐ)",
            example = "100000")
    @DecimalMin(value = "2000", message = "Số tiền tối thiểu là 2.000 VNĐ")
    private BigDecimal amountVnd;

    @Schema(
            description = "Số tiền thanh toán bằng USD (nếu khách chọn thanh toán bằng USD)",
            example = "100.00")
    @DecimalMin(value = "0.01", message = "Số tiền USD tối thiểu là 0.01 USD")
    private BigDecimal amountUsd;

    @Schema(
            description = "Số tiền thanh toán theo loại tiền tệ (currency) đã chọn (VND hoặc USD)",
            example = "100000")
    private BigDecimal amount;

    @Schema(
            description = "Loại tiền tệ thanh toán: VND (Tiền Việt) hoặc USD",
            example = "VND")
    private String currency;

    @Schema(
            description = "Kênh thanh toán qua SePay: VIETQR (mặc định), VISA, hoặc MASTERCARD",
            example = "VIETQR")
    private String paymentChannel;

    @Schema(
            description = "Thương hiệu thẻ quốc tế (nếu thanh toán bằng thẻ): VISA hoặc MASTERCARD",
            example = "VISA")
    private String cardBrand;

    @Schema(
            description = "Số thẻ Visa / Mastercard (nếu thanh toán trực tiếp bằng thẻ)",
            example = "4111111111114242")
    private String cardNumber;

    @Schema(description = "Tên chủ thẻ quốc tế", example = "NGUYEN VAN A")
    private String cardHolderName;

    @Schema(description = "Tháng hết hạn thẻ (1-12)", example = "12")
    private Integer expiryMonth;

    @Schema(description = "Năm hết hạn thẻ (YYYY)", example = "2029")
    private Integer expiryYear;

    @Schema(description = "Mã bảo mật CVV/CVC", example = "123")
    private String cvv;

    @Schema(description = "Token thẻ quốc tế đã lưu", example = "tok_sepay_visa_4242")
    private String cardToken;

    @Schema(description = "Tỷ giá USD sang VND tuỳ chỉnh (nếu muốn override tỷ giá mặc định)")
    private BigDecimal customExchangeRate;

    @Schema(
            description =
                    "Tự động gạch nợ ngay khi thanh toán bằng thẻ Visa/Mastercard (mặc định: true)",
            example = "true")
    private Boolean autoCapture;

    @Schema(
            description =
                    "Nội dung chuyển khoản chuẩn CASMS (Ví dụ: CASMS BK20264512F). Nếu để trống, hệ"
                        + " thống sẽ tự sinh mã.",
            example = "CASMS BK20264512F")
    private String transferContent;

    @Schema(
            description = "Mô tả mục đích thanh toán",
            example = "Thanh toán dịch vụ du thuyền CASMS")
    private String description;

    @Schema(
            description = "Mã Idempotency chống tạo lệnh trùng lặp",
            example = "QUICK-20260401-1234")
    private String idempotencyKey;

    @Schema(description = "ID đơn đặt tour (tùy chọn nếu có sẵn booking)")
    private UUID bookingId;
}
