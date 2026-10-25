package com.casms.trip.dto.response;

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
@Schema(description = "Nguồn hoạt động trên tàu có thể tái sử dụng cho lập kế hoạch hành trình")
public class PlanningSourceActivityResponse {

    @Schema(description = "ID của hoạt động nguồn")
    private UUID activityId;

    @Schema(description = "ID nguồn hoạt động (alias)")
    private UUID sourceActivityId;

    @Schema(description = "Mã hoạt động nguồn")
    private String activityCode;

    @Schema(description = "Tên hoạt động")
    private String activityName;

    @Schema(description = "Mô tả hoạt động")
    private String description;

    @Schema(description = "Vị trí / Địa điểm tổ chức")
    private String location;

    @Schema(description = "Giá tham khảo")
    private BigDecimal price;

    @Schema(description = "Đơn vị tiền tệ")
    private String currencyCode;
}
