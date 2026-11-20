package com.casms.payment.dto.request;

import com.casms.payment.enums.PaymentType;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class SepayCheckoutRequest {

    @Schema(
            description = "ID của đơn đặt tour",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "11111111-2222-3333-4444-555555555555")
    @NotNull(message = "Booking ID is required")
    private UUID bookingId;

    @Schema(
            description =
                    "3 hình thức thanh toán: FULL (Toàn bộ 100%), DEPOSIT (Đặt cọc), STAGE (Theo"
                        + " giai đoạn/đợt)",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "DEPOSIT")
    @NotNull(message = "Payment type is required (FULL, DEPOSIT, STAGE)")
    private PaymentType paymentType;

    @Schema(description = "ID đợt thanh toán (dành cho STAGE, tuỳ chọn nếu muốn chỉ định rõ ID)")
    private UUID installmentId;

    @Schema(description = "Số thứ tự đợt thanh toán (dành cho STAGE, ví dụ 1, 2, 3...)")
    private Integer sequenceNo;

    @Schema(description = "Tỷ giá USD sang VND tuỳ chỉnh (nếu muốn override tỷ giá mặc định)")
    private BigDecimal customExchangeRate;

    @Schema(description = "Mã Idempotency chống tạo lệnh trùng lặp")
    private String idempotencyKey;

    @Schema(
            description = "Loại tiền tệ thanh toán qua SePay: VND (Tiền Việt) hoặc USD",
            example = "VND")
    private String currency;

    @Schema(
            description = "Kênh thanh toán qua SePay: VIETQR (mặc định), VISA, hoặc MASTERCARD",
            example = "VISA")
    private String paymentChannel;

    @Schema(
            description = "Thương hiệu thẻ quốc tế khi chọn thanh toán thẻ: VISA hoặc MASTERCARD",
            example = "VISA")
    private String cardBrand;

    @Schema(
            description =
                    "Số thẻ Visa (bắt đầu bằng 4) hoặc Mastercard (bắt đầu bằng 51-55 hoặc"
                        + " 2221-2720) nếu thanh toán trực tiếp bằng thẻ",
            example = "4111111111114242")
    private String cardNumber;

    @Schema(description = "Tên chủ thẻ quốc tế", example = "NGUYEN VAN A")
    private String cardHolderName;

    @Schema(description = "Tháng hết hạn thẻ (1-12)", example = "12")
    private Integer expiryMonth;

    @Schema(description = "Năm hết hạn thẻ (YYYY)", example = "2029")
    private Integer expiryYear;

    @Schema(description = "Mã CVV/CVC (không lưu DB)", example = "123")
    private String cvv;

    @Schema(description = "Token thẻ quốc tế nếu đã lưu", example = "tok_sepay_visa_4242")
    private String cardToken;

    @Schema(
            description =
                    "Tự động uỷ quyền & gạch nợ ngay khi thanh toán bằng thẻ Visa/Mastercard (mặc"
                        + " định: true)",
            example = "true")
    private Boolean autoCapture;

    @Schema(
            description =
                    "Số dư còn lại khách thấy trên trang trước khi bấm tạo QR (dùng phát hiện số dư"
                        + " đã thay đổi để yêu cầu FE tải lại)")
    private BigDecimal expectedOutstandingBalance;

    @Schema(
            description =
                    "Số tiền VNĐ của khoản chọn mà khách thấy trên trang (dùng phát hiện lệch số"
                        + " tiền trước khi tạo QR; BE vẫn tự tính số tiền chính thức)")
    private BigDecimal expectedAmountVnd;
}
