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
@Schema(description = "Báo cáo tồn kho chi tiết theo từng hạng cabin (BE-10)")
public class CabinTypeInventoryItem {

    @Schema(description = "Hạng phòng (INSIDE, OCEAN_VIEW, BALCONY, SUITE, ...)")
    private CabinType cabinType;

    @Schema(description = "Tổng số phòng vật lý của hạng phòng này trên tàu")
    private long totalCabins;

    @Schema(description = "Số phòng đang ở trạng thái AVAILABLE (vận hành được)")
    private long operationalCabins;

    @Schema(description = "Số phòng đang bảo trì hoặc bị khóa (MAINTENANCE, BLOCKED)")
    private long blockedCabins;

    @Schema(description = "Tổng số chỗ/sức chứa của các phòng vận hành được")
    private long totalSlots;

    @Schema(description = "Số chỗ đã được đặt và xác nhận (Confirmed)")
    private long bookedSlots;

    @Schema(description = "Số chỗ đang được giữ tạm thời (Draft/Hold)")
    private long heldSlots;

    @Schema(description = "Tổng số chỗ đang bị chiếm dụng (booked + held)")
    private long occupiedSlots;

    @Schema(description = "Số chỗ còn lại có thể bán")
    private long remainingSlots;

    @Schema(description = "Hạng phòng này còn được phép bán hay không (remainingSlots > 0 && operationalCabins > 0)")
    private boolean canSell;

    @Schema(description = "Số phòng hỗ trợ người khuyết tật (Accessible)")
    private long accessibleCabinsCount;

    @Schema(description = "Số phòng dành cho gia đình (Family Cabin)")
    private long familyCabinsCount;

    @Schema(description = "Số phòng có cửa thông nhau (Connecting Door)")
    private long connectingCabinsCount;

    @Schema(description = "Tóm tắt cấu hình giường (Twin, King, Double...)")
    private String bedConfigurationSummary;

    @Schema(description = "Giá vé cơ bản cho hạng phòng này")
    private BigDecimal baseFare;
}
