package com.casms.ship.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Kết quả áp dụng thay đổi lịch trình, tàu hoặc cấu hình chuyến đi (BE-R05b / C11, C17)")
public class CruiseApplyChangeResponse {

    @Schema(description = "ID chuyến đi")
    private UUID cruiseId;

    @Schema(description = "Mã chuyến đi")
    private String cruiseCode;

    @Schema(description = "Revision mới sau khi áp dụng thay đổi")
    private Long revision;

    @Schema(description = "Thời điểm áp dụng thay đổi")
    private Instant appliedAt;

    @Schema(description = "Thời điểm khởi hành trước thay đổi")
    private Instant beforeDepartureDatetime;

    @Schema(description = "Thời điểm khởi hành sau thay đổi")
    private Instant afterDepartureDatetime;

    @Schema(description = "ID tàu trước thay đổi")
    private UUID beforeShipId;

    @Schema(description = "ID tàu sau thay đổi")
    private UUID afterShipId;

    @Schema(description = "Số đặt chỗ bị ảnh hưởng")
    private long affectedBookingsCount;

    @Schema(description = "Số hành khách bị ảnh hưởng")
    private long affectedPassengersCount;

    @Schema(description = "Số hoạt động trên tàu đã được cập nhật lịch")
    private long affectedActivitiesCount;

    @Schema(description = "Số tour tham quan bờ đã được cập nhật lịch")
    private long affectedExcursionsCount;

    @Builder.Default
    @Schema(description = "Danh sách các công việc khắc phục C17 đã được tạo")
    private List<CruiseImpactTaskResponse> generatedTasks = new ArrayList<>();

    @Schema(description = "Thông báo kết quả")
    private String message;
}
