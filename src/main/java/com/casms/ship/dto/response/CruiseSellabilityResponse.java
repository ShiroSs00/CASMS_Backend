package com.casms.ship.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Mô hình trạng thái mở bán công khai authoritative (Public Sellability Read Model - BE-15)")
public class CruiseSellabilityResponse {

    @Schema(description = "ID chuyến đi")
    private UUID cruiseId;

    @Schema(description = "Mã chuyến đi")
    private String cruiseCode;

    @Schema(description = "Tên chuyến đi")
    private String cruiseName;

    @Schema(description = "Thời gian khởi hành")
    private Instant departureDatetime;

    @Schema(description = "Thời gian kết thúc")
    private Instant returnDatetime;

    @Schema(description = "Có thể mở bán/nhận booking ngay lúc này hay không (AND Cruise status, Itinerary OPEN_FOR_SALE, not stopped, departure in future, inventory > 0)")
    private boolean sellable;

    @Schema(description = "Trạng thái mở bán thống nhất duy nhất (OPEN_FOR_SALE, STOPPED, NOT_YET_OPEN, DEPARTED, SOLD_OUT, DRAFT, CANCELLED)")
    private String saleState;

    @Schema(description = "Lý do tạm dừng mở bán (nếu có)")
    private String stopReason;

    @Schema(description = "Thời điểm tạm dừng mở bán")
    private Instant stoppedAt;

    @Schema(description = "Tổng sức chứa thiết kế của tàu (Physical Capacity)")
    private int physicalCapacity;

    @Schema(description = "Tổng sức chứa của tàu (tương đương physicalCapacity)")
    private int totalCapacity;

    @Schema(description = "Tổng tải trọng phòng khả dụng thương mại (AVAILABLE cabins trừ booked/held)")
    private long sellableInventory;

    @Schema(description = "Số lượng chỗ còn trống (tương đương sellableInventory)")
    private long remainingCapacity;

    @Schema(description = "Phiên bản chính sách giá đang áp dụng")
    private String pricingPolicyVersion;

    @Schema(description = "Đơn vị tiền tệ chính sách giá")
    private String currency;

    @Schema(description = "Tỷ lệ đặt cọc yêu cầu (%)")
    private BigDecimal depositPercentage;

    @Schema(description = "Số ngày hạn chót bắt buộc thanh toán toàn bộ trước khởi hành")
    private Integer finalPaymentCutoffDays;

    @Builder.Default
    @Schema(description = "Danh sách tình trạng mở bán theo từng hạng phòng")
    private List<CabinTypeSellabilityItem> cabinTypes = new ArrayList<>();

    @Builder.Default
    @Schema(description = "Danh sách gói tour/dịch vụ khả dụng đính kèm")
    private List<PackageSellabilityItem> availablePackages = new ArrayList<>();
}
