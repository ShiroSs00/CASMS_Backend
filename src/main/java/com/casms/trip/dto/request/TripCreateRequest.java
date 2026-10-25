package com.casms.trip.dto.request;

import com.casms.ship.enums.ItineraryType;
import com.casms.trip.enums.TripDurationGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Yêu cầu tạo mới khung Trip")
public class TripCreateRequest {

    @NotBlank(message = "Trip name is required")
    @Size(max = 200, message = "Trip name must not exceed 200 characters")
    @Schema(description = "Tên hành trình định tuyến Trip", example = "Hải trình Di sản Vịnh Bắc Bộ")
    private String name;

    @Schema(description = "Mô tả chi tiết hành trình")
    private String description;

    @Schema(description = "Nhóm thời lượng (SHORT: 1-2 ngày, mặc định 1; LONG: 3-5 ngày, mặc định 3)", example = "SHORT")
    private TripDurationGroup durationGroup;

    @Min(value = 1, message = "Duration days must be at least 1")
    @Schema(description = "Số ngày (N) - không bắt buộc ở form tạo, BE tự gán theo durationGroup nếu để trống", example = "2")
    private Integer durationDays;

    @Min(value = 0, message = "Duration nights must be at least 0")
    @Schema(description = "Số đêm (tùy chọn, mặc định N-1 hoặc 0 nếu 1 ngày)", example = "1")
    private Integer durationNights;

    @Schema(description = "Loại chuyến (ROUND_TRIP hoặc ONE_WAY) - xác nhận khi tuyến đủ thông tin, không bắt chọn ở form đầu", example = "ROUND_TRIP")
    private ItineraryType tripType;

    @Schema(description = "Phạm vi chuyến đi (DOMESTIC | INTERNATIONAL)", example = "DOMESTIC")
    private String plannedScope = "DOMESTIC";

    @Schema(description = "Danh sách mã miền (nếu phạm vi là DOMESTIC)", example = "[\"BAC_BO\", \"TRUNG_BO\"]")
    private List<String> domesticRegions;

    @Schema(description = "Danh sách mã quốc gia/vùng lãnh thổ ISO-2 (nếu phạm vi là INTERNATIONAL)", example = "[\"VN\", \"TH\"]")
    private List<String> countryCodes;

    @Schema(description = "Yêu cầu kỹ thuật tàu (ghi chú tự do, không ràng buộc FK Fleet)", example = "Tàu tải trọng tối đa 50,000 GT, mớn nước < 8.5m")
    private String shipRequirements;

    @Schema(description = "Các điểm đến dự kiến (candidate destinations)")
    private String candidateDestinations;
}
