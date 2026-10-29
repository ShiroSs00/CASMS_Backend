package com.casms.ship.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Tổng hợp tồn kho và năng lực tiếp nhận của chuyến đi (BE-10)")
public class CruiseInventorySummaryResponse {

    @Schema(description = "ID chuyến đi")
    private UUID cruiseId;

    @Schema(description = "Mã chuyến đi")
    private String cruiseCode;

    @Schema(description = "ID tàu được gán")
    private UUID shipId;

    @Schema(description = "Tên tàu được gán")
    private String shipName;

    @Schema(description = "Sức chứa tối đa của tàu (số khách vật lý)")
    private int physicalCapacity;

    @Schema(description = "Tổng số phòng vật lý của tàu")
    private long totalCabins;

    @Schema(description = "Tổng số phòng khả dụng để vận hành (AVAILABLE)")
    private long operationalCabins;

    @Schema(description = "Tổng số phòng đang bảo trì hoặc bị khóa")
    private long blockedCabins;

    @Schema(description = "Số lượng hành khách đã xác nhận (Confirmed)")
    private long bookedPassengersCount;

    @Schema(description = "Số lượng hành khách đang giữ chỗ (Draft/Held)")
    private long heldPassengersCount;

    @Schema(description = "Sức chứa còn lại của toàn bộ chuyến đi")
    private long remainingCapacity;

    @Schema(description = "Chuyến đi còn có thể bán tiếp hay không (remainingCapacity > 0 && ít nhất 1 hạng phòng canSell)")
    private boolean canSell;

    @Schema(description = "Số gói dịch vụ đang kích hoạt mở bán")
    private long activePackagesCount;

    @Schema(description = "Phiên bản chính sách tồn kho áp dụng")
    private String policyVersion;

    @Schema(description = "Chi tiết tồn kho và tình trạng bán theo từng hạng phòng")
    private List<CabinTypeInventoryItem> cabinTypeInventory;
}
