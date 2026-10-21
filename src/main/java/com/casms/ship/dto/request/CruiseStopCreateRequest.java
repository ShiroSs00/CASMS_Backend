package com.casms.ship.dto.request;

import com.casms.ship.enums.CruiseStopCallType;
import com.casms.ship.enums.CruiseStopArrivalMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Schema(description = "Yêu cầu thêm điểm dừng mới vào lịch trình")
public class CruiseStopCreateRequest {

    @Schema(description = "ID lịch trình", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Itinerary ID is required")
    private UUID itineraryId;

    @Schema(description = "ID ngày đến", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Arrival itinerary day ID is required")
    private UUID arrivalItineraryDayId;

    @Schema(description = "ID ngày đi", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Departure itinerary day ID is required")
    private UUID departureItineraryDayId;

    @Schema(description = "ID cảng", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Port ID is required")
    private UUID portId;

    @Schema(description = "ID điểm đến", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID destinationId;

    @Schema(description = "Thứ tự dừng (Nếu để trống, tự động tăng)", example = "1")
    private Integer stopOrder;

    @Schema(description = "Thời gian dự kiến đến (ISO-8601)", example = "2026-01-15T08:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Planned arrival is required")
    private Instant plannedArrival;

    @Schema(description = "Thời gian dự kiến đi (ISO-8601)", example = "2026-01-15T18:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Planned departure is required")
    private Instant plannedDeparture;

    @Schema(description = "Loại cuộc gọi cảng (mặc định NORMAL)", example = "NORMAL")
    private CruiseStopCallType callType;

    @Schema(description = "Phương pháp tiếp cận", example = "BERTH")
    private CruiseStopArrivalMethod arrivalMethod;

    @Schema(description = "Thời gian tất cả hành khách lên tàu (ISO-8601)", example = "2026-01-15T17:30:00Z")
    private Instant allAboardTime;

    @Schema(description = "Thời gian chuyển bằng xuồng (phút)", example = "30")
    private Integer tenderTransferMinutes;

    @Schema(description = "Tên liên hệ cảng", example = "Nguyễn Văn A")
    @Size(max = 200, message = "Port contact name must not exceed 200 characters")
    private String portContactName;

    @Schema(description = "Vai trò liên hệ", example = "Quản lý cảng")
    @Size(max = 100, message = "Port contact role must not exceed 100 characters")
    private String portContactRole;

    @Schema(description = "Số điện thoại liên hệ", example = "+84-225-3838383")
    @Size(max = 50, message = "Port contact phone must not exceed 50 characters")
    private String portContactPhone;

    @Schema(description = "Email liên hệ", example = "port@halong.gov.vn")
    @Size(max = 255, message = "Port contact email must not exceed 255 characters")
    private String portContactEmail;

    @Schema(description = "Trách nhiệm liên hệ", example = "Điều phối tàu đến cảng")
    @Size(max = 500, message = "Port contact responsibility must not exceed 500 characters")
    private String portContactResponsibility;

    @Schema(description = "Ghi chú", example = "Cần xem xét thời tiết")
    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    private String notes;
}