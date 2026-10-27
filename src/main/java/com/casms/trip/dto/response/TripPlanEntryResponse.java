package com.casms.trip.dto.response;

import com.casms.activity.enums.OnboardActivityCategory;
import com.casms.trip.enums.TripPlanEntryKind;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalTime;
import java.util.UUID;

@Data
@Schema(description = "Chi tiết mục kế hoạch (hoạt động / tour / serving window)")
public class TripPlanEntryResponse {

    private UUID entryId;
    private String clientKey;
    private UUID dayId;
    private Integer dayNumber;
    private TripPlanEntryKind kind;
    private UUID stopId;
    private String stopClientKey;
    private UUID sourceActivityId;
    private String sourceActivityTitle;
    private UUID sourceExcursionId;
    private String sourceExcursionTitle;
    private UUID supplierId;
    private String supplierName;
    private UUID serviceId;
    private String serviceName;
    @Schema(description = "Khóa nhận diện nhóm lặp (series/group key) để liên kết và chỉnh sửa đồng loạt", example = "series-buffet-breakfast")
    private String seriesClientKey;
    private UUID destinationId;
    private String destinationName;
    private String title;
    private Integer startDayNumber;

    @JsonFormat(pattern = "HH:mm:ss", shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", pattern = "^(0[0-9]|1[0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]$", example = "09:00:00")
    private LocalTime plannedStartTime;

    private Integer endDayNumber;

    @JsonFormat(pattern = "HH:mm:ss", shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", pattern = "^(0[0-9]|1[0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]$", example = "16:00:00")
    private LocalTime plannedEndTime;

    private String timeZoneId;
    private String locationText;

    @Schema(description = "ID tầng / boong tàu nơi diễn ra hoạt động")
    private UUID deckId;

    @Schema(description = "Số tầng", example = "2")
    private Integer deckNumber;

    @Schema(description = "Tên tầng", example = "Lotus Wellness & Spa Deck")
    private String deckName;

    @Schema(description = "ID tàu tham chiếu mẫu (khi lập lịch trình trước khi chốt tàu cụ thể)")
    private UUID referenceShipId;

    @Schema(description = "Tên tàu tham chiếu mẫu", example = "Paradise Elegance")
    private String referenceShipName;

    @Schema(description = "Phân loại danh mục hoạt động trên tàu", example = "SPORTS_WELLNESS")
    private OnboardActivityCategory category;

    @Schema(description = "Tên hiển thị danh mục tiếng Việt", example = "Thể thao & Sức khỏe")
    private String categoryName;

    @Schema(description = "Biểu tượng danh mục", example = "🧘")
    private String categoryIcon;

    @Schema(description = "Phân loại không gian (FUNCTIONAL_ROOM: phòng có số, VENUE_OPEN_AREA: sảnh/boong mở)", example = "FUNCTIONAL_ROOM")
    private String locationType;

    @Schema(description = "ID phòng / cabin cụ thể")
    private UUID cabinId;

    @Schema(description = "Số phòng / cabin", example = "202")
    private String cabinNumber;

    @Schema(description = "Tên phòng / tên công năng chuyên biệt", example = "Phòng Massage Đá Nóng VIP")
    private String roomName;

    @Schema(description = "Địa điểm hiển thị trực quan tổng hợp cho khách", example = "Tầng 2 (Lotus Wellness & Spa Deck) - Phòng 202 (Phòng Massage Đá Nóng VIP)")
    private String displayLocation;

    private String meetingPoint;
    private String notes;
}
