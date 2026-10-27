package com.casms.ship.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Tình trạng mở bán của gói tour đính kèm chuyến đi - BE-15")
public class PackageSellabilityItem {

    @Schema(description = "ID gói dịch vụ")
    private UUID tourPackageId;

    @Schema(description = "Mã gói dịch vụ")
    private String packageCode;

    @Schema(description = "Tên gói dịch vụ")
    private String packageName;

    @Schema(description = "Giá bán gói dịch vụ")
    private BigDecimal price;

    @Schema(description = "Đơn vị tiền tệ")
    private String currency;

    @Schema(description = "Là gói bắt buộc hay tùy chọn")
    private boolean mandatory;

    @Schema(description = "Loại quyền lợi (OPTIONAL_ADDON, INCLUDED, MANDATORY)")
    private String entitlementType;

    @Schema(description = "Có đang mở bán được không (Cruise sellable AND package active)")
    private boolean sellable;
}
