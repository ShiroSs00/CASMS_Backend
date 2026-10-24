package com.casms.ship.dto.request;

import com.casms.ship.enums.PortStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Schema(description = "Yêu cầu tạo mới cảng")
public class PortCreateRequest {

    @Schema(description = "Mã cảng (duy nhất)", example = "JPYOK", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Port code is required")
    @Size(max = 50, message = "Port code must not exceed 50 characters")
    private String portCode;

    @Schema(description = "Tên cảng", example = "Cảng Yokohama", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Port name is required")
    @Size(max = 200, message = "Port name must not exceed 200 characters")
    private String portName;

    @Schema(description = "Mã quốc gia (ISO 3166-1 alpha-2)", example = "JP", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Country code is required")
    @Size(min = 2, max = 2, message = "Country code must be 2 characters")
    private String countryCode;

    @Schema(description = "Múi giờ IANA (nếu để trống, hệ thống tự động suy luận theo mã quốc gia, ví dụ JP -> Asia/Tokyo)", example = "Asia/Tokyo")
    @Size(max = 100, message = "Time zone ID must not exceed 100 characters")
    private String timeZoneId;

    @Schema(description = "ID điểm đến liên kết", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID destinationId;

    @Schema(description = "Mô tả cảng", example = "Cảng du thuyền quốc tế Osanbashi")
    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    @Schema(description = "Vĩ độ", example = "35.4522")
    private BigDecimal latitude;

    @Schema(description = "Kinh độ", example = "139.6486")
    private BigDecimal longitude;

    @Schema(description = "Trạng thái ban đầu", example = "ACTIVE")
    private PortStatus status = PortStatus.ACTIVE;
}