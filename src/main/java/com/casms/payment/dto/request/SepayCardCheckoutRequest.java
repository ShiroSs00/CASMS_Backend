package com.casms.payment.dto.request;

import com.casms.payment.enums.PaymentType;

import io.swagger.v3.oas.annotations.media.Schema;

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
public class SepayCardCheckoutRequest {

    @Schema(
            description = "ID của đơn đặt tour (tuỳ chọn nếu thanh toán trực tiếp theo số tiền)",
            example = "11111111-2222-3333-4444-555555555555")
    private UUID bookingId;

    @Schema(
            description =
                    "Hình thức thanh toán theo đơn đặt chỗ: FULL (Toàn bộ 100%), DEPOSIT (Đặt cọc),"
                        + " STAGE (Theo giai đoạn/đợt)",
            example = "DEPOSIT")
    private PaymentType paymentType;

    @Schema(description = "ID đợt thanh toán (dành cho STAGE)")
    private UUID installmentId;

    @Schema(description = "Số thứ tự đợt thanh toán (dành cho STAGE, ví dụ 1, 2, 3...)")
    private Integer sequenceNo;

    @Schema(
            description = "Loại tiền tệ thanh toán qua SePay: VND (Tiền Việt) hoặc USD",
            example = "VND")
    private String currency;

    @Schema(
            description =
                    "Số tiền thanh toán trực tiếp (theo đơn vị currency đã chọn, dùng khi không"
                        + " tính tự động từ booking)",
            example = "7620000")
    private BigDecimal amount;

    @Schema(description = "Số tiền thanh toán bằng VNĐ (tuỳ chọn)", example = "7620000")
    private BigDecimal amountVnd;

    @Schema(description = "Số tiền thanh toán bằng USD (tuỳ chọn)", example = "300.00")
    private BigDecimal amountUsd;

    @Schema(
            description = "Thương hiệu thẻ quốc tế: VISA hoặc MASTERCARD",
            example = "VISA")
    private String cardBrand;

    @Schema(
            description =
                    "Số thẻ Visa (bắt đầu bằng 4) hoặc Mastercard (bắt đầu bằng 51-55 hoặc"
                        + " 2221-2720). Hệ thống chỉ giữ 4 số cuối để đối soát PCI-DSS.",
            example = "4111111111114242")
    private String cardNumber;

    @Schema(description = "Tên in trên thẻ (Cardholder Name)", example = "NGUYEN VAN A")
    private String cardHolderName;

    @Schema(description = "Tháng hết hạn thẻ (1-12)", example = "12")
    private Integer expiryMonth;

    @Schema(description = "Năm hết hạn thẻ (YYYY)", example = "2029")
    private Integer expiryYear;

    @Schema(description = "Mã bảo mật CVV/CVC (chỉ kiểm tra trong bộ nhớ, tuyệt đối không lưu DB)", example = "123")
    private String cvv;

    @Schema(
            description = "Token thẻ quốc tế đã mã hoá (nếu thanh toán bằng thẻ đã lưu)",
            example = "tok_sepay_visa_4242")
    private String cardToken;

    @Schema(description = "Tỷ giá USD sang VND tuỳ chỉnh (nếu muốn override tỷ giá mặc định)")
    private BigDecimal customExchangeRate;

    @Schema(description = "Mô tả giao dịch thanh toán", example = "Thanh toán tour du thuyền bằng thẻ Visa/Mastercard")
    private String description;

    @Schema(description = "Mã Idempotency chống tạo lệnh trùng lặp")
    private String idempotencyKey;

    @Schema(description = "Miễn phụ phí quẹt thẻ (áp dụng cho khách VIP hoặc chiến dịch đặc biệt)")
    private Boolean waiveSurcharge;

    @Schema(description = "Tỷ lệ phụ phí thẻ tuỳ chỉnh (%) nếu muốn ghi đè cấu hình mặc định", example = "2.00")
    private BigDecimal customSurchargePercent;

    @Schema(
            description =
                    "Tự động uỷ quyền & gạch nợ ngay lập tức qua cổng thẻ SePay (mặc định: true)",
            example = "true")
    private Boolean autoCapture;
}

