package com.casms.payment.dto.request;

import com.casms.payment.enums.ChargeDisputeResolutionType;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ChargeDisputeResolveRequest {

    @Schema(
            description =
                    "Loại giải quyết (NO_CHANGE, REVERSE_CHARGE, ADJUST_CHARGE, REFUND_REQUIRED)",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Resolution type is required")
    private ChargeDisputeResolutionType resolutionType;

    @Schema(description = "Số tiền được phê duyệt giải quyết (nếu có)")
    @DecimalMin(value = "0.0", message = "Approved amount must be non-negative")
    private BigDecimal approvedAmount;

    @Schema(description = "Ghi chú xử lý giải quyết", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Resolution note is required")
    @Size(max = 1000, message = "Resolution note must not exceed 1000 characters")
    private String resolutionNote;
}
