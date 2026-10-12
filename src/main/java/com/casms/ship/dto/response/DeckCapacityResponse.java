package com.casms.ship.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.UUID;

@Data
@Schema(description = "Báo cáo sức chứa của một tầng/boong")
public class DeckCapacityResponse {

    @Schema(description = "ID của tầng", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID deckId;

    @Schema(description = "Tên hiển thị của tầng", example = "Promenade Deck")
    private String deckName;

    @Schema(description = "Số thứ tự tầng", example = "3")
    private Integer deckNumber;

    @Schema(description = "Tổng số phòng trên tầng", example = "40")
    private Long totalCabins;

    @Schema(description = "Số phòng đang còn trống", example = "35")
    private Long availableCabins;

    @Schema(description = "Tổng sức chứa hành khách của toàn bộ tầng (tổng capacity của các cabin)", example = "120")
    private Long totalPassengerCapacity;
}
