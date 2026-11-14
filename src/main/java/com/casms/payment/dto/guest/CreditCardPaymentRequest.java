package com.casms.payment.dto.guest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class CreditCardPaymentRequest {

    @Schema(description = "ID đơn đặt tour", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Booking ID is required")
    private UUID bookingId;

    @Schema(description = "ID đợt thanh toán (tuỳ chọn)")
    private UUID installmentId;

    @Schema(description = "Số tiền thanh toán", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Payment amount is required")
    @DecimalMin(value = "1000", message = "Amount must be at least 1,000 VND")
    private BigDecimal amount;

    @Schema(description = "Đơn vị tiền tệ", example = "VND")
    private String currency = "VND";

    @Schema(description = "Số thẻ quốc tế (16 chữ số)", example = "4111222233334444", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Card number is required")
    private String cardNumber;

    @Schema(description = "Tên chủ thẻ", example = "NGUYEN VAN A", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Cardholder name is required")
    private String cardHolderName;

    @Schema(description = "Tháng/Năm hết hạn (MM/YY)", example = "12/28", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Expiry date is required")
    private String expiryDate;

    @Schema(description = "Mã bảo mật CVV/CVC (3-4 số)", example = "123", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "CVV is required")
    private String cvv;

    @Schema(description = "Khóa chống trùng lặp (Idempotency Key)")
    private String idempotencyKey;

    // Tuỳ chọn xuất hoá đơn đỏ VAT doanh nghiệp
    @Schema(description = "Yêu cầu xuất hoá đơn đỏ VAT doanh nghiệp")
    private Boolean requiresVatInvoice;

    @Schema(description = "Tên công ty nhận hoá đơn")
    private String companyName;

    @Schema(description = "Mã số thuế doanh nghiệp")
    private String taxCode;

    @Schema(description = "Địa chỉ doanh nghiệp")
    private String companyAddress;

    @Schema(description = "Email nhận hoá đơn điện tử")
    private String vatEmail;
}

