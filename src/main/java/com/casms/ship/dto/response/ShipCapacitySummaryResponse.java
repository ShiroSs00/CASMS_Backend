package com.casms.ship.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.UUID;

@Data
@Schema(description = "Báo cáo tổng hợp sức chứa và cấu hình của tàu")
public class ShipCapacitySummaryResponse {

    @Schema(description = "ID tàu", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID shipId;

    @Schema(description = "Tên tàu", example = "Ocean Voyager")
    private String shipName;

    @Schema(description = "Sức chứa tối đa theo thiết kế", example = "2500")
    private Integer configuredTotalCapacity;

    @Schema(description = "Tổng số boong theo thiết kế", example = "12")
    private Integer configuredTotalDecks;

    @Schema(description = "Số lượng phòng/cabin thực tế đã cấu hình", example = "850")
    private Long actualCabinCount;

    @Schema(description = "Số lượng phòng đang trống (AVAILABLE)", example = "720")
    private Long availableCabinCount;

    @Schema(description = "Số lượng boong thực tế đã cấu hình trong hệ thống", example = "12")
    private Long actualDeckCount;
}
