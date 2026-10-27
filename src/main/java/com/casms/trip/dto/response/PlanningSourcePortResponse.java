package com.casms.trip.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin cảng ghé (Port) cho lập kế hoạch tuyến tàu và tour tham quan")
public class PlanningSourcePortResponse {

    @Schema(description = "ID cảng")
    private UUID portId;

    @Schema(description = "Mã UN/LOCODE của cảng", example = "VNSGN")
    private String portCode;

    @Schema(description = "Tên cảng biển", example = "Saigon Hiep Phuoc Cruise Port")
    private String portName;

    @Schema(description = "Mã quốc gia ISO", example = "VN")
    private String countryCode;

    @Schema(description = "Miền địa lý nội địa (BAC_BO, TRUNG_BO, NAM_BO) đối với cảng Việt Nam", example = "BAC_BO")
    private String domesticRegion;

    @Schema(description = "Múi giờ cảng", example = "Asia/Ho_Chi_Minh")
    private String timeZoneId;

    @Schema(description = "ID điểm đến / vùng liên kết")
    private UUID destinationId;

    @Schema(description = "Tên điểm đến / vùng liên kết", example = "TP. Hồ Chí Minh & Nam Bộ")
    private String destinationName;

    @Schema(description = "Tọa độ vĩ độ")
    private BigDecimal latitude;

    @Schema(description = "Tọa độ kinh độ")
    private BigDecimal longitude;

    @Schema(description = "Mô tả cảng")
    private String description;
}
