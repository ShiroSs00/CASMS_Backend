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
@Schema(description = "Thông tin chi tiết một phân hệ chuẩn bị của chuyến đi")
public class CruisePreparationSectionResponse {

    @Schema(description = "Mã phân hệ chuẩn bị", example = "TECHNICAL_NAUTICAL")
    private String sectionCode;

    @Schema(description = "Tên phân hệ chuẩn bị", example = "Kỹ thuật hàng hải & Tàu")
    private String sectionName;

    @Schema(description = "Vai trò phụ trách phân hệ", example = "SHIP_MANAGER")
    private String ownerRole;

    @Schema(description = "Trạng thái phân hệ (NOT_STARTED, IN_PROGRESS, SUBMITTED, CONFIRMED, RETURNED)", example = "IN_PROGRESS")
    private String status;

    @Schema(description = "Bằng chứng/kết quả chuẩn bị đã nộp")
    private String evidence;

    @Schema(description = "Ghi chú bổ sung")
    private String notes;

    @Schema(description = "ID người nộp xác nhận")
    private UUID submittedByUserId;

    @Schema(description = "Thời điểm nộp xác nhận")
    private Instant submittedAt;

    @Schema(description = "ID người phê duyệt xác nhận")
    private UUID confirmedByUserId;

    @Schema(description = "Tên người phê duyệt xác nhận", example = "ops_manager")
    private String confirmedByUsername;

    @Schema(description = "Thời điểm phê duyệt xác nhận")
    private Instant confirmedAt;

    @Schema(description = "Lý do trả lại yêu cầu bổ sung (nếu bị RETURNED)")
    private String returnReason;

    @Schema(description = "Thời điểm trả lại")
    private Instant returnedAt;

    @Schema(description = "Số revision phục vụ kiểm soát xung đột", example = "1")
    private Long revision;

    @Schema(description = "Danh mục công việc/hạng mục kiểm tra chi tiết thuộc phân hệ")
    private List<PreparationCheckItemDto> checkItems;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreparationCheckItemDto {
        private String itemCode;
        private String itemName;
        private boolean ready;
        private String details;
        private Boolean required;
        private String state;
        private String evidence;

        public PreparationCheckItemDto(String itemCode, String itemName, boolean ready, String details) {
            this.itemCode = itemCode;
            this.itemName = itemName;
            this.ready = ready;
            this.details = details;
            this.required = true;
            this.state = ready ? "PASSED" : "BLOCKED";
            this.evidence = details;
        }
    }
}
