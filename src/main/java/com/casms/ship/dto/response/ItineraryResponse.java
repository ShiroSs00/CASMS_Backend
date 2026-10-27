package com.casms.ship.dto.response;

import com.casms.ship.enums.ItineraryStatus;
import com.casms.ship.enums.ItineraryType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Schema(description = "Thông tin chi tiết lịch trình")
public class ItineraryResponse {

    @Schema(description = "ID lịch trình", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID itineraryId;

    @Schema(description = "ID chuyến đi liên quan", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID cruiseId;

    @Schema(description = "Mã chuyến đi", example = "CR-0001")
    private String cruiseCode;

    @Schema(description = "Tên lịch trình", example = "Lịch trình Hạ Long 3N2Đ")
    private String itineraryName;

    @Schema(description = "Ngày bắt đầu", example = "2026-01-15")
    private LocalDate startDate;

    @Schema(description = "Ngày kết thúc", example = "2026-01-17")
    private LocalDate endDate;

    @Schema(description = "Loại lịch trình", example = "ROUND_TRIP")
    private ItineraryType itineraryType;

    @Schema(description = "Trạng thái lịch trình", example = "DRAFT")
    private ItineraryStatus status;

    @Schema(description = "Lịch trình có kế thừa phê duyệt từ TripScheduleVersion PUBLISHED hay không", example = "true")
    private Boolean approvalInherited;

    @Schema(description = "Nội dung lịch trình có bị sửa đổi sau khi khởi tạo từ nguồn hay không", example = "false")
    private Boolean contentModifiedAfterMaterialization;

    @Schema(description = "ID người phê duyệt", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID approvedByUserId;

    @Schema(description = "Thời điểm phê duyệt")
    private Instant approvedAt;

    @Schema(description = "ID người tạo", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID createdByUserId;

    @Schema(description = "Thời điểm tạo")
    private Instant createdAt;

    @Schema(description = "Thời điểm cập nhật gần nhất")
    private Instant updatedAt;

    @Schema(description = "Danh sách ngày trong lịch trình")
    private List<ItineraryDayResponse> days;
}
