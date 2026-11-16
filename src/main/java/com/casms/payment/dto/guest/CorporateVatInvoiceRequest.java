package com.casms.payment.dto.guest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CorporateVatInvoiceRequest {

    @Schema(description = "ID đơn đặt tour hoặc giao dịch", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Booking ID is required")
    private UUID bookingId;

    @Schema(description = "Tên đầy đủ của công ty / doanh nghiệp", example = "CÔNG TY CỔ PHẦN DU LỊCH VÀ DU THUYỀN VIỆT NAM", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Company name is required")
    private String companyName;

    @Schema(description = "Mã số thuế doanh nghiệp (10 hoặc 13 chữ số)", example = "0312345678", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Tax code is required")
    private String taxCode;

    @Schema(description = "Địa chỉ trụ sở công ty theo đăng ký kinh doanh", example = "Tầng 5, Tòa nhà Landmark, Quận 1, TP. Hồ Chí Minh", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Company address is required")
    private String companyAddress;

    @Schema(description = "Email nhận hoá đơn điện tử (.pdf & .xml)", example = "ketoan@congty.com", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "VAT receiver email is required")
    private String vatEmail;

    @Schema(description = "Ghi chú bổ sung trên hoá đơn (nếu có)")
    private String notes;
}

