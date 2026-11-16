package com.casms.payment.dto.guest;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
public class PromoCodeResponse {

    @Schema(description = "Mã khuyến mãi đã áp dụng")
    private String code;

    @Schema(description = "Mã có hợp lệ hay không")
    private boolean valid;

    @Schema(description = "Kiểu giảm giá (PERCENT hoặc FIXED)")
    private String discountType;

    @Schema(description = "Giá trị giảm (ví dụ 10% hoặc 500,000 VND)")
    private BigDecimal discountValue;

    @Schema(description = "Số tiền được giảm thực tế")
    private BigDecimal discountAmount;

    @Schema(description = "Số tiền gốc ban đầu")
    private BigDecimal originalAmount;

    @Schema(description = "Số tiền phải thanh toán sau giảm giá")
    private BigDecimal finalAmount;

    @Schema(description = "Mô tả chương trình ưu đãi")
    private String description;

    @Schema(description = "Thông điệp phản hồi")
    private String message;
}

