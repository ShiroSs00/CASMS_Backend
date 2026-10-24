package com.casms.ship.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin lựa chọn múi giờ chuẩn IANA")
public class TimeZoneOptionResponse {

    @Schema(description = "Mã múi giờ chuẩn IANA", example = "Asia/Tokyo")
    private String timeZoneId;

    @Schema(description = "Tên hiển thị kèm độ lệch UTC", example = "Asia/Tokyo (UTC+09:00)")
    private String displayName;

    @Schema(description = "Mã quốc gia liên kết nếu có (ISO 3166-1 alpha-2)", example = "JP")
    private String countryCode;

    @Schema(description = "Độ lệch giờ UTC hiện tại", example = "+09:00")
    private String utcOffset;

    @Schema(description = "Thời gian địa phương hiện tại theo chuẩn ISO-8601", example = "2026-10-24T19:50:00+09:00")
    private String currentLocalTime;

    @Schema(description = "Được khuyến nghị cho quốc gia được chọn hay không", example = "true")
    private boolean isRecommended;
}
