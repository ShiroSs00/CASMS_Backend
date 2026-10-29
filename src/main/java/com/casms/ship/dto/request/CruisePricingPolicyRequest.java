package com.casms.ship.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu cấu hình chính sách giá thương mại cho chuyến đi (BE-H01)")
public class CruisePricingPolicyRequest {

    @Schema(description = "Phiên bản chính sách giá (ví dụ: PRC-2026.10-V1). Tự động tạo nếu để trống", example = "PRC-2026.10-V1")
    private String policyVersion;

    @Builder.Default
    @Pattern(regexp = "^[A-Z]{3}$", message = "Mã tiền tệ phải gồm 3 chữ cái viết hoa (ví dụ: USD, VND)")
    @Schema(description = "Mã tiền tệ", example = "USD")
    private String currency = "USD";

    @DecimalMin(value = "0.0", message = "Giá vé phòng Interior không được âm")
    @Schema(description = "Giá cơ sở phòng Interior (USD). 0 hợp lệ (miễn phí/bao gồm), null nghĩa là chưa định giá", example = "300.00")
    private BigDecimal interiorBaseFare;

    @DecimalMin(value = "0.0", message = "Giá vé phòng Ocean View không được âm")
    @Schema(description = "Giá cơ sở phòng Ocean View (USD)", example = "450.00")
    private BigDecimal oceanViewBaseFare;

    @DecimalMin(value = "0.0", message = "Giá vé phòng Balcony không được âm")
    @Schema(description = "Giá cơ sở phòng Balcony (USD)", example = "650.00")
    private BigDecimal balconyBaseFare;

    @DecimalMin(value = "0.0", message = "Giá vé phòng Suite không được âm")
    @Schema(description = "Giá cơ sở phòng Suite (USD)", example = "1100.00")
    private BigDecimal suiteBaseFare;

    @Builder.Default
    @DecimalMin(value = "0.0", message = "Cảng phí mỗi khách không được âm")
    @Schema(description = "Cảng phí thu trên mỗi hành khách (USD)", example = "50.00")
    private BigDecimal portFeePerPassenger = BigDecimal.ZERO;

    @Builder.Default
    @DecimalMin(value = "0.0", message = "Thuế suất không được âm")
    @DecimalMax(value = "1.0", message = "Thuế suất tối đa là 1.0 (100%)")
    @Schema(description = "Tỷ lệ thuế áp dụng (ví dụ: 0.08 tương đương 8%)", example = "0.08")
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Builder.Default
    @DecimalMin(value = "0.0", message = "Tỷ lệ đặt cọc không được âm")
    @DecimalMax(value = "1.0", message = "Tỷ lệ đặt cọc tối đa là 1.0 (100%)")
    @Schema(description = "Tỷ lệ đặt cọc bắt buộc (ví dụ: 0.30 tương đương 30%)", example = "0.30")
    private BigDecimal depositPercentage = BigDecimal.valueOf(0.30);

    @Builder.Default
    @Min(value = 0, message = "Số ngày hạn tất toán cuối cùng không được âm")
    @Schema(description = "Số ngày trước khởi hành cần thanh toán toàn bộ tiền vé còn lại", example = "14")
    private int finalPaymentDueDays = 14;

    @Builder.Default
    @Min(value = 0, message = "Số ngày cutoff thanh toán đủ 100% không được âm")
    @Schema(description = "Khoảng ngày cận khởi hành bắt buộc thanh toán 100% không cho đặt cọc", example = "7")
    private int fullPaymentCutoffDays = 7;

    @Schema(description = "Điều khoản và chính sách hủy vé / hoàn tiền chi tiết",
            example = "Hủy trước 30 ngày hoàn 100%; hủy 14-29 ngày hoàn 50%; dưới 14 ngày không hoàn tiền.")
    private String cancellationPolicy;

    @Min(0)
    private Long expectedRevision;

    /** Retired input is rejected instead of silently enabling approval. */
    @com.fasterxml.jackson.annotation.JsonProperty("autoApprove")
    public void rejectAutoApprove(Boolean ignored) {
        if(Boolean.TRUE.equals(ignored))throw new IllegalArgumentException("AUTO_APPROVAL_RETIRED");
    }
}
