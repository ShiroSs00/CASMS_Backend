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
@Schema(description = "Nguồn tour tham quan trên bờ có thể tái sử dụng cho lập kế hoạch hành trình")
public class PlanningSourceExcursionResponse {

    @Schema(description = "ID của tour tham quan nguồn")
    private UUID excursionId;

    @Schema(description = "ID nguồn tham quan (alias)")
    private UUID sourceExcursionId;

    @Schema(description = "Mã tour tham quan nguồn")
    private String excursionCode;

    @Schema(description = "Tên tour tham quan")
    private String excursionName;

    @Schema(description = "Mô tả tour tham quan")
    private String description;

    @Schema(description = "Điểm tập trung")
    private String meetingPoint;

    @Schema(description = "ID cảng liên kết (nếu có)")
    private UUID portId;

    @Schema(description = "Mã cảng liên kết")
    private String portCode;

    @Schema(description = "Tên cảng liên kết")
    private String portName;

    @Schema(description = "ID điểm đến liên kết (nếu có)")
    private UUID destinationId;

    @Schema(description = "Tên điểm đến")
    private String destinationName;

    @Schema(description = "Nhà cung cấp")
    private String supplierName;

    @Schema(description = "Giá tham khảo")
    private BigDecimal price;

    @Schema(description = "Đơn vị tiền tệ")
    private String currencyCode;
}
