package com.casms.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class SepayWebhookPayload {

    /** ID giao dịch trên hệ thống SePay (ví dụ: 92704). */
    private Long id;

    /** Tên ngân hàng nhận (ví dụ: MBBank, VCB, ACB, v.v.). */
    private String gateway;

    /** Thời gian giao dịch phía ngân hàng (định dạng chuỗi yyyy-MM-dd HH:mm:ss). */
    private String transactionDate;

    /** Số tài khoản ngân hàng thụ hưởng. */
    private String accountNumber;

    /** Mã giao dịch / mã nhận diện nếu SePay trích xuất được. */
    private String code;

    /** Toàn bộ nội dung chuyển khoản do người gửi nhập. */
    private String content;

    /** Loại chuyển tiền: "in" (tiền vào) hoặc "out" (tiền ra). */
    private String transferType;

    /** Số tiền giao dịch thực nhận bằng VNĐ. */
    private BigDecimal transferAmount;

    /** Số dư tài khoản sau giao dịch. */
    private BigDecimal accumulated;

    /** Tài khoản phụ (subAccount) nếu có. */
    private String subAccount;

    /** Mã tham chiếu phía ngân hàng (FT number, trace code). */
    private String referenceCode;

    /** Chuỗi miêu tả đầy đủ do ngân hàng trả về. */
    private String description;
}
