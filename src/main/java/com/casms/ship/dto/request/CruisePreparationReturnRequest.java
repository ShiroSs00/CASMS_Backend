package com.casms.ship.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Yêu cầu trả lại phân hệ chuẩn bị để yêu cầu bổ sung bằng chứng")
public class CruisePreparationReturnRequest {

    @NotBlank(message = "Return reason is required")
    @Schema(description = "Lý do trả lại yêu cầu bổ sung", requiredMode = Schema.RequiredMode.REQUIRED, example = "Chứng chỉ an toàn thiết bị chưa được cập nhật ngày hết hạn mới nhất")
    private String reason;

    @Schema(description = "Ghi chú bổ sung")
    private String notes;

    @Schema(description = "Số revision hiện tại để chống xung đột optimistic concurrency", example = "1")
    private Long expectedRevision;
}
