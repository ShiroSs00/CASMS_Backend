package com.casms.ship.dto.request;

import com.casms.ship.enums.ItineraryType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Schema(description = "Yêu cầu tạo mới lịch trình")
public class ItineraryCreateRequest {

    @Schema(description = "ID của chuyến đi (Cruise)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Cruise ID is required")
    private UUID cruiseId;

    @Schema(description = "Tên lịch trình (Nếu để trống, tự động sinh theo tên chuyến đi)", example = "Lịch trình Hạ Long 3N2Đ")
    @Size(max = 200, message = "Itinerary name must not exceed 200 characters")
    private String itineraryName;

    @Schema(description = "Ngày bắt đầu (Nếu để trống, tự động lấy từ ngày khởi hành của chuyến đi)", example = "2026-01-15")
    private LocalDate startDate;

    @Schema(description = "Ngày kết thúc (Nếu để trống, tự động lấy từ ngày kết thúc của chuyến đi)", example = "2026-01-17")
    private LocalDate endDate;

    @Schema(description = "Loại lịch trình (Nếu để trống, tự động suy luận: ROUND_TRIP nếu cảng đi = cảng về, ngược lại ONE_WAY)", example = "ROUND_TRIP")
    private ItineraryType itineraryType;

}
