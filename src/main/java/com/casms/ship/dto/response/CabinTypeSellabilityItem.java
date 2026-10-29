package com.casms.ship.dto.response;

import com.casms.ship.enums.CabinType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Tình trạng mở bán của từng hạng phòng (Cabin Type) - BE-15")
public class CabinTypeSellabilityItem {

    @Schema(description = "Hạng phòng (INTERIOR, OCEAN_VIEW, BALCONY, SUITE)")
    private CabinType cabinType;

    @Schema(description = "Giá cơ bản của hạng phòng")
    private BigDecimal baseFare;

    @Schema(description = "Đơn vị tiền tệ")
    private String currency;

    @Schema(description = "Tổng số chỗ theo hạng phòng")
    private long totalSlots;

    @Schema(description = "Số chỗ còn trống theo hạng phòng")
    private long availableSlots;

    @Schema(description = "Hạng phòng có thể bán được (Cruise sellable && availableSlots > 0)")
    private boolean sellable;
}
