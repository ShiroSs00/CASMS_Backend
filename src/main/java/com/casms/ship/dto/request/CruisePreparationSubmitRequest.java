package com.casms.ship.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Yêu cầu nộp kết quả/bằng chứng chuẩn bị phân hệ để xin xác nhận (Sign-off)")
public class CruisePreparationSubmitRequest {

    @NotBlank(message = "Evidence is required")
    @Schema(description = "Bằng chứng/kết quả thực hiện chuẩn bị của phân hệ", requiredMode = Schema.RequiredMode.REQUIRED, example = "Đã kiểm tra kỹ thuật máy chính, máy phụ, hệ thống cứu hỏa và nạp 100% nhiên liệu")
    private String evidence;

    @Schema(description = "Ghi chú bổ sung")
    private String notes;

    @Schema(description = "Số revision hiện tại để chống xung đột optimistic concurrency", example = "0")
    private Long expectedRevision;
}
