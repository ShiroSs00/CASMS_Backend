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

    @Builder.Default
    @Schema(description = "Danh mục các miền trong nước (Bắc - Trung - Nam)")
    private List<RegionCatalogItem> domesticRegions = new ArrayList<>();

    @Builder.Default
    @Schema(description = "Danh mục các quốc gia/vùng lãnh thổ chuẩn")
    private List<CountryCatalogItem> countries = new ArrayList<>();

    @Builder.Default
    @Schema(description = "Danh mục nhóm thời lượng hành trình")
    private List<DurationGroupCatalogItem> durationGroups = new ArrayList<>();

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RegionCatalogItem {
        private String code;
        private String name;
        private String description;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CountryCatalogItem {
        private String code;
        private String name;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DurationGroupCatalogItem {
        private String code;
        private String name;
        private int minDays;
        private int maxDays;
        private int defaultDays;
    }
}
