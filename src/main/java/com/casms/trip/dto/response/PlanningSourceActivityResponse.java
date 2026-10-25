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
@Schema(description = "Nguồn hoạt động trên tàu có thể tái sử dụng cho lập kế hoạch hành trình")
public class PlanningSourceActivityResponse {

    @Schema(description = "ID của hoạt động nguồn")
    private UUID activityId;

    @Schema(description = "ID nguồn hoạt động (alias)")
    private UUID sourceActivityId;

    @Schema(description = "Mã hoạt động nguồn")
    private String activityCode;

    @Schema(description = "Tên hoạt động")
    private String activityName;

    @Schema(description = "Mã phân loại danh mục hoạt động", example = "SPORTS_WELLNESS")
    private com.casms.activity.enums.OnboardActivityCategory category;

    @Schema(description = "Tên hiển thị danh mục tiếng Việt", example = "Thể thao & Sức khỏe")
    private String categoryName;

    @Schema(description = "Icon đại diện cho danh mục", example = "🧘")
    private String categoryIcon;

    @Schema(description = "Mô tả hoạt động")
    private String description;

    @Schema(description = "Phân loại không gian tổ chức (FUNCTIONAL_ROOM: phòng có số, VENUE_OPEN_AREA: sảnh/boong mở)", example = "FUNCTIONAL_ROOM")
    private String locationType;

    @Schema(description = "Vị trí / Địa điểm tổ chức")
    private String location;

    @Schema(description = "ID tầng (deck) nơi hoạt động diễn ra")
    private UUID deckId;

    @Schema(description = "Số thứ tự tầng", example = "2")
    private Integer deckNumber;

    @Schema(description = "Tên tầng hiển thị", example = "Promenade Deck")
    private String deckName;

    @Schema(description = "ID phòng/cabin cụ thể (nếu có)")
    private UUID cabinId;

    @Schema(description = "Số phòng/cabin", example = "202")
    private String cabinNumber;

    @Schema(description = "Tên phòng / tên công năng chuyên biệt", example = "Phòng Massage Đá Nóng VIP")
    private String roomName;

    @Schema(description = "Địa điểm hiển thị trực quan tổng hợp cho khách", example = "Tầng 2 (Lotus Wellness & Spa Deck) - Phòng 202 (Phòng Massage Đá Nóng VIP)")
    private String displayLocation;

    @Schema(description = "Giá tham khảo")
    private BigDecimal price;

    @Schema(description = "Đơn vị tiền tệ")
    private String currencyCode;
}
