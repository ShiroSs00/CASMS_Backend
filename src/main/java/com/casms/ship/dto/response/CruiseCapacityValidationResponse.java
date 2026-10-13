package com.casms.ship.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.UUID;

@Data
@Schema(description = "Kết quả kiểm tra sức chứa của chuyến đi")
public class CruiseCapacityValidationResponse {

    @Schema(description = "ID chuyến đi", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID cruiseId;

    @Schema(description = "ID tàu được gán", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID shipId;

    @Schema(description = "Tên tàu", example = "Ocean Voyager")
    private String shipName;

    @Schema(description = "Tổng sức chứa tối đa của tàu", example = "2500")
    private Integer shipTotalCapacity;

    @Schema(description = "Số lượng hành khách đã đặt chỗ hiện tại", example = "1200")
    private Long bookedPassengersCount;

    @Schema(description = "Số chỗ còn trống cho chuyến đi", example = "1300")
    private Long availableCapacity;

    @Schema(description = "Sức chứa có đáp ứng đủ lượng khách hiện tại hay không", example = "true")
    private boolean capacitySufficient;
}
