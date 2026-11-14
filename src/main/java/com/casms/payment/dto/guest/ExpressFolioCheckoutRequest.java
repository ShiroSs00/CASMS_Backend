package com.casms.payment.dto.guest;

import com.casms.payment.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class ExpressFolioCheckoutRequest {

    @Schema(description = "ID tài khoản chi tiêu trên tàu", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Onboard account ID is required")
    private UUID onboardAccountId;

    @Schema(description = "Phương thức thanh toán phần chênh lệch (nếu số dư > 0)", example = "CreditCard")
    private PaymentMethod paymentMethod = PaymentMethod.CreditCard;

    @Schema(description = "4 số cuối thẻ thanh toán (nếu thanh toán thẻ)")
    private String cardLast4;

    @Schema(description = "Xác nhận của khách hàng đồng ý chốt sao kê chi tiêu", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Confirmation is required")
    private Boolean confirmedByGuest;

    @Schema(description = "Email nhận hoá đơn quyết toán cuối cùng (Final Statement)")
    private String receiptEmail;
}

