package com.casms.ship.dto.response;

import com.casms.ship.enums.PortStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Schema(description = "Thông tin tóm tắt cảng biển kèm thiết lập múi giờ và vị trí")
public class PortSummaryResponse {

    @Schema(description = "ID cảng", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID portId;

    @Schema(description = "Mã cảng (UN/LOCODE)", example = "JPYOK")
    private String portCode;

    @Schema(description = "Tên cảng", example = "Cảng Yokohama")
    private String portName;

    @Schema(description = "Mã quốc gia (ISO 3166-1 alpha-2)", example = "JP")
    private String countryCode;

    @Schema(description = "Múi giờ IANA", example = "Asia/Tokyo")
    private String timeZoneId;

    @Schema(description = "Độ lệch giờ UTC hiện tại", example = "+09:00")
    private String utcOffset;

    @Schema(description = "Chuỗi hiển thị múi giờ", example = "Asia/Tokyo (UTC+09:00)")
    private String formattedTimeZone;

    @Schema(description = "Giờ địa phương hiện tại ở cảng (ISO-8601)", example = "2026-10-24T15:30:00+09:00")
    private String currentLocalTime;

    @Schema(description = "Vĩ độ", example = "35.4522")
    private BigDecimal latitude;

    @Schema(description = "Kinh độ", example = "139.6486")
    private BigDecimal longitude;

    @Schema(description = "Trạng thái cảng", example = "ACTIVE")
    private PortStatus status;

    @Schema(description = "ID điểm đến liên kết", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID destinationId;

    @Schema(description = "Tên điểm đến liên kết", example = "Tokyo Bay & Kanto")
    private String destinationName;
}