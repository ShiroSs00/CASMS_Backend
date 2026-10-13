package com.casms.ship.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Schema(description = "Thông tin lịch sử gán phòng cho hành khách")
public class CabinAssignmentHistoryResponse {

    @Schema(description = "ID bản ghi gán phòng", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID cabinAssignmentId;

    @Schema(description = "ID hành khách trong đặt chỗ", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID bookingPassengerId;

    @Schema(description = "Thời điểm bắt đầu có hiệu lực")
    private Instant validFrom;

    @Schema(description = "Thời điểm kết thúc hiệu lực")
    private Instant validTo;

    @Schema(description = "ID người dùng thực hiện gán phòng", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID assignedByUserId;

    @Schema(description = "Lý do thay đổi hoặc nâng cấp phòng", example = "Nâng cấp hạng phòng theo yêu cầu khách")
    private String changeReason;
}
