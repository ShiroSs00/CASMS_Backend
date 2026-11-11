package com.casms.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RefundCompleteRequest {

    @Schema(
            description = "Mã biên lai hoặc mã giao dịch từ ngân hàng/cổng thanh toán",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Processor or receipt reference is required")
    private String processorOrReceiptReference;
}
