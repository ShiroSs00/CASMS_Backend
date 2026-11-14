package com.casms.payment.dto.guest;

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
public class CorporateVatInvoiceResponse {

    @Schema(description = "ID hoá đơn trong hệ thống")
    private UUID invoiceId;

    @Schema(description = "Số hoá đơn / Mã tra cứu hoá đơn điện tử", example = "EINV-202611-00042")
    private String invoiceReference;

    @Schema(description = "Mã ký hiệu mẫu số hoá đơn (Ký hiệu hoá đơn điện tử)", example = "1C26TAA")
    private String invoiceSeries;

    @Schema(description = "ID đơn đặt tour")
    private UUID bookingId;

    @Schema(description = "Mã đơn đặt tour")
    private String bookingReference;

    @Schema(description = "Tên đơn vị mua hàng (Doanh nghiệp)")
    private String companyName;

    @Schema(description = "Mã số thuế doanh nghiệp")
    private String taxCode;

    @Schema(description = "Địa chỉ doanh nghiệp")
    private String companyAddress;

    @Schema(description = "Email người nhận hoá đơn")
    private String vatEmail;

    @Schema(description = "Tiền hàng chưa bao gồm thuế (Subtotal)")
    private BigDecimal subtotalAmount;

    @Schema(description = "Thuế suất GTGT (%)", example = "8.00")
    private BigDecimal vatRate;

    @Schema(description = "Tiền thuế GTGT")
    private BigDecimal vatAmount;

    @Schema(description = "Tổng cộng tiền thanh toán đã bao gồm thuế VAT")
    private BigDecimal totalAmount;

    @Schema(description = "Đơn vị tiền tệ", example = "VND")
    private String currency;

    @Schema(description = "Thời điểm lập và ký hoá đơn điện tử")
    private Instant issuedAt;

    @Schema(description = "Chữ ký số điện tử của doanh nghiệp phát hành")
    private String digitalSignatureThumbprint;

    @Schema(description = "Trạng thái hoá đơn (ISSUED / SENT_TO_TAX_AUTHORITY)")
    private String status;

    @Schema(description = "Link tra cứu hoặc tải hoá đơn PDF")
    private String downloadUrl;
}

