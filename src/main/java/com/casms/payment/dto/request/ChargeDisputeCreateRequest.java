package com.casms.payment.dto.request;

import com.casms.payment.enums.ChargeDisputeReasonCode;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class ChargeDisputeCreateRequest {

    @Schema(
            description = "ID khoản chi phí trên tàu bị tranh chấp",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Charge ID is required")
    private UUID chargeId;

    @Schema(description = "ID hành khách khiếu nại", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Passenger ID is required")
    private UUID passengerId;

    @Schema(
            description = "Số tiền khiếu nại tranh chấp",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Disputed amount is required")
    @DecimalMin(value = "0.01", message = "Disputed amount must be strictly positive")
    private BigDecimal disputedAmount;

    @Schema(
            description =
                    "Mã lý do khiếu nại (UNRECOGNIZED_CHARGE, WRONG_AMOUNT, DUPLICATE_CHARGE,"
                        + " SERVICE_NOT_RECEIVED, OTHER)",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Reason code is required")
    private ChargeDisputeReasonCode reasonCode;

    @Schema(
            description = "Mô tả chi tiết lý do khiếu nại",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Description is required")
    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;
}
