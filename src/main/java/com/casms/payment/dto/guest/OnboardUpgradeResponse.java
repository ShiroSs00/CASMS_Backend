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
public class OnboardUpgradeResponse {

    @Schema(description = "ID giao dịch ghi nợ vào sao kê phòng")
    private UUID transactionId;

    @Schema(description = "ID tài khoản chi tiêu trên tàu")
    private UUID onboardAccountId;

    @Schema(description = "Loại nâng hạng (CABIN_UPGRADE hoặc ITINERARY_EXTENSION)")
    private String upgradeType;

    @Schema(description = "Tên phòng cabin mới sau nâng hạng")
    private String newCabinNumber;

    @Schema(description = "Số đêm gia hạn thêm")
    private Integer additionalNights;

    @Schema(description = "Số tiền phụ thu chênh lệch đã ghi nợ vào tài khoản phòng")
    private BigDecimal fareDifference;

    @Schema(description = "Đơn vị tiền tệ")
    private String currency;

    @Schema(description = "Chi tiết thông báo kết quả")
    private String message;

    @Schema(description = "Thời điểm xác nhận nâng hạng dịch vụ")
    private Instant upgradedAt;
}
