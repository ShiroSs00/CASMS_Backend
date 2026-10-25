package com.casms.trip.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Yêu cầu hủy bản nháp dở dang của phiên bản lịch trình")
public class VersionCancelRequest {

    @Schema(description = "Revision mong đợi của phiên bản để kiểm tra lạc quan", example = "1")
    private Long expectedRevision;

    @Schema(description = "Lý do hủy bản nháp", example = "Thay đổi phương án kinh doanh hoặc lịch trình không còn phù hợp")
    private String reason;
}
