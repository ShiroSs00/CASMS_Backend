package com.casms.ship.dto.response;

import com.casms.activity.enums.OnboardActivityCategory;
import com.casms.ship.enums.CruiseStopArrivalMethod;
import com.casms.ship.enums.CruiseStopCallType;
import com.casms.ship.enums.ItineraryDayType;
import com.casms.ship.enums.ItineraryDayRole;
import com.casms.trip.enums.TripPlanEntryKind;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Kết quả tính toán xem trước hải trình và lịch trình thực tế cho chuyến khởi hành (C02)")
public class CruiseSchedulePreviewResponse {

    @Schema(description = "ID của Trip mẫu nguồn")
    private UUID sourceTripId;

    @Schema(description = "Mã Trip mẫu nguồn", example = "VN-DOM-01")
    private String sourceTripCode;

    @Schema(description = "Tên Trip nguồn", example = "Hải trình khám phá Vịnh Hạ Long 3N2Đ")
    private String sourceTripName;

    @Schema(description = "ID của phiên bản mẫu nguồn")
    private UUID sourceVersionId;

    @Schema(description = "Số phiên bản mẫu nguồn", example = "1")
    private Integer sourceVersionNumber;

    @Schema(description = "Revision của phiên bản mẫu nguồn tại thời điểm preview", example = "3")
    private Long sourceVersionRevision;

    @Schema(description = "Thời điểm khởi hành thực tế dự kiến (ISO-8601)")
    private Instant departureDatetime;

    @Schema(description = "Thời điểm kết thúc / trở về dự kiến (ISO-8601)")
    private Instant returnDatetime;

    @Schema(description = "Số ngày hải trình", example = "3")
    private Integer durationDays;

    @Schema(description = "Số đêm hải trình", example = "2")
    private Integer durationNights;

    @Schema(description = "ID cảng khởi hành")
    private UUID departurePortId;

    @Schema(description = "Tên cảng khởi hành")
    private String departurePortName;

    @Schema(description = "Mã cảng khởi hành")
    private String departurePortCode;

    @Schema(description = "ID cảng kết thúc")
    private UUID returnPortId;

    @Schema(description = "Tên cảng kết thúc")
    private String returnPortName;

    @Schema(description = "Mã cảng kết thúc")
    private String returnPortCode;

    @Schema(description = "ID của hải trình thực tế đã lưu (ItineraryId) - null khi ở chế độ preview")
    private UUID itineraryId;

    @Schema(description = "Trạng thái hải trình thực tế (DRAFT, APPROVED, OPEN_FOR_SALE, SUSPENDED, ...) - null khi ở chế độ preview")
    private String itineraryStatus;

    @Schema(description = "Cờ xác định lịch trình đã được lưu vào database hay chưa (false = xem trước/chưa lưu, true = đã lưu)")
    private Boolean materialized;

    @Schema(description = "Danh sách cảnh báo liên quan đến tính toán múi giờ và lịch trình")
    private List<String> warnings;

    @Schema(description = "Danh sách các ngày hải trình được chuyển đổi sang ngày lịch thực tế")
    private List<ProjectedDayDto> days;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Thông tin ngày hải trình dự kiến")
    public static class ProjectedDayDto {
        @Schema(description = "ID ngày hải trình thực tế đã lưu (ItineraryDayId) - null khi ở chế độ preview")
        private UUID itineraryDayId;

        @Schema(description = "Số thứ tự ngày (1-indexed)", example = "1")
        private Integer dayNumber;

        @Schema(description = "Ngày dương lịch thực tế (YYYY-MM-DD)", example = "2026-11-01")
        private LocalDate calendarDate;

        @Schema(description = "Trạng thái ngày (SEA_DAY hoặc PORT_DAY); vai trò khởi hành/kết thúc nằm ở dayRole")
        private ItineraryDayType dayType;

        private ItineraryDayRole dayRole;

        @Schema(description = "Tiêu đề ngày")
        private String title;

        @Schema(description = "Múi giờ tàu áp dụng cho ngày này", example = "Asia/Ho_Chi_Minh")
        private String shipTimeZoneId;

        @Schema(description = "Danh sách các lần cập cảng diễn ra trong ngày này")
        private List<ProjectedStopDto> stops;

        @Schema(description = "Danh sách các hoạt động / chương trình vận hành trong ngày này")
        private List<ProjectedEntryDto> entries;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Thông tin điểm dừng cảng dự kiến")
    public static class ProjectedStopDto {
        @Schema(description = "ID của lần cập cảng thực tế đã lưu (CruiseStopId) - null khi ở chế độ preview")
        private UUID cruiseStopId;

        @Schema(description = "ID của điểm dừng mẫu nguồn")
        private UUID sourceStopId;

        @Schema(description = "Thứ tự ghé cảng", example = "1")
        private Integer stopOrder;

        @Schema(description = "ID cảng ghé")
        private UUID portId;

        @Schema(description = "Mã cảng ghé", example = "VNHLG")
        private String portCode;

        @Schema(description = "Tên cảng ghé", example = "Cảng Quốc tế Hạ Long")
        private String portName;

        @Schema(description = "Mã quốc gia của cảng", example = "VN")
        private String countryCode;

        @Schema(description = "Múi giờ của cảng", example = "Asia/Ho_Chi_Minh")
        private String portTimeZoneId;

        @Schema(description = "Thời điểm cập cảng dự kiến (ISO-8601)")
        private Instant plannedArrival;

        @Schema(description = "Thời điểm rời cảng dự kiến (ISO-8601)")
        private Instant plannedDeparture;

        @Schema(description = "Hạn chót toàn bộ khách lên tàu (All Aboard Time)")
        private Instant allAboardTime;

        @Schema(description = "Loại ghé cảng (EMBARKATION, DISEMBARKATION, PORT_CALL, TECHNICAL, OVERNIGHT)")
        private CruiseStopCallType callType;

        @Schema(description = "Phương thức cập cảng (BERTH, ANCHOR_TENDER)")
        private CruiseStopArrivalMethod arrivalMethod;

        @Schema(description = "Thời gian di chuyển tender (phút)")
        private Integer tenderTransferMinutes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Thông tin hoạt động / chương trình dự kiến")
    public static class ProjectedEntryDto {
        @Schema(description = "ID mục lịch trình nguồn")
        private UUID sourceEntryId;

        @Schema(description = "Client key của mục nguồn")
        private String clientKey;

        @Schema(description = "Loại mục (ONBOARD_ACTIVITY, SHORE_EXCURSION, SERVICE_WINDOW)")
        private TripPlanEntryKind kind;

        @Schema(description = "Danh mục hoạt động (nếu có)")
        private OnboardActivityCategory category;

        @Schema(description = "ID hoạt động trên tàu (nếu có)")
        private UUID sourceActivityId;

        @Schema(description = "ID tour tham quan bờ (nếu có)")
        private UUID sourceExcursionId;

        @Schema(description = "Thời điểm bắt đầu dự kiến (ISO-8601)")
        private Instant plannedStartTime;

        @Schema(description = "Thời điểm kết thúc dự kiến (ISO-8601)")
        private Instant plannedEndTime;

        @Schema(description = "Thứ tự cảng ghé liên kết (nếu có)")
        private Integer stopOrder;

        @Schema(description = "ID bản ghi thực tế đã lưu (OnboardActivityId hoặc ShoreExcursionId)")
        private UUID targetId;

        @Schema(description = "Mã nghiệp vụ thực tế đã lưu (activityCode hoặc excursionCode)")
        private String targetCode;

        @Schema(description = "Trạng thái vận hành thực tế")
        private String status;
    }
}
