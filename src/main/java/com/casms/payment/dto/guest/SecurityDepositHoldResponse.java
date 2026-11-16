package com.casms.payment.dto.guest;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
public class SecurityDepositHoldResponse {

    @Schema(description = "ID giao dịch tạm giữ cọc")
    private UUID holdId;

    @Schema(description = "ID tài khoản chi tiêu trên tàu")
    private UUID onboardAccountId;

    @Schema(description = "Số tiền cọc bảo đảm đang được phong tỏa")
    private BigDecimal depositAmount;

    @Schema(description = "Đơn vị tiền tệ", example = "VND")
    private String currency;

    @Schema(description = "Mã phong tỏa tiền thẻ (Pre-Auth Code)")
    private String preAuthCode;

    @Schema(description = "Trạng thái tạm giữ (HELD / ACTIVE)")
    private String status;

    @Schema(description = "Thời điểm ghi nhận phong tỏa")
    private Instant heldAt;

    @Schema(description = "Chính sách cấn trừ và hoàn trả cọc khi rời tàu")
    private String policyTerms;
}

