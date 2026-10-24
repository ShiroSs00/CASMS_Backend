package com.casms.trip.dto.request;

import com.casms.ship.enums.ItineraryType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Yêu cầu tạo mới khung Trip")
public class TripCreateRequest {

    @NotBlank(message = "Trip name is required")
    @Size(max = 200, message = "Trip name must not exceed 200 characters")
    @Schema(description = "Tên hành trình định tuyến Trip", example = "Hải trình Di sản Vịnh Bắc Bộ 4N3Đ")
    private String name;

    @Schema(description = "Mô tả chi tiết hành trình")
    private String description;

    @NotNull(message = "Duration days is required")
    @Min(value = 1, message = "Duration days must be at least 1")
    @Schema(description = "Số ngày (N)", example = "4")
    private Integer durationDays;

    @Min(value = 0, message = "Duration nights must be at least 0")
    @Schema(description = "Số đêm (độc lập, gợi ý N-1, có thể tùy chỉnh)", example = "3")
    private Integer durationNights;

    @NotNull(message = "Trip type is required")
    @Schema(description = "Loại chuyến (ROUND_TRIP hoặc ONE_WAY)", example = "ROUND_TRIP")
    private ItineraryType tripType;

    @Schema(description = "Phạm vi chuyến đi (DOMESTIC, REGIONAL, INTERNATIONAL)", example = "DOMESTIC")
    private String plannedScope;

    @Schema(description = "Yêu cầu kỹ thuật tàu (ghi chú tự do, không ràng buộc FK Fleet)", example = "Tàu tải trọng tối đa 50,000 GT, mớn nước < 8.5m")
    private String shipRequirements;

    @Schema(description = "Các điểm đến dự kiến (candidate destinations)")
    private String candidateDestinations;
}
