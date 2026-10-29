package com.casms.ship.dto.response;

import com.casms.ship.enums.ReadinessStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Kết quả đánh giá tính sẵn sàng mở bán của chuyến đi (Sale Readiness Gate - BE-08/BE-09)")
public class CruiseSaleReadinessResponse {

    @Schema(description = "ID chuyến đi")
    private UUID cruiseId;

    @Schema(description = "Loại cổng kiểm tra (SALE_GATE)")
    @Builder.Default
    private String gateType = "SALE_GATE";

    @Schema(description = "Phiên bản chính sách áp dụng")
    @Builder.Default
    private String policyVersion = "v3.0-2026";

    @Schema(description = "Chuyến đi đã đạt điều kiện mở bán thương mại hay chưa")
    private boolean ready;

    @Schema(description = "Tình trạng sẵn sàng vận hành tham chiếu")
    private boolean operationalReady;

    @Schema(description = "Đã cấu hình các gói dịch vụ đính kèm hay chưa")
    private boolean packagesConfigured;

    @Schema(description = "Cho phép mở bán chỉ phòng (cabin-only sale) khi không có gói tour")
    private boolean cabinOnlySalePermitted;

    @Schema(description = "Số lượng gói dịch vụ đang hoạt động")
    private long activePackagesCount;

    @Schema(description = "Còn chỗ trống khả dụng để bán hay không")
    private boolean inventoryAvailable;

    @Schema(description = "Sức chứa hành khách còn lại")
    private long remainingCapacity;

    @Schema(description = "Sức chứa vật lý tối đa của tàu")
    private int physicalCapacity;

    @Schema(description = "Thời điểm khởi hành nằm trong tương lai")
    private boolean departureInFuture;

    @Schema(description = "Giá và tiền tệ đã được cấu hình hợp lệ")
    private boolean pricingConfigured;

    @Builder.Default
    @Schema(description = "Danh sách mã rào cản ngăn chặn mở bán")
    private List<String> blockers = new ArrayList<>();

    @Builder.Default
    @Schema(description = "Danh sách cảnh báo cần lưu ý")
    private List<String> warnings = new ArrayList<>();

    @Builder.Default
    @Schema(description = "Danh sách mô tả các vấn đề phát hiện được")
    private List<String> issues = new ArrayList<>();

    @Schema(description = "Thời điểm thực hiện đánh giá")
    private Instant evaluatedAt;

    @Schema(description = "Thời hạn hiệu lực của kết quả đánh giá (Freshness/Valid-until)")
    private Instant validUntil;

    @Schema(description = "Revision của Cruise tại thời điểm đánh giá")
    private Long cruiseRevision;

    @Schema(description = "Trạng thái chi tiết (READY, NOT_READY, INDETERMINATE, WARNING)")
    private ReadinessStatus status;

    @Schema(description = "Dấu vân tay trạng thái (Fingerprint hash) phản ánh toàn bộ state nguồn")
    private String fingerprint;

    @Schema(description = "Phiên bản hoặc hash của thông tin giá tại thời điểm đánh giá")
    private String priceVersion;

    @Schema(description = "Phiên bản hoặc hash của thông tin tàu tại thời điểm đánh giá")
    private String shipVersion;

    @Schema(description = "Phiên bản hoặc hash của thông tin nhà cung cấp tại thời điểm đánh giá")
    private String supplierVersion;

    @Builder.Default
    @Schema(description = "Danh sách chi tiết bằng chứng và hướng dẫn khắc phục của từng vấn đề")
    private List<ReadinessIssueDetail> details = new ArrayList<>();
}
