package com.casms.ship.dto.response;

import com.casms.ship.enums.PortStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Schema(description = "Thông tin chi tiết cảng")
public class PortResponse {

    @Schema(description = "ID duy nhất của cảng", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID portId;

    @Schema(description = "Mã cảng", example = "VN-HPH")
    private String portCode;

    @Schema(description = "Tên cảng", example = "Cảng Hải Phòng")
    private String portName;

    @Schema(description = "Mã quốc gia (ISO 3166-1 alpha-2)", example = "VN")
    private String countryCode;

    @Schema(description = "Mô tả cảng", example = "Cảng biển chính của miền Bắc")
    private String description;

    @Schema(description = "Múi giờ (IANA time zone)", example = "Asia/Ho_Chi_Minh")
    private String timeZoneId;

    @Schema(description = "Vĩ độ", example = "20.8449")
    private BigDecimal latitude;

    @Schema(description = "Kinh độ", example = "106.6881")
    private BigDecimal longitude;

    @Schema(description = "Trạng thái cảng", example = "ACTIVE")
    private PortStatus status;

    @Schema(description = "Thời điểm tạo")
    private Instant createdAt;

    @Schema(description = "Thời điểm cập nhật gần nhất")
    private Instant updatedAt;
}