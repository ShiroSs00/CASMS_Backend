package com.casms.ship.dto.request;

import com.casms.ship.enums.CruiseCruiseType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Schema(description = "Yêu cầu tạo mới chuyến đi du thuyền (Cruise)")
public class CruiseCreateRequest {

    @Schema(description = "Tên chuyến đi", example = "Hải trình khám phá Vịnh Hạ Long 3N2Đ", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Cruise name is required")
    private String cruiseName;

    @Schema(description = "ID của tàu được gán (có thể để trống khi ở trạng thái DRAFT)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID shipId;

    @Schema(description = "Loại chuyến đi (ONE_DAY hoặc MULTI_DAYS). Nếu để trống, hệ thống tự động suy luận theo thời gian đi và về", example = "MULTI_DAYS")
    private CruiseCruiseType cruiseType;

    @Schema(description = "Thời điểm khởi hành (ISO-8601)", example = "2026-11-01T08:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Departure datetime is required")
    private Instant departureDatetime;

    @Schema(description = "Thời điểm kết thúc / trở về (ISO-8601). Tự động tính toán và khóa từ nguồn nếu có sourceVersionId (G04)", example = "2026-11-03T18:00:00Z")
    private Instant returnDatetime;

    @Schema(description = "ID cảng xuất phát. Tự động lấy từ điểm dừng đầu tiên nếu có sourceVersionId (G04)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID departurePortId;

    @Schema(description = "ID cảng kết thúc. Tự động lấy từ điểm dừng cuối cùng nếu có sourceVersionId (G04)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID returnPortId;

    @Schema(description = "(Tùy chọn) ID phiên bản TripScheduleVersion (đã PUBLISHED) dùng làm nguồn cho chuyến đi này", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID sourceVersionId;

    @Schema(description = "(Tùy chọn) Revision của phiên bản nguồn tại thời điểm preview, dùng để phát hiện stale preview", example = "1")
    private Long expectedSourceVersionRevision;
    private String itineraryMode;
    private String plannedScope;
}
