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
@Schema(description = "Kết quả kiểm tra tính tương thích và khả dụng của tàu đối với chuyến khởi hành")
public class ShipCompatibilityResponse {

    @Schema(description = "ID chuyến khởi hành")
    private UUID cruiseId;

    @Schema(description = "ID tàu được kiểm tra")
    private UUID shipId;

    @Schema(description = "Tàu có tương thích để gán vào chuyến khởi hành hay không")
    private boolean compatible;

    @Schema(description = "Tàu có đang ở trạng thái cho phép khai thác READY hoặc IN_SERVICE hay không")
    private boolean shipActive;

    @Schema(description = "Lịch trình tàu có trống trong khoảng thời gian chuyến khởi hành hay không")
    private boolean scheduleAvailable;

    @Schema(description = "Sức chứa hành khách Ship.totalCapacity hợp lệ và đủ cho các hành khách đang giữ/đặt chỗ; không có hạn mức riêng của Departure")
    private boolean capacitySufficient;

    @Schema(description = "Tàu đã được cấu hình đầy đủ boong và phòng hay chưa")
    private boolean shipConfigured;

    @Schema(description = "Số lượng boong (decks) của tàu")
    private long decksCount;

    @Schema(description = "Số lượng phòng (cabins) của tàu")
    private long cabinsCount;

    @Schema(description = "Tổng sức chứa hành khách của tàu")
    private Integer totalCapacity;

    @Schema(description = "Số hành khách Active trong các Booking Draft/Confirmed, gồm cả giữ chỗ theo convention hiện có")
    private long bookedPassengersCount;

    @Builder.Default
    @Schema(description = "Danh sách các vấn đề hoặc blocker ngăn cản việc gán tàu")
    private List<String> issues = new ArrayList<>();
}
