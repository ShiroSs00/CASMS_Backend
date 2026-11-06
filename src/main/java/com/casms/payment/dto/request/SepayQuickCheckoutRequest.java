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
            description = "Số tiền thanh toán (VNĐ)",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "100000")
    @NotNull(message = "Số tiền thanh toán là bắt buộc")
    @DecimalMin(value = "2000", message = "Số tiền tối thiểu là 2.000 VNĐ")
    private BigDecimal amountVnd;

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
