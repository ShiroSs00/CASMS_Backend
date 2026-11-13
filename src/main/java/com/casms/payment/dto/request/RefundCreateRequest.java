package com.casms.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class RefundCreateRequest {

    @Schema(
            description = "ID giao dịch thanh toán gốc",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Original payment ID is required")
    private UUID originalPaymentId;

    @Schema(description = "ID đợt quyết toán cuối (nếu có)")
    private UUID finalSettlementId;

    @Schema(
            description = "Lý do hoàn tiền",
            example = "Tính trùng dịch vụ / Khách hủy tour theo quy định")
    @NotBlank(message = "Refund reason is required")
    private String refundReason;

    @Schema(description = "Số tiền yêu cầu hoàn lại", example = "50.00")
    @NotNull(message = "Requested amount is required")
    @DecimalMin(value = "0.01", message = "Requested amount must be strictly positive")
    private BigDecimal requestedAmount;

    @Schema(description = "Đơn vị tiền tệ (mặc định USD)", example = "USD")
    private String currency = "USD";

    @Schema(
            description = "Khóa chống trùng lặp Idempotency",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;

    @Schema(description = "Chi tiết từng dòng hóa đơn cần hoàn lại (tuỳ chọn)")
    @Valid
    private List<RefundLineRequest> lines;
}
