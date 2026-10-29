package com.casms.ship.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Schema(description = "Thông tin tầng/boong tàu")
public class DeckResponse {

    @Schema(description = "ID duy nhất của tầng (UUID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID deckId;

    @Schema(description = "ID của tàu sở hữu", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID shipId;

    @Schema(description = "Số thứ tự tầng", example = "3")
    private Integer deckNumber;

    @Schema(description = "Tên hiển thị của tầng", example = "Promenade Deck")
    private String deckName;

    private boolean serviceLevel;
    private String modelMetadataJson;

    @Schema(description = "Hệ số giá tầng boong (mặc định 1.00)", example = "1.15")
    private BigDecimal priceMultiplier;

    private Double modelPositionX;
    private Double modelPositionY;
    private Double modelPositionZ;
    private Double modelRotationX;
    private Double modelRotationY;
    private Double modelRotationZ;

    @Schema(description = "Thời điểm tạo")
    private Instant createdAt;

    @Schema(description = "Thời điểm cập nhật gần nhất")
    private Instant updatedAt;
}
