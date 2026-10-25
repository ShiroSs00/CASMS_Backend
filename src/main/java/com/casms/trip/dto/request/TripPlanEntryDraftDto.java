package com.casms.trip.dto.request;

import com.casms.trip.enums.TripPlanEntryKind;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalTime;
import java.util.UUID;

@Data
@Schema(description = "Dự thảo mục kế hoạch (hoạt động trên tàu, tour trên bờ, hoặc khung giờ dịch vụ)")
public class TripPlanEntryDraftDto {

    @NotBlank(message = "Client key is required for correlation")
    @Schema(description = "Khóa tương quan do client sinh (duy nhất trong version)", example = "entry-halong-01")
    private String clientKey;

    @NotNull(message = "Day number is required")
    @Schema(description = "Số thứ tự ngày của hoạt động (1..N)", example = "1")
    private Integer dayNumber;

    @NotNull(message = "Entry kind is required")
    @Schema(description = "Loại mục kế hoạch (ONBOARD_ACTIVITY, SHORE_EXCURSION, SERVING_WINDOW)", example = "SHORE_EXCURSION")
    private TripPlanEntryKind kind;

    @Schema(description = "Khóa tương quan clientKey của điểm dừng (BẮT BUỘC nếu kind == SHORE_EXCURSION)", example = "stop-hph-01")
    private String stopClientKey;

    @Schema(description = "ID hoạt động nguồn được chọn từ danh mục (nếu kind == ONBOARD_ACTIVITY)")
    private UUID sourceActivityId;

    @Schema(description = "ID tour tham quan nguồn được chọn từ danh mục (nếu kind == SHORE_EXCURSION)")
    private UUID sourceExcursionId;

    @Schema(description = "ID dịch vụ liên kết trong Service Catalog hiện hữu (nếu kind == SERVING_WINDOW)")
    private UUID serviceId;

    @Schema(description = "Khóa nhận diện nhóm lặp (series/group key) để liên kết và chỉnh sửa đồng loạt các khung phục vụ/hoạt động lặp qua nhiều ngày", example = "series-buffet-breakfast")
    private String seriesClientKey;

    @Schema(description = "ID điểm đến liên kết cho tour trên bờ (nếu có)")
    private UUID destinationId;

    @NotBlank(message = "Title is required")
    @Size(max = 200)
    @Schema(description = "Tên hoạt động / mục kế hoạch", example = "Tham quan Vịnh Hạ Long & Hang Sửng Sốt")
    private String title;

    @NotNull(message = "Start day number is required")
    @Schema(description = "Ngày bắt đầu tương đối", example = "1")
    private Integer startDayNumber;

    @NotNull(message = "Planned start time is required")
    @JsonFormat(pattern = "HH:mm:ss", shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", pattern = "^(0[0-9]|1[0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]$", description = "Giờ bắt đầu tương đối", example = "09:00:00")
    private LocalTime plannedStartTime;

    @NotNull(message = "End day number is required")
    @Schema(description = "Ngày kết thúc tương đối", example = "1")
    private Integer endDayNumber;

    @NotNull(message = "Planned end time is required")
    @JsonFormat(pattern = "HH:mm:ss", shape = JsonFormat.Shape.STRING)
    @Schema(type = "string", pattern = "^(0[0-9]|1[0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]$", description = "Giờ kết thúc tương đối", example = "16:00:00")
    private LocalTime plannedEndTime;

    @Schema(description = "Múi giờ IANA áp dụng cho sự kiện này (mặc định dùng múi giờ cảng cho tour, Asia/Ho_Chi_Minh cho tàu/khung phục vụ)", example = "Asia/Ho_Chi_Minh")
    private String timeZoneId;

    @Size(max = 255)
    @Schema(description = "Địa điểm cụ thể", example = "Nhà hát chính / Cảng tàu quốc tế Tuần Châu")
    private String locationText;

    @Schema(description = "ID tầng / boong tàu nơi diễn ra hoạt động", example = "d1000000-0000-0000-0000-000000000001")
    private UUID deckId;

    @Schema(description = "ID phòng / cabin cụ thể (spa, nhà hàng, phòng họp,...) nơi diễn ra hoạt động", example = "c1000000-0000-0000-0000-000000000001")
    private UUID cabinId;

    @Size(max = 255)
    @Schema(description = "Điểm tập trung", example = "Sảnh Lễ tân boong 3")
    private String meetingPoint;

    @Schema(description = "Ghi chú thêm")
    private String notes;
}
