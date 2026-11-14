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
public class PromoCodeValidateRequest {

    @Schema(description = "Mã khuyến mãi / Voucher ưu đãi", example = "SUMMER2026", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Promo code is required")
    private String code;

    @Schema(description = "Số tiền gốc trước giảm giá", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Original amount is required")
    @DecimalMin(value = "0.01", message = "Original amount must be positive")
    private BigDecimal originalAmount;

    @Schema(description = "ID đơn đặt tour (tuỳ chọn)")
    private UUID bookingId;
}

