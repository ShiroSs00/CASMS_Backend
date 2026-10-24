package com.casms.trip.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.UUID;

@Data
@Schema(description = "Yêu cầu tạo phiên bản lịch trình mới cho Trip")
public class ScheduleVersionCreateRequest {

    @Schema(description = "ID của phiên bản nguồn để nhân bản nội dung (tùy chọn, phải thuộc cùng Trip)")
    private UUID sourceVersionId;
}
