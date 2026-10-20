package com.casms.ship.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.UUID;

@Data
@Schema(description = "Yêu cầu tạo mới tầng/boong tàu")
public class DeckCreateRequest {

    @Schema(description = "ID của tàu sở hữu tầng này", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Ship ID is required")
    private UUID shipId;

    @Schema(description = "Số thứ tự tầng (bắt đầu từ 1, không vượt quá tổng số boong của tàu)", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Deck number is required")
    @Min(value = 1, message = "Deck number must be at least 1")
    private Integer deckNumber;

    @Schema(description = "Tên hiển thị của tầng", example = "Promenade Deck")
    private String deckName;

    private Double modelPositionX;
    private Double modelPositionY;
    private Double modelPositionZ;
    private Double modelRotationX;
    private Double modelRotationY;
    private Double modelRotationZ;
}
