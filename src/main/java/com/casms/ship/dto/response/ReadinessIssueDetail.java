package com.casms.ship.dto.response;

import com.casms.ship.enums.ReadinessSeverity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Chi tiết một phát hiện hoặc rào cản trong quá trình đánh giá tính sẵn sàng")
public class ReadinessIssueDetail {

    @Schema(description = "Mã ổn định của vấn đề", example = "MISSING_CAPTAIN")
    private String code;

    @Schema(description = "Loại đối tượng mục tiêu", example = "CRUISE")
    private String targetType;

    @Schema(description = "ID của đối tượng mục tiêu", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID targetId;

    @Schema(description = "Phạm vi ảnh hưởng (GLOBAL, CRUISE, PACKAGE, EXCURSION, SHIP)", example = "CRUISE")
    private String scope;

    @Schema(description = "Mức độ ảnh hưởng", example = "BLOCKER")
    private ReadinessSeverity severity;

    @Schema(description = "Mô tả vấn đề", example = "Chuyến đi chưa được phân công Thuyền trưởng (CAPTAIN)")
    private String message;

    @Schema(description = "Bằng chứng cụ thể kiểm tra được", example = "Staff list contains 0 ACTIVE members with role CAPTAIN")
    private String evidence;

    @Schema(description = "Hướng xử lý được phép", example = "Phân công nhân sự có vai trò Thuyền trưởng qua API /cruises/{id}/staff")
    private String allowedAction;

    @Schema(description = "Quyền cần thiết để thực hiện hành động khắc phục", example = "CRUISE_ASSIGN_STAFF")
    private String requiredPermission;
}
