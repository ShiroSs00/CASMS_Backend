package com.casms.payment.dto.response;

import com.casms.booking.enums.BookingStatus;
import com.casms.booking.enums.PaymentPlan;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingPaymentOptionsResponse {

    @Schema(description = "ID đơn đặt chỗ")
    private UUID bookingId;

    @Schema(description = "Mã tham chiếu đơn đặt chỗ")
    private String bookingReference;

    @Schema(description = "Trạng thái đơn đặt chỗ hiện tại")
    private BookingStatus bookingStatus;

    @Schema(description = "Mã tiền tệ gốc của tour")
    private String currency;

    @Schema(description = "Tổng giá tour phải trả (USD)")
    private BigDecimal agreedFareAmount;

    @Schema(description = "Tổng giá tour quy đổi sang VNĐ")
    private BigDecimal agreedFareAmountVnd;

    @Schema(description = "Tổng số tiền đã thanh toán (USD)")
    private BigDecimal totalPaidAmount;

    @Schema(description = "Tổng số tiền đã thanh toán (VNĐ)")
    private BigDecimal totalPaidAmountVnd;

    @Schema(description = "Số tiền còn lại cần thanh toán (USD)")
    private BigDecimal outstandingBalance;

    @Schema(description = "Số tiền còn lại cần thanh toán (VNĐ)")
    private BigDecimal outstandingBalanceVnd;

    @Schema(description = "Tỷ giá quy đổi hiện hành (USD -> VNĐ)")
    private BigDecimal exchangeRate;

    @Schema(description = "Kế hoạch thanh toán đã đăng ký ban đầu")
    private PaymentPlan configuredPaymentPlan;

    @Schema(description = "Tuỳ chọn 1: Thanh toán toàn bộ 100% (FULL)")
    private FullPaymentOption fullPaymentOption;

    @Schema(description = "Tuỳ chọn 2: Đặt cọc (DEPOSIT)")
    private DepositPaymentOption depositPaymentOption;

    @Schema(description = "Tuỳ chọn 3: Thanh toán theo giai đoạn (STAGE)")
    private StagePaymentOption stagePaymentOption;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FullPaymentOption {
        private boolean isAvailable;
        private BigDecimal amountUsd;
        private BigDecimal amountVnd;
        private String description;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DepositPaymentOption {
        private boolean isAvailable;
        private boolean isPaid;
        private UUID installmentId;
        private Integer sequenceNo;
        private BigDecimal requiredAmountUsd;
        private BigDecimal requiredAmountVnd;
        private BigDecimal paidAmountUsd;
        private BigDecimal remainingAmountUsd;
        private BigDecimal remainingAmountVnd;
        private Instant dueAt;
        private String description;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StagePaymentOption {
        private boolean isAvailable;
        private Integer totalStages;
        private List<StageDetail> stages;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StageDetail {
        private UUID installmentId;
        private Integer sequenceNo;
        private String installmentType;
        private BigDecimal requiredAmountUsd;
        private BigDecimal requiredAmountVnd;
        private BigDecimal paidAmountUsd;
        private BigDecimal remainingAmountUsd;
        private BigDecimal remainingAmountVnd;
        private Instant dueAt;
        private String status;
        private boolean isPaid;
        private boolean isCurrentDue;
        private String description;
    }
}
