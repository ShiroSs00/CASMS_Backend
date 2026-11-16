package com.casms.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Setter
public class CardSurchargeConfigRequest {

    @Schema(description = "Bật / tắt chính sách tính phụ phí quẹt thẻ", example = "true")
    private Boolean cardSurchargeEnabled;

    @Schema(description = "Tỷ lệ phụ phí mặc định cho thẻ quốc tế Visa / Mastercard (%)", example = "2.50")
    @DecimalMin(value = "0.0", message = "Surcharge percent cannot be negative")
    @DecimalMax(value = "10.0", message = "Surcharge percent cannot exceed 10%")
    private BigDecimal cardSurchargePercent;

    @Schema(description = "Tỷ lệ phụ phí cho thẻ nội địa ATM / NAPAS (%)", example = "1.50")
    @DecimalMin(value = "0.0", message = "Domestic surcharge percent cannot be negative")
    @DecimalMax(value = "10.0", message = "Domestic surcharge percent cannot exceed 10%")
    private BigDecimal domesticCardSurchargePercent;

    @Schema(
            description = "Bảng tỷ lệ phụ phí linh hoạt theo từng thương hiệu thẻ (VISA, MASTERCARD, JCB, AMEX, NAPAS)",
            example = "{\"VISA\": 2.0, \"MASTERCARD\": 2.0, \"AMEX\": 3.5, \"JCB\": 2.0, \"NAPAS\": 1.0}")
    private Map<String, BigDecimal> brandSurchargeRates;
}
