package com.casms.trip.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Danh mục chuẩn cho phân hệ lập kế hoạch hành trình (Trip Planning Catalogs)")
public class TripCatalogResponse {

    @Builder.Default
    @Schema(description = "Danh mục các khu vực hoạt động khả dụng")
    private List<String> operatingAreas = new ArrayList<>();

    @Builder.Default
    @Schema(description = "Danh mục loại chuyến đi hỗ trợ")
    private List<String> tripTypes = new ArrayList<>();

    @Builder.Default
    @Schema(description = "Trạng thái tích hợp phân hệ Fleet (DEFERRED_EXTERNAL theo P0)", example = "DEFERRED_EXTERNAL")
    private String fleetIntegrationStatus = "DEFERRED_EXTERNAL";

    @Builder.Default
    @Schema(description = "Danh sách các chính sách quy tắc hành trình hỗ trợ")
    private List<String> supportedPolicies = new ArrayList<>();
}
