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

    @Schema(description = "Mã chuyến đi (duy nhất)", example = "CR-2026-001", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Cruise code is required")
    private String cruiseCode;

    @Schema(description = "Tên chuyến đi", example = "Hải trình khám phá Vịnh Hạ Long 3N2Đ", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Cruise name is required")
    private String cruiseName;

    @Schema(description = "ID của tàu được gán (có thể để trống khi ở trạng thái DRAFT)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID shipId;

    @Schema(description = "Loại chuyến đi (ONE_DAY hoặc MULTI_DAYS)", example = "MULTI_DAYS", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Cruise type is required")
    private CruiseCruiseType cruiseType;

    @Schema(description = "Thời điểm khởi hành (ISO-8601)", example = "2026-11-01T08:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Departure datetime is required")
    private Instant departureDatetime;

    @Schema(description = "Thời điểm kết thúc / trở về (ISO-8601)", example = "2026-11-03T18:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Return datetime is required")
    private Instant returnDatetime;

    @Schema(description = "ID cảng xuất phát", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Departure port ID is required")
    private UUID departurePortId;

    @Schema(description = "ID cảng kết thúc (có thể trùng cảng xuất phát nếu là chuyến vòng)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Return port ID is required")
    private UUID returnPortId;
}
