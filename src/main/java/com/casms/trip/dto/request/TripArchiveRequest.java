package com.casms.trip.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu lưu trữ Trip kèm revision kiểm tra xung đột")
public class TripArchiveRequest {

    @Schema(description = "Revision kỳ vọng của Trip để kiểm tra xung đột cạnh tranh", example = "1")
    private Long expectedRevision;

    @Schema(description = "Lý do lưu trữ Trip", example = "Ngưng khai thác lịch trình này")
    private String reason;
}
