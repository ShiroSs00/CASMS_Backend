package com.casms.payment.dto.guest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class GroupSplitCreateRequest {

    @Schema(description = "ID đơn đặt tour", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Booking ID is required")
    private UUID bookingId;

    @Schema(description = "Tổng số tiền cần chia sẻ thanh toán", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Total amount is required")
    @DecimalMin(value = "1000", message = "Total amount must be at least 1,000 VND")
    private BigDecimal totalAmount;

    @Schema(description = "Số lượng người chia tiền (từ 2 đến 20)", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
    @Min(value = 2, message = "Group split requires at least 2 payers")
    @Max(value = 20, message = "Maximum 20 payers supported per group")
    private int numberOfShares;

    @Schema(description = "Danh sách tên người cùng thanh toán (tuỳ chọn)")
    private List<String> memberNames;

    @Schema(description = "Danh sách email nhận link thanh toán (tuỳ chọn)")
    private List<String> memberEmails;
}

