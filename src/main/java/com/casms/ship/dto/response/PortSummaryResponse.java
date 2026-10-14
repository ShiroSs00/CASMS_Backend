package com.casms.ship.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.UUID;

@Data
@Schema(description = "Thông tin tóm tắt cảng")
public class PortSummaryResponse {

    @Schema(description = "ID cảng", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID portId;

    @Schema(description = "Mã cảng", example = "VN-HPH")
    private String portCode;

    @Schema(description = "Tên cảng", example = "Cảng Hải Phòng")
    private String portName;
}