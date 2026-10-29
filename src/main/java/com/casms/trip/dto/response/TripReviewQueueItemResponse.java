package com.casms.trip.dto.response;

import com.casms.ship.enums.ItineraryType;
import com.casms.trip.enums.TripDurationGroup;
import com.casms.trip.enums.TripScheduleVersionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Hạng mục trong hàng đợi duyệt lịch trình (Review Queue)")
public class TripReviewQueueItemResponse {

    @Schema(description = "ID của phiên bản lịch trình đang chờ duyệt")
    private UUID versionId;

    @Schema(description = "Số thứ tự phiên bản", example = "1")
    private Integer versionNumber;

    @Schema(description = "Trạng thái phiên bản (IN_REVIEW)")
    private TripScheduleVersionStatus status;

    @Schema(description = "Revision hiện tại của phiên bản", example = "2")
    private Long revision;

    @Schema(description = "Revision tại thời điểm gửi duyệt", example = "2")
    private Long submittedRevision;

    @Schema(description = "Thời điểm gửi duyệt")
    private Instant submittedAt;

    @Schema(description = "ID người gửi duyệt")
    private UUID submitterId;

    @Schema(description = "Tên tài khoản người gửi duyệt", example = "planner_nv")
    private String submitterName;

    @Schema(description = "ID người khởi tạo phiên bản")
    private UUID creatorId;

    @Schema(description = "Tên tài khoản người khởi tạo", example = "planner_lead")
    private String creatorName;

    @Schema(description = "ID của Trip liên kết")
    private UUID tripId;

    @Schema(description = "Mã chuyến đi", example = "TR000001")
    private String tripCode;

    @Schema(description = "Tên chuyến đi", example = "Hành trình Vịnh Hạ Long 3N2Đ")
    private String tripName;

    @Schema(description = "Nhóm thời lượng (SHORT, LONG)")
    private TripDurationGroup durationGroup;

    @Schema(description = "Số ngày (N)", example = "3")
    private Integer durationDays;

    @Schema(description = "Số đêm (N - 1)", example = "2")
    private Integer durationNights;

    @Schema(description = "Loại tuyến hành trình")
    private ItineraryType tripType;

    @Schema(description = "Phạm vi chuyến đi", example = "DOMESTIC")
    private String plannedScope;

    @Schema(description = "Thời điểm tạo phiên bản")
    private Instant createdAt;

    @Schema(description = "Thời điểm cập nhật gần nhất")
    private Instant updatedAt;
}
