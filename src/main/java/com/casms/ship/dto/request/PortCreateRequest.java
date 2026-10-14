package com.casms.ship.dto.request;

import com.casms.ship.enums.PortStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Yêu cầu tạo mới cảng")
public class PortCreateRequest {

    @Schema(description = "Mã cảng (duy nhất)", example = "VN-HPH", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Port code is required")
    @Size(max = 50, message = "Port code must not exceed 50 characters")
    private String portCode;

    @Schema(description = "Tên cảng", example = "Cảng Hải Phòng", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Port name is required")
    @Size(max = 200, message = "Port name must not exceed 200 characters")
    private String portName;

    @Schema(description = "Mã quốc gia (ISO 3166-1 alpha-2)", example = "VN", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Country code is required")
    @Size(min = 2, max = 2, message = "Country code must be 2 characters")
    private String countryCode;

    @Schema(description = "Múi giờ (IANA time zone)", example = "Asia/Ho_Chi_Minh", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Time zone ID is required")
    @Size(max = 100, message = "Time zone ID must not exceed 100 characters")
    private String timeZoneId;

    @Schema(description = "Vĩ độ", example = "20.8449")
    private BigDecimal latitude;

    @Schema(description = "Kinh độ", example = "106.6881")
    private BigDecimal longitude;

    @Schema(description = "Trạng thái ban đầu", example = "ACTIVE")
    private PortStatus status = PortStatus.ACTIVE;
}