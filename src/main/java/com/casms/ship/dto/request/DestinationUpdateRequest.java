package com.casms.ship.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Yêu cầu cập nhật thông tin điểm đến. Lưu ý: countryCode là bất biến; chỉ destinationName và description có thể cập nhật. Trạng thái hoạt động quản lý qua activate/deactivate.")
public class DestinationUpdateRequest {

    @Schema(description = "Tên điểm đến", example = "Vịnh Hạ Long")
    @Size(max = 200, message = "Destination name must not exceed 200 characters")
    private String destinationName;

    @Schema(description = "Mô tả điểm đến", example = "Vịnh Hạ Long - Di sản thiên nhiên thế giới")
    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;
}