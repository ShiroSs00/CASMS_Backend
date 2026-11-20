package com.casms.payment.dto.response;

import com.casms.booking.enums.BookingContractStatus;
import com.casms.booking.enums.BookingStatus;
import com.casms.payment.enums.PaymentMethod;
import com.casms.payment.enums.PaymentStatus;
import com.casms.payment.enums.PaymentType;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatusCheckResponse {

    @Schema(description = "ID giao dịch thanh toán")
    private UUID paymentId;

    @Schema(description = "ID đơn đặt tour")
    private UUID bookingId;

    @Schema(description = "Mã tham chiếu đơn đặt tour")
    private String bookingReference;

    @Schema(description = "Trạng thái thanh toán (Pending, Confirmed, Failed, Cancelled)")
    private PaymentStatus status;

    @Schema(description = "Phương thức thanh toán")
    private PaymentMethod paymentMethod;

    @Schema(description = "Số tiền yêu cầu thanh toán (USD)")
    private BigDecimal requestedAmount;

    @Schema(description = "Số tiền đã xác nhận thành công (USD)")
    private BigDecimal confirmedAmount;

    @Schema(description = "Đơn vị tiền tệ")
    private String currency;

    @Schema(description = "Thời gian gửi yêu cầu thanh toán")
    private Instant requestedAt;

    @Schema(description = "Thời gian xác nhận thành công")
    private Instant confirmedAt;

    @Schema(description = "Mã tham chiếu giao dịch đối tác/ngân hàng")
    private String processorReference;

    @Schema(description = "Trạng thái hiện tại của đơn đặt tour")
    private BookingStatus bookingStatus;

    @Schema(description = "Đơn đặt tour đã được xác nhận (Confirmed) hay chưa")
    private boolean isBookingConfirmed;

    @Schema(description = "Trạng thái hiện tại của hợp đồng đặt chỗ")
    private BookingContractStatus contractStatus;

    @Schema(description = "Tổng số tiền đã thu ròng của đơn đặt chỗ")
    private BigDecimal totalPaidAmount;

    @Schema(description = "Số dư còn lại của đơn sau giao dịch này")
    private BigDecimal remainingBookingBalance;

    @Schema(description = "Đơn đặt chỗ đã thanh toán đủ 100% hay chưa")
    private boolean fullyPaid;

    @Schema(description = "Nội dung chuyển khoản / mã đối soát")
    private String transferContent;

    @Schema(description = "Loại hình thanh toán của lệnh (FULL, DEPOSIT, STAGE)")
    private PaymentType paymentType;

    @Schema(description = "ID đợt thanh toán gắn với lệnh")
    private UUID installmentId;

    @Schema(description = "Số thứ tự đợt thanh toán")
    private Integer sequenceNo;

    @Schema(description = "Lý do thất bại / hủy lệnh nếu có")
    private String failureReason;

    @Schema(description = "Hạn giữ chỗ của đơn Draft nếu có")
    private Instant holdExpiresAt;

    @Schema(description = "Nhà cung cấp / kênh thanh toán")
    private String provider;
}
