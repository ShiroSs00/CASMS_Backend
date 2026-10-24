package com.casms.trip.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Yêu cầu trả về bản nháp từ trạng thái chờ duyệt (kèm lý do từ chối)")
public class VersionReturnRequest {

    @Schema(description = "Revision hiện tại của phiên bản để đối chiếu lạc quan")
    private Long expectedRevision;

    @NotBlank(message = "Rejection reason is required")
    @Size(max = 500, message = "Rejection reason must not exceed 500 characters")
    @Schema(description = "Lý do từ chối / yêu cầu chỉnh sửa", example = "Thời gian allAboardTime ở Ngày 2 chưa đủ giãn cách với tour trên bờ.")
    private String rejectionReason;
}
