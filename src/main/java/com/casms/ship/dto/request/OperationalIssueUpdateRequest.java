
package com.casms.ship.dto.request;

import com.casms.ship.enums.OperationalIssueSeverity;
import com.casms.ship.enums.OperationalIssueType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Schema(description = "Yêu cầu cập nhật sự cố vận hành (chỉ áp dụng cho sự cố ở trạng thái OPEN)")
public class OperationalIssueUpdateRequest {

    @Schema(description = "Mức độ nghiêm trọng mới", example = "HIGH", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Severity is required")
    private OperationalIssueSeverity severity;

    @Schema(description = "Mô tả chi tiết sự cố mới", example = "Đã kiểm tra lại hệ thống điều hòa, cần thay van áp suất", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Description cannot be blank")
    private String description;

    @Schema(description = "(Bất biến / Bị bỏ qua) ID chuyến đi gắn với sự cố", accessMode = Schema.AccessMode.READ_ONLY)
    private UUID cruiseId;

    @Schema(description = "(Bất biến / Bị bỏ qua) ID tàu gắn với sự cố", accessMode = Schema.AccessMode.READ_ONLY)
    private UUID shipId;

    @Schema(description = "(Bất biến / Bị bỏ qua) ID cabin gắn với sự cố", accessMode = Schema.AccessMode.READ_ONLY)
    private UUID cabinId;

    @Schema(description = "(Bất biến / Bị bỏ qua) Loại sự cố", accessMode = Schema.AccessMode.READ_ONLY)
    private OperationalIssueType issueType;
}
