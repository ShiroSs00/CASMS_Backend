package com.casms.payment.dto.guest;

import com.casms.payment.enums.OnboardAccountStatus;
import com.casms.payment.enums.TransactionStatus;
import com.casms.payment.enums.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
public class LiveFolioStatementResponse {

    @Schema(description = "ID tài khoản chi tiêu trên tàu (Onboard Account)")
    private UUID onboardAccountId;

    @Schema(description = "Mã đặt phòng / tour", example = "BK-202611-001")
    private String bookingReference;

    @Schema(description = "Số cabin / phòng", example = "Cabin 7012 (Suite)")
    private String cabinNumber;

    @Schema(description = "Tên hải trình / du thuyền", example = "Hạ Long - Lan Hạ 3N2Đ - Emperor Cruise")
    private String cruiseTitle;

    @Schema(description = "Tên chủ phòng / khách đại diện", example = "Nguyễn Văn A")
    private String primaryGuestName;

    @Schema(description = "Trạng thái tài khoản (Active, Suspended, Settled, Closed)")
    private OnboardAccountStatus accountStatus;

    @Schema(description = "Hạn mức thấu chi chi tiêu tối đa (Credit Limit)")
    private BigDecimal creditLimit;

    @Schema(description = "Tiền đặt cọc phòng đang được giữ (Pre-Auth Security Deposit)")
    private BigDecimal securityDepositHeld;

    @Schema(description = "Tổng các khoản chi phí phát sinh trên tàu (Charges)")
    private BigDecimal totalCharges;

    @Schema(description = "Tổng các khoản đã thanh toán hoặc nạp thêm (Credits/Payments)")
    private BigDecimal totalCredits;

    @Schema(description = "Số dư hiện tại phải thanh toán (Net Payable Balance = Charges - Credits)")
    private BigDecimal netPayableBalance;

    @Schema(description = "Hạn mức chi tiêu còn lại khả dụng")
    private BigDecimal remainingCredit;

    @Schema(description = "Đơn vị tiền tệ", example = "VND")
    private String currency;

    @Schema(description = "Thời điểm cập nhật sao kê gần nhất")
    private Instant statementAsOf;

    @Schema(description = "Danh sách chi tiết từng giao dịch chi tiêu trên tàu")
    private List<FolioTransactionItem> transactions;

    @Schema(description = "Hạn mức chi tiêu phân bổ theo từng thành viên phòng")
    private List<FolioMemberLimitItem> memberLimits;

    @Getter
    @Setter
    @Builder
    public static class FolioTransactionItem {
        private UUID transactionId;
        private Instant transactionTime;
        private String passengerName;
        private String outletOrLocation; // ví dụ: "Sky Bar Deck 9", "Lotus Restaurant", "Ocean Spa"
        private String description;
        private TransactionType transactionType;
        private BigDecimal amount;
        private String currency;
        private TransactionStatus status;
    }

    @Getter
    @Setter
    @Builder
    public static class FolioMemberLimitItem {
        private UUID passengerId;
        private String passengerName;
        private String memberRole;
        private BigDecimal limitAmount;
        private BigDecimal spentAmount;
        private BigDecimal remainingAmount;
        private String status;
    }
}

