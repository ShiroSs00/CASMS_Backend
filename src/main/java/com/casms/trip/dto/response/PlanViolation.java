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
@Schema(description = "Chi tiết vi phạm ràng buộc kế hoạch (Validation Violation)")
public class PlanViolation {

    @Schema(description = "Mã vi phạm", example = "MISSING_EMBARKATION")
    private String code;

    @Schema(description = "Mức độ vi phạm (ERROR, WARNING)", example = "ERROR")
    private String severity;

    @Schema(description = "Loại thực thể vi phạm (DAY, STOP, ENTRY, VERSION)", example = "STOP")
    private String entityType;

    @Schema(description = "Khóa clientKey của đối tượng vi phạm (nếu có)")
    private String clientKey;

    @Schema(description = "Ngày thứ N của đối tượng vi phạm (nếu có)")
    private Integer dayNumber;

    @Schema(description = "Trường dữ liệu gây vi phạm (nếu có)")
    private String field;

    @Schema(description = "Đường dẫn JSON path đầy đủ đến trường vi phạm để điều hướng UI focus (ví dụ: entries[clientKey=uuid].end)")
    private String fieldPath;

    @Schema(description = "Gợi ý hành động khắc phục cho FE (ví dụ: EDIT_FIELD, ADD_STOP, SELECT_DAY_TYPE)")
    private String actionHint;

    @Schema(description = "Thông điệp mô tả lỗi chi tiết")
    private String message;
}
