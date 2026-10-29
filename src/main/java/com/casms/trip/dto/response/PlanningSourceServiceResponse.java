package com.casms.trip.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Nguồn dịch vụ có thể tái sử dụng làm khung giờ phục vụ (serving window)")
public class PlanningSourceServiceResponse {

    @Schema(description = "ID của dịch vụ")
    private UUID serviceId;

    @Schema(description = "Mã dịch vụ")
    private String serviceCode;

    @Schema(description = "Tên dịch vụ")
    private String serviceName;

    @Schema(description = "Mô tả dịch vụ")
    private String description;

    @Schema(description = "Loại dịch vụ")
    private String serviceType;

    @Schema(description = "Tên danh mục dịch vụ")
    private String categoryName;
}
