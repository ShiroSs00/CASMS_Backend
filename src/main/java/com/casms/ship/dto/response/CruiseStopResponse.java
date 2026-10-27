package com.casms.ship.dto.response;

import com.casms.ship.enums.CruiseStopArrivalMethod;
import com.casms.ship.enums.CruiseStopCallType;
import com.casms.ship.enums.CruiseStopStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Schema(description = "Thông tin chi tiết điểm dừng chuyến đi")
public class CruiseStopResponse {

    @Schema(description = "ID điểm dừng", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID cruiseStopId;

    @Schema(description = "ID lịch trình", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID itineraryId;

    @Schema(description = "ID ngày đến", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID arrivalItineraryDayId;

    @Schema(description = "ID ngày đi", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID departureItineraryDayId;

    @Schema(description = "ID cảng", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID portId;

    @Schema(description = "Mã cảng", example = "VN-HPH")
    private String portCode;

    @Schema(description = "Tên cảng", example = "Cảng Hải Phòng")
    private String portName;

    @Schema(description = "Múi giờ của cảng", example = "Asia/Tokyo")
    private String portTimeZoneId;

    @Schema(description = "Độ lệch giờ UTC của cảng", example = "+09:00")
    private String portUtcOffset;

    @Schema(description = "Chuỗi hiển thị múi giờ cảng", example = "Asia/Tokyo (UTC+09:00)")
    private String formattedPortTimeZone;

    @Schema(description = "Múi giờ trên tàu", example = "Asia/Singapore")
    private String shipTimeZoneId;

    @Schema(description = "Độ lệch giờ UTC trên tàu", example = "+08:00")
    private String shipUtcOffset;

    @Schema(description = "Chuỗi hiển thị múi giờ trên tàu", example = "Asia/Singapore (UTC+08:00)")
    private String formattedShipTimeZone;

    @Schema(description = "ID điểm đến", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID destinationId;

    @Schema(description = "Tên điểm đến", example = "Hạ Long")
    private String destinationName;

    @Schema(description = "Thứ tự dừng", example = "1")
    private Integer stopOrder;

    @Schema(description = "Thời gian dự kiến đến", example = "2026-01-15T08:00:00Z")
    private Instant plannedArrival;

    @Schema(description = "Thời gian dự kiến đi", example = "2026-01-15T18:00:00Z")
    private Instant plannedDeparture;

    @Schema(description = "Thời gian thực tế đến", example = "2026-01-15T08:15:00Z")
    private Instant actualArrival;

    @Schema(description = "Thời gian thực tế đi", example = "2026-01-15T18:30:00Z")
    private Instant actualDeparture;

    @Schema(description = "Loại cuộc gọi cảng", example = "NORMAL")
    private CruiseStopCallType callType;

    @Schema(description = "Phương pháp tiếp cận", example = "BERTH")
    private CruiseStopArrivalMethod arrivalMethod;

    @Schema(description = "Thời gian tất cả hành khách lên tàu", example = "2026-01-15T17:30:00Z")
    private Instant allAboardTime;

    @Schema(description = "Thời gian chuyển bằng xuồng (phút)", example = "30")
    private Integer tenderTransferMinutes;

    @Schema(description = "Trạng thái điểm dừng", example = "PLANNED")
    private CruiseStopStatus status;

    @Schema(description = "Lý do hủy bỏ", example = "Thời tiết xấu")
    private String cancellationReason;

    @Schema(description = "ID điểm dừng thay thế", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID substitutedByStopId;

    @Schema(description = "Tên liên hệ cảng", example = "Nguyễn Văn A")
    private String portContactName;

    @Schema(description = "Vai trò liên hệ", example = "Quản lý cảng")
    private String portContactRole;

    @Schema(description = "Số điện thoại liên hệ", example = "+84-225-3838383")
    private String portContactPhone;

    @Schema(description = "Email liên hệ", example = "port@halong.gov.vn")
    private String portContactEmail;

    @Schema(description = "Trách nhiệm liên hệ", example = "Điều phối tàu đến cảng")
    private String portContactResponsibility;

    @Schema(description = "Ghi chú", example = "Cần xem xét thời tiết")
    private String notes;

    @Schema(description = "Trạng thái phối hợp cảng (NOT_STARTED, SUBMITTED, IN_COORDINATION, CONFIRMED, REJECTED)", example = "CONFIRMED")
    private String coordinationStatus;

    @Schema(description = "Hạn chót phối hợp cảng", example = "2026-01-14T12:00:00Z")
    private Instant coordinationDeadline;

    @Schema(description = "Mã hồ sơ phối hợp cảng", example = "DOS-HPH-2026-01")
    private String dossierReference;

    @Schema(description = "Mã tham chiếu giấy phép chính thức từ cơ quan quản lý cảng (nếu có tích hợp ngoài)", example = "CLR-VPA-2026-9981")
    private String officialClearanceReference;

    @Schema(description = "Cơ quan thẩm quyền cấp giấy phép cảng", example = "Cảng vụ Hàng hải Hải Phòng")
    private String clearanceAuthority;

    @Schema(description = "Lý do thay đổi thứ tự hoặc điều chỉnh lượt ghé", example = "Thời tiết vùng vịnh chuyển xấu, đảo thứ tự cập cảng")
    private String sequenceChangeReason;
}