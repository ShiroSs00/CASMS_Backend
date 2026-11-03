package com.casms.ship.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Kết quả đánh giá tác động khi đổi tàu được gán cho chuyến khởi hành")
public class ShipChangeImpactResponse {

    @Schema(description = "ID chuyến khởi hành")
    private UUID cruiseId;

    @Schema(description = "ID tàu hiện tại đang gán")
    private UUID currentShipId;

    @Schema(description = "ID tàu mới dự kiến gán")
    private UUID newShipId;

    @Schema(description = "Số hành khách Active trong các Booking Draft/Confirmed, gồm cả giữ chỗ theo convention hiện có")
    private long bookedPassengersCount;

    @Schema(description = "Sức chứa hành khách của tàu mới hợp lệ và đủ cho các hành khách đang giữ/đặt chỗ")
    private boolean capacitySufficient;

    @Schema(description = "Số lượng cabin đã phân bổ có thể bị ảnh hưởng khi đổi tàu")
    private long affectedCabinAssignmentsCount;

    @Schema(description = "Số lượng hoạt động trên tàu (onboard activities) có thể bị ảnh hưởng cấu hình boong/phòng")
    private long affectedOnboardActivitiesCount;

    @Builder.Default
    @Schema(description = "Danh sách các điều kiện chặn (blockers) không cho phép đổi tàu")
    private List<String> blockers = new ArrayList<>();

    @Builder.Default
    @Schema(description = "Danh sách cảnh báo cần lưu ý hoặc xử lý khi đổi tàu")
    private List<String> warnings = new ArrayList<>();

    @Schema(description = "Có thể tiến hành đổi tàu hay không")
    private boolean canProceed;
    private ShipAssignmentSafetyResponse shipAssignmentSafety;
}
