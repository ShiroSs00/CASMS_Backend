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
@Schema(description = "Chi tiết một phát hiện hoặc rào cản trong quá trình đánh giá tính sẵn sàng (BE-09)")
public class ReadinessIssueDetail {

    @Schema(description = "Mã ổn định của vấn đề", example = "SUPPLIER_NOT_VERIFIED")
    private String code;

    @Schema(description = "Loại đối tượng mục tiêu (CRUISE, SHIP, CABIN_TYPE, PACKAGE, EXCURSION, SUPPLIER, PREPARATION_SECTION)", example = "EXCURSION")
    private String targetType;

    @Schema(description = "ID của đối tượng mục tiêu", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID targetId;

    @Schema(description = "Phạm vi ảnh hưởng (WHOLE_CRUISE, CABIN_TYPE, PACKAGE, EXCURSION, SHIP)", example = "EXCURSION")
    private String scope;

    @Schema(description = "Mức độ ảnh hưởng (BLOCKER, WARNING, INDETERMINATE)", example = "BLOCKER")
    private ReadinessSeverity severity;

    @Schema(description = "Mô tả vấn đề", example = "Tour bờ chưa có nhà cung cấp được xác thực")
    private String message;

    @Schema(description = "Bằng chứng cụ thể kiểm tra được từ cơ sở dữ liệu", example = "Supplier status is PENDING_VERIFICATION on excursion EXC-001")
    private String evidence;

    @Schema(description = "Vai trò sở hữu chịu trách nhiệm khắc phục", example = "CRUISE_MANAGER")
    private String ownerRole;

    @Schema(description = "Hướng xử lý được phép", example = "Xác thực nhà cung cấp hoặc đổi nhà cung cấp hợp lệ")
    private String allowedAction;

    @Schema(description = "Quyền cần thiết để thực hiện hành động khắc phục (Khớp 100% với Permission enum thật)", example = "EXCURSION_UPDATE")
    private String requiredPermission;

    @Schema(description = "URL endpoint API để thực hiện khắc phục", example = "/api/shore-excursions/{id}")
    private String remediationUrl;

    @Schema(description = "Phương thức HTTP của thao tác khắc phục", example = "PUT")
    private String remediationOperation;
}
