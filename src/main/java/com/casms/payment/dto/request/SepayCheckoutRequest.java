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

    @Schema(description = "ID của đơn đặt tour", requiredMode = Schema.RequiredMode.REQUIRED, example = "11111111-2222-3333-4444-555555555555")
    @NotNull(message = "Booking ID is required")
    private UUID bookingId;

    @Schema(description = "3 hình thức thanh toán: FULL (Toàn bộ 100%), DEPOSIT (Đặt cọc), STAGE (Theo giai đoạn/đợt)", requiredMode = Schema.RequiredMode.REQUIRED, example = "DEPOSIT")
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
}

