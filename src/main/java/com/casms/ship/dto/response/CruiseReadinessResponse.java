package com.casms.ship.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Schema(description = "Kết quả kiểm tra tính sẵn sàng vận hành của chuyến đi (Operational Readiness)")
public class CruiseReadinessResponse {

    @Schema(description = "ID chuyến đi", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID cruiseId;

    @Schema(description = "Đã sẵn sàng vận hành / phát hành hay chưa", example = "true")
    private boolean ready;

    @Schema(description = "Đã được gán tàu hay chưa", example = "true")
    private boolean shipAssigned;

    @Schema(description = "Tàu được gán có đang ở trạng thái ACTIVE hay không", example = "true")
    private boolean shipActive;

    @Schema(description = "Tàu đã có boong và cabin cấu hình hay chưa", example = "true")
    private boolean shipConfigurationValid;

    @Schema(description = "Lịch trình hợp lệ (thời điểm về sau thời điểm đi)", example = "true")
    private boolean scheduleValid;

    @Schema(description = "Cảng đi và cảng đến hợp lệ và đang ACTIVE", example = "true")
    private boolean portsActive;

    @Schema(description = "Đã có hải trình (Itinerary) được liên kết", example = "true")
    private boolean itineraryConfigured;

    @Schema(description = "Danh sách các thông báo hoặc điểm chưa hoàn tất")
    private List<String> issues;
}
