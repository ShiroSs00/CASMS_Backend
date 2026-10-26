package com.casms.ship.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu tính toán xem trước lịch trình thực tế từ TripScheduleVersion (C02)")
public class CruiseSchedulePreviewRequest {

    @NotNull(message = "sourceVersionId là bắt buộc")
    @Schema(description = "ID của phiên bản TripScheduleVersion nguồn đã xuất bản (PUBLISHED)", required = true)
    private UUID sourceVersionId;

    @NotNull(message = "departureDatetime là bắt buộc")
    @Schema(description = "Thời điểm khởi hành thực tế dự kiến (ISO-8601)", example = "2026-11-01T08:00:00Z", required = true)
    private Instant departureDatetime;
}
