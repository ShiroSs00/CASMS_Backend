package com.casms.payment.config;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

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
}
