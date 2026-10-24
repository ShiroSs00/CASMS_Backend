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

    @Schema(description = "Độ lệch giờ UTC hiện tại", example = "+07:00")
    private String utcOffset;

    @Schema(description = "Chuỗi hiển thị múi giờ", example = "Asia/Ho_Chi_Minh (UTC+07:00)")
    private String formattedTimeZone;

    @Schema(description = "Giờ địa phương hiện tại ở cảng (ISO-8601)", example = "2026-10-24T13:30:00+07:00")
    private String currentLocalTime;

    @Schema(description = "Vĩ độ", example = "20.8449")
    private BigDecimal latitude;

    @Schema(description = "Kinh độ", example = "106.6881")
    private BigDecimal longitude;

    @Schema(description = "Loại vị trí cảng/neo đậu", example = "PORT")
    private com.casms.ship.enums.PortLocationType locationType;

    @Schema(description = "Trạng thái cảng", example = "ACTIVE")
    private PortStatus status;

    @Schema(description = "ID điểm đến liên kết", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID destinationId;

    @Schema(description = "Tên điểm đến liên kết", example = "Hạ Long Bay")
    private String destinationName;

    @Schema(description = "Thời điểm tạo")
    private Instant createdAt;

    @Schema(description = "Thời điểm cập nhật gần nhất")
    private Instant updatedAt;
}