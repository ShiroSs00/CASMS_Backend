package com.casms.ship.dto.response;

import com.casms.ship.enums.ReadinessStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Schema(description = "Kết quả kiểm tra tính sẵn sàng vận hành & khởi hành của chuyến đi (Operational & Departure Readiness Gate - BE-08/BE-09)")
public class CruiseReadinessResponse {

    @Schema(description = "ID chuyến đi", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID cruiseId;

    @Schema(description = "Loại cổng kiểm tra (OPERATIONAL_GATE / DEPARTURE_GATE)", example = "DEPARTURE_GATE")
    private String gateType = "DEPARTURE_GATE";

    @Schema(description = "Phiên bản chính sách áp dụng", example = "v3.0-2026")
    private String policyVersion = "v3.0-2026";

    @Schema(description = "Đã sẵn sàng vận hành / khởi hành hay chưa", example = "true")
    private boolean ready;

    @Schema(description = "Đã được gán tàu hay chưa", example = "true")
    private boolean shipAssigned;

    @Schema(description = "Tàu được gán có đang ở trạng thái ACTIVE hay không", example = "true")
    private boolean shipActive;

    @Schema(description = "Tàu đã có boong và cabin cấu hình hay chưa", example = "true")
    private boolean shipConfigurationValid;

    @Schema(description = "Lịch trình hợp lệ (thời điểm về sau thời điểm đi)", example = "true")
    private boolean scheduleValid;

    @Schema(description = "Cảng đi và cảng đến hợp lệ và đang ACTIVE", example = "true")
    private boolean portsActive;

    @Schema(description = "Đã có hải trình (Itinerary) được liên kết", example = "true")
    private boolean itineraryConfigured;

    @Schema(description = "Đã phân công nhân sự vận hành cho chuyến đi hay chưa", example = "true")
    private boolean staffAssigned;

    @Schema(description = "Đã phân công Thuyền trưởng (CAPTAIN) hợp lệ cho chuyến đi hay chưa", example = "true")
    private boolean captainAssigned;

    @Schema(description = "Tất cả các phân hệ chuẩn bị liên phòng ban (BE-07) đã được ký duyệt xác nhận", example = "true")
    private boolean preparationSectionsConfirmed;

    @Schema(description = "Số phân hệ chuẩn bị chưa ký duyệt", example = "0")
    private int unconfirmedPreparationSectionsCount;

    @Schema(description = "Danh sách xung đột lịch trình nhân sự (nếu có)")
    private List<String> staffConflicts;

    @Schema(description = "Các quy định vận hành áp dụng có tuân thủ và không có xung đột hay không", example = "true")
    private boolean regulationsCompliant;

    @Schema(description = "Danh sách xung đột hoặc vi phạm quy định (nếu có)")
    private List<String> regulationConflicts;

    @Schema(description = "Danh sách các điều kiện chặn (blockers) ngăn cản khởi hành")
    private List<String> blockers = new ArrayList<>();

    @Schema(description = "Danh sách cảnh báo cần lưu ý")
    private List<String> warnings = new ArrayList<>();

    @Schema(description = "Danh sách các thông báo hoặc điểm chưa hoàn tất")
    private List<String> issues = new ArrayList<>();

    @Schema(description = "Thời điểm thực hiện đánh giá (server time, ISO-8601)", example = "2026-10-26T09:00:00Z")
    private Instant evaluatedAt;

    @Schema(description = "Revision của Cruise tại thời điểm đánh giá", example = "3")
    private Long cruiseRevision;

    @Schema(description = "Trạng thái đánh giá chi tiết (READY, NOT_READY, INDETERMINATE, WARNING, NOT_APPLICABLE)")
    private ReadinessStatus status;

    @Schema(description = "Dấu vân tay trạng thái (Fingerprint hash) phản ánh toàn bộ state nguồn")
    private String fingerprint;

    @Schema(description = "Thời hạn hiệu lực của kết quả đánh giá (Freshness/Valid-until)")
    private Instant validUntil;

    @Schema(description = "Danh sách chi tiết bằng chứng và hướng dẫn khắc phục của từng vấn đề")
    private List<ReadinessIssueDetail> details = new ArrayList<>();
}
