package com.casms.ship.dto.response;

import com.casms.trip.enums.TripPlanEntryKind;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Chi tiết kết quả mapping từng mục chương trình vận hành")
public class OperationalProgramMappingItemResponse {

    @Schema(description = "ID mục kế hoạch nguồn trong TripPlanEntry")
    private UUID sourceEntryId;

    @Schema(description = "Client Key của mục kế hoạch nguồn")
    private String clientKey;

    @Schema(description = "Phân loại mục chương trình (ONBOARD_ACTIVITY, SHORE_EXCURSION, SERVING_WINDOW)")
    private TripPlanEntryKind kind;

    @Schema(description = "Tiêu đề / tên chương trình")
    private String title;

    @Schema(description = "ID bản ghi đích đã tạo hoặc liên kết (OnboardActivity ID, ShoreExcursion ID, Service ID)")
    private UUID targetId;

    @Schema(description = "Mã định danh bản ghi đích")
    private String targetCode;

    @Schema(description = "Loại bản ghi đích (ONBOARD_ACTIVITY, SHORE_EXCURSION, SERVING_WINDOW)")
    private String targetType;

    @Schema(description = "Trạng thái mapping: CREATED, LINKED, SKIPPED, FAILED")
    private String status;

    @Schema(description = "Có bắt buộc khách đăng ký/giữ chỗ hay không (false cho SERVING_WINDOW)")
    private boolean reservationRequired;

    @Schema(description = "Lý do hoặc ghi chú kết quả mapping")
    private String reason;

    @Builder.Default
    @Schema(description = "Danh sách cảnh báo riêng cho mục này")
    private List<String> warnings = new ArrayList<>();

    @Schema(description = "Dữ liệu nghiệp vụ còn thiếu cần bổ sung (ví dụ: Nhà cung cấp, Sức chứa, Boong tàu)")
    private String requiredData;
}
