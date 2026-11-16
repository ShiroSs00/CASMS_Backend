package com.casms.payment.dto.guest;

import com.casms.payment.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class SecurityDepositHoldRequest {

    @Schema(description = "ID tài khoản chi tiêu trên tàu", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Onboard account ID is required")
    private UUID onboardAccountId;

    @Schema(description = "Số tiền cọc bảo đảm tạm giữ", example = "2000000", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Deposit amount is required")
    @DecimalMin(value = "100000", message = "Minimum deposit hold is 100,000 VND")
    private BigDecimal depositAmount;

    @Schema(description = "Phương thức tạm giữ cọc", example = "CreditCard")
    private PaymentMethod paymentMethod = PaymentMethod.CreditCard;

    @Schema(description = "4 số cuối của thẻ tạm giữ (nếu qua thẻ)")
    private String cardLast4;

    @Schema(description = "Ghi chú tạm giữ")
    private String notes;
}

