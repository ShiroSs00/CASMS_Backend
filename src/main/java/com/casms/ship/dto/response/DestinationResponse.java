package com.casms.ship.dto.response;

import com.casms.ship.enums.DestinationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Schema(description = "Thông tin chi tiết điểm đến")
public class DestinationResponse {

    @Schema(description = "ID duy nhất của điểm đến", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID destinationId;

    @Schema(description = "Tên điểm đến", example = "Hạ Long")
    private String destinationName;

    @Schema(description = "Mã quốc gia (ISO 3166-1 alpha-2)", example = "VN")
    private String countryCode;

    @Schema(description = "Mô tả điểm đến", example = "Vịnh Hạ Long - Di sản thiên nhiên thế giới")
    private String description;

    @Schema(description = "Trạng thái điểm đến", example = "ACTIVE")
    private DestinationStatus status;

    @Schema(description = "Danh sách cảng thuộc điểm đến này")
    private List<PortSummaryResponse> ports;

    @Schema(description = "Thời điểm tạo")
    private Instant createdAt;

    @Schema(description = "Thời điểm cập nhật gần nhất")
    private Instant updatedAt;
}