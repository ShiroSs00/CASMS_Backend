package com.casms.payment.config;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "casms.payment.sepay")
public class SepayProperties {

    /** Bật / tắt tính năng tích hợp thanh toán SePay. */
    private boolean enabled = true;

    /** API Key dùng để xác thực webhook gửi từ SePay (Header: Authorization: Apikey <api_key>). */
    private String apiKey;

    /** Tên ngân hàng nhận thanh toán (VD: MBBank, VCB, TPBank, v.v.). */
    private String bankName = "MBBank";

    /** Số tài khoản ngân hàng nhận tiền. */
    private String accountNumber = "0901452366";

    /** Tên chủ tài khoản ngân hàng nhận tiền. */
    private String accountName = "CASMS CRUISE";

    /** Tỷ giá quy đổi cố định hoặc mặc định từ USD sang VNĐ (VD: 25,400 VNĐ / 1 USD). */
    private BigDecimal usdToVndRate = new BigDecimal("25400");

    /** Mẫu giao diện VietQR từ SePay ("compact", "qronly", "print"). */
    private String qrTemplate = "compact";

    /**
     * Độ sai lệch cho phép (%) khi so khớp số tiền chuyển khoản VNĐ (phòng trường hợp làm tròn số).
     */
    private double tolerancePercent = 1.0;

    /** Bật / tắt thanh toán thẻ quốc tế (Visa / Mastercard) qua cổng SePay. */
    private Boolean cardEnabled = Boolean.TRUE;

    public boolean isCardEnabled() {
        return !Boolean.FALSE.equals(cardEnabled);
    }

    /** Đơn vị tiền tệ mặc định khi không truyền currency ("VND" hoặc "USD"). */
    private String defaultCurrency = "VND";

    /** Danh sách đơn vị tiền tệ được phép thanh toán qua SePay (Tiền Việt VND và USD). */
    private List<String> supportedCurrencies = new ArrayList<>(List.of("VND", "USD"));

    /** Danh sách thương hiệu thẻ quốc tế được chấp nhận qua SePay (VISA, MASTERCARD). */
    private List<String> supportedCardBrands = new ArrayList<>(List.of("VISA", "MASTERCARD"));

    /** Danh sách kênh thanh toán SePay hỗ trợ (VIETQR, VISA, MASTERCARD). */
    private List<String> supportedPaymentChannels =
            new ArrayList<>(List.of("VIETQR", "VISA", "MASTERCARD"));

    /** Mã đơn vị chấp nhận thẻ (Merchant ID) trên cổng thanh toán quốc tế SePay. */
    private String merchantId = "CASMS-SEPAY-INTL-001";

    /** Đường dẫn cổng thanh toán thẻ quốc tế SePay (Visa / Mastercard). */
    private String cardGatewayUrl = "https://pay.sepay.vn/v1/card-checkout";

    /** Bật xác thực bảo mật 3D-Secure (3DS2) cho giao dịch thẻ Visa / Mastercard. */
    private boolean threeDSecureEnabled = true;

    /** Phụ phí thanh toán thẻ quốc tế Visa / Mastercard (thông thường 2.0% - 3.0% trên các du thuyền 5 sao Hạ Long). */
    private BigDecimal cardSurchargePercent = new BigDecimal("2.50");

    /** Phụ phí thanh toán thẻ nội địa ATM (thông thường 1.0% - 1.5%). */
    private BigDecimal domesticCardSurchargePercent = new BigDecimal("1.50");

    /** Bật / tắt áp dụng phụ phí khi thanh toán bằng thẻ. */
    private boolean cardSurchargeEnabled = true;

    /** Tỷ lệ phụ phí linh hoạt theo từng thương hiệu thẻ (VISA, MASTERCARD, JCB, AMEX, NAPAS). */
    private java.util.Map<String, BigDecimal> brandSurchargeRates = new java.util.concurrent.ConcurrentHashMap<>(
            java.util.Map.of(
                    "VISA", new BigDecimal("2.50"),
                    "MASTERCARD", new BigDecimal("2.50"),
                    "JCB", new BigDecimal("2.00"),
                    "AMEX", new BigDecimal("3.50"),
                    "NAPAS", new BigDecimal("1.50")
            )
    );

    /** Phương thức tra cứu tỷ lệ phụ phí linh hoạt theo thương hiệu thẻ */
    public BigDecimal resolveCardSurcharge(String cardBrand) {
        if (!cardSurchargeEnabled) {
            return BigDecimal.ZERO;
        }
        if (cardBrand != null) {
            String norm = cardBrand.trim().toUpperCase(java.util.Locale.ROOT);
            if (brandSurchargeRates != null && brandSurchargeRates.containsKey(norm)) {
                return brandSurchargeRates.get(norm);
            }
            if ("ATM".equalsIgnoreCase(norm) || "NAPAS".equalsIgnoreCase(norm)) {
                return domesticCardSurchargePercent != null ? domesticCardSurchargePercent : new BigDecimal("1.50");
            }
        }
        return cardSurchargePercent != null ? cardSurchargePercent : new BigDecimal("2.50");
    }
}

