package com.casms.ship.dto.request;

import com.casms.ship.enums.CruiseStopCallType;
import com.casms.ship.enums.CruiseStopArrivalMethod;
import com.casms.ship.enums.CruiseStopStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Schema(description = "Yêu cầu cập nhật điểm dừng chuyến đi")
public class CruiseStopUpdateRequest {

    @Schema(description = "ID lịch trình", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID itineraryId;

    @Schema(description = "ID ngày đến mới", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID arrivalItineraryDayId;

    @Schema(description = "ID ngày đi mới", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID departureItineraryDayId;

    @Schema(description = "ID cảng mới", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID portId;

    @Schema(description = "ID điểm đến mới", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID destinationId;

    @Schema(description = "Thứ tự dừng mới", example = "2")
    private Integer stopOrder;

    @Schema(description = "Thời gian dự kiến đến mới (ISO-8601)", example = "2026-01-15T09:00:00Z")
    private Instant plannedArrival;

    @Schema(description = "Thời gian dự kiến đi mới (ISO-8601)", example = "2026-01-15T19:00:00Z")
    private Instant plannedDeparture;

    @Schema(description = "Loại cuộc gọi cảng mới", example = "LATE_NIGHT")
    private CruiseStopCallType callType;

    @Schema(description = "Phương pháp tiếp cận mới", example = "TENDER")
    private CruiseStopArrivalMethod arrivalMethod;

    @Schema(description = "Thời gian tất cả hành khách lên tàu mới (ISO-8601)", example = "2026-01-15T18:30:00Z")
    private Instant allAboardTime;

    @Schema(description = "Thời gian chuyển bằng xuồng mới (phút)", example = "45")
    private Integer tenderTransferMinutes;

    @Schema(description = "Trạng thái mới", example = "DELAYED")
    private CruiseStopStatus status;

    @Schema(description = "Lý do hủy bỏ", example = "Thời tiết xấu")
    @Size(max = 500, message = "Cancellation reason must not exceed 500 characters")
    private String cancellationReason;

    @Schema(description = "ID điểm dừng thay thế", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID substitutedByStopId;

    @Schema(description = "Tên liên hệ cảng mới", example = "Trần Văn B")
    @Size(max = 200, message = "Port contact name must not exceed 200 characters")
    private String portContactName;

    @Schema(description = "Vai trò liên hệ mới", example = "Phó quản lý cảng")
    @Size(max = 100, message = "Port contact role must not exceed 100 characters")
    private String portContactRole;

    @Schema(description = "Số điện thoại liên hệ mới", example = "+84-225-3838384")
    @Size(max = 50, message = "Port contact phone must not exceed 50 characters")
    private String portContactPhone;

    @Schema(description = "Email liên hệ mới", example = "port2@halong.gov.vn")
    @Size(max = 255, message = "Port contact email must not exceed 255 characters")
    private String portContactEmail;

    @Schema(description = "Trách nhiệm liên hệ mới", example = "Điều phối tàu rời cảng")
    @Size(max = 500, message = "Port contact responsibility must not exceed 500 characters")
    private String portContactResponsibility;

    @Schema(description = "Ghi chú mới", example = "Đã cập nhật thời gian")
    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    private String notes;
}