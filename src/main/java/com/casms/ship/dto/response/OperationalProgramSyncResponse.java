package com.casms.ship.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Kết quả đồng bộ chương trình vận hành (Onboard Activities, Shore Excursions) từ Trip Plan Entry")
public class OperationalProgramSyncResponse {

    @Schema(description = "ID chuyến đi")
    private UUID cruiseId;

    @Schema(description = "Số lượng hoạt động trên tàu (Onboard Activities) đã đồng bộ")
    private int onboardActivitiesCount;

    @Schema(description = "Số lượng tour tham quan (Shore Excursions) đã đồng bộ")
    private int shoreExcursionsCount;

    @Builder.Default
    @Schema(description = "Danh sách cảnh báo trong quá trình đồng bộ")
    private List<String> warnings = new ArrayList<>();

    @Schema(description = "Thời điểm đồng bộ (ISO-8601)")
    private Instant syncedAt;
}
