package com.casms.ship.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Yêu cầu cập nhật thông tin cảng. Lưu ý: portCode và countryCode là bất biến; trạng thái cảng được quản lý qua endpoint activate/deactivate.")
public class PortUpdateRequest {

    @Schema(description = "Tên cảng", example = "Cảng Hải Phòng Quốc tế")
    @Size(max = 200, message = "Port name must not exceed 200 characters")
    private String portName;

    @Schema(description = "Mô tả cảng", example = "Cảng biển chính của miền Bắc")
    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    @Schema(description = "Vĩ độ", example = "20.8449")
    private BigDecimal latitude;

    @Schema(description = "Kinh độ", example = "106.6881")
    private BigDecimal longitude;

    @Schema(description = "Múi giờ (IANA time zone)", example = "Asia/Ho_Chi_Minh")
    @Size(max = 100, message = "Time zone ID must not exceed 100 characters")
    private String timeZoneId;
}