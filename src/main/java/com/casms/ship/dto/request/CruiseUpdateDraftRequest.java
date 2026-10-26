package com.casms.ship.dto.request;

import com.casms.ship.enums.CruiseCruiseType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Schema(description = "Yêu cầu cập nhật bản nháp chuyến đi (chỉ áp dụng khi chuyến ở trạng thái DRAFT)")
public class CruiseUpdateDraftRequest {

    @Schema(description = "Revision kỳ vọng của chuyến đi để bảo vệ chống ghi đè đồng thời (Optimistic lock)", example = "1")
    private Long expectedRevision;

    @Schema(description = "Tên chuyến đi mới", example = "Hải trình khám phá Vịnh Hạ Long - Cát Bà 3N2Đ")
    private String cruiseName;

    @Schema(description = "Loại chuyến đi mới (ONE_DAY hoặc MULTI_DAYS)", example = "MULTI_DAYS")
    private CruiseCruiseType cruiseType;

    @Schema(description = "Thời điểm khởi hành mới (ISO-8601)", example = "2026-11-01T09:00:00Z")
    private Instant departureDatetime;

    @Schema(description = "Thời điểm kết thúc mới (ISO-8601)", example = "2026-11-03T17:00:00Z")
    private Instant returnDatetime;

    @Schema(description = "ID cảng xuất phát mới", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID departurePortId;

    @Schema(description = "ID cảng kết thúc mới", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID returnPortId;
}
