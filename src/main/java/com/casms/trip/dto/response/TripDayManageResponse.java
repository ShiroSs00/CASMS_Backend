package com.casms.trip.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Kết quả sau khi thêm hoặc xóa ngày an toàn thành công")
public class TripDayManageResponse {

    @Schema(description = "Thông tin mới nhất của Trip sau thao tác")
    private TripDetailResponse trip;

    @Schema(description = "Thông tin mới nhất của phiên bản lịch trình sau thao tác")
    private TripScheduleVersionDetailResponse version;
}
