package com.casms.trip.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "Yêu cầu lưu bản nháp mẫu lịch trình (Aggregate PUT với kiểm soát optimistic lock qua expectedRevision)")
public class ScheduleDraftSaveRequest {

    @NotNull(message = "Expected revision is required for optimistic concurrency check")
    @Schema(description = "Revision hiện tại client đang giữ để đối chiếu chống xung đột ghi đè", example = "1")
    private Long expectedRevision;

    @Valid
    @NotEmpty(message = "Day templates must not be empty")
    @Schema(description = "Danh sách các ngày tương đối (1..N)")
    private List<TripPlanDayDraftDto> days = new ArrayList<>();

    @Valid
    @Schema(description = "Danh sách điểm dừng cảng trong hành trình (hỗ trợ qua đêm xuyên ngày)")
    private List<TripPlanStopDraftDto> stops = new ArrayList<>();

    @Valid
    @Schema(description = "Danh sách mục kế hoạch (hoạt động trên tàu, tour trên bờ gắn với stop, hoặc khung giờ phục vụ)")
    private List<TripPlanEntryDraftDto> entries = new ArrayList<>();
}
