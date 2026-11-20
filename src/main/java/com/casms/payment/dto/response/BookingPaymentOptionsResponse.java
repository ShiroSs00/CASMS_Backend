package com.casms.payment.dto.response;

import com.casms.booking.enums.BookingContractStatus;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.enums.PaymentPlan;
import com.casms.payment.enums.PaymentType;

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

    @Schema(description = "Trạng thái hợp đồng hiện tại")
    private BookingContractStatus contractStatus;

    @Schema(description = "Hạn giữ chỗ cho đơn Draft (nếu có)")
    private Instant holdExpiresAt;

    @Schema(description = "Mã tiền tệ gốc của tour")
    private String currency;

    @Schema(description = "Tổng giá tour phải trả (USD hoặc đơn vị gốc)")
    private BigDecimal agreedFareAmount;

    @Schema(description = "Tổng giá tour quy đổi sang VNĐ")
    private BigDecimal agreedFareAmountVnd;

    @Schema(description = "Tổng số tiền đã thanh toán ròng (đã trừ hoàn tiền hoàn tất)")
    private BigDecimal totalPaidAmount;

    @Schema(description = "Tổng số tiền đã thanh toán ròng bằng VNĐ")
    private BigDecimal totalPaidAmountVnd;

    @Schema(description = "Số tiền còn lại cần thanh toán")
    private BigDecimal outstandingBalance;

    @Schema(description = "Số tiền còn lại cần thanh toán bằng VNĐ")
    private BigDecimal outstandingBalanceVnd;

    @Schema(description = "Đơn đặt chỗ đã thanh toán đủ toàn bộ giá vé hay chưa")
    private boolean fullyPaid;

    @Schema(description = "Tỷ giá quy đổi hiện hành (USD -> VNĐ)")
    private BigDecimal exchangeRate;

    @Schema(description = "Đơn đặt chỗ có đủ điều kiện tạo lệnh VietQR VND ở thời điểm hiện tại hay không")
    private boolean vietQrEligible;

    @Schema(description = "Mã lý do nếu chưa thể tạo lệnh thanh toán VietQR")
    private String ineligibleCode;

    @Schema(description = "Lý do bằng tiếng Việt nếu chưa thể tạo lệnh thanh toán VietQR")
    private String ineligibleReason;

    @Schema(description = "Khoản thanh toán đề xuất chính do hệ thống tính toán theo kế hoạch và lịch trả")
    private RecommendedPaymentOption recommendedOption;

    @Schema(description = "Lệnh VietQR đang chờ thanh toán của đặt chỗ (dùng để mở lại sau tải lại trang)")
    private SepayCheckoutResponse activePendingOrder;

    @Schema(description = "Danh sách tiền tệ hỗ trợ thanh toán qua SePay (VND, USD)")
    private List<String> supportedCurrencies;

    @Schema(description = "Danh sách kênh thanh toán SePay hỗ trợ (VIETQR, VISA, MASTERCARD)")
    private List<String> supportedPaymentChannels;

    @Schema(description = "Danh sách thương hiệu thẻ quốc tế hỗ trợ (VISA, MASTERCARD)")
    private List<String> supportedCardBrands;

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
    public static class RecommendedPaymentOption {
        private PaymentType paymentType;
        private UUID installmentId;
        private Integer sequenceNo;
        private String label;
        private String description;
        private BigDecimal amount;
        private BigDecimal amountVnd;
        private String currency;
        private Instant dueAt;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FullPaymentOption {
        private boolean isAvailable;
        private BigDecimal amountUsd;
        private BigDecimal amountVnd;
        private Instant dueAt;
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
        private BigDecimal paidAmountVnd;
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
        private BigDecimal paidAmountVnd;
        private BigDecimal remainingAmountUsd;
        private BigDecimal remainingAmountVnd;
        private Instant dueAt;
        private String status;
        private boolean isPaid;
        private boolean isCurrentDue;
        private String description;
    }
}
