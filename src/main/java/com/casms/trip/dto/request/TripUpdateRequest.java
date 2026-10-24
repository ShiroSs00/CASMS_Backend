package com.casms.trip.dto.request;

import com.casms.ship.enums.ItineraryType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Yêu cầu cập nhật thông tin khung Trip")
public class TripUpdateRequest {

    @Size(max = 200, message = "Trip name must not exceed 200 characters")
    @Schema(description = "Tên hành trình định tuyến Trip")
    private String name;

    @Schema(description = "Mô tả chi tiết hành trình")
    private String description;

    @Min(value = 1, message = "Duration days must be at least 1")
    @Schema(description = "Số ngày (chỉ sửa được khi chưa có phiên bản nào PUBLISHED)")
    private Integer durationDays;

    @Min(value = 0, message = "Duration nights must be at least 0")
    @Schema(description = "Số đêm (chỉ sửa được khi chưa có phiên bản nào PUBLISHED)")
    private Integer durationNights;

    @Schema(description = "Loại chuyến (ROUND_TRIP hoặc ONE_WAY)")
    private ItineraryType tripType;

    @Schema(description = "Phạm vi chuyến đi (DOMESTIC, REGIONAL, INTERNATIONAL)")
    private String plannedScope;

    @Schema(description = "Yêu cầu kỹ thuật tàu (ghi chú tự do)")
    private String shipRequirements;

    @Schema(description = "Các điểm đến dự kiến (candidate destinations)")
    private String candidateDestinations;
}
