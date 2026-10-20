package com.casms.booking.dto.response;

import com.casms.payment.enums.InstallmentStatus;
import com.casms.payment.enums.InstallmentType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
public class InstallmentResponse {

    @Schema(description = "Số thứ tự đợt thanh toán", example = "1")
    private Integer sequenceNo;

    @Schema(description = "Loại đợt thanh toán (Deposit, FinalFare)", example = "Deposit")
    private InstallmentType installmentType;

    @Schema(description = "Số tiền phải trả của đợt này", example = "300.0000")
    private BigDecimal requiredAmount;

    @Schema(description = "Đơn vị tiền tệ", example = "USD")
    private String currency;

    @Schema(description = "Hạn chót thanh toán đợt này")
    private Instant dueAt;

    @Schema(description = "Trạng thái đợt thanh toán (Scheduled, Cancelled)", example = "Scheduled")
    private InstallmentStatus status;

    @Schema(description = "Mô tả đợt thanh toán", example = "Đợt 1: Đặt cọc 30% khi ký hợp đồng")
    private String description;
}
