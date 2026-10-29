package com.casms.ship.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Schema(description = "Yêu cầu cập nhật thông tin tầng/boong tàu")
public class DeckUpdateRequest {

    @Schema(description = "Số thứ tự tầng mới", example = "4")
    @Min(value = 1, message = "Deck number must be at least 1")
    private Integer deckNumber;

    @Schema(description = "Tên hiển thị mới của tầng", example = "Sky Deck")
    private String deckName;

    @Schema(description = "Hệ số giá tầng boong mới (ví dụ 1.20 là +20%)", example = "1.20")
    @DecimalMin(value = "0.50", message = "Price multiplier must be at least 0.50")
    @DecimalMax(value = "5.00", message = "Price multiplier cannot exceed 5.00")
    private BigDecimal priceMultiplier;

    private Double modelPositionX;
    private Double modelPositionY;
    private Double modelPositionZ;
    private Double modelRotationX;
    private Double modelRotationY;
    private Double modelRotationZ;
}
