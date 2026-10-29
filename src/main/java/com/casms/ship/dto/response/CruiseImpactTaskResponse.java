package com.casms.ship.dto.response;

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
@Schema(description = "Công việc xử lý tác động thay đổi chuyến đi (C17 Recovery Task)")
public class CruiseImpactTaskResponse {

    @Schema(description = "ID công việc")
    private UUID taskId;

    @Schema(description = "ID chuyến đi")
    private UUID cruiseId;

    @Schema(description = "Mã định danh công việc (duy nhất)")
    private String taskCode;

    @Schema(description = "Phân loại tác động", example = "CABIN_REASSIGNMENT")
    private String category;

    @Schema(description = "Tiêu đề công việc")
    private String title;

    @Schema(description = "Mô tả chi tiết nội dung cần xử lý")
    private String description;

    @Schema(description = "Trạng thái công việc", example = "PENDING")
    private String status;

    @Schema(description = "Mức độ nghiêm trọng", example = "BLOCKING")
    private String severity;

    @Schema(description = "Định danh sự kiện gốc phát sinh công việc (Event Identity)")
    private String eventIdentity;

    @Schema(description = "Loại đối tượng chịu tác động (CRUISE, CABIN, EXCURSION, BOOKING)")
    private String targetType;

    @Schema(description = "ID đối tượng chịu tác động")
    private String targetId;

    @Schema(description = "ID người được phân công xử lý")
    private UUID assignedToUserId;

    @Schema(description = "Tên tài khoản người được phân công")
    private String assignedToUsername;

    @Schema(description = "ID người thực hiện giải quyết")
    private UUID resolvedByUserId;

    @Schema(description = "Tên tài khoản người thực hiện giải quyết")
    private String resolvedByUsername;

    @Schema(description = "Thời điểm giải quyết công việc")
    private Instant resolvedAt;

    @Schema(description = "Bằng chứng hoặc dữ liệu tham chiếu")
    private String evidence;

    @Schema(description = "Ghi chú giải quyết hoặc xử lý")
    private String resolutionNotes;

    @Schema(description = "Phiên bản sửa đổi của công việc")
    private Long revision;

    @Schema(description = "Thời điểm tạo")
    private Instant createdAt;

    @Schema(description = "Thời điểm cập nhật gần nhất")
    private Instant updatedAt;
}
