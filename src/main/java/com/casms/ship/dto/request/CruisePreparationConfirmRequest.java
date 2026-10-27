package com.casms.ship.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Yêu cầu phê duyệt xác nhận phân hệ chuẩn bị")
public class CruisePreparationConfirmRequest {

    @Schema(description = "Ghi chú phê duyệt")
    private String notes;

    @Schema(description = "Số revision hiện tại để chống xung đột optimistic concurrency", example = "1")
    private Long expectedRevision;
}
