package com.casms.ship.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
@Schema(description = "Yêu cầu cập nhật thông tin tầng/boong tàu")
public class DeckUpdateRequest {

    @Schema(description = "Số thứ tự tầng mới", example = "4")
    @Min(value = 1, message = "Deck number must be at least 1")
    private Integer deckNumber;

    @Schema(description = "Tên hiển thị mới của tầng", example = "Sky Deck")
    private String deckName;
}
