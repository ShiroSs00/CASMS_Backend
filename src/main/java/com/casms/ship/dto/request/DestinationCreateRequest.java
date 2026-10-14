package com.casms.ship.dto.request;

import com.casms.ship.enums.DestinationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Yêu cầu tạo mới điểm đến")
public class DestinationCreateRequest {

    @Schema(description = "Tên điểm đến", example = "Hạ Long", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Destination name is required")
    @Size(max = 200, message = "Destination name must not exceed 200 characters")
    private String destinationName;

    @Schema(description = "Mã quốc gia (ISO 3166-1 alpha-2)", example = "VN", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Country code is required")
    @Size(min = 2, max = 2, message = "Country code must be 2 characters")
    private String countryCode;

    @Schema(description = "Mô tả điểm đến", example = "Vịnh Hạ Long - Di sản thế giới")
    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    @Schema(description = "Trạng thái ban đầu", example = "ACTIVE")
    private DestinationStatus status = DestinationStatus.ACTIVE;
}