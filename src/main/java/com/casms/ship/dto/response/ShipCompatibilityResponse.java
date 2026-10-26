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
@Schema(description = "Kết quả kiểm tra tính tương thích và khả dụng của tàu đối với chuyến đi")
public class ShipCompatibilityResponse {

    @Schema(description = "ID chuyến đi")
    private UUID cruiseId;

    @Schema(description = "ID tàu được kiểm tra")
    private UUID shipId;

    @Schema(description = "Tàu có tương thích để gán vào chuyến đi hay không")
    private boolean compatible;

    @Schema(description = "Tàu có đang ở trạng thái ACTIVE hay không")
    private boolean shipActive;

    @Schema(description = "Lịch trình tàu có trống trong khoảng thời gian chuyến đi hay không")
    private boolean scheduleAvailable;

    @Schema(description = "Sức chứa của tàu có đủ đáp ứng lượng khách đã đặt hay không")
    private boolean capacitySufficient;

    @Schema(description = "Tàu đã được cấu hình đầy đủ boong và phòng hay chưa")
    private boolean shipConfigured;

    @Schema(description = "Số lượng boong (decks) của tàu")
    private long decksCount;

    @Schema(description = "Số lượng phòng (cabins) của tàu")
    private long cabinsCount;

    @Schema(description = "Tổng sức chứa hành khách của tàu")
    private int totalCapacity;

    @Schema(description = "Số lượng hành khách đã đặt chỗ trên chuyến đi")
    private long bookedPassengersCount;

    @Builder.Default
    @Schema(description = "Danh sách các vấn đề hoặc blocker ngăn cản việc gán tàu")
    private List<String> issues = new ArrayList<>();
}
