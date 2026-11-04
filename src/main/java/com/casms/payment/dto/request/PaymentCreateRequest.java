package com.casms.payment.dto.request;

import com.casms.payment.enums.PaymentMethod;
import com.casms.payment.enums.PaymentType;

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
public class PaymentCreateRequest {

    @Schema(description = "ID đơn đặt tour", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Booking ID is required")
    private UUID bookingId;

    @Schema(description = "ID đợt thanh toán (tuỳ chọn)")
    private UUID installmentId;

    @Schema(description = "Số thứ tự đợt thanh toán (tuỳ chọn)")
    private Integer sequenceNo;

    @Schema(description = "Hình thức thanh toán: FULL, DEPOSIT, STAGE (tuỳ chọn)")
    private PaymentType paymentType;

    @NotNull(message = "Requested amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be strictly positive")
    private BigDecimal requestedAmount;

    private String currency = "USD";

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;
}
