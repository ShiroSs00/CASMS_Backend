package com.casms.trip.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Báo cáo thẩm định tính khả thi của bản kế hoạch lịch trình (Validation Report)")
public class ValidationReport {

    @Schema(description = "ID của phiên bản kế hoạch")
    private UUID versionId;

    @Schema(description = "Revision của phiên bản lúc thẩm định")
    private Long revision;

    @Schema(description = "Kết luận bản kế hoạch có đủ điều kiện để gửi phê duyệt hay không")
    private Boolean validForSubmit;

    @Builder.Default
    @Schema(description = "Danh sách chi tiết các vi phạm hoặc cảnh báo")
    private List<PlanViolation> violations = new ArrayList<>();

    @Builder.Default
    @Schema(description = "Danh sách các khâu kiểm tra hoãn sang giai đoạn actual (ví dụ: FLEET_COMPATIBILITY_DEFERRED, NEEDS_SAILING_DATE)")
    private List<String> deferredChecks = new ArrayList<>();

    @Schema(description = "Phiên bản chính sách quy tắc áp dụng", example = "v1.0")
    private String policyVersion;
}
