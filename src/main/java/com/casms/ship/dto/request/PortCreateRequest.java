package com.casms.ship.dto.request;

import com.casms.ship.enums.PortLocationType;
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

    @Schema(description = "Múi giờ cảng IANA bắt buộc do người dùng xác nhận; không tự gán theo quốc gia", example = "Asia/Tokyo")
    @NotBlank(message = "Port time zone is required")
    @Size(max = 100, message = "Time zone ID must not exceed 100 characters")
    private String timeZoneId;

    @Schema(description = "ID điểm đến liên kết", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID destinationId;

    @Schema(description = "Mô tả cảng", example = "Cảng du thuyền quốc tế Osanbashi")
    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    @Schema(description = "Vĩ độ", example = "35.4522")
    @jakarta.validation.constraints.DecimalMin("-90")
    @jakarta.validation.constraints.DecimalMax("90")
    @jakarta.validation.constraints.NotNull(message = "Latitude is required")
    private BigDecimal latitude;

    @Schema(description = "Nguồn tọa độ thành phố/đảo bắt buộc khi tạo Port. provider=OPEN_METEO_GEOCODING, dataset=GEONAMES, coordinateReferenceType=GEOCODED_LOCALITY; recordedAt do server ghi.")
    @jakarta.validation.constraints.NotNull(message = "Select a Geocoding result for a new port")
    @jakarta.validation.Valid
    private com.casms.ship.dto.PortCoordinateSource coordinateSource;


    @Schema(description = "Kinh độ", example = "139.6486")
    @jakarta.validation.constraints.DecimalMin("-180")
    @jakarta.validation.constraints.DecimalMax("180")
    @jakarta.validation.constraints.NotNull(message = "Longitude is required")
    private BigDecimal longitude;

    @Schema(description = "Loại vị trí cảng/neo đậu (PORT, PIER, ANCHORAGE)", example = "PORT")
    private PortLocationType locationType = PortLocationType.PORT;

    @Schema(description = "Trạng thái ban đầu", example = "ACTIVE")
    private PortStatus status = PortStatus.ACTIVE;
}
