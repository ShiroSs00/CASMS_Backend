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
@Schema(description = "Kết quả đánh giá tác động thay đổi chuyến đi (Change Impact Assessment / C11, C17)")
public class CruiseChangeImpactResponse {

    @Schema(description = "ID chuyến đi")
    private UUID cruiseId;

    @Schema(description = "Revision hiện tại của Cruise")
    private Long currentRevision;

    @Schema(description = "Lịch trình ngày giờ có bị tác động hay không")
    private boolean scheduleImpacted;

    @Schema(description = "Số ngày dịch chuyển của lịch trình (0 nếu không đổi ngày)")
    private long dateShiftDays;

    @Schema(description = "Tàu được gán có bị thay đổi hay không")
    private boolean shipImpacted;

    @Schema(description = "Số lượng hành khách đã đặt chỗ hiện tại")
    private long bookedPassengersCount;

    @Schema(description = "Số lượng booking bị tác động cần xử lý")
    private long affectedBookingsCount;

    @Schema(description = "Số lượng hoạt động trên tàu bị tác động cần dời lịch hoặc kiểm tra vị trí")
    private long affectedActivitiesCount;

    @Schema(description = "Số lượng tour tham quan bị tác động cần dời lịch")
    private long affectedExcursionsCount;

    @Schema(description = "Số lượng cabin cần phân bổ lại (BE-18)")
    private long affectedCabinsCount;

    @Schema(description = "Số lượng gói dịch vụ bị ảnh hưởng (BE-18)")
    private long affectedPackagesCount;

    @Schema(description = "Số lượng thông báo cần phát cho khách hàng (BE-18/BE-19)")
    private long requiredNotificationsCount;

    @Builder.Default
    @Schema(description = "Danh sách điều kiện chặn ngăn cản thực hiện thay đổi")
    private List<String> blockers = new ArrayList<>();

    @Builder.Default
    @Schema(description = "Danh sách cảnh báo về tác động của thay đổi")
    private List<String> warnings = new ArrayList<>();

    @Builder.Default
    @Schema(description = "Danh sách các công việc xử lý tác động phát sinh (C17 Impact Tasks)")
    private List<CruiseImpactTaskResponse> generatedTasks = new ArrayList<>();

    @Schema(description = "Có thể tiến hành áp dụng thay đổi hay không")
    private boolean canProceed;
}
