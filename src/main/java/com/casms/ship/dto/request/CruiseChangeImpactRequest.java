package com.casms.ship.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
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
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Yêu cầu đánh giá tác động thay đổi lịch trình, tàu hoặc cấu hình chuyến đi (C11, C17)")
public class CruiseChangeImpactRequest {

    @Schema(description = "Thời điểm khởi hành mới dự kiến")
    private Instant newDepartureDatetime;

    @Schema(description = "Thời điểm trở về mới dự kiến")
    private Instant newReturnDatetime;

    @Schema(description = "ID tàu mới dự kiến gán")
    private UUID newShipId;

    @Schema(description = "ID cảng khởi hành mới (nếu có)")
    private UUID newDeparturePortId;

    @Schema(description = "ID cảng trở về mới (nếu có)")
    private UUID newReturnPortId;

    @Schema(description = "Định danh sự kiện thay đổi (Event Identity phục vụ deduplication / C17)")
    private String eventIdentity;

    @Schema(description = "Revision mong đợi của Cruise để chống ghi đè (Optimistic Lock / C11)", example = "1")
    private Long expectedRevision;

    @Schema(description = "Lý do thay đổi cấu hình hoặc lịch trình", example = "Điều chỉnh theo yêu cầu kỹ thuật luồng hàng hải")
    private String reason;
}
