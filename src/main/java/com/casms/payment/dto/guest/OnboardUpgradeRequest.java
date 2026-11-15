package com.casms.payment.dto.guest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class OnboardUpgradeRequest {

    @Schema(description = "ID tài khoản chi tiêu trên tàu Onboard Account", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Onboard account ID is required")
    private UUID onboardAccountId;

    @Schema(description = "Hình thức nâng hạng: CABIN_UPGRADE (Nâng hạng phòng) hoặc ITINERARY_EXTENSION (Kéo dài tour 2N1Đ -> 3N2Đ)", example = "CABIN_UPGRADE", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Upgrade type is required")
    private String upgradeType;

    @Schema(description = "ID phòng mới nâng hạng (tuỳ chọn nếu có quản lý phòng)")
    private UUID newCabinId;

    @Schema(description = "Tên / Số phòng cabin mới", example = "Suite President - Deck 8")
    private String newCabinNumber;

    @Schema(description = "Số đêm ở thêm nếu kéo dài tour (ví dụ: 1 đêm cho tour 3N2Đ)", example = "1")
    private Integer additionalNights;

    @Schema(description = "Số tiền phụ thu chênh lệch nâng hạng", requiredMode = Schema.RequiredMode.REQUIRED, example = "4500000")
    @NotNull(message = "Fare difference is required")
    @DecimalMin(value = "0.01", message = "Fare difference must be positive")
    private BigDecimal fareDifference;

    @Schema(description = "Đơn vị tiền tệ (VND hoặc USD)", example = "VND")
    private String currency = "VND";

    @Schema(description = "Ghi chú dịch vụ hoặc yêu cầu đặc biệt")
    private String notes;
}
