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
@Schema(description = "Bản ghi nhật ký kiểm toán sự kiện và quyết định vận hành của chuyến đi (BE-25 Audit Trail)")
public class CruiseAuditEventResponse {

    @Schema(description = "ID sự kiện kiểm toán")
    private UUID eventId;

    @Schema(description = "ID chuyến đi")
    private UUID cruiseId;

    @Schema(description = "Mã chuyến đi")
    private String cruiseCode;

    @Schema(description = "Loại sự kiện (SOURCE_MATERIALIZE, SHIP_ASSIGN, SHIP_CHANGE, SALE_OPEN, SALE_STOP, SALE_RESUME, CHANGE_APPLY, DEPARTURE_START, CRUISE_COMPLETE, READINESS_EVAL)")
    private String eventType;

    @Schema(description = "Mã định danh sự kiện bất biến (Event Identity)")
    private String eventIdentity;

    @Schema(description = "Tên tài khoản người thực hiện")
    private String actorUsername;

    @Schema(description = "Vai trò người thực hiện")
    private String actorRole;

    @Schema(description = "Quyền hạn yêu cầu")
    private String requiredPermission;

    @Schema(description = "Phạm vi áp dụng (WHOLE_CRUISE, PACKAGE, CABIN_TYPE...)")
    private String scope;

    @Schema(description = "Tóm tắt trạng thái trước khi thay đổi (Before Digest)")
    private String beforeDigest;

    @Schema(description = "Tóm tắt trạng thái sau khi thay đổi (After Digest)")
    private String afterDigest;

    @Schema(description = "Lý do quyết định")
    private String reason;

    @Schema(description = "Căn cứ/bằng chứng đính kèm")
    private String evidence;

    @Schema(description = "Chữ ký/Fingerprint xác thực dữ liệu")
    private String fingerprint;

    @Schema(description = "ID phiên bản lịch trình mẫu nguồn (nếu có)")
    private UUID sourceVersionId;

    @Schema(description = "Số revision của chuyến đi tại thời điểm ghi nhận")
    private Long revision;

    @Schema(description = "Thời điểm ghi nhận sự kiện")
    private Instant timestamp;
}
