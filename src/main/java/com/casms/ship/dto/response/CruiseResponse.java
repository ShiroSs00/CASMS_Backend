package com.casms.ship.dto.response;

import com.casms.ship.enums.CruiseCruiseType;
import com.casms.ship.enums.CruiseStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Schema(description = "Thông tin chi tiết chuyến đi du thuyền (Cruise)")
public class CruiseResponse {

    @Schema(description = "ID chuyến đi (UUID)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID cruiseId;

    @Schema(description = "Mã chuyến đi", example = "CR-2026-001")
    private String cruiseCode;

    @Schema(description = "Tên chuyến đi", example = "Hải trình khám phá Vịnh Hạ Long 3N2Đ")
    private String cruiseName;

    @Schema(description = "ID của tàu được gán", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID shipId;

    @Schema(description = "Tên của tàu được gán", example = "Ocean Voyager")
    private String shipName;

    @Schema(description = "Mã của tàu được gán", example = "SH-001")
    private String shipCode;

    @Schema(description = "Loại chuyến đi", example = "MULTI_DAYS")
    private CruiseCruiseType cruiseType;

    @Schema(description = "Thời điểm khởi hành (ISO-8601)")
    private Instant departureDatetime;

    @Schema(description = "Thời điểm trở về (ISO-8601)")
    private Instant returnDatetime;

    @Schema(description = "ID cảng xuất phát", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID departurePortId;

    @Schema(description = "Tên cảng xuất phát", example = "Cảng Quốc tế Tuần Châu")
    private String departurePortName;

    @Schema(description = "ID cảng trở về", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID returnPortId;

    @Schema(description = "Tên cảng trở về", example = "Cảng Quốc tế Tuần Châu")
    private String returnPortName;

    @Schema(description = "Trạng thái chuyến đi (DRAFT, PUBLISHED, IN_PROGRESS, COMPLETED, CANCELLED)", example = "DRAFT")
    private CruiseStatus status;

    @Schema(description = "Thời điểm tạo")
    private Instant createdAt;

    @Schema(description = "Thời điểm cập nhật gần nhất")
    private Instant updatedAt;
}
