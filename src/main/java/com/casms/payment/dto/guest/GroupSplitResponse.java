package com.casms.payment.dto.guest;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
public class GroupSplitResponse {

    @Schema(description = "Mã định danh nhóm chia tiền", example = "SPLIT-BK202611-001")
    private String splitGroupId;

    @Schema(description = "ID đơn đặt tour")
    private UUID bookingId;

    @Schema(description = "Mã đặt chỗ tour")
    private String bookingReference;

    @Schema(description = "Tổng số tiền cần thanh toán cho đơn hàng")
    private BigDecimal totalAmount;

    @Schema(description = "Số tiền chuẩn cho mỗi người")
    private BigDecimal shareAmount;

    @Schema(description = "Số người cùng thanh toán")
    private int numberOfShares;

    @Schema(description = "Tổng số tiền đã được thanh toán")
    private BigDecimal paidAmount;

    @Schema(description = "Tỷ lệ hoàn thành thanh toán (%)")
    private BigDecimal progressPercent;

    @Schema(description = "Toàn bộ nhóm đã thanh toán đủ 100% hay chưa")
    private boolean fullyPaid;

    @Schema(description = "Danh sách phần thanh toán của từng thành viên")
    private List<GroupSplitShareItem> shares;

    @Getter
    @Setter
    @Builder
    public static class GroupSplitShareItem {
        private int shareIndex;
        private String shareId;
        private UUID paymentId;
        private String payerName;
        private String payerEmail;
        private BigDecimal amount;
        private String status; // PENDING, CONFIRMED
        private String paymentReference;
        private String bankAccountName;
        private String bankAccountNumber;
        private String bankCode;
        private String qrCodeUrl;
        private String checkoutUrl;
    }
}

