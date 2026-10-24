package com.casms.trip.dto.request;

import com.casms.ship.enums.CruiseStopArrivalMethod;
import com.casms.ship.enums.CruiseStopCallType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;
import java.util.UUID;

@Data
@Schema(description = "Dự thảo điểm dừng cảng trong kế hoạch (hỗ trợ qua đêm xuyên ngày)")
public class TripPlanStopDraftDto {

    @NotBlank(message = "Client key is required for correlation")
    @Schema(description = "Khóa tương quan do client sinh (duy nhất trong version)", example = "stop-hph-01")
    private String clientKey;

    @NotNull(message = "Port ID is required")
    @Schema(description = "ID cảng ghé thăm")
    private UUID portId;

    @Schema(description = "ID điểm đến liên kết (nếu có)")
    private UUID destinationId;

    @NotNull(message = "Stop order is required")
    @Schema(description = "Thứ tự cập cảng trong hành trình", example = "1")
    private Integer stopOrder;

    @NotNull(message = "Arrival day number is required")
    @Schema(description = "Ngày cập cảng tương đối (1..N)", example = "1")
    private Integer arrivalDayNumber;

    @Schema(description = "Giờ cập cảng dự kiến tương đối", example = "08:00:00")
    private LocalTime plannedArrivalTime;

    @NotNull(message = "Departure day number is required")
    @Schema(description = "Ngày rời cảng tương đối (>= arrivalDayNumber)", example = "1")
    private Integer departureDayNumber;

    @Schema(description = "Giờ rời cảng dự kiến tương đối", example = "18:00:00")
    private LocalTime plannedDepartureTime;

    @Schema(description = "Loại ghé cảng (NORMAL, LATE_NIGHT, OVERNIGHT)", example = "NORMAL")
    private CruiseStopCallType callType = CruiseStopCallType.NORMAL;

    @Schema(description = "Phương thức cập bến (BERTH, TENDER)", example = "BERTH")
    private CruiseStopArrivalMethod arrivalMethod = CruiseStopArrivalMethod.BERTH;

    @Schema(description = "Ngày tất cả lên tàu tương đối (thường là departureDayNumber)", example = "1")
    private Integer allAboardDayNumber;

    @Schema(description = "Giờ tất cả lên tàu tương đối (<= departureTime)", example = "17:30:00")
    private LocalTime allAboardTime;

    @Schema(description = "Đánh dấu là cảng đón khách (Embarkation)", example = "true")
    private Boolean isEmbarkation = false;

    @Schema(description = "Đánh dấu là cảng trả khách (Disembarkation)", example = "false")
    private Boolean isDisembarkation = false;
}
