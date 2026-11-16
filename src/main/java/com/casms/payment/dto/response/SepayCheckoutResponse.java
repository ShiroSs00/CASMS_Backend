package com.casms.payment.dto.response;

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
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SepayCheckoutResponse {

    @Schema(description = "ID giao dịch thanh toán trong hệ thống")
    private UUID paymentId;

    @Schema(description = "ID đơn đặt chỗ")
    private UUID bookingId;

    @Schema(description = "Mã tham chiếu đơn đặt chỗ")
    private String bookingReference;

    @Schema(description = "Loại hình thanh toán khách chọn (FULL, DEPOSIT, STAGE)")
    private PaymentType paymentType;

    @Schema(description = "ID đợt thanh toán (nếu có)")
    private UUID installmentId;

    @Schema(description = "Số thứ tự đợt thanh toán (1, 2, 3...)")
    private Integer sequenceNo;

    @Schema(description = "Mô tả đợt thanh toán")
    private String stageDescription;

    @Schema(description = "Số tiền thanh toán quy đổi USD")
    private BigDecimal amountUsd;

    @Schema(description = "Số tiền thanh toán thực tế bằng VNĐ (qua ngân hàng SePay)")
    private BigDecimal amountVnd;

    @Schema(description = "Tỷ giá quy đổi áp dụng (USD -> VND)")
    private BigDecimal exchangeRate;

    @Schema(description = "Đơn vị tiền tệ thanh toán khách đã chọn (VND hoặc USD)")
    private String paymentCurrency;

    @Schema(description = "Số tiền thực thu theo đơn vị tiền tệ khách chọn (VND hoặc USD)")
    private BigDecimal chargedAmount;

    @Schema(description = "Tỷ lệ phụ phí thẻ ngân hàng áp dụng (%)", example = "2.50")
    private BigDecimal surchargePercent;

    @Schema(description = "Số tiền phụ phí quẹt thẻ", example = "190500")
    private BigDecimal surchargeAmount;

    @Schema(description = "Tổng số tiền thanh toán bao gồm phụ phí thẻ", example = "7810500")
    private BigDecimal totalAmountWithSurcharge;

    @Schema(description = "Kênh thanh toán qua SePay (VIETQR, VISA, MASTERCARD)")
    private String paymentChannel;

    @Schema(description = "Thương hiệu thẻ quốc tế sử dụng (VISA, MASTERCARD)")
    private String cardBrand;

    @Schema(description = "Số thẻ quốc tế đã che (PCI-DSS masked PAN)")
    private String maskedCardNumber;

    @Schema(description = "Tên chủ thẻ quốc tế")
    private String cardHolderName;

    @Schema(description = "Mã tham chiếu giao dịch cổng thanh toán SePay / Visa / Mastercard")
    private String processorReference;

    @Schema(description = "Đường dẫn cổng thanh toán thẻ quốc tế SePay (Visa / Mastercard)")
    private String checkoutUrl;

    @Schema(description = "Danh sách tiền tệ được hỗ trợ (VND, USD)")
    private List<String> supportedCurrencies;

    @Schema(description = "Danh sách loại thẻ quốc tế được hỗ trợ (VISA, MASTERCARD)")
    private List<String> supportedCardBrands;

    @Schema(description = "Tên ngân hàng thụ hưởng")
    private String bankName;

    @Schema(description = "Số tài khoản ngân hàng thụ hưởng")
    private String accountNumber;

    @Schema(description = "Tên chủ tài khoản ngân hàng thụ hưởng")
    private String accountHolderName;

    @Schema(description = "Nội dung chuyển khoản (bắt buộc khách phải ghi đúng)")
    private String transferContent;

    @Schema(description = "Đường dẫn quét mã VietQR tự động qua SePay")
    private String qrCodeUrl;

    @Schema(description = "Trạng thái giao dịch")
    private PaymentStatus status;

    @Schema(description = "Thời gian tạo yêu cầu")
    private Instant createdAt;

    @Schema(description = "Hướng dẫn thanh toán chi tiết cho khách")
    private String instructions;
}
