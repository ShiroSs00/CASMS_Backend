package com.casms.trip.dto.request;

import com.casms.ship.enums.ItineraryDayType;
import com.casms.trip.enums.TripDayManageAction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Yêu cầu thêm hoặc xóa ngày an toàn trong lịch trình")
public class TripDayManageRequest {

    @NotNull(message = "expectedTripRevision is required")
    @Schema(description = "Revision mong đợi của Trip", example = "1")
    private Long expectedTripRevision;

    @NotNull(message = "expectedVersionRevision is required")
    @Schema(description = "Revision mong đợi của phiên bản lịch trình", example = "1")
    private Long expectedVersionRevision;

    @NotNull(message = "action is required (ADD | REMOVE)")
    @Schema(description = "Hành động thực hiện (ADD hoặc REMOVE)", example = "ADD")
    private TripDayManageAction action;

    @Schema(description = "Loại ngày khi thêm (PORT_DAY hoặc SEA_DAY, tùy chọn)", example = "PORT_DAY")
    private ItineraryDayType dayType;

    @Schema(description = "Số thứ tự ngày cần xóa khi action = REMOVE (mặc định xóa ngày cuối nếu không truyền)", example = "2")
    private Integer dayNumber;
}
