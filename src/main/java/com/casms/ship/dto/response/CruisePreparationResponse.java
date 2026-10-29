package com.casms.ship.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Báo cáo tổng hợp tình trạng chuẩn bị liên phòng ban cho chuyến đi (BE-07 / Mainflow S3)")
public class CruisePreparationResponse {

    @Schema(description = "ID chuyến đi")
    private UUID cruiseId;

    @Schema(description = "Mã chuyến đi", example = "CR-0001")
    private String cruiseCode;

    @Schema(description = "Tên chuyến đi")
    private String cruiseName;

    @Schema(description = "Trạng thái chuyến đi hiện tại (DRAFT, PUBLISHED, ...)", example = "DRAFT")
    private String cruiseStatus;

    private UUID coordinatorUserId;
    private String coordinatorUsername;
    private Long cruiseRevision;
    private boolean canAssignCoordinator;

    @Schema(description = "Trạng thái chuẩn bị tổng thể (INCOMPLETE, READY_FOR_SIGNOFF, CONFIRMED)", example = "INCOMPLETE")
    private String overallStatus;

    @Schema(description = "Đã hoàn thành toàn bộ ký nhận liên phòng ban hay chưa", example = "false")
    private boolean allConfirmed;

    @Schema(description = "Danh sách các phân hệ chuẩn bị liên phòng ban")
    private List<CruisePreparationSectionResponse> sections;

    @Schema(description = "Thời điểm cập nhật gần nhất")
    private Instant lastUpdatedAt;
}
