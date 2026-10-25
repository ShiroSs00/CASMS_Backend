package com.casms.trip.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Sự kiện kiểm toán của phiên bản kế hoạch")
public class TripAuditResponse {

    @Schema(description = "ID bản ghi audit")
    private UUID auditId;

    @Schema(description = "ID người thực hiện thao tác")
    private UUID actorUserId;

    @Schema(description = "Tên tài khoản người thực hiện thao tác", example = "planner_admin")
    private String actorName;

    @Schema(description = "Tên hành động thực hiện (SAVE_DRAFT, SUBMIT, RETURN, PUBLISH, CLONE)")
    private String action;

    @Schema(description = "Loại thực thể")
    private String entityType;

    @Schema(description = "ID thực thể")
    private UUID entityId;

    @Schema(description = "Kết quả thao tác (SUCCESS, REJECTED)")
    private String result;

    @Schema(description = "Chi tiết thao tác / lý do")
    private String detail;

    @Schema(description = "Thời điểm phát sinh sự kiện")
    private Instant occurredAt;
}
