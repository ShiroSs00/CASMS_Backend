package com.casms.ship.dto.request;

import com.casms.ship.dto.PortCoordinateSource;
import com.casms.ship.enums.PortLocationType;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.Set;

@Data
@Schema(
        description =
                "Yêu cầu cập nhật thông tin cảng. Lưu ý: portCode và countryCode là bất biến; trạng"
                    + " thái cảng được quản lý qua endpoint activate/deactivate.")
public class PortUpdateRequest {

    @Schema(description = "Tên cảng", example = "Cảng Yokohama Quốc tế")
    @Size(max = 200, message = "Port name must not exceed 200 characters")
    private String portName;

    @Schema(description = "Mô tả cảng", example = "Bến cảng du thuyền quốc tế Osanbashi, Yokohama")
    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    @Schema(description = "ID điểm đến liên kết", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID destinationId;

    @Schema(description = "Thay toàn bộ danh sách địa điểm mà cảng có thể phục vụ. Bỏ trống trường để giữ nguyên danh sách.")
    @Size(max = 100, message = "A port can serve at most 100 destinations")
    private Set<UUID> servedDestinationIds;

    @Schema(description = "Vĩ độ", example = "35.4522")
    @DecimalMin("-90")
    @DecimalMax("90")
    private BigDecimal latitude;

    @Schema(
            description =
                    "Chỉ gửi cùng đủ latitude/longitude khi chọn mới kết quả Geocoding. Bỏ trường"
                        + " này ở PATCH thông tin khác để giữ nguyên nguồn, kể cả legacy null.")
    @Valid
    private PortCoordinateSource coordinateSource;

    @Schema(description = "Kinh độ", example = "139.6486")
    @DecimalMin("-180")
    @DecimalMax("180")
    private BigDecimal longitude;

    @Schema(description = "Múi giờ (IANA time zone)", example = "Asia/Tokyo")
    @Size(max = 100, message = "Time zone ID must not exceed 100 characters")
    private String timeZoneId;

    @Schema(description = "Loại vị trí cảng/neo đậu (PORT, PIER, ANCHORAGE)", example = "PORT")
    private PortLocationType locationType;
}
