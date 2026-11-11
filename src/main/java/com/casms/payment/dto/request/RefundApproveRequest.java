package com.casms.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class RefundApproveRequest {

    @Schema(
            description = "Số tiền được phê duyệt hoàn trả",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Approved amount is required")
    @DecimalMin(value = "0.0", message = "Approved amount must be non-negative")
    private BigDecimal approvedAmount;

    @Schema(description = "Ghi chú phê duyệt")
    private String note;
}
