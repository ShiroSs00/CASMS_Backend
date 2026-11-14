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
public class ExpressFolioCheckoutResponse {

    @Schema(description = "ID quyết toán tài khoản (Final Settlement ID)")
    private UUID finalSettlementId;

    @Schema(description = "ID tài khoản chi tiêu")
    private UUID onboardAccountId;

    @Schema(description = "Mã hoá đơn quyết toán hoàn tất (Final Statement Reference)")
    private String finalStatementReference;

    @Schema(description = "Tổng chi phí tiêu dùng phát sinh trên tàu")
    private BigDecimal totalChargesAmount;

    @Schema(description = "Số tiền cọc đảm bảo đã được cấn trừ")
    private BigDecimal depositOffsetAmount;

    @Schema(description = "Số tiền khách đã thanh toán thêm khi chốt phòng")
    private BigDecimal additionalPaidAmount;

    @Schema(description = "Số tiền cọc thừa hoàn trả lại thẻ của khách (nếu có)")
    private BigDecimal refundDueAmount;

    @Schema(description = "Trạng thái quyết toán (SETTLED, CLEARED)")
    private String settlementStatus;

    @Schema(description = "Mã thông hành rời tàu số (Gangway Digital Clearance Pass Token)")
    private String gangwayClearancePassToken;

    @Schema(description = "Mã QR thông hành xuất trình tại cửa tàu (Gangway Security QR Code)")
    private String clearanceQrData;

    @Schema(description = "Thời điểm hoàn tất quyết toán")
    private Instant settledAt;

    @Schema(description = "Thông báo hướng dẫn khách hàng rời tàu")
    private String clearanceNotice;
}

