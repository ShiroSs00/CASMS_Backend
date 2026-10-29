package com.casms.trip.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Yêu cầu thu hồi phiên bản đang chờ duyệt (IN_REVIEW -> DRAFT)")
public class VersionWithdrawRequest {

    @Schema(description = "Revision kỳ vọng của phiên bản", example = "2")
    private Long expectedRevision;

    @Schema(description = "Lý do thu hồi bản nháp", example = "Cần điều chỉnh thêm giờ tham quan trên bờ")
    private String reason;
}
