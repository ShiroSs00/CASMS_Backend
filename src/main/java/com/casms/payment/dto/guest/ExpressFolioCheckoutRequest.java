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

    // --- Văn hóa tiền thưởng / Tip trên du thuyền 5 sao (Tự nguyện / Không bắt buộc) ---
    @Schema(description = "Số tiền tip tự nguyện (VND hoặc USD)", example = "200000")
    private java.math.BigDecimal gratuityAmount;

    @Schema(description = "Hình thức tip: GENERAL_CREW_POOL (Hòm tip chung tại lễ tân) hoặc INDIVIDUAL_STAFF (Tip riêng)", example = "GENERAL_CREW_POOL")
    private String gratuityType;

    @Schema(description = "Mã nhân viên được tip (nếu tip riêng)")
    private UUID targetStaffUserId;

    @Schema(description = "Tên nhân viên được tip (nếu tip riêng)")
    private String targetStaffName;

    @Schema(description = "Lời nhắn cảm ơn gửi đến thủy thủ đoàn / nhân viên")
    private String gratuityNote;

    @Schema(description = "Miễn phụ phí quẹt thẻ (dành cho khách VIP hoặc chính sách đặc biệt)")
    private Boolean waiveCardSurcharge;
}

