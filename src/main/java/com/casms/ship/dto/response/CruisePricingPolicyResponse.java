package com.casms.ship.dto.response;

import com.casms.ship.enums.PricingPolicyStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin chi tiết chính sách giá thương mại của chuyến đi (BE-H01)")
public class CruisePricingPolicyResponse {

    @Schema(description = "ID chính sách giá")
    private UUID pricingPolicyId;

    @Schema(description = "ID chuyến đi")
    private UUID cruiseId;

    @Schema(description = "Mã chuyến đi")
    private String cruiseCode;

    @Schema(description = "Tên chuyến đi")
    private String cruiseName;

    @Schema(description = "Phiên bản chính sách giá hiệu lực")
    private String policyVersion;

    @Schema(description = "Trạng thái chính sách giá (DRAFT, APPROVED, ARCHIVED)")
    private PricingPolicyStatus status;

    @Schema(description = "Mã tiền tệ áp dụng (USD, VND,...)")
    private String currency;

    @Schema(description = "Giá cơ sở phòng Interior (null nếu chưa định giá)")
    private BigDecimal interiorBaseFare;

    @Schema(description = "Giá cơ sở phòng Ocean View (null nếu chưa định giá)")
    private BigDecimal oceanViewBaseFare;

    @Schema(description = "Giá cơ sở phòng Balcony (null nếu chưa định giá)")
    private BigDecimal balconyBaseFare;

    @Schema(description = "Giá cơ sở phòng Suite (null nếu chưa định giá)")
    private BigDecimal suiteBaseFare;

    @Schema(description = "Cảng phí mỗi khách")
    private BigDecimal portFeePerPassenger;

    @Schema(description = "Thuế suất áp dụng")
    private BigDecimal taxRate;

    @Schema(description = "Tỷ lệ cọc tối thiểu")
    private BigDecimal depositPercentage;

    @Schema(description = "Số ngày trước khởi hành cần tất toán tiền vé")
    private int finalPaymentDueDays;

    @Schema(description = "Số ngày cận khởi hành bắt buộc thanh toán 100%")
    private int fullPaymentCutoffDays;

    @Schema(description = "Điều khoản và chính sách hủy vé / hoàn cọc")
    private String cancellationPolicy;

    @Schema(description = "ID người phê duyệt")
    private UUID approvedBy;

    @Schema(description = "Tên người phê duyệt")
    private String approvedByName;

    @Schema(description = "Thời điểm phê duyệt")
    private Instant approvedAt;

    @Schema(description = "Thời điểm tạo")
    private Instant createdAt;

    @Schema(description = "Thời điểm cập nhật lần cuối")
    private Instant updatedAt;
}
