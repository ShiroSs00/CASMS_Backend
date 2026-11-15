package com.casms.payment.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SepayConfigResponse {

    @Schema(description = "Trạng thái bật/tắt cổng thanh toán SePay")
    private boolean enabled;

    @Schema(description = "Trạng thái bật/tắt thanh toán thẻ quốc tế Visa / Mastercard qua SePay")
    private boolean cardEnabled;

    @Schema(description = "Đơn vị tiền tệ mặc định")
    private String defaultCurrency;

    @Schema(description = "Danh sách tiền tệ hỗ trợ thanh toán (VND, USD)")
    private List<String> supportedCurrencies;

    @Schema(description = "Danh sách thương hiệu thẻ quốc tế hỗ trợ (VISA, MASTERCARD)")
    private List<String> supportedCardBrands;

    @Schema(description = "Danh sách kênh thanh toán hỗ trợ (VIETQR, VISA, MASTERCARD)")
    private List<String> supportedPaymentChannels;

    @Schema(description = "Tỷ giá quy đổi mặc định USD -> VND")
    private BigDecimal usdToVndRate;

    @Schema(description = "Tên ngân hàng nhận chuyển khoản VietQR")
    private String bankName;

    @Schema(description = "Số tài khoản ngân hàng thụ hưởng")
    private String accountNumber;

    @Schema(description = "Tên chủ tài khoản ngân hàng thụ hưởng")
    private String accountName;

    @Schema(description = "Mã Merchant ID cổng thẻ quốc tế SePay")
    private String merchantId;

    @Schema(description = "URL cổng thanh toán thẻ quốc tế SePay")
    private String cardGatewayUrl;

    @Schema(description = "Trạng thái bật bảo mật 3D-Secure (3DS2)")
    private boolean threeDSecureEnabled;
}

