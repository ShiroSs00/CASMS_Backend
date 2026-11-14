package com.casms.payment.dto.guest;

import com.casms.booking.enums.BookingStatus;
import com.casms.payment.enums.PaymentStatus;
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
public class CreditCardPaymentResponse {

    @Schema(description = "ID bản ghi thanh toán")
    private UUID paymentId;

    @Schema(description = "ID đơn đặt tour")
    private UUID bookingId;

    @Schema(description = "Mã tham chiếu đơn đặt", example = "BK-202611-001")
    private String bookingReference;

    @Schema(description = "Số tiền đã thanh toán")
    private BigDecimal amount;

    @Schema(description = "Loại tiền tệ")
    private String currency;

    @Schema(description = "Trạng thái thanh toán")
    private PaymentStatus paymentStatus;

    @Schema(description = "Mã xác thực giao dịch thẻ (Gateway Authorization Code)")
    private String authorizationCode;

    @Schema(description = "4 số cuối của thẻ", example = "4444")
    private String cardLast4;

    @Schema(description = "Loại thẻ (VISA, MASTERCARD, JCB, AMEX)")
    private String cardBrand;

    @Schema(description = "Trạng thái đơn hàng sau thanh toán")
    private BookingStatus bookingStatus;

    @Schema(description = "Thời gian ghi nhận giao dịch")
    private Instant paidAt;

    @Schema(description = "Thông báo kết quả cho khách hàng")
    private String message;

    @Schema(description = "Mã hoá đơn điện tử VAT phát hành kèm theo (nếu có)")
    private String vatInvoiceReference;
}

